package com.example.onehandcommander.ui.drawables

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

class GestureTenkeyHudDrawable(
    private val density: Float
) : Drawable() {

    var isActive: Boolean = false
    var originX: Float = 0f
    var originY: Float = 0f
    var currentX: Float = 0f
    var currentY: Float = 0f
    var activeDigit: String? = null
    var isLongPressZero: Boolean = false
    var enteredBufferText: String = ""

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3360A5FA") // Subtle cyan/blue ring
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D38BDF8")
        style = Paint.Style.FILL
    }

    private val activeCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC2563EB")
        style = Paint.Style.FILL
    }

    private val activeZeroPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CCF59E0B") // Amber/gold for 0 / Search
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8060A5FA")
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val activeDirectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2563EB") // Electric blue for active sector
        style = Paint.Style.FILL
    }

    private val guideDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4D94A3B8")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E60F172A")
        style = Paint.Style.FILL
    }

    private val badgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4038BDF8")
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#38BDF8")
        textSize = 16f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val badgeRect = RectF()

    // 8-direction positions (angle in degrees relative to center):
    // 1: Top-Left (-135°), 2: Top (-90°), 3: Top-Right (-45°)
    // 4: Left (180°),      5: Center,     6: Right (0°)
    // 7: Bottom-Left (135°), 8: Bottom (90°), 9: Bottom-Right (45°)
    private val directions = listOf(
        DirectionItem("1", -135.0, isDiagonal = true),
        DirectionItem("2", -90.0, isDiagonal = false),
        DirectionItem("3", -45.0, isDiagonal = true),
        DirectionItem("6", 0.0, isDiagonal = false),
        DirectionItem("9", 45.0, isDiagonal = true),
        DirectionItem("8", 90.0, isDiagonal = false),
        DirectionItem("7", 135.0, isDiagonal = true),
        DirectionItem("4", 180.0, isDiagonal = false)
    )

    private data class DirectionItem(
        val digit: String,
        val angleDeg: Double,
        val isDiagonal: Boolean
    )

    override fun draw(canvas: Canvas) {
        if (!isActive && enteredBufferText.isEmpty()) return

        val radius = 48f * density
        val innerThreshold = 20f * density

        if (isActive) {
            // 1. Draw connecting line from origin to current point
            canvas.drawLine(originX, originY, currentX, currentY, linePaint)

            // 2. Draw outer boundary ring and inner deadzone ring
            canvas.drawCircle(originX, originY, radius, ringPaint)
            canvas.drawCircle(originX, originY, innerThreshold, ringPaint)

            // 3. Draw Center (5 or 0)
            if (isLongPressZero) {
                canvas.drawCircle(originX, originY, innerThreshold, activeZeroPaint)
                canvas.drawText("0", originX, originY + (4f * density), textPaint)
            } else if (activeDigit == "5") {
                canvas.drawCircle(originX, originY, innerThreshold, activeCenterPaint)
                canvas.drawText("5", originX, originY + (4f * density), textPaint)
            } else {
                canvas.drawCircle(originX, originY, 4f * density, centerPaint)
            }

            // 4. Draw 8 directions
            for (dir in directions) {
                val rad = Math.toRadians(dir.angleDeg)
                val dotX = (originX + radius * cos(rad)).toFloat()
                val dotY = (originY + radius * sin(rad)).toFloat()

                val isSelected = activeDigit == dir.digit
                if (isSelected) {
                    val dotRadius = if (dir.isDiagonal) 14f * density else 12f * density
                    canvas.drawCircle(dotX, dotY, dotRadius, activeDirectionPaint)
                    canvas.drawText(dir.digit, dotX, dotY + (4.5f * density), textPaint)
                } else {
                    canvas.drawCircle(dotX, dotY, 3f * density, guideDotPaint)
                    canvas.drawText(dir.digit, dotX, dotY + (4f * density), textPaint)
                }
            }

            // 5. Draw current touch indicator dot
            canvas.drawCircle(currentX, currentY, 5f * density, activeCenterPaint)
        }

        // 6. Draw entered buffer badge (if non-empty or active)
        if (enteredBufferText.isNotEmpty() || isActive) {
            val badgeX = if (isActive) originX else bounds.exactCenterX()
            val badgeY = if (isActive) (originY - radius - 24f * density) else (bounds.bottom - 48f * density)

            val displayText = when {
                isLongPressZero -> "0 (SEARCH)"
                enteredBufferText.isNotEmpty() && activeDigit != null -> "$enteredBufferText$activeDigit"
                enteredBufferText.isNotEmpty() -> "$enteredBufferText ─"
                activeDigit != null -> activeDigit!!
                else -> "─"
            }

            val badgeWidth = 84f * density
            val badgeHeight = 28f * density
            badgeRect.set(
                badgeX - badgeWidth / 2f,
                badgeY - badgeHeight / 2f,
                badgeX + badgeWidth / 2f,
                badgeY + badgeHeight / 2f
            )

            val corner = 8f * density
            canvas.drawRoundRect(badgeRect, corner, corner, badgeBgPaint)
            canvas.drawRoundRect(badgeRect, corner, corner, badgeStrokePaint)
            canvas.drawText(displayText, badgeX, badgeY + (5f * density), badgeTextPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        ringPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        ringPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
