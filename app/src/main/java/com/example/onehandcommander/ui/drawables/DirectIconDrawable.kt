package com.example.onehandcommander.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.example.onehandcommander.ui.overlays.model.DirectIconType

/**
 * DirectIconDrawable
 * XMLリソースのロード・パース処理を完全排除 (0ms) し、
 * Canvas で直接幾何学パスを描画する超軽量・単色フラットアイコン Drawable。
 */
class DirectIconDrawable(
    private val iconType: DirectIconType,
    private val color: Int = Color.WHITE,
    private val strokeWidthDp: Float = 2.5f
) : Drawable() {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@DirectIconDrawable.color
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@DirectIconDrawable.color
        style = Paint.Style.FILL
    }

    private val path = Path()

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        val w = b.width().toFloat()
        val h = b.height().toFloat()
        val minDim = minOf(w, h)
        val stroke = (minDim / 24f) * strokeWidthDp
        paint.strokeWidth = stroke

        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        val size = minDim * 0.65f
        val left = cx - size / 2f
        val top = cy - size / 2f
        val right = cx + size / 2f
        val bottom = cy + size / 2f

        path.reset()

        when (iconType) {
            DirectIconType.HOME -> {
                // 家の輪郭：屋根（三角） + 本体（四角）
                val roofPeakY = top
                val roofBaseY = top + size * 0.42f
                val wallBottomY = bottom
                val wallLeft = left + size * 0.15f
                val wallRight = right - size * 0.15f

                path.moveTo(wallLeft, wallBottomY)
                path.lineTo(wallLeft, roofBaseY)
                path.lineTo(cx, roofPeakY)
                path.lineTo(wallRight, roofBaseY)
                path.lineTo(wallRight, wallBottomY)
                path.close()
                canvas.drawPath(path, paint)

                // ドア
                val doorW = size * 0.22f
                val doorH = size * 0.32f
                canvas.drawRect(cx - doorW / 2f, wallBottomY - doorH, cx + doorW / 2f, wallBottomY, paint)
            }

            DirectIconType.BACK -> {
                // 左向きのシンプルなシェブロン矢印
                val arrowTipX = left + size * 0.15f
                val arrowEndX = right - size * 0.15f
                val arrowSpanY = size * 0.40f

                path.moveTo(arrowEndX, cy - arrowSpanY)
                path.lineTo(arrowTipX, cy)
                path.lineTo(arrowEndX, cy + arrowSpanY)
                canvas.drawPath(path, paint)
            }

            DirectIconType.RECENTS -> {
                // 2つの重なるスクエア（タスク切り替え）
                val sqSize = size * 0.58f
                // 背面の四角
                canvas.drawRoundRect(right - sqSize, top, right, top + sqSize, 4f, 4f, paint)
                // 前面の四角
                canvas.drawRoundRect(left, bottom - sqSize, left + sqSize, bottom, 4f, 4f, paint)
            }

            DirectIconType.NOTIFICATIONS -> {
                // ベルの輪郭
                val bellTop = top + size * 0.1f
                val bellBottom = bottom - size * 0.18f
                val bellLeft = left + size * 0.12f
                val bellRight = right - size * 0.12f

                path.moveTo(bellLeft, bellBottom)
                path.quadTo(bellLeft, bellTop + size * 0.25f, cx, bellTop)
                path.quadTo(bellRight, bellTop + size * 0.25f, bellRight, bellBottom)
                path.close()
                canvas.drawPath(path, paint)

                // 下部のベルツマミ
                canvas.drawLine(bellLeft - size * 0.08f, bellBottom, bellRight + size * 0.08f, bellBottom, paint)
                // ベルの舌（クラッパー）
                canvas.drawCircle(cx, bottom - size * 0.04f, stroke * 0.8f, fillPaint)
            }

            DirectIconType.SCREENSHOT -> {
                // 四隅のフォーカス枠角カッコ
                val cornerLen = size * 0.28f
                // 左上
                path.moveTo(left, top + cornerLen)
                path.lineTo(left, top)
                path.lineTo(left + cornerLen, top)
                // 右上
                path.moveTo(right - cornerLen, top)
                path.lineTo(right, top)
                path.lineTo(right, top + cornerLen)
                // 右下
                path.moveTo(right, bottom - cornerLen)
                path.lineTo(right, bottom)
                path.lineTo(right - cornerLen, bottom)
                // 左下
                path.moveTo(left + cornerLen, bottom)
                path.lineTo(left, bottom)
                path.lineTo(left, bottom - cornerLen)

                canvas.drawPath(path, paint)
                // 中央のドット
                canvas.drawCircle(cx, cy, stroke * 0.9f, fillPaint)
            }

            DirectIconType.POWER_DIALOG -> {
                // 電源マーク：上部が開いた円弧 + 上部の縦直線
                val arcRect = RectF(left + size * 0.08f, top + size * 0.08f, right - size * 0.08f, bottom - size * 0.08f)
                canvas.drawArc(arcRect, 300f, 300f, false, paint)
                canvas.drawLine(cx, top, cx, cy, paint)
            }

            DirectIconType.OPEN_FILE -> {
                // 右上角折れのシンプルな書類
                val foldSize = size * 0.3f
                path.moveTo(left, top)
                path.lineTo(right - foldSize, top)
                path.lineTo(right, top + foldSize)
                path.lineTo(right, bottom)
                path.lineTo(left, bottom)
                path.close()
                canvas.drawPath(path, paint)

                // 折れ線
                val foldPath = Path().apply {
                    moveTo(right - foldSize, top)
                    lineTo(right - foldSize, top + foldSize)
                    lineTo(right, top + foldSize)
                }
                canvas.drawPath(foldPath, paint)

                // 書類のテキスト横線2本
                val lineLeft = left + size * 0.2f
                val lineRight = right - size * 0.2f
                canvas.drawLine(lineLeft, cy + size * 0.08f, lineRight, cy + size * 0.08f, paint)
                canvas.drawLine(lineLeft, cy + size * 0.25f, lineRight - size * 0.15f, cy + size * 0.25f, paint)
            }

            DirectIconType.TOUCHPAD -> {
                // カーソル矢印ポインター
                path.moveTo(left + size * 0.1f, top)
                path.lineTo(left + size * 0.1f, bottom)
                path.lineTo(left + size * 0.45f, bottom - size * 0.3f)
                path.lineTo(right, bottom - size * 0.1f)
                path.close()
                canvas.drawPath(path, paint)
            }

            DirectIconType.SETTINGS -> {
                // 歯車マーク：外枠の円 + 中心円 + 8つの突起
                val radius = size * 0.42f
                canvas.drawCircle(cx, cy, radius * 0.45f, paint)
                canvas.drawCircle(cx, cy, radius, paint)

                for (i in 0 until 4) {
                    val angle = Math.toRadians((i * 45).toDouble())
                    val cos = Math.cos(angle).toFloat()
                    val sin = Math.sin(angle).toFloat()
                    val startR = radius * 0.9f
                    val endR = radius * 1.25f
                    canvas.drawLine(cx + cos * startR, cy + sin * startR, cx + cos * endR, cy + sin * endR, paint)
                    canvas.drawLine(cx - cos * startR, cy - sin * startR, cx - cos * endR, cy - sin * endR, paint)
                }
            }

            DirectIconType.DEFAULT_APP -> {
                // アプリ汎用：4つのグリッドスクエア
                val block = size * 0.38f
                canvas.drawRoundRect(left, top, left + block, top + block, 3f, 3f, paint)
                canvas.drawRoundRect(right - block, top, right, top + block, 3f, 3f, paint)
                canvas.drawRoundRect(left, bottom - block, left + block, bottom, 3f, 3f, paint)
                canvas.drawRoundRect(right - block, bottom - block, right, bottom, 3f, 3f, paint)
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        fillPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
