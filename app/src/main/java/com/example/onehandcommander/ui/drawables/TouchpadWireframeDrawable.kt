package com.example.onehandcommander.ui.drawables

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.example.onehandcommander.utils.UiHelper

/**
 * タッチパッド領域用のゼロ・アロケーション直接描画 Drawable
 * 半透明ダーク背景 + 境界ストローク + 角丸を GC 発生なしで瞬時に描画
 */
class TouchpadWireframeDrawable(context: Context) : Drawable() {

    private val strokeWidthPx = UiHelper.dpToPx(context, 2).toFloat()
    private val cornerRadiusPx = UiHelper.dpToPx(context, 8).toFloat()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#33000000")
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.WHITE
    }

    private val drawRect = RectF()

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        val halfStroke = strokeWidthPx / 2f

        drawRect.set(
            bounds.left + halfStroke,
            bounds.top + halfStroke,
            bounds.right - halfStroke,
            bounds.bottom - halfStroke
        )

        // 背景の半透明塗りつぶし
        canvas.drawRoundRect(drawRect, cornerRadiusPx, cornerRadiusPx, fillPaint)
        // 外枠線
        canvas.drawRoundRect(drawRect, cornerRadiusPx, cornerRadiusPx, strokePaint)
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = (alpha * 0x33) / 255
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        strokePaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
