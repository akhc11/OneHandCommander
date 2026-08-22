package com.example.onehandcommander.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/**
 * テンキー等のHUDコーナー枠線（L字アングル）を描画する軽量Drawable
 * XMLリソースの多重作成を排除し、1クラスで全四隅の描画・色・太さを一元管理
 */
class HudCornerDrawable(
    private val cornerMask: Int,
    private val strokeColor: Int = Color.parseColor("#88FFFFFF"),
    private val strokeWidthPx: Float = 2f,
    private val cornerLengthPx: Float = 24f
) : Drawable() {

    companion object {
        const val TOP_LEFT = 1
        const val TOP_RIGHT = 2
        const val BOTTOM_LEFT = 4
        const val BOTTOM_RIGHT = 8
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = strokeColor
        strokeWidth = strokeWidthPx
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.SQUARE
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val left = b.left.toFloat() + strokeWidthPx / 2
        val top = b.top.toFloat() + strokeWidthPx / 2
        val right = b.right.toFloat() - strokeWidthPx / 2
        val bottom = b.bottom.toFloat() - strokeWidthPx / 2

        val len = cornerLengthPx.coerceAtMost((right - left) / 2)

        // Top-Left corner (1)
        if ((cornerMask and TOP_LEFT) != 0) {
            canvas.drawLine(left, top, left + len, top, paint)
            canvas.drawLine(left, top, left, top + len, paint)
        }

        // Top-Right corner (3)
        if ((cornerMask and TOP_RIGHT) != 0) {
            canvas.drawLine(right - len, top, right, top, paint)
            canvas.drawLine(right, top, right, top + len, paint)
        }

        // Bottom-Left corner (7)
        if ((cornerMask and BOTTOM_LEFT) != 0) {
            canvas.drawLine(left, bottom, left + len, bottom, paint)
            canvas.drawLine(left, bottom - len, left, bottom, paint)
        }

        // Bottom-Right corner (9)
        if ((cornerMask and BOTTOM_RIGHT) != 0) {
            canvas.drawLine(right - len, bottom, right, bottom, paint)
            canvas.drawLine(right, bottom - len, right, bottom, paint)
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
