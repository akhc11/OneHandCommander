package com.example.onehandcommander.core

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.view.WindowManager
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.ui.overlays.AppMenu
import com.example.onehandcommander.ui.overlays.FloatingButton
import com.example.onehandcommander.ui.overlays.Tenkey
import com.example.onehandcommander.ui.overlays.Touchpad

/**
 * UIオーバーレイの生成・状態適用（render）・依存管理を一手に引き受けるクラス
 */
class OverlayManager(
    private val context: Context,
    private val gestureDispatcher: GestureDispatcher,
    onMenuClosed: () -> Unit = {}
) {
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    val btnManager = FloatingButton(context, windowManager)
    val tenkeyManager = Tenkey(context, windowManager)
    val padManager = Touchpad(context, windowManager, gestureDispatcher)
    val appMenu = AppMenu(context, windowManager, onItemSelected = onMenuClosed)

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SavedData.KEY_SIZE_BUTTON, SavedData.KEY_BUTTON_ALPHA -> {
                btnManager.updateSize()
                btnManager.updateAlpha()
            }
            SavedData.KEY_SIZE_PAD_W, SavedData.KEY_SIZE_PAD_H, 
            SavedData.KEY_PAD_ALPHA, SavedData.KEY_PAD_X, SavedData.KEY_PAD_Y -> padManager.updateSize()
            
            SavedData.KEY_SIZE_TENKEY, SavedData.KEY_TENKEY_ALPHA, 
            SavedData.KEY_TENKEY_X, SavedData.KEY_TENKEY_Y -> tenkeyManager.updateSize()
        }
    }

    init {
        val metrics = windowManager.currentWindowMetrics
        val bounds = metrics.bounds
        padManager.setScreenSize(bounds.width(), bounds.height())
        SavedData.registerListener(prefsListener)
    }

    /**
     * ServiceState に基づき、全オーバーレイの描画・可視性を決定論的に更新する (MVI render)
     */
    fun render(state: ServiceState) {
        when (state) {
            is ServiceState.Suspended -> {
                btnManager.updateVisibility(false)
                tenkeyManager.hide()
                padManager.hide()
                appMenu.hide()
            }
            is ServiceState.Idle -> {
                btnManager.updateVisibility(true)
                tenkeyManager.hide()
                padManager.hide()
                appMenu.hide()
            }
            is ServiceState.MenuNormal -> {
                btnManager.updateVisibility(true)
                padManager.hide()
                appMenu.show()
                tenkeyManager.show()
            }
            is ServiceState.MenuSearch -> {
                btnManager.updateVisibility(true)
                padManager.hide()
                appMenu.show()
                appMenu.focusSearch()
                tenkeyManager.show()
            }
            is ServiceState.TouchpadActive -> {
                btnManager.updateVisibility(true)
                tenkeyManager.hide()
                appMenu.hide()
                padManager.show()
            }
        }
    }

    fun isKeyguardLocked(): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? android.app.KeyguardManager
        return keyguardManager?.isKeyguardLocked ?: false
    }

    fun cleanup() {
        SavedData.unregisterListener(prefsListener)
        btnManager.remove()
        tenkeyManager.remove()
        padManager.remove()
        appMenu.remove()
    }
}
