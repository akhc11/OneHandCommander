package com.example.onehandcommander.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

/**
 * フローティングボタンのリングアイコンを高速かつ省メモリに直接描画する軽量Drawable
 * - VectorDrawable の XML パース・6重グループ回転行列計算を完全排除
 * - drawArc / drawCircle による 2D ネイティブ直接描画
 */
class FloatRingDrawable(
    private val strokeColor: Int = Color.parseColor("#4DEEE9"),
    private val centerColor: Int = Color.parseColor("#4DEEE9"),
    private val strokeWidthRatio: Float = 0.05f,
    private val segments: Int = 6,
    private val sweepAngle: Float = 44f
) : Drawable() {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = centerColor
        style = Paint.Style.FILL
    }

    private val arcRect = RectF()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return

        val size = minOf(b.width(), b.height()).toFloat()
        val strokeW = size * strokeWidthRatio
        ringPaint.strokeWidth = strokeW

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val radius = (size - strokeW) / 2f

        // 外周の弧（アーク）を描画
        arcRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val step = 360f / segments
        val startOffset = (step - sweepAngle) / 2f

        for (i in 0 until segments) {
            val startAngle = i * step + startOffset
            canvas.drawArc(arcRect, startAngle, sweepAngle, false, ringPaint)
        }

        // 中心の小さなドット
        val centerDotRadius = size * 0.06f
        canvas.drawCircle(cx, cy, centerDotRadius, centerPaint)
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
        centerPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter
        centerPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
