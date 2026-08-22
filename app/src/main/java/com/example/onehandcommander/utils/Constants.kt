package com.example.onehandcommander.utils

/**
 * アプリ全体で使用する定数を管理
 */
object Constants {
    const val LOG_TAG = "OneHandCommander"
    
    // タイミング設定
    const val SCREEN_UNLOCK_DEBOUNCE_MS = 1000L
    
    // タッチ・ジェスチャー設定
    const val SWIPE_THRESHOLD_DP = 20
    const val CURSOR_MOVE_THRESHOLD_DEFAULT = 0
    const val MOVE_MODE_DISTANCE_THRESHOLD = 10
    
    // ジェスチャー設定
    const val GESTURE_CLICK_DURATION_MS = 10L
    const val GESTURE_LONG_CLICK_DURATION_MS = 1000L
    const val GESTURE_MAX_STROKE_DURATION_MS = 5000L
    
    const val ALPHA_PERCENTAGE_DIVISOR = 100f
    
    // デフォルト値および設定レンジ
    object Defaults {
        const val BUTTON_SIZE_DP = 56
        const val BUTTON_SIZE_MIN = 20
        const val BUTTON_SIZE_MAX = 100
        const val BUTTON_ALPHA = 100
        const val BUTTON_ALPHA_MIN = 10
        const val BUTTON_ALPHA_MAX = 100
        const val BUTTON_POS_X = 0
        const val BUTTON_POS_Y = 1100
        
        const val TENKEY_SIZE_DP = 55
        const val TENKEY_SIZE_MIN = 30
        const val TENKEY_SIZE_MAX = 150
        const val TENKEY_ALPHA = 100
        const val TENKEY_ALPHA_MIN = 10
        const val TENKEY_ALPHA_MAX = 100
        const val TENKEY_X = 20
        const val TENKEY_Y = 1200
        const val TENKEY_POS_MAX = 3000
        const val TENKEY_POS_MIN = 0
        
        const val TOUCHPAD_WIDTH_DP = 220
        const val TOUCHPAD_WIDTH_MIN = 50
        const val TOUCHPAD_WIDTH_MAX = 500
        const val TOUCHPAD_HEIGHT_DP = 180
        const val TOUCHPAD_HEIGHT_MIN = 50
        const val TOUCHPAD_HEIGHT_MAX = 800
        const val TOUCHPAD_ALPHA = 80
        const val TOUCHPAD_ALPHA_MIN = 10
        const val TOUCHPAD_ALPHA_MAX = 100
        const val TOUCHPAD_X = 20
        const val TOUCHPAD_Y = 1400
        const val TOUCHPAD_POS_MAX = 3000
        const val TOUCHPAD_POS_MIN = 0
        
        const val CURSOR_SPEED = 2.0f
        const val CURSOR_SPEED_MIN = 1.0f
        const val CURSOR_SPEED_MAX = 10.0f
        const val CURSOR_SPEED_STEP = 0.1f
        
        const val CURSOR_THRESHOLD = 0
        const val CURSOR_THRESHOLD_MIN = 0
        const val CURSOR_THRESHOLD_MAX = 20
        
        const val TOUCHPAD_LP_DELAY_MS = 500L
        const val TOUCHPAD_LP_DELAY_MIN = 200
        const val TOUCHPAD_LP_DELAY_MAX = 2000
        
        const val TOUCHPAD_LP_PLAY_PX = 10
        const val TOUCHPAD_LP_PLAY_MIN = 0
        const val TOUCHPAD_LP_PLAY_MAX = 50
        
        const val VIBRATION_ENABLED = true
    }
    
    // システムアクション
    object SystemActions {
        const val BACK = "BACK"
        const val HOME = "HOME"
        const val RECENTS = "RECENTS"
    }

    // UI設定
    object UI {
        const val ICON_CACHE_SIZE_MB = 4
        const val TOAST_DURATION_SHORT = android.widget.Toast.LENGTH_SHORT
        
        object Colors {
            const val TENKEY_HIGHLIGHT = "#88FFFFFF"
        }
    }
}
