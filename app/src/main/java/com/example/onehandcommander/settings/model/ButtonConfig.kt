package com.example.onehandcommander.settings.model

import com.example.onehandcommander.utils.Constants

/**
 * メインフローティングボタンの設定モデル
 */
data class ButtonConfig(
    val sizeDp: Int = Constants.Defaults.BUTTON_SIZE_DP,
    val alphaPercent: Int = Constants.Defaults.BUTTON_ALPHA,
    val isVibrationEnabled: Boolean = Constants.Defaults.VIBRATION_ENABLED,
    val posX: Int = Constants.Defaults.BUTTON_POS_X,
    val posY: Int = Constants.Defaults.BUTTON_POS_Y
)
