package com.example.onehandcommander.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.model.SettingItem
import com.example.onehandcommander.utils.UiHelper

/**
 * 設定画面の View 生成およびバインディングを担うレンダラー
 * Material 3 デザイン指針に準拠し、カード型セクション、ピル型数値バッジ、
 * わかりやすい説明テキスト、高操作性のスイッチを提供します。
 */
class SettingsViewBinder(private val context: Context) {

    private val textColorPrimary: Int by lazy {
        ContextCompat.getColor(context, R.color.text_primary)
    }

    private val textColorSecondary: Int by lazy {
        ContextCompat.getColor(context, R.color.text_secondary)
    }

    private val colorPrimary: Int by lazy {
        ContextCompat.getColor(context, R.color.primary)
    }

    fun populateContainer(container: LinearLayout, items: List<SettingItem>) {
        container.removeAllViews()

        var currentCardLayout: LinearLayout? = null

        for (item in items) {
            when (item) {
                is SettingItem.SectionHeader -> {
                    // 新しいセクションカードを作成してコンテナに追加
                    val card = createSectionCard(item)
                    container.addView(card)
                    currentCardLayout = card
                }
                is SettingItem.Slider -> {
                    val sliderView = createSliderItem(item)
                    (currentCardLayout ?: container).addView(sliderView)
                }
                is SettingItem.Toggle -> {
                    val toggleView = createToggleItem(item)
                    (currentCardLayout ?: container).addView(toggleView)
                }
            }
        }
    }

    private fun createSectionCard(item: SettingItem.SectionHeader): LinearLayout {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(context, R.drawable.bg_settings_card)
            val pad = UiHelper.dpToPx(context, 16)
            setPadding(pad, pad, pad, pad)
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = UiHelper.dpToPx(context, 12)
                bottomMargin = UiHelper.dpToPx(context, 4)
            }
            layoutParams = params
        }

        // セクションタイトル
        val titleView = TextView(context).apply {
            text = context.getString(item.titleResId)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colorPrimary)
        }
        card.addView(titleView)

        // セクション説明文（あれば）
        if (item.subtitleResId != null) {
            val subtitleView = TextView(context).apply {
                text = context.getString(item.subtitleResId)
                textSize = 12f
                setTextColor(textColorSecondary)
                setPadding(0, UiHelper.dpToPx(context, 2), 0, UiHelper.dpToPx(context, 12))
            }
            card.addView(subtitleView)
        } else {
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    UiHelper.dpToPx(context, 8)
                )
            }
            card.addView(spacer)
        }

        return card
    }

    private fun createSliderItem(item: SettingItem.Slider): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padV = UiHelper.dpToPx(context, 10)
            setPadding(0, padV, 0, padV)
        }

        // 上段ヘッダー (左: ラベル+説明, 右: ピル型数値バッジ)
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val labelView = TextView(context).apply {
            text = context.getString(item.labelResId)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColorPrimary)
        }
        textContainer.addView(labelView)

        if (item.descriptionResId != null) {
            val descView = TextView(context).apply {
                text = context.getString(item.descriptionResId)
                textSize = 12f
                setTextColor(textColorSecondary)
                setPadding(0, UiHelper.dpToPx(context, 2), 0, 0)
            }
            textContainer.addView(descView)
        }

        // ピル型バッジ
        val badgeView = TextView(context).apply {
            background = ContextCompat.getDrawable(context, R.drawable.bg_pill_value)
            val padH = UiHelper.dpToPx(context, 10)
            val padV = UiHelper.dpToPx(context, 4)
            setPadding(padH, padV, padH, padV)
            textSize = 13f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(colorPrimary)
            gravity = Gravity.CENTER
        }

        val updateText = { value: Int ->
            val formatted = item.formatValue?.invoke(value) ?: "$value"
            badgeView.text = formatted
        }
        updateText(item.currentValue)

        headerLayout.addView(textContainer)
        headerLayout.addView(badgeView)
        layout.addView(headerLayout)

        // 下段: シークバー
        val seekBarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, UiHelper.dpToPx(context, 6), 0, 0)
        }

        val minLabel = TextView(context).apply {
            val minText = item.formatValue?.invoke(item.minValue) ?: "${item.minValue}"
            text = minText
            textSize = 10f
            setTextColor(textColorSecondary)
        }

        val maxLabel = TextView(context).apply {
            val maxText = item.formatValue?.invoke(item.maxValue) ?: "${item.maxValue}"
            text = maxText
            textSize = 10f
            setTextColor(textColorSecondary)
        }

        val seekBar = SeekBar(context).apply {
            max = item.maxValue - item.minValue
            progress = item.currentValue - item.minValue
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = UiHelper.dpToPx(context, 4)
                marginEnd = UiHelper.dpToPx(context, 4)
            }
            progressTintList = ColorStateList.valueOf(colorPrimary)
            thumbTintList = ColorStateList.valueOf(colorPrimary)

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

        seekBarLayout.addView(minLabel)
        seekBarLayout.addView(seekBar)
        seekBarLayout.addView(maxLabel)

        layout.addView(seekBarLayout)

        return layout
    }

    private fun createToggleItem(item: SettingItem.Toggle): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padV = UiHelper.dpToPx(context, 10)
            setPadding(0, padV, 0, padV)
            isClickable = true
            isFocusable = true
            background = ContextCompat.getDrawable(context, R.drawable.bg_item_ripple)
        }

        val textContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val labelView = TextView(context).apply {
            text = context.getString(item.labelResId)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(textColorPrimary)
        }
        textContainer.addView(labelView)

        if (item.descriptionResId != null) {
            val descView = TextView(context).apply {
                text = context.getString(item.descriptionResId)
                textSize = 12f
                setTextColor(textColorSecondary)
                setPadding(0, UiHelper.dpToPx(context, 2), 0, 0)
            }
            textContainer.addView(descView)
        }

        val switchView = SwitchCompat(context).apply {
            isChecked = item.isChecked
            thumbTintList = ColorStateList.valueOf(if (isChecked) colorPrimary else Color.GRAY)
            trackTintList = ColorStateList.valueOf(if (isChecked) ContextCompat.getColor(context, R.color.primary_container) else Color.DKGRAY)
            setOnCheckedChangeListener { _, checked ->
                thumbTintList = ColorStateList.valueOf(if (checked) colorPrimary else Color.GRAY)
                trackTintList = ColorStateList.valueOf(if (checked) ContextCompat.getColor(context, R.color.primary_container) else Color.DKGRAY)
                item.onToggleChanged(checked)
            }
        }

        // 行全体タップでスイッチトグル
        layout.setOnClickListener {
            switchView.toggle()
        }

        layout.addView(textContainer)
        layout.addView(switchView)

        return layout
    }
}


