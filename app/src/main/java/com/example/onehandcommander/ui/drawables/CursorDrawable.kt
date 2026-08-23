package com.example.onehandcommander.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * 仮想カーソル用のゼロ・アロケーション直接描画 Drawable
 * 視認性の高い外側ホワイトリング + 内側レッドクロスヘア（中心点）を描画
 */
class CursorDrawable : Drawable() {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
        setShadowLayer(4f, 0f, 0f, Color.argb(180, 0, 0, 0))
    }

    private val innerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#FF1744")
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF1744")
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val radius = (Math.min(bounds.width(), bounds.height()) / 2f) - 4f

        if (radius <= 0) return

        // 外側リング (白・シャドウ付き)
        canvas.drawCircle(cx, cy, radius, ringPaint)
        // 内側リング (赤)
        canvas.drawCircle(cx, cy, radius - 2f, innerRingPaint)
        // 中心ポインター (赤点)
        canvas.drawCircle(cx, cy, 3f, centerPaint)
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
        innerRingPaint.alpha = alpha
        centerPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter
        innerRingPaint.colorFilter = colorFilter
        centerPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
