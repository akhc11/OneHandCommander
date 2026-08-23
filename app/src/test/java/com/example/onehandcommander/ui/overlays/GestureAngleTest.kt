package com.example.onehandcommander.ui.overlays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2

class GestureAngleTest {

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

    @Test
    fun testAllEightDirections() {
        // Top-Left: -135° -> "1"
        assertEquals("1", getDigitForAngle(-135.0))
        assertTrue(isDiagonal("1"))

        // Top: -90° -> "2"
        assertEquals("2", getDigitForAngle(-90.0))
        assertFalse(isDiagonal("2"))

        // Top-Right: -45° -> "3"
        assertEquals("3", getDigitForAngle(-45.0))
        assertTrue(isDiagonal("3"))

        // Right: 0° -> "6"
        assertEquals("6", getDigitForAngle(0.0))
        assertFalse(isDiagonal("6"))

        // Bottom-Right: +45° -> "9"
        assertEquals("9", getDigitForAngle(45.0))
        assertTrue(isDiagonal("9"))

        // Bottom: +90° -> "8"
        assertEquals("8", getDigitForAngle(90.0))
        assertFalse(isDiagonal("8"))

        // Bottom-Left: +135° -> "7"
        assertEquals("7", getDigitForAngle(135.0))
        assertTrue(isDiagonal("7"))

        // Left: 180° / -180° -> "4"
        assertEquals("4", getDigitForAngle(180.0))
        assertEquals("4", getDigitForAngle(-180.0))
        assertFalse(isDiagonal("4"))
    }

    @Test
    fun testVectorAngles() {
        // dx = -50, dy = -50 (Top-Left)
        val deg1 = Math.toDegrees(atan2(-50.0, -50.0))
        assertEquals("1", getDigitForAngle(deg1))

        // dx = 0, dy = -50 (Top)
        val deg2 = Math.toDegrees(atan2(-50.0, 0.0))
        assertEquals("2", getDigitForAngle(deg2))

        // dx = 50, dy = -50 (Top-Right)
        val deg3 = Math.toDegrees(atan2(-50.0, 50.0))
        assertEquals("3", getDigitForAngle(deg3))

        // dx = 50, dy = 0 (Right)
        val deg6 = Math.toDegrees(atan2(0.0, 50.0))
        assertEquals("6", getDigitForAngle(deg6))

        // dx = 50, dy = 50 (Bottom-Right)
        val deg9 = Math.toDegrees(atan2(50.0, 50.0))
        assertEquals("9", getDigitForAngle(deg9))

        // dx = 0, dy = 50 (Bottom)
        val deg8 = Math.toDegrees(atan2(50.0, 0.0))
        assertEquals("8", getDigitForAngle(deg8))

        // dx = -50, dy = 50 (Bottom-Left)
        val deg7 = Math.toDegrees(atan2(50.0, -50.0))
        assertEquals("7", getDigitForAngle(deg7))

        // dx = -50, dy = 0 (Left)
        val deg4 = Math.toDegrees(atan2(0.0, -50.0))
        assertEquals("4", getDigitForAngle(deg4))
    }
}
