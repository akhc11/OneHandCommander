package com.example.onehandcommander.ui.overlays

import android.content.Context
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.core.GestureDispatcher
import com.example.onehandcommander.utils.Constants
import com.example.onehandcommander.utils.ErrorHandler
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration
import kotlin.math.hypot

/**
 * 高パフォーマンス・高精度タッチパッド・オーバーレイ
 * - ダーティチェックによる無駄な Binder IPC の完全排除
 * - 正確なジェスチャーステート（タップ / 長押しタップ / 長押しドラッグ）
 * - 初動・微小移動時の滑らかな追従
 * - 指の移動速度と距離・経過時間に忠実なドラッグジェスチャー生成
 */
class Touchpad(
    context: Context,
    windowManager: WindowManager,
    private val gestureDispatcher: GestureDispatcher
) : BaseOverlay(context, windowManager) {

    private enum class TouchState {
        IDLE,
        NORMAL_MOVING,     // 移動・タップ待機
        LONG_PRESSED_STILL,// 長押し成功・その場待機（指を離せば長押しタップ）
        DRAGGING           // 長押し後の移動（ドラッグジェスチャー記録中）
    }

    // --- UI Components ---
    private var cursorView: View? = null
    private lateinit var cursorParams: WindowManager.LayoutParams

    // --- Screen & Coordinates ---
    private var screenWidth = 0
    private var screenHeight = 0
    private var cursorX = 0f
    private var cursorY = 0f

    // 視覚的描画座標（Visual Lead 適用後の最新座標）
    private var visualCursorX = 0f
    private var visualCursorY = 0f

    // ダーティチェック用（無駄な WindowManager.updateViewLayout を抑止）
    private var lastRenderedPxX = Int.MIN_VALUE
    private var lastRenderedPxY = Int.MIN_VALUE

    // --- Velocity & Prediction ---
    private var velocityX = 0f
    private var velocityY = 0f
    private var lastEventTime = 0L
    private var isFirstMove = true

    // --- Settings Cache (ホットパスでの SharedPreferences アクセスを排除) ---
    private var cachedSpeed = Constants.Defaults.CURSOR_SPEED
    private var cachedThreshold = Constants.Defaults.CURSOR_THRESHOLD
    private var cachedLpDelay = Constants.Defaults.TOUCHPAD_LP_DELAY_MS
    private var cachedLpPlayPx = Constants.Defaults.TOUCHPAD_LP_PLAY_PX

    // --- Touch & Gesture Tracking ---
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var lastStillX = 0f
    private var lastStillY = 0f
    private var touchDownX = 0f
    private var touchDownY = 0f

    private var touchState = TouchState.IDLE
    private var longPressTriggerTimeMs = 0L

    // 実際の指の動きの軌跡を忠実に Accessibility Gesture Path に変換
    private val dragPath = Path()

    private val handler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    fun setScreenSize(w: Int, h: Int) {
        screenWidth = w
        screenHeight = h
    }

    fun updateSize() {
        val w = UiHelper.dpToPx(context, SavedData.getTouchpadWidth())
        val h = UiHelper.dpToPx(context, SavedData.getTouchpadHeight())
        val alpha = SavedData.getTouchpadAlpha()

        if (isVisible()) {
            params.width = w
            params.height = h
            params.x = SavedData.getTouchpadX()
            params.y = SavedData.getTouchpadY()
            overlayView?.alpha = UiHelper.percentToAlpha(alpha)
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        } else {
            overlayView?.alpha = UiHelper.percentToAlpha(alpha)
        }
    }

    override fun createView(): View {
        return LayoutInflater.from(context).inflate(R.layout.layout_touchpad, null).also { view ->
            view.alpha = UiHelper.percentToAlpha(SavedData.getTouchpadAlpha())
            setupTouchLogic(view)
        }
    }

    override fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            UiHelper.dpToPx(context, SavedData.getTouchpadWidth()),
            UiHelper.dpToPx(context, SavedData.getTouchpadHeight()),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    override fun show() {
        super.show()
        refreshCachedSettings()

        val alpha = SavedData.getTouchpadAlpha()
        params.width = UiHelper.dpToPx(context, SavedData.getTouchpadWidth())
        params.height = UiHelper.dpToPx(context, SavedData.getTouchpadHeight())
        params.x = SavedData.getTouchpadX()
        params.y = SavedData.getTouchpadY()

        overlayView?.let { view ->
            view.alpha = UiHelper.percentToAlpha(alpha)
            windowManager.updateViewLayout(view, params)
        }
        UiHelper.showToast(context, context.getString(R.string.toast_touchpad_mode))
    }

    private fun refreshCachedSettings() {
        cachedSpeed = SavedData.getCursorSpeed()
        cachedThreshold = SavedData.getCursorThreshold()
        cachedLpDelay = SavedData.getTouchpadLongPressDelay()
        cachedLpPlayPx = SavedData.getTouchpadLongPressPlay()
    }

    override fun onHidden() {
        cancelLongPressTimer()
        removeCursorViewSafely()
        super.onHidden()
    }

    override fun cleanup() {
        cancelLongPressTimer()
        removeCursorViewSafely()
        super.cleanup()
    }

    override fun onRemoved() {
        cancelLongPressTimer()
        removeCursorViewSafely()
        super.onRemoved()
    }

    private fun setupTouchLogic(view: View) {
        view.setOnTouchListener { v, event ->
            val vw = v.width.toFloat()
            val vh = v.height.toFloat()
            if (vw <= 0 || vh <= 0) return@setOnTouchListener false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    handleTouchDown(event, vw, vh)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    handleTouchMove(event)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handleTouchUp()
                    true
                }
                else -> false
            }
        }
    }

    private fun handleTouchDown(event: MotionEvent, vw: Float, vh: Float) {
        Vibration.vibrateTick()
        showCursor()
        refreshCachedSettings()

        // タッチパッド上の比率から画面上のカーソル絶対座標を決定
        cursorX = screenWidth * (event.x / vw)
        cursorY = screenHeight * (event.y / vh)
        visualCursorX = cursorX
        visualCursorY = cursorY

        touchDownX = event.x
        touchDownY = event.y
        lastTouchX = event.x
        lastTouchY = event.y
        lastStillX = event.x
        lastStillY = event.y
        lastEventTime = event.eventTime

        velocityX = 0f
        velocityY = 0f
        isFirstMove = true

        touchState = TouchState.NORMAL_MOVING
        dragPath.reset()

        renderCursorPos(cursorX, cursorY, force = true)
        startLongPressTimer()
    }

    private fun handleTouchMove(event: MotionEvent) {
        val dx = event.x - lastTouchX
        val dy = event.y - lastTouchY
        val dt = (event.eventTime - lastEventTime).toFloat()

        if (dt > 0) {
            val instantVx = dx / dt
            val instantVy = dy / dt

            if (isFirstMove) {
                velocityX = instantVx
                velocityY = instantVy
                isFirstMove = false
            } else {
                velocityX = velocityX * 0.8f + instantVx * 0.2f
                velocityY = velocityY * 0.8f + instantVy * 0.2f
            }
        }
        lastEventTime = event.eventTime

        val dist = hypot(dx, dy)
        // 基準座標は常に更新（閾値以下の微小移動が急に累積してワープするのを防止）
        lastTouchX = event.x
        lastTouchY = event.y

        val movedFromStill = hypot(event.x - lastStillX, event.y - lastStillY)

        // 閾値以上の意図的な移動のみカーソルへ反映
        if (dist > cachedThreshold) {
            cursorX = (cursorX + dx * cachedSpeed).coerceIn(0f, screenWidth.toFloat())
            cursorY = (cursorY + dy * cachedSpeed).coerceIn(0f, screenHeight.toFloat())

            // ドラッグ中なら移動経路を Path に逐次記録
            if (touchState == TouchState.DRAGGING) {
                dragPath.lineTo(cursorX, cursorY)
            }
        }

        // 予測描画座標の計算 (Visual Lead)
        visualCursorX = (cursorX + velocityX * PREDICTION_OFFSET_MS * cachedSpeed).coerceIn(0f, screenWidth.toFloat())
        visualCursorY = (cursorY + velocityY * PREDICTION_OFFSET_MS * cachedSpeed).coerceIn(0f, screenHeight.toFloat())

        // 位置が変わった場合のみ WindowManager IPC でカーソル描画
        renderCursorPos(visualCursorX, visualCursorY)

        when (touchState) {
            TouchState.NORMAL_MOVING -> {
                // 指が動いている間は基準位置を更新し、指が静止した地点から長押し判定を再カウント
                if (movedFromStill > cachedLpPlayPx) {
                    lastStillX = event.x
                    lastStillY = event.y
                    startLongPressTimer()
                }
            }
            TouchState.LONG_PRESSED_STILL -> {
                // 長押し成立後に遊び幅を超えて動いたら、ドラッグモードへ移行
                if (movedFromStill > cachedLpPlayPx) {
                    touchState = TouchState.DRAGGING
                    dragPath.lineTo(cursorX, cursorY)
                }
            }
            TouchState.DRAGGING -> {
                // dragPath に記録中
            }
            TouchState.IDLE -> {}
        }
    }

    private fun handleTouchUp() {
        cancelLongPressTimer()

        when (touchState) {
            TouchState.NORMAL_MOVING -> {
                // 通常タップ: ユーザーが見ていた描画位置（visualCursorX, visualCursorY）で正確にタップ
                performClick(visualCursorX, visualCursorY)
            }
            TouchState.LONG_PRESSED_STILL -> {
                // 長押しタップ: その場で長押し判定が成立したまま指を離した
                performLongPress(visualCursorX, visualCursorY)
            }
            TouchState.DRAGGING -> {
                // ドラッグ: 指を動かした時間と実際の軌跡でジェスチャー実行
                performDragGesture()
            }
            TouchState.IDLE -> {}
        }

        touchState = TouchState.IDLE
        cursorView?.visibility = View.GONE
        velocityX = 0f
        velocityY = 0f
        dragPath.reset()
    }

    private fun startLongPressTimer() {
        cancelLongPressTimer()
        longPressRunnable = Runnable {
            if (touchState == TouchState.NORMAL_MOVING) {
                touchState = TouchState.LONG_PRESSED_STILL
                longPressTriggerTimeMs = SystemClock.uptimeMillis()

                // 長押しが成立した瞬間の正確なカーソル座標をドラッグ Path の起点として初期化
                dragPath.reset()
                dragPath.moveTo(cursorX, cursorY)
                lastStillX = lastTouchX
                lastStillY = lastTouchY

                Vibration.vibrateClick()
                UiHelper.showToast(context, context.getString(R.string.toast_drag_mode))
            }
        }
        handler.postDelayed(longPressRunnable!!, cachedLpDelay)
    }

    private fun cancelLongPressTimer() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun performClick(x: Float, y: Float) {
        gestureDispatcher.dispatchTap(
            x = x,
            y = y,
            duration = Constants.GESTURE_CLICK_DURATION_MS
        )
        Vibration.vibrateClick()
    }

    private fun performLongPress(x: Float, y: Float) {
        gestureDispatcher.dispatchLongPress(
            x = x,
            y = y,
            duration = Constants.GESTURE_LONG_CLICK_DURATION_MS
        )
        Vibration.vibrateClick()
    }

    private fun performDragGesture() {
        val maxDuration = Constants.GESTURE_MAX_STROKE_DURATION_MS

        // 実際にユーザーが指を動かしていた時間（ms）を正確に計算
        val elapsedMs = (SystemClock.uptimeMillis() - longPressTriggerTimeMs).coerceIn(100L, maxDuration)

        gestureDispatcher.dispatchGesture(
            path = dragPath,
            startTime = 0,
            duration = elapsedMs
        )
        Vibration.vibrateClick()
    }

    private fun showCursor() {
        if (cursorView == null) {
            cursorView = LayoutInflater.from(context).inflate(R.layout.layout_cursor, null)
            cursorView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)

            cursorParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            try {
                windowManager.addView(cursorView, cursorParams)
            } catch (e: Exception) {
                ErrorHandler.logError("Failed to add cursorView to WindowManager", e)
            }
        }
        cursorView?.visibility = View.VISIBLE
        lastRenderedPxX = Int.MIN_VALUE
        lastRenderedPxY = Int.MIN_VALUE
    }

    private fun removeCursorViewSafely() {
        cursorView?.let { view ->
            try {
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            } catch (e: Exception) {
                ErrorHandler.logError("Failed to remove cursorView from WindowManager", e)
            }
        }
        cursorView = null
        lastRenderedPxX = Int.MIN_VALUE
        lastRenderedPxY = Int.MIN_VALUE
    }

    /**
     * ダーティチェック付きカーソル描画
     * ピクセル単位で変化がない場合は WindowManager の IPC をスキップして CPU・描画負荷を最小化
     */
    private fun renderCursorPos(x: Float, y: Float, force: Boolean = false) {
        val cursor = cursorView ?: return
        if (cursor.parent == null) return

        val viewW = if (cursor.width > 0) cursor.width else cursor.measuredWidth
        val viewH = if (cursor.height > 0) cursor.height else cursor.measuredHeight

        val targetPxX = (x - viewW / 2).toInt()
        val targetPxY = (y - viewH / 2).toInt()

        if (force || targetPxX != lastRenderedPxX || targetPxY != lastRenderedPxY) {
            cursorParams.x = targetPxX
            cursorParams.y = targetPxY
            lastRenderedPxX = targetPxX
            lastRenderedPxY = targetPxY

            try {
                windowManager.updateViewLayout(cursor, cursorParams)
            } catch (e: Exception) {
                ErrorHandler.logError("Failed to update cursorView layout", e)
            }
        }
    }

    companion object {
        private const val PREDICTION_OFFSET_MS = 15f
    }
}
