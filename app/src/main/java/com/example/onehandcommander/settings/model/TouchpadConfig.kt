package com.example.onehandcommander.settings.model

import com.example.onehandcommander.utils.Constants

/**
 * タッチパッドおよびバーチャルカーソルの設定モデル
 */
data class TouchpadConfig(
    val widthDp: Int = Constants.Defaults.TOUCHPAD_WIDTH_DP,
    val heightDp: Int = Constants.Defaults.TOUCHPAD_HEIGHT_DP,
    val alphaPercent: Int = Constants.Defaults.TOUCHPAD_ALPHA,
    val posX: Int = Constants.Defaults.TOUCHPAD_X,
    val posY: Int = Constants.Defaults.TOUCHPAD_Y,
    val cursorSpeed: Float = Constants.Defaults.CURSOR_SPEED,
    val cursorThresholdPx: Int = Constants.Defaults.CURSOR_THRESHOLD,
    val longPressDelayMs: Long = Constants.Defaults.TOUCHPAD_LP_DELAY_MS,
    val longPressPlayPx: Int = Constants.Defaults.TOUCHPAD_LP_PLAY_PX
)
