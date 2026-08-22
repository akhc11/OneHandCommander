package com.example.onehandcommander.settings.model

import com.example.onehandcommander.utils.Constants

/**
 * テンキーオーバーレイの設定モデル
 */
data class TenkeyConfig(
    val sizeDp: Int = Constants.Defaults.TENKEY_SIZE_DP,
    val alphaPercent: Int = Constants.Defaults.TENKEY_ALPHA,
    val posX: Int = Constants.Defaults.TENKEY_X,
    val posY: Int = Constants.Defaults.TENKEY_Y
)
