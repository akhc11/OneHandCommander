package com.example.onehandcommander.ui.overlays

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.onehandcommander.R
import com.example.onehandcommander.settings.SavedData
import com.example.onehandcommander.settings.SettingsActivity
import com.example.onehandcommander.ui.drawables.DirectIconDrawable
import com.example.onehandcommander.ui.drawables.GestureTenkeyHudDrawable
import com.example.onehandcommander.ui.overlays.model.AppFeatureType
import com.example.onehandcommander.ui.overlays.model.DirectIconType
import com.example.onehandcommander.ui.overlays.model.MenuGridItem
import com.example.onehandcommander.ui.overlays.model.MenuSlotAction
import com.example.onehandcommander.ui.overlays.model.SystemActionType
import com.example.onehandcommander.utils.AppIconCache
import com.example.onehandcommander.utils.Constants
import com.example.onehandcommander.utils.ErrorHandler
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration
import android.os.Handler
import android.os.Looper
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 超高速・文字なし・正方形等間隔セル・40番まで数字バッジ完備の AppMenu:
 * - 上部: 検索窓 (0キーでフォーカス)
 * - 真ん中: 横4列 × 縦4段(56dp×56dpセル、アイコン44dp)の縦横等間隔グリッド (最大40個)
 * - 01〜40スロットの自由割り当て対応 (アプリ / ホーム・戻る等のOS操作 / ファイル / タッチパッド起動 / 設定)
 * - 最速手法: DirectIconDrawable (純粋KotlinコードによるCanvas直接描画・パース負荷0ms)
 * - スロット長押しによるクイックカスタマイズ
 * - 数字バッジ: 1〜40番まですべてのスロットに割り当て
 * - ListAdapter + DiffUtil + Payload 部分バインドによる極限の ViewHolder 再利用
 * - LruCache による省メモリ・高速 0ms 表示
 */
