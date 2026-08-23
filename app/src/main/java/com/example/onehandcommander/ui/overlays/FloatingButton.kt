package com.example.onehandcommander.ui.overlays

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.ui.drawables.FloatRingDrawable
import com.example.onehandcommander.utils.Constants
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration
import kotlin.math.abs
import kotlin.math.hypot

/**
 * フローティングボタンの表示と操作を管理
 * - BaseOverlay による一貫したライフサイクル管理
 * - FloatRingDrawable によるゼロ・オーバーヘッド直接描画
 * - activePointerId によるマルチタッチ/誤タッチ完全防止
 * - 座標 Clamp による画面外飛び出し防止
 * - dirty-check による毎フレーム IPC (updateViewLayout) 呼び出しの徹底削減
 * - StateManager / MVI に準拠したクリーンなコールバック設計
 */
class FloatingButton(
    context: Context,
    windowManager: WindowManager
) : BaseOverlay(context, windowManager) {

    // コールバック
    var onTap: (() -> Unit)? = null
    var onSwipe: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeDown: (() -> Unit)? = null

    // タッチ状態 & ポインタ追跡
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    // 移動モード
    private var isMoveMode = false

    // 画面境界キャッシュ (Clamp用)
    private var screenWidth = 1080
    private var screenHeight = 1920

    private val swipeThresholdPx by lazy {
        UiHelper.dpToPx(context, Constants.SWIPE_THRESHOLD_DP)
    }

    private val floatRingDrawable by lazy { FloatRingDrawable() }

    init {
        updateScreenBounds()
    }

    private fun updateScreenBounds() {
        val metrics = windowManager.currentWindowMetrics
        screenWidth = metrics.bounds.width()
        screenHeight = metrics.bounds.height()
    }

    override fun createView(): View {
        return View(context).also { view ->
            view.background = floatRingDrawable
            setupTouchListener(view)
        }
    }

    override fun createLayoutParams(): WindowManager.LayoutParams {
        val sizeDp = SavedData.getButtonSize()
        val sizePx = UiHelper.dpToPx(context, sizeDp)
        return WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = SavedData.getPositionX()
            y = SavedData.getPositionY()
            alpha = UiHelper.percentToAlpha(SavedData.getButtonAlpha())
        }
    }

    /**
     * 移動モードの切り替え
     */
    fun setMoveMode(enabled: Boolean) {
        isMoveMode = enabled
    }

    /**
     * 表示状態を同期
     */
    fun updateVisibility(visible: Boolean) {
        if (visible) {
            show()
        } else {
            hide()
        }
    }

    /**
     * サイズ更新（設定変更時にリアルタイム反映）
     */
    fun updateSize() {
        val sizeDp = SavedData.getButtonSize()
        val sizePx = UiHelper.dpToPx(context, sizeDp)
        if (params.width != sizePx || params.height != sizePx) {
            params.width = sizePx
            params.height = sizePx
            overlayView?.let { view ->
                if (isVisible()) {
                    windowManager.updateViewLayout(view, params)
                }
            }
        }
    }

    /**
     * 透明度更新（設定変更時にリアルタイム反映）
     */
    fun updateAlpha() {
        val newAlpha = UiHelper.percentToAlpha(SavedData.getButtonAlpha())
        if (params.alpha != newAlpha) {
            params.alpha = newAlpha
            overlayView?.let { view ->
                if (isVisible()) {
                    windowManager.updateViewLayout(view, params)
                }
            }
        }
    }

    private fun setupTouchListener(view: View) {
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> handleTouchDown(event)
                MotionEvent.ACTION_MOVE -> handleTouchMove(event)
                MotionEvent.ACTION_UP -> handleTouchUp(event)
                MotionEvent.ACTION_CANCEL -> handleTouchCancel()
                MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
                else -> false
            }
        }
    }

    private fun handleTouchDown(event: MotionEvent): Boolean {
        activePointerId = event.getPointerId(0)
        initialX = params.x
        initialY = params.y
        initialTouchX = event.rawX
        initialTouchY = event.rawY
        updateScreenBounds()
        return true
    }

    private fun handleTouchMove(event: MotionEvent): Boolean {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val pointerIndex = event.findPointerIndex(activePointerId)
        if (pointerIndex < 0) return true

        if (isMoveMode) {
            val currentRawX = event.rawX
            val currentRawY = event.rawY
            val targetX = initialX + (currentRawX - initialTouchX).toInt()
            val targetY = initialY + (currentRawY - initialTouchY).toInt()

            // 画面境界内に Clamp
            val btnSize = params.width
            val clampedX = targetX.coerceIn(0, (screenWidth - btnSize).coerceAtLeast(0))
            val clampedY = targetY.coerceIn(0, (screenHeight - btnSize).coerceAtLeast(0))

            // Dirty Check: 座標変化時のみ IPC (updateViewLayout) を発行
            if (params.x != clampedX || params.y != clampedY) {
                params.x = clampedX
                params.y = clampedY
                overlayView?.let { windowManager.updateViewLayout(it, params) }
            }
        }
        return true
    }

    private fun handleTouchUp(event: MotionEvent): Boolean {
        if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
        val diffX = event.rawX - initialTouchX
        val diffY = event.rawY - initialTouchY
        val distance = hypot(diffX.toDouble(), diffY.toDouble()).toFloat()

        if (isMoveMode) {
            handleMoveModeRelease(distance)
        } else {
            if (distance > swipeThresholdPx) {
                handleDirectionalSwipe(diffX, diffY)
            } else {
                onTap?.invoke()
            }
        }

        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    private fun handleTouchCancel(): Boolean {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        return true
    }

    private fun handlePointerUp(event: MotionEvent): Boolean {
        val pointerIndex = event.actionIndex
        if (event.getPointerId(pointerIndex) == activePointerId) {
            // 操作中の指が離れた場合は無効化
            activePointerId = MotionEvent.INVALID_POINTER_ID
        }
        return true
    }

    private fun handleMoveModeRelease(distance: Float) {
        val isMoved = distance > Constants.MOVE_MODE_DISTANCE_THRESHOLD
        if (isMoved) {
            isMoveMode = false
            SavedData.savePosition(params.x, params.y)
            UiHelper.showToast(context, context.getString(R.string.toast_position_saved))
            Vibration.vibrateClick()
        } else {
            onTap?.invoke()
        }
    }

    private fun handleDirectionalSwipe(diffX: Float, diffY: Float) {
        Vibration.vibrateTick()
        val isVerticalSwipe = abs(diffY) > abs(diffX)
        if (isVerticalSwipe) {
            if (diffY < 0) {
                // 上スワイプ (diffY < 0) -> ホーム
                onSwipeUp?.invoke()
            } else {
                // 下スワイプ (diffY > 0) -> 最近のタスク
                onSwipeDown?.invoke()
            }
        } else {
            // 水平スワイプ -> タッチパッド
            onSwipe?.invoke()
        }
    }

    override fun cleanup() {
        super.cleanup()
        onTap = null
        onSwipe = null
        onSwipeUp = null
        onSwipeDown = null
        overlayView?.setOnTouchListener(null)
    }
}
