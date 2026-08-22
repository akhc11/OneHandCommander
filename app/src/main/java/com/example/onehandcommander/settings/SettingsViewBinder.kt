package com.example.onehandcommander.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.onehandcommander.settings.model.SettingItem

/**
 * 設定画面の View 生成およびバインディングを担うレンダラー
 * テーマ属性 (textColorPrimary / textColorSecondary) に準拠し、
 * ダークテーマ・ライトテーマ双方で高いコントラストと視認性を保証します。
 */
class SettingsViewBinder(private val context: Context) {

    private val textColorPrimary: Int by lazy {
        resolveThemeColor(android.R.attr.textColorPrimary, Color.WHITE)
    }

    private val textColorSecondary: Int by lazy {
        resolveThemeColor(android.R.attr.textColorSecondary, Color.LTGRAY)
    }

    private fun resolveThemeColor(attrResId: Int, fallbackColor: Int): Int {
        val typedValue = TypedValue()
        return if (context.theme.resolveAttribute(attrResId, typedValue, true)) {
            if (typedValue.resourceId != 0) {
                ContextCompat.getColor(context, typedValue.resourceId)
            } else {
                typedValue.data
            }
        } else {
            fallbackColor
        }
    }

    fun populateContainer(container: LinearLayout, items: List<SettingItem>) {
        container.removeAllViews()
        for (item in items) {
            when (item) {
                is SettingItem.SectionHeader -> container.addView(createSectionHeader(item))
                is SettingItem.Slider -> container.addView(createSliderItem(item))
                is SettingItem.Toggle -> container.addView(createToggleItem(item))
            }
        }
    }

    private fun createSectionHeader(item: SettingItem.SectionHeader): View {
        return TextView(context).apply {
            text = context.getString(item.titleResId)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColorPrimary)
            setPadding(0, 32, 0, 12)
        }
    }

    private fun createSliderItem(item: SettingItem.Slider): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 8, 0, 16)
        }

        val labelView = TextView(context).apply {
            textSize = 14f
            setTextColor(textColorPrimary)
        }

        val baseLabel = context.getString(item.labelResId)
        val updateText = { value: Int ->
            val formatted = item.formatValue?.invoke(value) ?: "$value"
            labelView.text = "$baseLabel: $formatted"
        }
        updateText(item.currentValue)

        val seekBar = SeekBar(context).apply {
            max = item.maxValue - item.minValue
            progress = item.currentValue - item.minValue
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar, progress: Int, fromUser: Boolean) {
                    val actualVal = progress + item.minValue
                    updateText(actualVal)
                    if (fromUser) {
                        item.onValueChanged(actualVal)
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar) {}
                override fun onStopTrackingTouch(s: SeekBar) {}
            })
        }

        layout.addView(labelView)
        layout.addView(seekBar)
        return layout
    }

    private fun createToggleItem(item: SettingItem.Toggle): View {
        return CheckBox(context).apply {
            text = context.getString(item.labelResId)
            isChecked = item.isChecked
            textSize = 14f
            setTextColor(textColorPrimary)
            setPadding(8, 12, 8, 12)
            setOnCheckedChangeListener { _, isChecked ->
                item.onToggleChanged(isChecked)
            }
        }
    }
}

