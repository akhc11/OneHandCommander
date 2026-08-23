package com.example.onehandcommander.settings.model

/**
 * 設定画面の宣言的 UI アイテム
 */
sealed class SettingItem {
    data class SectionHeader(
        val titleResId: Int,
        val subtitleResId: Int? = null
    ) : SettingItem()

    data class Slider(
        val key: String,
        val labelResId: Int,
        val descriptionResId: Int? = null,
        val currentValue: Int,
        val minValue: Int,
        val maxValue: Int,
        val formatValue: ((Int) -> String)? = null,
        val onValueChanged: (Int) -> Unit
    ) : SettingItem()

    data class Toggle(
        val key: String,
        val labelResId: Int,
        val descriptionResId: Int? = null,
        val isChecked: Boolean,
        val onToggleChanged: (Boolean) -> Unit
    ) : SettingItem()
}

