package com.example.onehandcommander.ui.overlays

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.ui.drawables.HudCornerDrawable
import com.example.onehandcommander.utils.Constants
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration

/**
 * テンキー入力UIの管理
 * - HudCornerDrawable によるサイバー/HUDコーナー枠線の一元描画（XML多重作成の完全廃止）
 * - 親GridLayoutレベルでの一元タッチハンドリングと $O(1)$ 高速ヒットテスト
 * - マルチタッチ耐性（activePointerId 追跡）
 * - 1〜40番のアプリ起動用スワイプ入力（単押し、2桁スワイプ入力、スワイプアウトによるゾロ目入力）
 */
class Tenkey(
    context: Context,
    windowManager: WindowManager
) : BaseOverlay(context, windowManager) {

    // コールバック
    var onInput: ((String) -> Unit)? = null
    var onInputUpdating: ((String) -> Unit)? = null

    private var startNumberBtn: Button? = null
    private var currentHoverBtn: Button? = null
    private var hasLeftStartBtn = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private val tenkeyButtons = mutableListOf<Button>()

    // コーナー描画用キャッシュDrawable（不要なGC・アロケーションを抑止）
    private val transparentDrawable = ColorDrawable(Color.TRANSPARENT)
    private val highlightDrawable = ColorDrawable(Color.parseColor(Constants.UI.Colors.TENKEY_HIGHLIGHT))
    private val corner1Drawable by lazy { HudCornerDrawable(HudCornerDrawable.TOP_LEFT) }
    private val corner3Drawable by lazy { HudCornerDrawable(HudCornerDrawable.TOP_RIGHT) }
    private val corner7Drawable by lazy { HudCornerDrawable(HudCornerDrawable.BOTTOM_LEFT) }
    private val corner9Drawable by lazy { HudCornerDrawable(HudCornerDrawable.BOTTOM_RIGHT) }

    override fun createView(): View {
        return LayoutInflater.from(context).inflate(R.layout.layout_tenkey, null).also { view ->
            val alpha = SavedData.getTenkeyAlpha()
            view.alpha = UiHelper.percentToAlpha(alpha)
            val sizeDp = SavedData.getTenkeySize()
            applySizeAndStyles(view, UiHelper.dpToPx(context, sizeDp))
            setupSwipeInput(view)
        }
    }

    override fun createLayoutParams(): WindowManager.LayoutParams {
        val alpha = SavedData.getTenkeyAlpha()
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = SavedData.getTenkeyX()
            y = SavedData.getTenkeyY()
            this.alpha = UiHelper.percentToAlpha(alpha)
        }
    }

    /**
     * サイズ・透明度・座標の更新（設定変更時に即座に反映）
     */
    fun updateSize() {
        val sizeDp = SavedData.getTenkeySize()
        val sizePx = UiHelper.dpToPx(context, sizeDp)
        val alpha = SavedData.getTenkeyAlpha()

        params.x = SavedData.getTenkeyX()
        params.y = SavedData.getTenkeyY()

        overlayView?.let { view ->
            view.alpha = UiHelper.percentToAlpha(alpha)
            applySizeAndStyles(view, sizePx)
            if (isVisible()) {
                windowManager.updateViewLayout(view, params)
            }
        }
    }

    override fun show() {
        super.show()
        val alpha = SavedData.getTenkeyAlpha()
        params.x = SavedData.getTenkeyX()
        params.y = SavedData.getTenkeyY()

        overlayView?.let { view ->
            view.alpha = UiHelper.percentToAlpha(alpha)
            windowManager.updateViewLayout(view, params)
        }
    }

    override fun onHidden() {
        resetState()
        super.onHidden()
    }

    override fun cleanup() {
        resetState()
        super.cleanup()
    }

    private fun resetState() {
        highlight(currentHoverBtn, false)
        highlight(startNumberBtn, false)
        startNumberBtn = null
        currentHoverBtn = null
        hasLeftStartBtn = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        onInputUpdating?.invoke("")
    }

    private fun applySizeAndStyles(view: View, sizePx: Int) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                if (child is Button) {
                    val p = child.layoutParams
                    if (p.width != sizePx || p.height != sizePx) {
                        p.width = sizePx
                        p.height = sizePx
                        child.layoutParams = p
                    }
                    child.background = getDefaultBackground(child.id)
                    child.text = ""
                }
            }
        }
    }

    private fun setupSwipeInput(view: View) {
        tenkeyButtons.clear()
        val ids = listOf(
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3,
            R.id.btn_4, R.id.btn_5, R.id.btn_6,
            R.id.btn_7, R.id.btn_8, R.id.btn_9
        )
        ids.forEach { id ->
            val btn = view.findViewById<Button>(id)
            if (btn != null) {
                tenkeyButtons.add(btn)
            }
        }

        // 親 GridLayout 全体でタッチイベントをキャプチャ
        view.setOnTouchListener { _, event -> handleRootTouch(event) }
    }

    private fun handleRootTouch(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(0)
                val initialBtn = findButtonFast(event.x, event.y) ?: return false
                startNumberBtn = initialBtn
                currentHoverBtn = initialBtn
                hasLeftStartBtn = false

                highlight(initialBtn, true)
                Vibration.vibrateTick()
                onInputUpdating?.invoke(getNumberFromButton(initialBtn))
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == MotionEvent.INVALID_POINTER_ID) return false
                val pointerIndex = event.findPointerIndex(activePointerId)
                if (pointerIndex < 0) return true

                val currentX = event.getX(pointerIndex)
                val currentY = event.getY(pointerIndex)
                val hovered = findButtonFast(currentX, currentY)

                if (hovered != null && hovered != startNumberBtn) {
                    hasLeftStartBtn = true
                } else if (hovered == null && startNumberBtn != null) {
                    hasLeftStartBtn = true
                }

                if (hovered != currentHoverBtn) {
                    highlight(currentHoverBtn, false)
                    currentHoverBtn = hovered
                    highlight(currentHoverBtn, true)

                    if (startNumberBtn != null) {
                        val s = getNumberFromButton(startNumberBtn)
                        val previewStr = when {
                            hovered == null -> if (hasLeftStartBtn) s + s else s
                            hovered == startNumberBtn -> if (hasLeftStartBtn) s + s else s
                            else -> s + getNumberFromButton(hovered)
                        }
                        onInputUpdating?.invoke(previewStr)
                    }
                    if (hovered != null) {
                        Vibration.vibrateTick()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                highlight(currentHoverBtn, false)
                highlight(startNumberBtn, false)

                if (startNumberBtn != null && event.actionMasked == MotionEvent.ACTION_UP) {
                    val s = getNumberFromButton(startNumberBtn)
                    val finalNumber = when {
                        currentHoverBtn != null -> {
                            if (startNumberBtn == currentHoverBtn) {
                                if (hasLeftStartBtn) s + s else s
                            } else {
                                s + getNumberFromButton(currentHoverBtn)
                            }
                        }
                        hasLeftStartBtn -> s + s
                        else -> null
                    }

                    if (finalNumber != null) {
                        onInput?.invoke(finalNumber)
                    } else {
                        onInputUpdating?.invoke("")
                    }
                } else {
                    onInputUpdating?.invoke("")
                }

                startNumberBtn = null
                currentHoverBtn = null
                hasLeftStartBtn = false
                activePointerId = MotionEvent.INVALID_POINTER_ID
                return true
            }

            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                }
                return true
            }
        }
        return false
    }

    /**
     * 高速 $O(1)$ ヒットテスト:
     * 親GridLayoutのローカル座標 (rootX, rootY) で各Buttonの矩形を即座に判定
     */
    private fun findButtonFast(rootX: Float, rootY: Float): Button? {
        for (btn in tenkeyButtons) {
            if (rootX >= btn.left && rootX <= btn.right &&
                rootY >= btn.top && rootY <= btn.bottom) {
                return btn
            }
        }
        return null
    }

    private fun getNumberFromButton(btn: Button?): String {
        if (btn == null) return ""
        return when (btn.id) {
            R.id.btn_0 -> "0"
            R.id.btn_1 -> "1"
            R.id.btn_2 -> "2"
            R.id.btn_3 -> "3"
            R.id.btn_4 -> "4"
            R.id.btn_5 -> "5"
            R.id.btn_6 -> "6"
            R.id.btn_7 -> "7"
            R.id.btn_8 -> "8"
            R.id.btn_9 -> "9"
            else -> ""
        }
    }

    private fun getDefaultBackground(btnId: Int): Drawable {
        return when (btnId) {
            R.id.btn_1 -> corner1Drawable
            R.id.btn_3 -> corner3Drawable
            R.id.btn_7 -> corner7Drawable
            R.id.btn_9 -> corner9Drawable
            else -> transparentDrawable
        }
    }

    private fun highlight(btn: Button?, on: Boolean) {
        if (btn == null) return
        btn.background = if (on) highlightDrawable else getDefaultBackground(btn.id)
    }
}
