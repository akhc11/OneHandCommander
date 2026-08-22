package com.example.onehandcommander.core

import com.example.onehandcommander.R
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.ui.overlays.AppMenu
import com.example.onehandcommander.utils.ErrorHandler
import com.example.onehandcommander.utils.Vibration
import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * アクセシビリティサービス基盤クラス
 * MVI (StateManager & ServiceIntent) パターンに基づき、単一方向のデータフローで UI 状態を制御します。
 */
class MainService : AccessibilityService() {

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "one_hand_commander_service"
        private const val NOTIFICATION_ID = 1001
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private val stateManager = StateManager()
    private lateinit var gestureDispatcher: GestureDispatcher
    private lateinit var overlayManager: OverlayManager

    private var wasKeyboardVisible = false

    // スリープ / ロック画面監視レシーバー
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    stateManager.processIntent(ServiceIntent.Suspend)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                    val isLocked = keyguardManager?.isKeyguardLocked ?: false
                    if (!isLocked) {
                        stateManager.processIntent(ServiceIntent.Resume)
                    }
                }
            }
        }
    }

    // アプリのインストール・アンインストール監視レシーバー（キャッシュ即時更新用）
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED, Intent.ACTION_PACKAGE_REPLACED -> {
                    if (context != null) {
                        AppMenu.preload(context, clearIconCache = true)
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (::overlayManager.isInitialized) {
            val isLocked = overlayManager.isKeyguardLocked()
            if (isLocked) {
                stateManager.processIntent(ServiceIntent.Suspend)
            } else {
                stateManager.processIntent(ServiceIntent.Resume)
            }
        }
        return START_STICKY
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        SavedData.init(this)
        Vibration.init(this)
        startForegroundSafely()

        gestureDispatcher = AccessibilityGestureDispatcher(this)

        // サービス接続時に AppMenu のアイコンキャッシュを完全先読み (0ms表示を保証)
        AppMenu.preload(this)

        overlayManager = OverlayManager(this, gestureDispatcher, onMenuClosed = {
            stateManager.processIntent(ServiceIntent.DismissMenu)
        })

        // StateFlow を購読して OverlayManager.render に状態を伝達 (単一方向データフロー)
        stateManager.state
            .onEach { state ->
                overlayManager.render(state)
            }
            .launchIn(serviceScope)

        setupCallbacks()

        val screenFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, screenFilter, Context.RECEIVER_NOT_EXPORTED)

        val packageFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, packageFilter)

        // 初期状態の評価
        if (overlayManager.isKeyguardLocked()) {
            stateManager.processIntent(ServiceIntent.Suspend)
        } else {
            stateManager.processIntent(ServiceIntent.Resume)
        }
    }

    private fun setupCallbacks() {
        // FAB タップでアプリメニュー ＋ テンキーをトグル表示
        overlayManager.btnManager.onTap = {
            stateManager.processIntent(ServiceIntent.TapFloatingButton)
        }

        // 右/左スワイプでタッチパッド起動
        overlayManager.btnManager.onSwipe = {
            stateManager.processIntent(ServiceIntent.SwipeFloatingButton)
        }

        // 上スワイプでホーム画面へ
        overlayManager.btnManager.onSwipeUp = {
            stateManager.processIntent(ServiceIntent.VerticalSwipeFloatingButton)
            performGlobalAction(GLOBAL_ACTION_HOME)
            Vibration.vibrateClick()
        }

        // 下スワイプで最近使ったタスク一覧（Recents）へ
        overlayManager.btnManager.onSwipeDown = {
            stateManager.processIntent(ServiceIntent.VerticalSwipeFloatingButton)
            performGlobalAction(GLOBAL_ACTION_RECENTS)
            Vibration.vibrateClick()
        }

        // テンキー入力コールバック
        overlayManager.tenkeyManager.onInput = { num ->
            Vibration.vibrateClick()
            if (num == "0" || num == "00") {
                // 0キー押下で即座にアプリ検索バーへフォーカス＆ソフトキーボード展開
                stateManager.processIntent(ServiceIntent.EnterSearch())
                overlayManager.appMenu.focusSearch()
            } else {
                val launched = overlayManager.appMenu.launchByNumber(num)
                if (launched) {
                    stateManager.processIntent(ServiceIntent.DismissMenu)
                } else {
                    stateManager.processIntent(ServiceIntent.PressTenkey(num))
                }
            }
        }

        // テンキースワイプ中のリアルタイムプレビュー連携
        overlayManager.tenkeyManager.onInputUpdating = { previewNum ->
            overlayManager.appMenu.previewByNumber(previewNum)
        }
    }

    private fun startForegroundSafely() {
        try {
            createNotificationChannel()
            val settingsIntent = Intent(this, com.example.onehandcommander.settings.SettingsActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, settingsIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(getString(R.string.notification_service_title))
                .setContentText(getString(R.string.notification_service_text))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            ErrorHandler.logError("Failed to start foreground mode", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, getString(R.string.app_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayManager.isInitialized) overlayManager.cleanup()
        serviceJob.cancel()
        try { unregisterReceiver(screenReceiver) } catch (e: Exception) {}
        try { unregisterReceiver(packageReceiver) } catch (e: Exception) {}
    }

    override fun onInterrupt() {
        stateManager.processIntent(ServiceIntent.CloseAllOverlays)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event != null && event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            val hasKb = windows.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            if (!wasKeyboardVisible && hasKb) {
                stateManager.processIntent(ServiceIntent.KeyboardOpened)
            }
            wasKeyboardVisible = hasKb
        }
    }
}
