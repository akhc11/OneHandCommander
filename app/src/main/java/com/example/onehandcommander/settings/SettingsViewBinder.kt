package com.example.onehandcommander.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.example.onehandcommander.settings.model.SettingItem

/**
 * 設定画面の View 生成およびバインディングを担うレンダラー
 */
class SettingsViewBinder(private val context: Context) {

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
            setTextColor(Color.parseColor("#1E293B"))
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
            setTextColor(Color.parseColor("#334155"))
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
            setTextColor(Color.parseColor("#334155"))
            setPadding(8, 12, 8, 12)
            setOnCheckedChangeListener { _, isChecked ->
                item.onToggleChanged(isChecked)
            }
        }
    }
}
