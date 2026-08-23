package com.example.onehandcommander.ui.overlays

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.ui.drawables.GestureTenkeyHudDrawable
import com.example.onehandcommander.utils.Constants
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * ブラインド・相対座標ジェスチャーテンキー
 * - 画面上の任意の位置をタッチ開始点 (startX, startY) とし、相対ベクトルで数字を判定
 * - 2ストローク入力（1桁目フリック -> 200ms以内に2桁目フリックで即確定、200ms無操作で1桁確定）
 * - 0の長押し（200ms）即確定（アプリ検索等の即時起動）
 * - 方向ごとの触覚フィードバック（直交=軽、斜め=強、中心=ダブル、長押し0=重）
 * - 視界を遮らないミニマルHUD (GestureTenkeyHudDrawable)
 */
class Tenkey(
    context: Context,
    windowManager: WindowManager
) : BaseOverlay(context, windowManager) {

    // コールバック
    var onInput: ((String) -> Unit)? = null
    var onInputUpdating: ((String) -> Unit)? = null

    private val density = context.resources.displayMetrics.density
    private val flickThresholdPx = UiHelper.dpToPx(context, Constants.SWIPE_THRESHOLD_DP).toFloat()

    private val hudDrawable = GestureTenkeyHudDrawable(density)
    private var gestureView: GestureSurfaceView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var startX = 0f
    private var startY = 0f
    private var currentX = 0f
    private var currentY = 0f

    private var isZeroCommitted = false
    private var lastVibratedDigit: String? = null

    private val enteredBuffer = StringBuilder()

    // 0判定用長押しタイマー (200ms)
    private val longPressZeroRunnable = Runnable {
        onZeroLongPressed()
    }

    // 1桁確定タイマー (200ms)
    private val singleDigitCommitRunnable = Runnable {
        commitSingleDigit()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun createView(): View {
        val view = GestureSurfaceView(context)
        view.setOnTouchListener { _, event -> handleTouchEvent(event) }
        val alpha = SavedData.getTenkeyAlpha()
        view.alpha = UiHelper.percentToAlpha(alpha)
        gestureView = view
        return view
    }

    override fun createLayoutParams(): WindowManager.LayoutParams {
        val alpha = SavedData.getTenkeyAlpha()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            this.alpha = UiHelper.percentToAlpha(alpha)
        }
    }

    fun updateSize() {
        val alpha = SavedData.getTenkeyAlpha()
        overlayView?.let { view ->
            view.alpha = UiHelper.percentToAlpha(alpha)
            if (isVisible()) {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    private fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.rawX
                startY = event.rawY
                currentX = event.rawX
                currentY = event.rawY

                isZeroCommitted = false
                lastVibratedDigit = null

                // 次のストローク開始により、1桁確定タイマーをキャンセル
                mainHandler.removeCallbacks(singleDigitCommitRunnable)

                // HUD更新
                hudDrawable.isActive = true
                hudDrawable.originX = startX
                hudDrawable.originY = startY
                hudDrawable.currentX = currentX
                hudDrawable.currentY = currentY
                hudDrawable.activeDigit = null
                hudDrawable.isLongPressZero = false
                hudDrawable.enteredBufferText = enteredBuffer.toString()

                // 長押し「0」タイマー開始 (300ms)
                mainHandler.removeCallbacks(longPressZeroRunnable)
                mainHandler.postDelayed(longPressZeroRunnable, Constants.TENKEY_LONG_PRESS_MS)

                gestureView?.invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isZeroCommitted) return true

                currentX = event.rawX
                currentY = event.rawY
                hudDrawable.currentX = currentX
                hudDrawable.currentY = currentY

                val dx = currentX - startX
                val dy = currentY - startY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                if (distance >= flickThresholdPx) {
                    // 移動が発生したため長押し0をキャンセル
                    mainHandler.removeCallbacks(longPressZeroRunnable)

                    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    val digit = getDigitForAngle(angleDeg)
                    hudDrawable.activeDigit = digit

                    val previewStr = enteredBuffer.toString() + digit
                    onInputUpdating?.invoke(previewStr)

                    if (digit != lastVibratedDigit) {
                        lastVibratedDigit = digit
                        if (isDiagonal(digit)) {
                            Vibration.vibrateDiagonal()
                        } else {
                            Vibration.vibrateOrthogonal()
                        }
                    }
                } else {
                    hudDrawable.activeDigit = "5"
                    val previewStr = enteredBuffer.toString() + "5"
                    onInputUpdating?.invoke(previewStr)
                }

                gestureView?.invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressZeroRunnable)
                hudDrawable.isActive = false

                if (isZeroCommitted) {
                    gestureView?.invalidate()
                    return true
                }

                val dx = currentX - startX
                val dy = currentY - startY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()

                val digit = if (distance >= flickThresholdPx) {
                    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
                    getDigitForAngle(angleDeg)
                } else {
                    Vibration.vibrateCenterTap()
                    "5"
                }

                onDigitReceived(digit)
                gestureView?.invalidate()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressZeroRunnable)
                hudDrawable.isActive = false
                onInputUpdating?.invoke("")
                gestureView?.invalidate()
                return true
            }
        }
        return false
    }

    /**
     * 長押しで「0」が確定した際の処理（即時実行・0番/検索呼び出し）
     */
    private fun onZeroLongPressed() {
        isZeroCommitted = true
        hudDrawable.isLongPressZero = true
        hudDrawable.activeDigit = "0"
        Vibration.vibrateLongPress()

        mainHandler.removeCallbacks(singleDigitCommitRunnable)
        enteredBuffer.clear()
        enteredBuffer.append("0")

        gestureView?.invalidate()
        commitAndReset()
    }

    /**
     * 1桁または2桁目の数字入力を受け付け
     */
    private fun onDigitReceived(digit: String) {
        if (enteredBuffer.isEmpty()) {
            // 1桁目ストック
            enteredBuffer.append(digit)
            hudDrawable.enteredBufferText = digit
            onInputUpdating?.invoke(digit)

            // 650ms待機タイマー開始（2回目のスワイプを行うための十分な猶予時間を確保）
            mainHandler.removeCallbacks(singleDigitCommitRunnable)
            mainHandler.postDelayed(singleDigitCommitRunnable, Constants.TENKEY_SINGLE_DIGIT_TIMEOUT_MS)
        } else {
            // 2桁目決定 -> 即確定
            enteredBuffer.append(digit)
            mainHandler.removeCallbacks(singleDigitCommitRunnable)
            Vibration.vibrateSuccess()
            commitAndReset()
        }
    }

    /**
     * 1桁確定タイマー満了時（1桁の数字として確定）
     */
    private fun commitSingleDigit() {
        if (enteredBuffer.isNotEmpty()) {
            commitAndReset()
        }
    }

    /**
     * バッファの内容をリスナーに通知し、リセット
     */
    private fun commitAndReset() {
        val result = enteredBuffer.toString()
        enteredBuffer.clear()
        hudDrawable.enteredBufferText = ""
        hudDrawable.isActive = false
        hudDrawable.isLongPressZero = false
        hudDrawable.activeDigit = null
        gestureView?.invalidate()
        onInputUpdating?.invoke("")

        if (result.isNotEmpty()) {
            onInput?.invoke(result)
        }
    }

    /**
     * 角度（-180° 〜 +180°）から数字（1〜4, 6〜9）をマッピング
     * 1: 左上 (-135°), 2: 上 (-90°), 3: 右上 (-45°)
     * 4: 左 (±180°),   6: 右 (0°)
     * 7: 左下 (+135°), 8: 下 (+90°), 9: 右下 (+45°)
     */
    private fun getDigitForAngle(angle: Double): String {
        return when {
            angle >= -157.5 && angle < -112.5 -> "1"
            angle >= -112.5 && angle < -67.5 -> "2"
            angle >= -67.5 && angle < -22.5 -> "3"
            angle >= -22.5 && angle < 22.5 -> "6"
            angle >= 22.5 && angle < 67.5 -> "9"
            angle >= 67.5 && angle < 112.5 -> "8"
            angle >= 112.5 && angle < 157.5 -> "7"
            else -> "4"
        }
    }

    private fun isDiagonal(digit: String): Boolean {
        return digit == "1" || digit == "3" || digit == "7" || digit == "9"
    }

    override fun onHidden() {
        mainHandler.removeCallbacks(longPressZeroRunnable)
        mainHandler.removeCallbacks(singleDigitCommitRunnable)
        enteredBuffer.clear()
        hudDrawable.isActive = false
        hudDrawable.enteredBufferText = ""
        onInputUpdating?.invoke("")
        super.onHidden()
    }

    override fun cleanup() {
        mainHandler.removeCallbacks(longPressZeroRunnable)
        mainHandler.removeCallbacks(singleDigitCommitRunnable)
        enteredBuffer.clear()
        super.cleanup()
    }

    /**
     * 軽量Canvas描画用サーフェスビュー
     */
    private inner class GestureSurfaceView(context: Context) : View(context) {
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            hudDrawable.setBounds(0, 0, width, height)
            hudDrawable.draw(canvas)
        }
    }
}
