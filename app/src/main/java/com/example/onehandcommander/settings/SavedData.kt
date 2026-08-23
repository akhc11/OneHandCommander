package com.example.onehandcommander.settings

import android.content.Context
import android.content.SharedPreferences
import com.example.onehandcommander.settings.model.ButtonConfig
import com.example.onehandcommander.settings.model.TenkeyConfig
import com.example.onehandcommander.settings.model.TouchpadConfig
import com.example.onehandcommander.ui.overlays.model.MenuSlotAction
import com.example.onehandcommander.utils.Constants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * アプリケーション全体の設定永続化・状態同期を担当するリポジトリ兼シングルトン。
 * - バックグラウンド非同期書き込み (.apply() / Coroutines Dispatchers.IO)
 * - ユニットテスト用の SharedPreferences 差し替え対応
 * - ドメインモデル（ButtonConfig, TenkeyConfig, TouchpadConfig）との相互変換
 */
object SavedData {
    private const val PREF_NAME = "ohc_SavedData"
    private const val KEY_INITIALIZED = "is_initialized"

    // SharedPreferences キー定数（OverlayManager 等で監視される公開キー）
    const val KEY_POS_X = "pos_x"
    const val KEY_POS_Y = "pos_y"
    const val KEY_SIZE_BUTTON = "size_button"
    const val KEY_ENABLE_VIBRATION = "enable_vibration"
    const val KEY_BUTTON_ALPHA = "button_alpha"

    const val KEY_SIZE_TENKEY = "size_tenkey"
    const val KEY_TENKEY_ALPHA = "tenkey_alpha"
    const val KEY_TENKEY_X = "tenkey_x"
    const val KEY_TENKEY_Y = "tenkey_y"

    const val KEY_SIZE_PAD_W = "size_pad_w"
    const val KEY_SIZE_PAD_H = "size_pad_h"
    const val KEY_PAD_ALPHA = "pad_alpha"
    const val KEY_PAD_X = "pad_x"
    const val KEY_PAD_Y = "pad_y"
    const val KEY_CURSOR_SPEED = "cursor_speed"
    const val KEY_CURSOR_THRESHOLD = "cursor_threshold"
    const val KEY_TOUCHPAD_LP_DELAY = "touchpad_lp_delay"
    const val KEY_TOUCHPAD_LP_PLAY = "touchpad_lp_play"
    const val KEY_APP_MENU_X = "app_menu_x"
    const val KEY_APP_MENU_Y = "app_menu_y"
    private const val KEY_RECENT_APPS = "recent_apps_list"

