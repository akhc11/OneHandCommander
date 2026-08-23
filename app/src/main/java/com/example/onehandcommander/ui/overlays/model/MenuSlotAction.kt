package com.example.onehandcommander.ui.overlays.model

import org.json.JSONObject

/**
 * DirectIconType
 * DirectIconDrawable で描画する幾何学アイコン種別
 */
enum class DirectIconType {
    HOME,
    BACK,
    RECENTS,
    NOTIFICATIONS,
    SCREENSHOT,
    POWER_DIALOG,
    OPEN_FILE,
    TOUCHPAD,
    SETTINGS,
    DEFAULT_APP
}

/**
 * OS システム操作種別
 */
enum class SystemActionType(val displayName: String, val iconType: DirectIconType) {
    HOME("ホーム", DirectIconType.HOME),
    BACK("戻る", DirectIconType.BACK),
    RECENTS("タスク履歴", DirectIconType.RECENTS),
    NOTIFICATIONS("通知パネル", DirectIconType.NOTIFICATIONS),
    SCREENSHOT("スクリーンショット", DirectIconType.SCREENSHOT),
    POWER_DIALOG("電源メニュー", DirectIconType.POWER_DIALOG);

    companion object {
        fun fromString(name: String?): SystemActionType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * 便利機能種別
 */
enum class AppFeatureType(val displayName: String, val iconType: DirectIconType) {
    LAUNCH_TOUCHPAD("タッチパッド起動", DirectIconType.TOUCHPAD),
    OPEN_SETTINGS("設定画面を開く", DirectIconType.SETTINGS);

    companion object {
        fun fromString(name: String?): AppFeatureType? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

/**
 * MenuSlotAction
 * 01〜40のスロットに割り当て可能なアクションモデル
 */
sealed class MenuSlotAction {
    abstract val label: String
    abstract val iconType: DirectIconType

    // ① アプリ起動
    data class LaunchApp(
        val packageName: String,
        val appLabel: String
    ) : MenuSlotAction() {
        override val label: String get() = appLabel
        override val iconType: DirectIconType get() = DirectIconType.DEFAULT_APP
    }

    // ② OSシステム操作 (AccessibilityService)
    data class SystemCommand(
        val actionType: SystemActionType
    ) : MenuSlotAction() {
        override val label: String get() = actionType.displayName
        override val iconType: DirectIconType get() = actionType.iconType
    }

    // ③ ファイル / ドキュメントを開く
    data class OpenFile(
        val uriString: String,
        val fileName: String,
        val mimeType: String? = null
    ) : MenuSlotAction() {
        override val label: String get() = fileName
        override val iconType: DirectIconType get() = DirectIconType.OPEN_FILE
    }

    // ④ アプリ独自機能
    data class CustomFeature(
        val featureType: AppFeatureType
    ) : MenuSlotAction() {
        override val label: String get() = featureType.displayName
        override val iconType: DirectIconType get() = featureType.iconType
    }

    // ⑤ デフォルト（自動アプリ割り当て）
    object AutoApp : MenuSlotAction() {
        override val label: String get() = ""
        override val iconType: DirectIconType get() = DirectIconType.DEFAULT_APP
    }

    fun toJson(): String {
        val json = JSONObject()
        when (this) {
            is LaunchApp -> {
                json.put("type", "LAUNCH_APP")
                json.put("package", packageName)
                json.put("label", appLabel)
            }
            is SystemCommand -> {
                json.put("type", "SYSTEM_COMMAND")
                json.put("action", actionType.name)
            }
            is OpenFile -> {
                json.put("type", "OPEN_FILE")
                json.put("uri", uriString)
                json.put("name", fileName)
                mimeType?.let { json.put("mime", it) }
            }
            is CustomFeature -> {
                json.put("type", "CUSTOM_FEATURE")
                json.put("feature", featureType.name)
            }
            is AutoApp -> {
                json.put("type", "AUTO_APP")
            }
        }
        return json.toString()
    }

    companion object {
        fun fromJson(jsonStr: String?): MenuSlotAction {
            if (jsonStr.isNullOrBlank()) return AutoApp
            return try {
                val json = JSONObject(jsonStr)
                when (json.optString("type")) {
                    "LAUNCH_APP" -> LaunchApp(
                        packageName = json.getString("package"),
                        appLabel = json.optString("label", "")
                    )
                    "SYSTEM_COMMAND" -> {
                        val action = SystemActionType.fromString(json.getString("action"))
                        if (action != null) SystemCommand(action) else AutoApp
                    }
                    "OPEN_FILE" -> OpenFile(
                        uriString = json.getString("uri"),
                        fileName = json.getString("name"),
                        mimeType = json.optString("mime", null)
                    )
                    "CUSTOM_FEATURE" -> {
                        val feat = AppFeatureType.fromString(json.getString("feature"))
                        if (feat != null) CustomFeature(feat) else AutoApp
                    }
                    else -> AutoApp
                }
            } catch (e: Exception) {
                AutoApp
            }
        }
    }
}