class AppMenu(
    context: Context,
    windowManager: WindowManager,
    private val onItemSelected: () -> Unit,
    var onSystemActionRequested: ((SystemActionType) -> Unit)? = null,
    var onFeatureActionRequested: ((AppFeatureType) -> Unit)? = null
) : BaseOverlay(context, windowManager) {

    data class AppItem(
        val label: String,
        val packageName: String
    )

    data class FileItem(
        val name: String,
        val path: String,
        val sizeFormatted: String,
        val timeFormatted: String,
        val lastModified: Long
    )

    companion object {
        private const val PAYLOAD_HIGHLIGHT_CHANGE = "PAYLOAD_HIGHLIGHT_CHANGE"

        // プロセス起動後も常時保持される静的キャッシュ（超高速0ms表示用）
        @Volatile
        var memoryCachedApps: List<AppItem>? = null
        @Volatile
        var memoryCachedFiles: List<FileItem>? = null

        /**
         * サービス起動時やパッケージ変更時に呼び出すキャッシュ更新メソッド
         */
        fun preload(context: Context, clearIconCache: Boolean = false) {
            if (clearIconCache) {
                AppIconCache.clear()
            }
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    loadStaticData(context)
                } catch (e: Exception) {
                    ErrorHandler.logError("Failed to preload static cache", e)
                }
            }
        }

        private fun loadStaticData(context: Context) {
            // 1. 全アプリのリストを取得し、表示対象（上位40個）のアイコンを優先キャッシュ
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
            val iconSizePx = UiHelper.dpToPx(context, 48)

            val sortedApps = resolveInfos.map { ri ->
                AppItem(
                    label = ri.loadLabel(pm).toString(),
                    packageName = ri.activityInfo.packageName
                )
            }.sortedBy { it.label.lowercase() }

            // 上位40個のアイコンを高速先読み
            val top40 = sortedApps.take(40)
            val infoMap = resolveInfos.associateBy { it.activityInfo.packageName }
            for (app in top40) {
                if (AppIconCache.get(app.packageName) == null) {
                    try {
                        val ri = infoMap[app.packageName]
                        if (ri != null) {
                            val icon = ri.loadIcon(pm)
                            AppIconCache.putDrawable(app.packageName, icon, iconSizePx)
                        }
                    } catch (e: Exception) {
                        ErrorHandler.logError("Failed to cache icon for ${app.packageName}", e)
                    }
                }
            }

            memoryCachedApps = sortedApps

            // 2. 最近のファイル (最新4件)
            val list = mutableListOf<FileItem>()
            val timeFormat = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

            val candidateDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                context.getExternalFilesDir(null)
            )

            val allFoundFiles = mutableListOf<File>()
            for (dir in candidateDirs) {
                if (dir.exists() && dir.canRead()) {
                    dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.let {
                        allFoundFiles.addAll(it)
                    }
                }
            }

            allFoundFiles.sortByDescending { it.lastModified() }
            allFoundFiles.take(10).forEach { f ->
                list.add(
                    FileItem(
                        name = f.name,
                        path = f.absolutePath,
                        sizeFormatted = formatFileSize(f.length()),
                        timeFormatted = timeFormat.format(Date(f.lastModified())),
                        lastModified = f.lastModified()
                    )
                )
            }

            memoryCachedFiles = list
        }

        private fun formatFileSize(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }

    private val menuJob = SupervisorJob()
    private val menuScope = CoroutineScope(Dispatchers.Main + menuJob)
    private var loadJob: Job? = null

    private var allApps = listOf<AppItem>()
    private var allFiles = listOf<FileItem>()

    private var searchInput: EditText? = null
    private var appsRecyclerView: RecyclerView? = null
    private var recentRecyclerView: RecyclerView? = null
    private var emptyAppsTextView: TextView? = null
    private var emptyRecentTextView: TextView? = null

    private val themedContext by lazy { ContextThemeWrapper(context, R.style.Theme_OneHandCommander) }
    private val themedInflater by lazy { LayoutInflater.from(themedContext) }

    private val gridAdapter by lazy { MenuGridListAdapter() }
    private val recentAdapter by lazy { RecentFilesListAdapter() }

    private var previewIndex = -1
    private var isClearingSearch = false

    private var menuCardView: View? = null
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var initialCardX = 0f
    private var initialCardY = 0f
    private var initialTouchRawX = 0f
    private var initialTouchRawY = 0f

    // 背景ジェスチャーテンキー関連
    private val density by lazy { context.resources.displayMetrics.density }
    private val flickThresholdPx by lazy { UiHelper.dpToPx(context, Constants.SWIPE_THRESHOLD_DP).toFloat() }
    private val hudDrawable by lazy { GestureTenkeyHudDrawable(density) }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var gestureStartX = 0f
    private var gestureStartY = 0f
    private var gestureCurrentX = 0f
    private var gestureCurrentY = 0f
    private var isZeroCommitted = false
    private var lastVibratedDigit: String? = null
    private val enteredBuffer = StringBuilder()

    private val longPressZeroRunnable = Runnable {
        if (!isZeroCommitted) {
            isZeroCommitted = true
            Vibration.vibrateLongPress()
            hudDrawable.isLongPressZero = true
            hudDrawable.activeDigit = "0"
            hudDrawable.enteredBufferText = enteredBuffer.toString() + "0"
            menuCardView?.invalidate()
            focusSearch()
        }
    }

    private val singleDigitCommitRunnable = Runnable {
        if (enteredBuffer.isNotEmpty()) {
            val finalInput = enteredBuffer.toString()
            enteredBuffer.clear()
            hudDrawable.isActive = false
            hudDrawable.enteredBufferText = ""
            overlayView?.invalidate()
            launchByNumber(finalInput)
        }
    }

    override fun createLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            @Suppress("DEPRECATION")
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    override fun createView(): View {
        val view = themedInflater.inflate(R.layout.layout_app_menu, null)

        val dimBackground = view.findViewById<View>(R.id.menu_dim_background)
        val menuCard = view.findViewById<View>(R.id.menu_card)
        val dragHandle = view.findViewById<ImageView>(R.id.iv_drag_handle)
        menuCardView = menuCard

        // 背景にHUD Drawableを追加
        if (dimBackground is ViewGroup) {
            dimBackground.overlay.add(hudDrawable)
        }

        // 背景タッチ＆ジェスチャー処理
        dimBackground?.setOnTouchListener { v, event ->
            handleBackgroundTouch(v, event, menuCard)
        }

        // 保存された座標の復元 (未設定 -1 の場合はレイアウト完了後に画面中央へ配置)
        applySavedPosition(view, menuCard)

        // ドラッグハンドルによるメニュー位置移動
        dragHandle?.let { handle ->
            setupDragHandle(handle, view, menuCard)
        }

        searchInput = view.findViewById(R.id.edit_search)
        appsRecyclerView = view.findViewById(R.id.recycler_apps)
        recentRecyclerView = view.findViewById(R.id.recycler_recent_files)
        emptyAppsTextView = view.findViewById(R.id.text_empty_apps)
        emptyRecentTextView = view.findViewById(R.id.text_empty_recent)

        // 横4列の正方形グリッド
        appsRecyclerView?.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = gridAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        // 下部4行の最近リスト
        recentRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = recentAdapter
            setHasFixedSize(true)
            itemAnimator = null
        }

        // テンキーキーイベント処理 (1〜9, 0, Back)
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_UP) {
                when (keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        hide()
                        true
                    }
                    KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                        if (searchInput?.isFocused != true) {
                            focusSearch()
                            true
                        } else false
                    }
                    KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> handleNumberKey(1)
                    KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> handleNumberKey(2)
                    KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> handleNumberKey(3)
                    KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> handleNumberKey(4)
                    KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> handleNumberKey(5)
                    KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> handleNumberKey(6)
                    KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> handleNumberKey(7)
                    KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> handleNumberKey(8)
                    KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> handleNumberKey(9)
                    else -> false
                }
            } else false
        }

        // リアルタイム検索フィルター
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!isClearingSearch) {
                    filterAll(s?.toString().orEmpty())
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return view
    }

    fun previewByNumber(numStr: String) {
        val num = numStr.toIntOrNull()
        val newIndex = if (num != null && num > 0) num - 1 else -1
        if (newIndex != previewIndex) {
            val oldIndex = previewIndex
            previewIndex = newIndex
            if (oldIndex in 0 until gridAdapter.currentList.size) {
                gridAdapter.notifyItemChanged(oldIndex, PAYLOAD_HIGHLIGHT_CHANGE)
            }
            if (previewIndex in 0 until gridAdapter.currentList.size) {
                gridAdapter.notifyItemChanged(previewIndex, PAYLOAD_HIGHLIGHT_CHANGE)
                appsRecyclerView?.smoothScrollToPosition(previewIndex)
            }
        }
    }

    fun launchByNumber(numStr: String): Boolean {
        previewByNumber("")
        val num = numStr.toIntOrNull() ?: return false
        if (num <= 0) return false
        val index = num - 1
        val currentList = gridAdapter.currentList
        if (index in 0 until currentList.size) {
            val target = currentList[index]
            executeSlotAction(target)
            return true
        }
        return false
    }

    private fun handleNumberKey(num: Int): Boolean {
        if (searchInput?.isFocused == true) return false
        return launchByNumber(num.toString())
    }

    override fun show() {
        try {
            super.show()
            overlayView?.let { root ->
                menuCardView?.let { card ->
                    applySavedPosition(root, card)
                }
            }
            isClearingSearch = true
            searchInput?.setText("")
            isClearingSearch = false

            // キャッシュから 0ms で即座に描画
            val cachedApps = memoryCachedApps
            if (cachedApps != null) {
                allApps = cachedApps
                refreshGridItems()
            }

            val cachedFiles = memoryCachedFiles
            if (cachedFiles != null) {
                allFiles = cachedFiles
                val topFiles = cachedFiles.take(4)
                recentAdapter.submitList(topFiles) {
                    emptyRecentTextView?.visibility = if (topFiles.isEmpty()) View.VISIBLE else View.GONE
                }
            }

            // キャッシュが空の場合のみ非同期取得
            if (cachedApps == null || cachedFiles == null) {
                loadDataAsync()
            }
        } catch (e: Exception) {
            ErrorHandler.logError("Failed to show AppMenu", e)
        }
    }

    override fun hide() {
        try {
            mainHandler.removeCallbacks(longPressZeroRunnable)
            mainHandler.removeCallbacks(singleDigitCommitRunnable)
            enteredBuffer.clear()
            hudDrawable.isActive = false
            hudDrawable.enteredBufferText = ""
            previewByNumber("")

            searchInput?.let { edit ->
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(edit.windowToken, 0)
            }
            super.hide()
            onItemSelected()
        } catch (e: Exception) {
            ErrorHandler.logError("Failed to hide AppMenu", e)
        }
    }

    /**
     * テンキー「0」押下で検索欄へフォーカス＆キーボード展開
     */
    fun focusSearch() {
        if (!isVisible()) show()
        searchInput?.post {
            searchInput?.requestFocus()
            searchInput?.setSelection(searchInput?.text?.length ?: 0)
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun loadDataAsync() {
        loadJob?.cancel()
        loadJob = menuScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    loadStaticData(context)
                }
                memoryCachedApps?.let { allApps = it }
                memoryCachedFiles?.let { allFiles = it }

                val q = searchInput?.text?.toString().orEmpty()
                filterAll(q)
            } catch (e: Exception) {
                ErrorHandler.logError("Failed to load async menu data", e)
            }
        }
    }

    /**
     * グリッド用スロットデータ（最大40個）を構築して表示更新
     */
    private fun refreshGridItems() {
        val query = searchInput?.text?.toString().orEmpty().trim().lowercase()
        if (query.isNotEmpty()) {
            filterAll(query)
            return
        }

        val items = mutableListOf<MenuGridItem>()
        var autoAppPointer = 0

        for (i in 0 until 40) {
            val slotNumber = i + 1
            val customAction = SavedData.getMenuSlotAction(context, i)

            when (customAction) {
                is MenuSlotAction.SystemCommand -> {
                    items.add(
                        MenuGridItem(
                            slotNumber = slotNumber,
                            action = customAction,
                            title = customAction.actionType.displayName,
                            iconType = customAction.iconType,
                            isCustomized = true
                        )
                    )
                }
                is MenuSlotAction.OpenFile -> {
                    items.add(
                        MenuGridItem(
                            slotNumber = slotNumber,
                            action = customAction,
                            title = customAction.fileName,
                            iconType = DirectIconType.OPEN_FILE,
                            isCustomized = true
                        )
                    )
                }
                is MenuSlotAction.CustomFeature -> {
                    items.add(
                        MenuGridItem(
                            slotNumber = slotNumber,
                            action = customAction,
                            title = customAction.featureType.displayName,
                            iconType = customAction.iconType,
                            isCustomized = true
                        )
                    )
                }
                is MenuSlotAction.LaunchApp -> {
                    items.add(
                        MenuGridItem(
                            slotNumber = slotNumber,
                            action = customAction,
                            title = customAction.appLabel,
                            iconType = DirectIconType.DEFAULT_APP,
                            appPackageName = customAction.packageName,
                            isCustomized = true
                        )
                    )
                }
                is MenuSlotAction.AutoApp -> {
                    // 自動フォールバック（インストール済みアプリ順）
                    if (autoAppPointer < allApps.size) {
                        val app = allApps[autoAppPointer++]
                        items.add(
                            MenuGridItem(
                                slotNumber = slotNumber,
                                action = MenuSlotAction.LaunchApp(app.packageName, app.label),
                                title = app.label,
                                iconType = DirectIconType.DEFAULT_APP,
                                appPackageName = app.packageName,
                                isCustomized = false
                            )
                        )
                    }
                }
            }
        }

        gridAdapter.submitList(items) {
            emptyAppsTextView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun filterAll(query: String) {
        val q = query.trim().lowercase()

        if (q.isEmpty()) {
            refreshGridItems()
        } else {
            // 検索時はマッチする全アプリを一覧表示
            val filteredApps = allApps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }.take(40)

            val items = filteredApps.mapIndexed { index, app ->
                MenuGridItem(
                    slotNumber = index + 1,
                    action = MenuSlotAction.LaunchApp(app.packageName, app.label),
                    title = app.label,
                    iconType = DirectIconType.DEFAULT_APP,
                    appPackageName = app.packageName,
                    isCustomized = false
                )
            }
            gridAdapter.submitList(items) {
                emptyAppsTextView?.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        // 最近のファイル (最新4件固定)
        val matchedFiles = if (q.isEmpty()) {
            allFiles
        } else {
            allFiles.filter { it.name.lowercase().contains(q) }
        }
        val topFiles = matchedFiles.take(4)
        recentAdapter.submitList(topFiles) {
            emptyRecentTextView?.visibility = if (topFiles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    /**
     * スロットに設定されたアクションを実行
     */
    private fun executeSlotAction(item: MenuGridItem) {
        Vibration.vibrateClick()
        hide()

        when (val action = item.action) {
            is MenuSlotAction.LaunchApp -> {
                SavedData.addRecentApp(action.packageName)
                UiHelper.launchApp(context, action.packageName)
            }
            is MenuSlotAction.SystemCommand -> {
                onSystemActionRequested?.invoke(action.actionType)
            }
            is MenuSlotAction.OpenFile -> {
                try {
                    val file = File(action.uriString)
                    if (file.exists()) {
                        UiHelper.shareFile(context, file)
                    } else {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(action.uriString), action.mimeType ?: "*/*")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(intent)
                    }
                } catch (e: Exception) {
                    ErrorHandler.handleError(context, "ファイルを開けません", e)
                }
            }
            is MenuSlotAction.CustomFeature -> {
                when (action.featureType) {
                    AppFeatureType.LAUNCH_TOUCHPAD -> onFeatureActionRequested?.invoke(action.featureType)
                    AppFeatureType.OPEN_SETTINGS -> {
                        val intent = Intent(context, SettingsActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
            is MenuSlotAction.AutoApp -> {
                item.appPackageName?.let { pkg ->
                    SavedData.addRecentApp(pkg)
                    UiHelper.launchApp(context, pkg)
                }
            }
        }
    }

    /**
     * オーバーレイサービス上から安全かつ確実にダイアログを表示するヘルパー
     */
    private fun showOverlayDialog(dialog: AlertDialog) {
        dialog.window?.let { window ->
            // AccessibilityService のオーバーレイウィンドウタイプを設定
            window.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
        }
        dialog.show()
    }

    /**
     * スロット長押し時のカスタマイズダイアログを表示
     */
    private fun showSlotCustomizeDialog(slotIndex: Int) {
        Vibration.vibrateClick()
        val slotNum = slotIndex + 1

        val options = arrayOf(
            "⚡ システム操作 (ホーム・戻る・通知等)",
            "📱 アプリを固定割り当て",
            "📄 ファイル・ドキュメントを割り当て",
            "⚙️ 便利機能 (タッチパッド・設定)",
            "🔄 デフォルト (自動割り当て) に戻す"
        )

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("スロット $slotNum の設定")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSystemActionPicker(slotIndex)
                    1 -> showAppPicker(slotIndex)
                    2 -> showRecentFilePicker(slotIndex)
                    3 -> showFeaturePicker(slotIndex)
                    4 -> {
                        SavedData.resetMenuSlotAction(context, slotIndex)
                        refreshGridItems()
                        UiHelper.showToast(context, "スロット $slotNum を初期状態に戻しました")
                    }
                }
            }
            .setNegativeButton("キャンセル", null)
            .create()

        showOverlayDialog(dialog)
    }

    private fun showSystemActionPicker(slotIndex: Int) {
        val actions = SystemActionType.entries.toTypedArray()
        val names = actions.map { it.displayName }.toTypedArray()

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("システム操作を選択")
            .setItems(names) { _, which ->
                val selectedAction = actions[which]
                SavedData.saveMenuSlotAction(context, slotIndex, MenuSlotAction.SystemCommand(selectedAction))
                refreshGridItems()
                UiHelper.showToast(context, "スロット ${slotIndex + 1} に「${selectedAction.displayName}」を割り当てました")
            }
            .setNegativeButton("キャンセル", null)
            .create()

        showOverlayDialog(dialog)
    }

    private fun showAppPicker(slotIndex: Int) {
        val apps = allApps
        val names = apps.map { it.label }.toTypedArray()

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("アプリを選択")
            .setItems(names) { _, which ->
                val selectedApp = apps[which]
                SavedData.saveMenuSlotAction(context, slotIndex, MenuSlotAction.LaunchApp(selectedApp.packageName, selectedApp.label))
                refreshGridItems()
                UiHelper.showToast(context, "スロット ${slotIndex + 1} に「${selectedApp.label}」を割り当てました")
            }
            .setNegativeButton("キャンセル", null)
            .create()

        showOverlayDialog(dialog)
    }

    private fun showRecentFilePicker(slotIndex: Int) {
        val files = allFiles
        if (files.isEmpty()) {
            UiHelper.showToast(context, "選択可能なファイルが見つかりません")
            return
        }
        val names = files.map { "${it.name} (${it.sizeFormatted})" }.toTypedArray()

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("ファイルを選択")
            .setItems(names) { _, which ->
                val selectedFile = files[which]
                SavedData.saveMenuSlotAction(context, slotIndex, MenuSlotAction.OpenFile(selectedFile.path, selectedFile.name))
                refreshGridItems()
                UiHelper.showToast(context, "スロット ${slotIndex + 1} に「${selectedFile.name}」を割り当てました")
            }
            .setNegativeButton("キャンセル", null)
            .create()

        showOverlayDialog(dialog)
    }

    private fun showFeaturePicker(slotIndex: Int) {
        val features = AppFeatureType.entries.toTypedArray()
        val names = features.map { it.displayName }.toTypedArray()

        val dialog = AlertDialog.Builder(themedContext)
            .setTitle("便利機能を選択")
            .setItems(names) { _, which ->
                val selectedFeature = features[which]
                SavedData.saveMenuSlotAction(context, slotIndex, MenuSlotAction.CustomFeature(selectedFeature))
                refreshGridItems()
                UiHelper.showToast(context, "スロット ${slotIndex + 1} に「${selectedFeature.displayName}」を割り当てました")
            }
            .setNegativeButton("キャンセル", null)
            .create()

        showOverlayDialog(dialog)
    }

    /**
     * 保存されているメニュー位置の復元 (未保存時は中央配置)
     */
    private fun applySavedPosition(rootView: View, menuCard: View) {
        val savedX = SavedData.getAppMenuX()
        val savedY = SavedData.getAppMenuY()

        if (savedX >= 0 && savedY >= 0) {
            menuCard.x = savedX.toFloat()
            menuCard.y = savedY.toFloat()
        } else {
            // 初期状態は画面中央
            rootView.post {
                val parentWidth = rootView.width
                val parentHeight = rootView.height
                val cardWidth = menuCard.width
                val cardHeight = menuCard.height

                if (parentWidth > 0 && parentHeight > 0 && cardWidth > 0 && cardHeight > 0) {
                    val centerX = ((parentWidth - cardWidth) / 2).coerceAtLeast(0).toFloat()
                    val centerY = ((parentHeight - cardHeight) / 2).coerceAtLeast(0).toFloat()
                    menuCard.x = centerX
                    menuCard.y = centerY
                }
            }
        }
    }

    /**
     * ドラッグハンドルによる直感的なメニュー移動と座標自動保存
     */
    private fun setupDragHandle(handle: View, rootView: View, menuCard: View) {
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activePointerId = event.getPointerId(0)
                    initialCardX = menuCard.x
                    initialCardY = menuCard.y
                    initialTouchRawX = event.rawX
                    initialTouchRawY = event.rawY
                    Vibration.vibrateClick()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activePointerId == MotionEvent.INVALID_POINTER_ID) return@setOnTouchListener false
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex < 0) return@setOnTouchListener true

                    val diffX = event.rawX - initialTouchRawX
                    val diffY = event.rawY - initialTouchRawY

                    val targetX = initialCardX + diffX
                    val targetY = initialCardY + diffY

                    // 画面外にはみ出さないよう Clamping
                    val parentWidth = rootView.width.coerceAtLeast(1)
                    val parentHeight = rootView.height.coerceAtLeast(1)
                    val cardWidth = menuCard.width
                    val cardHeight = menuCard.height

                    val maxX = (parentWidth - cardWidth).coerceAtLeast(0).toFloat()
                    val maxY = (parentHeight - cardHeight).coerceAtLeast(0).toFloat()

                    menuCard.x = targetX.coerceIn(0f, maxX)
                    menuCard.y = targetY.coerceIn(0f, maxY)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (activePointerId != MotionEvent.INVALID_POINTER_ID) {
                        val finalX = menuCard.x.toInt()
                        val finalY = menuCard.y.toInt()
                        SavedData.saveAppMenuPosition(finalX, finalY)
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    true
                }
                MotionEvent.ACTION_POINTER_UP -> {
                    val pointerIndex = event.actionIndex
                    if (event.getPointerId(pointerIndex) == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 背景全画面でのタッチ＆ブラインドジェスチャーテンキー処理
     */
    private fun handleBackgroundTouch(v: View, event: MotionEvent, menuCard: View): Boolean {
        hudDrawable.setBounds(0, 0, v.width, v.height)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // カード内部のタッチなら子Viewに任せる
                if (isTouchInsideView(event.rawX, event.rawY, menuCard)) {
                    return false
                }

                gestureStartX = event.rawX
                gestureStartY = event.rawY
                gestureCurrentX = event.rawX
                gestureCurrentY = event.rawY

                isZeroCommitted = false
                lastVibratedDigit = null

                // 次のストローク開始により、1桁確定タイマーをキャンセル
                mainHandler.removeCallbacks(singleDigitCommitRunnable)

                // HUD更新
                hudDrawable.isActive = true
                hudDrawable.originX = gestureStartX
                hudDrawable.originY = gestureStartY
                hudDrawable.currentX = gestureCurrentX
                hudDrawable.currentY = gestureCurrentY
                hudDrawable.activeDigit = null
                hudDrawable.isLongPressZero = false
                hudDrawable.enteredBufferText = enteredBuffer.toString()

                // 長押し「0」タイマー開始 (300ms)
                mainHandler.removeCallbacks(longPressZeroRunnable)
                mainHandler.postDelayed(longPressZeroRunnable, Constants.TENKEY_LONG_PRESS_MS)

                v.invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isZeroCommitted) return true

                gestureCurrentX = event.rawX
                gestureCurrentY = event.rawY
                hudDrawable.currentX = gestureCurrentX
                hudDrawable.currentY = gestureCurrentY

                val dx = gestureCurrentX - gestureStartX
                val dy = gestureCurrentY - gestureStartY
                val dist = sqrt(dx * dx + dy * dy)

                if (dist > flickThresholdPx * 0.35f) {
                    mainHandler.removeCallbacks(longPressZeroRunnable)
                }

                if (dist >= flickThresholdPx) {
                    val digit = getDigitFromAngle(dx, dy)
                    hudDrawable.activeDigit = digit

                    if (lastVibratedDigit != digit) {
                        lastVibratedDigit = digit
                        val isDiagonal = (digit == "1" || digit == "3" || digit == "7" || digit == "9")
                        if (isDiagonal) {
                            Vibration.vibrateDiagonal()
                        } else {
                            Vibration.vibrateOrthogonal()
                        }
                    }

                    previewByNumber(enteredBuffer.toString() + digit)
                } else {
                    hudDrawable.activeDigit = null
                    lastVibratedDigit = null
                    previewByNumber(enteredBuffer.toString())
                }

                v.invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressZeroRunnable)
                hudDrawable.isActive = false
                v.invalidate()

                if (isZeroCommitted) {
                    isZeroCommitted = false
                    return true
                }

                val dx = event.rawX - gestureStartX
                val dy = event.rawY - gestureStartY
                val dist = sqrt(dx * dx + dy * dy)

                if (dist >= flickThresholdPx) {
                    val digit = getDigitFromAngle(dx, dy)
                    commitGestureDigit(digit)
                } else {
                    // スワイプなしの静止タップ -> メニューを閉じる (入力バッファがなければ)
                    if (enteredBuffer.isEmpty()) {
                        hide()
                    }
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressZeroRunnable)
                hudDrawable.isActive = false
                v.invalidate()
                return true
            }

            else -> return false
        }
    }

    private fun commitGestureDigit(digit: String) {
        if (enteredBuffer.isEmpty()) {
            // 1桁目決定
            enteredBuffer.append(digit)
            hudDrawable.enteredBufferText = digit
            previewByNumber(digit)

            // 650ms待機タイマー開始（2回目のスワイプを行うための十分な猶予時間を確保）
            mainHandler.removeCallbacks(singleDigitCommitRunnable)
            mainHandler.postDelayed(singleDigitCommitRunnable, Constants.TENKEY_SINGLE_DIGIT_TIMEOUT_MS)
        } else {
            // 2桁目決定 -> 即確定
            enteredBuffer.append(digit)
            val finalInput = enteredBuffer.toString()
            enteredBuffer.clear()
            hudDrawable.enteredBufferText = ""
            overlayView?.invalidate()
            Vibration.vibrateSuccess()
            launchByNumber(finalInput)
        }
    }

    private fun getDigitFromAngle(dx: Float, dy: Float): String {
        val rad = atan2(dy.toDouble(), dx.toDouble())
        var deg = Math.toDegrees(rad)
        if (deg < 0) deg += 360.0

        return when {
            deg >= 337.5 || deg < 22.5 -> "6" // 右 (0度)
            deg >= 22.5 && deg < 67.5 -> "3"   // 右下 (45度)
            deg >= 67.5 && deg < 112.5 -> "2"  // 下 (90度)
            deg >= 112.5 && deg < 157.5 -> "1" // 左下 (135度)
            deg >= 157.5 && deg < 202.5 -> "4" // 左 (180度)
            deg >= 202.5 && deg < 247.5 -> "7" // 左上 (225度)
            deg >= 247.5 && deg < 292.5 -> "8" // 上 (270度)
            deg >= 292.5 && deg < 337.5 -> "9" // 右上 (315度)
            else -> "5"
        }
    }

    private fun isTouchInsideView(rawX: Float, rawY: Float, targetView: View): Boolean {
        val location = IntArray(2)
        targetView.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + targetView.width
        val bottom = top + targetView.height
        return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
    }

    override fun cleanup() {
        super.cleanup()
        menuJob.cancel()
    }

    // --- ListAdapters with DiffUtil & Payload Support ---

    private class MenuGridDiffCallback : DiffUtil.ItemCallback<MenuGridItem>() {
        override fun areItemsTheSame(oldItem: MenuGridItem, newItem: MenuGridItem): Boolean {
            return oldItem.slotNumber == newItem.slotNumber
        }

        override fun areContentsTheSame(oldItem: MenuGridItem, newItem: MenuGridItem): Boolean {
            return oldItem == newItem
        }
    }

    private class FileDiffCallback : DiffUtil.ItemCallback<FileItem>() {
        override fun areItemsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem.path == newItem.path
        }

        override fun areContentsTheSame(oldItem: FileItem, newItem: FileItem): Boolean {
            return oldItem == newItem
        }
    }

    private inner class MenuGridListAdapter : ListAdapter<MenuGridItem, MenuGridListAdapter.GridViewHolder>(MenuGridDiffCallback()) {
        inner class GridViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val iconView: ImageView = view.findViewById(R.id.app_icon)
            val badgeView: TextView = view.findViewById(R.id.text_key_badge)

            fun applyHighlight(isHighlighted: Boolean, hasActivePreview: Boolean) {
                view.setBackgroundResource(R.drawable.bg_item_ripple)
                if (isHighlighted) {
                    view.scaleX = 1.15f
                    view.scaleY = 1.15f
                    view.alpha = 1.0f
                } else {
                    view.scaleX = 1.0f
                    view.scaleY = 1.0f
                    view.alpha = if (hasActivePreview) 0.5f else 1.0f
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
            val view = themedInflater.inflate(R.layout.item_app_grid, parent, false)
            return GridViewHolder(view)
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_HIGHLIGHT_CHANGE)) {
                holder.applyHighlight(position == previewIndex, previewIndex >= 0)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
            val item = getItem(position)
            val pkg = item.appPackageName

            // アイコン描画: システム操作・ファイル・独自機能は DirectIconDrawable (純粋Kotlinコード描画・0ms)
            if (pkg != null) {
                val cachedBitmap = AppIconCache.get(pkg)
                if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                    holder.iconView.setImageBitmap(cachedBitmap)
                } else {
                    holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                    menuScope.launch(Dispatchers.IO) {
                        try {
                            val pm = context.packageManager
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            val icon = appInfo.loadIcon(pm)
                            val iconSizePx = UiHelper.dpToPx(context, 48)
                            AppIconCache.putDrawable(pkg, icon, iconSizePx)
                            val bmp = AppIconCache.get(pkg)
                            if (bmp != null) {
                                withContext(Dispatchers.Main) {
                                    if (holder.bindingAdapterPosition == position) {
                                        holder.iconView.setImageBitmap(bmp)
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                // 最速手法: DirectIconDrawable
                holder.iconView.setImageDrawable(DirectIconDrawable(item.iconType, Color.WHITE))
            }

            // 1〜40番のスロット番号バッジ
            holder.badgeView.visibility = View.VISIBLE
            holder.badgeView.text = item.slotNumber.toString()

            holder.applyHighlight(position == previewIndex, previewIndex >= 0)

            // タップで実行
            holder.view.setOnClickListener {
                executeSlotAction(item)
            }

            // 長押しでカスタマイズダイアログ表示
            holder.view.setOnLongClickListener {
                showSlotCustomizeDialog(position)
                true
            }
        }
    }

    private inner class RecentFilesListAdapter : ListAdapter<FileItem, RecentFilesListAdapter.FileViewHolder>(FileDiffCallback()) {
        inner class FileViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val nameView: TextView = view.findViewById(R.id.file_name)
            val infoView: TextView = view.findViewById(R.id.file_info)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = themedInflater.inflate(R.layout.item_file_list, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val item = getItem(position)
            holder.nameView.text = item.name
            holder.infoView.text = "${item.sizeFormatted} • ${item.timeFormatted}"
            holder.view.setOnClickListener {
                Vibration.vibrateClick()
                hide()
                val f = File(item.path)
                UiHelper.shareFile(context, f)
            }
        }
    }
}