    private lateinit var prefs: SharedPreferences
    private val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
    private val internalPrefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        for (listener in listeners) {
            listener.onSharedPreferenceChanged(sharedPreferences, key)
        }
    }

    /**
     * 初期化処理
     */
    fun init(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.registerOnSharedPreferenceChangeListener(internalPrefChangeListener)
            if (!prefs.getBoolean(KEY_INITIALIZED, false)) {
                prefs.edit().putBoolean(KEY_INITIALIZED, true).apply()
            }
        }
    }

    /**
     * ユニットテスト用: SharedPreferences の明示的インジェクション
     */
    fun setPreferencesForTesting(sharedPreferences: SharedPreferences) {
        if (::prefs.isInitialized) {
            prefs.unregisterOnSharedPreferenceChangeListener(internalPrefChangeListener)
        }
        prefs = sharedPreferences
        prefs.registerOnSharedPreferenceChangeListener(internalPrefChangeListener)
    }

    // ==========================================
    // ドメインモデル単位の操作 (非同期 .apply() & Coroutines)
    // ==========================================

    fun getButtonConfig(): ButtonConfig = ButtonConfig(
        sizeDp = getButtonSize(),
        alphaPercent = getButtonAlpha(),
        isVibrationEnabled = isVibrationEnabled(),
        posX = getPositionX(),
        posY = getPositionY()
    )

    fun saveButtonConfig(config: ButtonConfig) {
        prefs.edit()
            .putInt(KEY_SIZE_BUTTON, config.sizeDp)
            .putInt(KEY_BUTTON_ALPHA, config.alphaPercent)
            .putBoolean(KEY_ENABLE_VIBRATION, config.isVibrationEnabled)
            .putInt(KEY_POS_X, config.posX)
            .putInt(KEY_POS_Y, config.posY)
            .apply()
    }

    suspend fun saveButtonConfigAsync(
        config: ButtonConfig,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        saveButtonConfig(config)
    }

    fun getTenkeyConfig(): TenkeyConfig = TenkeyConfig(
        sizeDp = getTenkeySize(),
        alphaPercent = getTenkeyAlpha(),
        posX = getTenkeyX(),
        posY = getTenkeyY()
    )

    fun saveTenkeyConfig(config: TenkeyConfig) {
        prefs.edit()
            .putInt(KEY_SIZE_TENKEY, config.sizeDp)
            .putInt(KEY_TENKEY_ALPHA, config.alphaPercent)
            .putInt(KEY_TENKEY_X, config.posX)
            .putInt(KEY_TENKEY_Y, config.posY)
            .apply()
    }

    suspend fun saveTenkeyConfigAsync(
        config: TenkeyConfig,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        saveTenkeyConfig(config)
    }

    fun getTouchpadConfig(): TouchpadConfig = TouchpadConfig(
        widthDp = getTouchpadWidth(),
        heightDp = getTouchpadHeight(),
        alphaPercent = getTouchpadAlpha(),
        posX = getTouchpadX(),
        posY = getTouchpadY(),
        cursorSpeed = getCursorSpeed(),
        cursorThresholdPx = getCursorThreshold(),
        longPressDelayMs = getTouchpadLongPressDelay(),
        longPressPlayPx = getTouchpadLongPressPlay()
    )

    fun saveTouchpadConfig(config: TouchpadConfig) {
        prefs.edit()
            .putInt(KEY_SIZE_PAD_W, config.widthDp)
            .putInt(KEY_SIZE_PAD_H, config.heightDp)
            .putInt(KEY_PAD_ALPHA, config.alphaPercent)
            .putInt(KEY_PAD_X, config.posX)
            .putInt(KEY_PAD_Y, config.posY)
            .putFloat(KEY_CURSOR_SPEED, config.cursorSpeed)
            .putInt(KEY_CURSOR_THRESHOLD, config.cursorThresholdPx)
            .putLong(KEY_TOUCHPAD_LP_DELAY, config.longPressDelayMs)
            .putInt(KEY_TOUCHPAD_LP_PLAY, config.longPressPlayPx)
            .apply()
    }

    suspend fun saveTouchpadConfigAsync(
        config: TouchpadConfig,
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ) = withContext(dispatcher) {
        saveTouchpadConfig(config)
    }

    // ==========================================
    // 個別設定アクセサ（すべて apply() による完全非同期ディスク保存）
    // ==========================================

    // ----- メインボタン -----
    fun savePosition(x: Int, y: Int) = prefs.edit().putInt(KEY_POS_X, x).putInt(KEY_POS_Y, y).apply()
    fun getPositionX() = prefs.getInt(KEY_POS_X, Constants.Defaults.BUTTON_POS_X)
    fun getPositionY() = prefs.getInt(KEY_POS_Y, Constants.Defaults.BUTTON_POS_Y)

    fun getButtonSize() = prefs.getInt(KEY_SIZE_BUTTON, Constants.Defaults.BUTTON_SIZE_DP)
    fun saveButtonSize(dp: Int) = prefs.edit().putInt(KEY_SIZE_BUTTON, dp).apply()

    fun isVibrationEnabled() = prefs.getBoolean(KEY_ENABLE_VIBRATION, Constants.Defaults.VIBRATION_ENABLED)
    fun setVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_VIBRATION, enabled).apply()

    fun getButtonAlpha() = prefs.getInt(KEY_BUTTON_ALPHA, Constants.Defaults.BUTTON_ALPHA)
    fun saveButtonAlpha(alphaPercent: Int) = prefs.edit().putInt(KEY_BUTTON_ALPHA, alphaPercent).apply()

    // ----- テンキー -----
    fun getTenkeySize() = prefs.getInt(KEY_SIZE_TENKEY, Constants.Defaults.TENKEY_SIZE_DP)
    fun saveTenkeySize(dp: Int) = prefs.edit().putInt(KEY_SIZE_TENKEY, dp).apply()

    fun getTenkeyAlpha() = prefs.getInt(KEY_TENKEY_ALPHA, Constants.Defaults.TENKEY_ALPHA)
    fun saveTenkeyAlpha(alphaPercent: Int) = prefs.edit().putInt(KEY_TENKEY_ALPHA, alphaPercent).apply()

    fun getTenkeyX() = prefs.getInt(KEY_TENKEY_X, Constants.Defaults.TENKEY_X)
    fun saveTenkeyX(x: Int) = prefs.edit().putInt(KEY_TENKEY_X, x).apply()

    fun getTenkeyY() = prefs.getInt(KEY_TENKEY_Y, Constants.Defaults.TENKEY_Y)
    fun saveTenkeyY(y: Int) = prefs.edit().putInt(KEY_TENKEY_Y, y).apply()

    // ----- タッチパッド / カーソル -----
    fun getTouchpadWidth() = prefs.getInt(KEY_SIZE_PAD_W, Constants.Defaults.TOUCHPAD_WIDTH_DP)
    fun saveTouchpadWidth(dp: Int) = prefs.edit().putInt(KEY_SIZE_PAD_W, dp).apply()

    fun getTouchpadHeight() = prefs.getInt(KEY_SIZE_PAD_H, Constants.Defaults.TOUCHPAD_HEIGHT_DP)
    fun saveTouchpadHeight(dp: Int) = prefs.edit().putInt(KEY_SIZE_PAD_H, dp).apply()

    fun getTouchpadAlpha() = prefs.getInt(KEY_PAD_ALPHA, Constants.Defaults.TOUCHPAD_ALPHA)
    fun saveTouchpadAlpha(alphaPercent: Int) = prefs.edit().putInt(KEY_PAD_ALPHA, alphaPercent).apply()

    fun getTouchpadX() = prefs.getInt(KEY_PAD_X, Constants.Defaults.TOUCHPAD_X)
    fun saveTouchpadX(x: Int) = prefs.edit().putInt(KEY_PAD_X, x).apply()

    fun getTouchpadY() = prefs.getInt(KEY_PAD_Y, Constants.Defaults.TOUCHPAD_Y)
    fun saveTouchpadY(y: Int) = prefs.edit().putInt(KEY_PAD_Y, y).apply()

    fun getCursorSpeed() = prefs.getFloat(KEY_CURSOR_SPEED, Constants.Defaults.CURSOR_SPEED)
    fun saveCursorSpeed(speed: Float) = prefs.edit().putFloat(KEY_CURSOR_SPEED, speed).apply()

    fun getCursorThreshold() = prefs.getInt(KEY_CURSOR_THRESHOLD, Constants.Defaults.CURSOR_THRESHOLD)
    fun saveCursorThreshold(px: Int) = prefs.edit().putInt(KEY_CURSOR_THRESHOLD, px).apply()

    fun getTouchpadLongPressDelay() = prefs.getLong(KEY_TOUCHPAD_LP_DELAY, Constants.Defaults.TOUCHPAD_LP_DELAY_MS)
    fun saveTouchpadLongPressDelay(ms: Long) = prefs.edit().putLong(KEY_TOUCHPAD_LP_DELAY, ms).apply()

    fun getTouchpadLongPressPlay() = prefs.getInt(KEY_TOUCHPAD_LP_PLAY, Constants.Defaults.TOUCHPAD_LP_PLAY_PX)
    fun saveTouchpadLongPressPlay(px: Int) = prefs.edit().putInt(KEY_TOUCHPAD_LP_PLAY, px).apply()

    // ----- アプリメニュー配置座標 (-1 の場合は画面中央) -----
    fun getAppMenuX() = prefs.getInt(KEY_APP_MENU_X, -1)
    fun getAppMenuY() = prefs.getInt(KEY_APP_MENU_Y, -1)
    fun saveAppMenuPosition(x: Int, y: Int) = prefs.edit()
        .putInt(KEY_APP_MENU_X, x)
        .putInt(KEY_APP_MENU_Y, y)
        .apply()

    // ----- 最近使ったアプリ履歴 (カンマ区切り文字列で永続化) -----
    fun getRecentApps(): List<String> {
        val raw = prefs.getString(KEY_RECENT_APPS, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }

    // ----- メニュースロット（01〜40）カスタム割り当て保存・読み込み -----
    private fun getSlotKey(slotIndex: Int) = "menu_slot_action_$slotIndex"

    fun getMenuSlotAction(context: Context, slotIndex: Int): MenuSlotAction {
        if (!::prefs.isInitialized) {
            init(context)
        }
        val json = prefs.getString(getSlotKey(slotIndex), null)
        return MenuSlotAction.fromJson(json)
    }

    fun saveMenuSlotAction(context: Context, slotIndex: Int, action: MenuSlotAction) {
        if (!::prefs.isInitialized) {
            init(context)
        }
        prefs.edit().putString(getSlotKey(slotIndex), action.toJson()).apply()
    }

    fun resetMenuSlotAction(context: Context, slotIndex: Int) {
        if (!::prefs.isInitialized) {
            init(context)
        }
        prefs.edit().remove(getSlotKey(slotIndex)).apply()
    }

    fun hasCustomSlotAction(context: Context, slotIndex: Int): Boolean {
        if (!::prefs.isInitialized) {
            init(context)
        }
        return prefs.contains(getSlotKey(slotIndex))
    }

    fun addRecentApp(packageName: String) {
        if (packageName.isBlank()) return
        val current = getRecentApps().toMutableList()
        current.remove(packageName)
        current.add(0, packageName)
        val trimmed = current.take(10)
        prefs.edit().putString(KEY_RECENT_APPS, trimmed.joinToString(",")).apply()
    }

    /**
     * すべてのパラメータを初期値（デフォルト設定）に一括リセット
     */
    fun resetToDefaults() {
        prefs.edit()
            .putInt(KEY_POS_X, Constants.Defaults.BUTTON_POS_X)
            .putInt(KEY_POS_Y, Constants.Defaults.BUTTON_POS_Y)
            .putInt(KEY_SIZE_BUTTON, Constants.Defaults.BUTTON_SIZE_DP)
            .putInt(KEY_BUTTON_ALPHA, Constants.Defaults.BUTTON_ALPHA)
            .putBoolean(KEY_ENABLE_VIBRATION, Constants.Defaults.VIBRATION_ENABLED)
            .putInt(KEY_SIZE_TENKEY, Constants.Defaults.TENKEY_SIZE_DP)
            .putInt(KEY_TENKEY_ALPHA, Constants.Defaults.TENKEY_ALPHA)
            .putInt(KEY_TENKEY_X, Constants.Defaults.TENKEY_X)
            .putInt(KEY_TENKEY_Y, Constants.Defaults.TENKEY_Y)
            .putInt(KEY_SIZE_PAD_W, Constants.Defaults.TOUCHPAD_WIDTH_DP)
            .putInt(KEY_SIZE_PAD_H, Constants.Defaults.TOUCHPAD_HEIGHT_DP)
            .putInt(KEY_PAD_ALPHA, Constants.Defaults.TOUCHPAD_ALPHA)
            .putInt(KEY_PAD_X, Constants.Defaults.TOUCHPAD_X)
            .putInt(KEY_PAD_Y, Constants.Defaults.TOUCHPAD_Y)
            .putFloat(KEY_CURSOR_SPEED, Constants.Defaults.CURSOR_SPEED)
            .putInt(KEY_CURSOR_THRESHOLD, Constants.Defaults.CURSOR_THRESHOLD)
            .putLong(KEY_TOUCHPAD_LP_DELAY, Constants.Defaults.TOUCHPAD_LP_DELAY_MS)
            .putInt(KEY_TOUCHPAD_LP_PLAY, Constants.Defaults.TOUCHPAD_LP_PLAY_PX)
            .putInt(KEY_APP_MENU_X, -1)
            .putInt(KEY_APP_MENU_Y, -1)
            .apply()
    }

    // ==========================================
    // 変更リスナー管理
    // ==========================================

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners.remove(listener)
    }
}
