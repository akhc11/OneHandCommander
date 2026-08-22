package com.example.onehandcommander.ui.overlays

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.utils.UiHelper

/**
 * すべてのオーバーレイUIの基底クラス
 * 共通のライフサイクルとWindowManager操作を提供
 */
abstract class BaseOverlay(
    protected val context: Context,
    protected val windowManager: WindowManager
) {
    protected var overlayView: View? = null
    protected lateinit var params: WindowManager.LayoutParams

    /**
     * オーバーレイが表示中かどうか
     */
    fun isVisible(): Boolean = overlayView?.parent != null

    /**
     * オーバーレイを表示
     */
    open fun show() {
        if (isVisible()) {
            bringToFront()
            return
        }
        
        val view = overlayView ?: createView().also { overlayView = it }
        if (view.parent == null) {
            params = createLayoutParams()
            windowManager.addView(view, params)
            onShown()
        }
    }

    /**
     * 最前面に再配置（WindowManager の Z-order 更新）
     */
    open fun bringToFront() {
        overlayView?.let { view ->
            if (view.parent != null) {
                windowManager.removeView(view)
                windowManager.addView(view, params)
            }
        }
    }

    /**
     * オーバーレイを非表示（WindowManagerから物理的に削除）
     */
    open fun hide() {
        overlayView?.let { view ->
            if (view.parent != null) {
                windowManager.removeView(view)
                onHidden()
            }
        }
    }

    /**
     * オーバーレイを完全に削除
     */
    open fun remove() {
        hide()
        cleanup()
        overlayView = null
        onRemoved()
    }

    /**
     * オーバーレイを強制的に削除（スリープ復帰時などに使用）
     * WindowManagerへの参照が無効になっている可能性があるため、
     * エラーを無視して確実にクリーンアップする
     */
    fun forceRemove() {
        try {
            overlayView?.let { view ->
                if (view.parent != null) {
                    windowManager.removeView(view)
                }
            }
        } catch (e: Exception) {
            // WindowManagerが無効な場合など、エラーを無視
            android.util.Log.w("BaseOverlay", "Failed to remove view during forceRemove", e)
        } finally {
            cleanup()
            overlayView = null
            onRemoved()
        }
    }

    /**
     * WindowManager.LayoutParamsの位置を更新
     */
    protected fun updatePosition(x: Int, y: Int) {
        if (isVisible()) {
            params.x = x
            params.y = y
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    /**
     * 透明度を更新（0-100）
     */
    protected fun updateAlpha(alphaPercent: Int) {
        if (isVisible()) {
            params.alpha = UiHelper.percentToAlpha(alphaPercent)
            overlayView?.let { windowManager.updateViewLayout(it, params) }
        }
    }

    /**
     * 基本的なLayoutParamsを作成
     */
    protected open fun createLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
    }

    /**
     * Viewを生成（サブクラスで実装）
     */
    protected abstract fun createView(): View

    /**
     * 可視性変更時のコールバック
     */
    protected open fun onVisibilityChanged(visible: Boolean) {}

    /**
     * 表示完了時のコールバック
     */
    protected open fun onShown() {}

    /**
     * 非表示完了時のコールバック
     */
    protected open fun onHidden() {}

    /**
     * 削除完了時のコールバック
     */
    protected open fun onRemoved() {}

    /**
     * リソースをクリーンアップ（サブクラスでオーバーライド）
     * リスナー解除、コールバックのnull化、ジョブキャンセルなどを実装
     */
    protected open fun cleanup() {
        // サブクラスで必要に応じてオーバーライド
    }
}
