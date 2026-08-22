package com.example.onehandcommander.settings

import android.content.Context
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.model.SettingItem
import com.example.onehandcommander.utils.Constants

/**
 * 設定項目の一覧と動作を宣言的に定義・提供するプロバイダー
 */
object SettingsConfigProvider {

    fun getSettingItems(context: Context): List<SettingItem> {
        val items = mutableListOf<SettingItem>()

        // 1. メインボタン設定
        items.add(SettingItem.SectionHeader(R.string.settings_section_button))
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_SIZE_BUTTON,
                labelResId = R.string.settings_label_size,
                currentValue = SavedData.getButtonSize(),
                minValue = Constants.Defaults.BUTTON_SIZE_MIN,
                maxValue = Constants.Defaults.BUTTON_SIZE_MAX,
                formatValue = { "$it dp" },
                onValueChanged = { SavedData.saveButtonSize(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_BUTTON_ALPHA,
                labelResId = R.string.settings_label_opacity,
                currentValue = SavedData.getButtonAlpha(),
                minValue = Constants.Defaults.BUTTON_ALPHA_MIN,
                maxValue = Constants.Defaults.BUTTON_ALPHA_MAX,
                formatValue = { "$it %" },
                onValueChanged = { SavedData.saveButtonAlpha(it) }
            )
        )
        items.add(
            SettingItem.Toggle(
                key = SavedData.KEY_ENABLE_VIBRATION,
                labelResId = R.string.settings_label_vibration,
                isChecked = SavedData.isVibrationEnabled(),
                onToggleChanged = { SavedData.setVibrationEnabled(it) }
            )
        )

        // 2. テンキー設定
        items.add(SettingItem.SectionHeader(R.string.settings_section_tenkey))
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_SIZE_TENKEY,
                labelResId = R.string.settings_label_size,
                currentValue = SavedData.getTenkeySize(),
                minValue = Constants.Defaults.TENKEY_SIZE_MIN,
                maxValue = Constants.Defaults.TENKEY_SIZE_MAX,
                formatValue = { "$it dp" },
                onValueChanged = { SavedData.saveTenkeySize(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_TENKEY_ALPHA,
                labelResId = R.string.settings_label_opacity,
                currentValue = SavedData.getTenkeyAlpha(),
                minValue = Constants.Defaults.TENKEY_ALPHA_MIN,
                maxValue = Constants.Defaults.TENKEY_ALPHA_MAX,
                formatValue = { "$it %" },
                onValueChanged = { SavedData.saveTenkeyAlpha(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_TENKEY_X,
                labelResId = R.string.settings_label_x,
                currentValue = SavedData.getTenkeyX(),
                minValue = Constants.Defaults.TENKEY_POS_MIN,
                maxValue = Constants.Defaults.TENKEY_POS_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveTenkeyX(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_TENKEY_Y,
                labelResId = R.string.settings_label_y,
                currentValue = SavedData.getTenkeyY(),
                minValue = Constants.Defaults.TENKEY_POS_MIN,
                maxValue = Constants.Defaults.TENKEY_POS_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveTenkeyY(it) }
            )
        )

        // 3. タッチパッド設定
        items.add(SettingItem.SectionHeader(R.string.settings_section_touchpad))
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_SIZE_PAD_W,
                labelResId = R.string.settings_label_width,
                currentValue = SavedData.getTouchpadWidth(),
                minValue = Constants.Defaults.TOUCHPAD_WIDTH_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_WIDTH_MAX,
                formatValue = { "$it dp" },
                onValueChanged = { SavedData.saveTouchpadWidth(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_SIZE_PAD_H,
                labelResId = R.string.settings_label_height,
                currentValue = SavedData.getTouchpadHeight(),
                minValue = Constants.Defaults.TOUCHPAD_HEIGHT_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_HEIGHT_MAX,
                formatValue = { "$it dp" },
                onValueChanged = { SavedData.saveTouchpadHeight(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_PAD_ALPHA,
                labelResId = R.string.settings_label_opacity,
                currentValue = SavedData.getTouchpadAlpha(),
                minValue = Constants.Defaults.TOUCHPAD_ALPHA_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_ALPHA_MAX,
                formatValue = { "$it %" },
                onValueChanged = { SavedData.saveTouchpadAlpha(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_PAD_X,
                labelResId = R.string.settings_label_x,
                currentValue = SavedData.getTouchpadX(),
                minValue = Constants.Defaults.TOUCHPAD_POS_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_POS_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveTouchpadX(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_PAD_Y,
                labelResId = R.string.settings_label_y,
                currentValue = SavedData.getTouchpadY(),
                minValue = Constants.Defaults.TOUCHPAD_POS_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_POS_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveTouchpadY(it) }
            )
        )

        // カーソル速度 (float を step=0.1 の整数として扱う)
        val speedFactor = 10
        val currentSpeedInt = ((SavedData.getCursorSpeed() - Constants.Defaults.CURSOR_SPEED_MIN) * speedFactor).toInt()
        val maxSpeedInt = ((Constants.Defaults.CURSOR_SPEED_MAX - Constants.Defaults.CURSOR_SPEED_MIN) * speedFactor).toInt()
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_CURSOR_SPEED,
                labelResId = R.string.settings_label_cursor_speed,
                currentValue = currentSpeedInt,
                minValue = 0,
                maxValue = maxSpeedInt,
                formatValue = {
                    val actualSpeed = Constants.Defaults.CURSOR_SPEED_MIN + (it.toFloat() / speedFactor)
                    context.getString(R.string.settings_label_cursor_speed_format, actualSpeed)
                },
                onValueChanged = {
                    val actualSpeed = Constants.Defaults.CURSOR_SPEED_MIN + (it.toFloat() / speedFactor)
                    SavedData.saveCursorSpeed(actualSpeed)
                }
            )
        )

        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_CURSOR_THRESHOLD,
                labelResId = R.string.settings_label_cursor_threshold,
                currentValue = SavedData.getCursorThreshold(),
                minValue = Constants.Defaults.CURSOR_THRESHOLD_MIN,
                maxValue = Constants.Defaults.CURSOR_THRESHOLD_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveCursorThreshold(it) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_TOUCHPAD_LP_DELAY,
                labelResId = R.string.settings_label_long_press_delay,
                currentValue = SavedData.getTouchpadLongPressDelay().toInt(),
                minValue = Constants.Defaults.TOUCHPAD_LP_DELAY_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_LP_DELAY_MAX,
                formatValue = { "$it ms" },
                onValueChanged = { SavedData.saveTouchpadLongPressDelay(it.toLong()) }
            )
        )
        items.add(
            SettingItem.Slider(
                key = SavedData.KEY_TOUCHPAD_LP_PLAY,
                labelResId = R.string.settings_label_long_press_play,
                currentValue = SavedData.getTouchpadLongPressPlay(),
                minValue = Constants.Defaults.TOUCHPAD_LP_PLAY_MIN,
                maxValue = Constants.Defaults.TOUCHPAD_LP_PLAY_MAX,
                formatValue = { "$it px" },
                onValueChanged = { SavedData.saveTouchpadLongPressPlay(it) }
            )
        )

        return items
    }
}
