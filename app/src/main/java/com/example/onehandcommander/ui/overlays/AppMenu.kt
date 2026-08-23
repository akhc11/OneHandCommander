package com.example.onehandcommander.ui.overlays

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.PixelFormat
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
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
import com.example.onehandcommander.utils.AppIconCache
import com.example.onehandcommander.utils.ErrorHandler
import com.example.onehandcommander.utils.UiHelper
import com.example.onehandcommander.utils.Vibration
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
 * - 数字バッジ: 1〜40番まですべてのアプリに割り当て
 * - ListAdapter + DiffUtil + Payload 部分バインドによる極限の ViewHolder 再利用
 * - LruCache による省メモリ・高速 0ms 表示
 */
class AppMenu(
    context: Context,
    windowManager: WindowManager,
    private val onItemSelected: () -> Unit
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
                bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
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

    private val appsAdapter by lazy { AppsListAdapter() }
    private val recentAdapter by lazy { RecentFilesListAdapter() }

    private var previewIndex = -1
    private var isClearingSearch = false

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
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    override fun createView(): View {
        val view = themedInflater.inflate(R.layout.layout_app_menu, null)

        val dimBackground = view.findViewById<View>(R.id.menu_dim_background)
        val menuCard = view.findViewById<View>(R.id.menu_card)

        // 背景タップで閉じる
        dimBackground?.setOnClickListener { hide() }
        menuCard?.setOnClickListener { /* イベント消費 */ }

        searchInput = view.findViewById(R.id.edit_search)
        appsRecyclerView = view.findViewById(R.id.recycler_apps)
        recentRecyclerView = view.findViewById(R.id.recycler_recent_files)
        emptyAppsTextView = view.findViewById(R.id.text_empty_apps)
        emptyRecentTextView = view.findViewById(R.id.text_empty_recent)

        // 横4列の正方形グリッド
        appsRecyclerView?.apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = appsAdapter
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
            if (oldIndex in 0 until appsAdapter.currentList.size) {
                appsAdapter.notifyItemChanged(oldIndex, PAYLOAD_HIGHLIGHT_CHANGE)
            }
            if (previewIndex in 0 until appsAdapter.currentList.size) {
                appsAdapter.notifyItemChanged(previewIndex, PAYLOAD_HIGHLIGHT_CHANGE)
                appsRecyclerView?.smoothScrollToPosition(previewIndex)
            }
        }
    }

    fun launchByNumber(numStr: String): Boolean {
        previewByNumber("")
        val num = numStr.toIntOrNull() ?: return false
        if (num <= 0) return false
        val index = num - 1
        val currentList = appsAdapter.currentList
        if (index in 0 until currentList.size) {
            val target = currentList[index]
            Vibration.vibrateClick()
            SavedData.addRecentApp(target.packageName)
            hide()
            UiHelper.launchApp(context, target.packageName)
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
            isClearingSearch = true
            searchInput?.setText("")
            isClearingSearch = false

            // キャッシュから 0ms で即座に描画
            val cachedApps = memoryCachedApps
            if (cachedApps != null) {
                allApps = cachedApps
                val displayList = cachedApps.take(40)
                appsAdapter.submitList(displayList) {
                    emptyAppsTextView?.visibility = if (displayList.isEmpty()) View.VISIBLE else View.GONE
                }
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

    private fun filterAll(query: String) {
        val q = query.trim().lowercase()

        // アプリ一覧フィルタリング (最大40個)
        val filteredApps = if (q.isEmpty()) {
            allApps.take(40)
        } else {
            allApps.filter { 
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q) 
            }.take(40)
        }
        appsAdapter.submitList(filteredApps) {
            emptyAppsTextView?.visibility = if (filteredApps.isEmpty()) View.VISIBLE else View.GONE
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

    override fun cleanup() {
        super.cleanup()
        menuJob.cancel()
    }

    // --- ListAdapters with DiffUtil & Payload Support ---

    private class AppDiffCallback : DiffUtil.ItemCallback<AppItem>() {
        override fun areItemsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
            return oldItem.packageName == newItem.packageName
        }

        override fun areContentsTheSame(oldItem: AppItem, newItem: AppItem): Boolean {
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

    private inner class AppsListAdapter : ListAdapter<AppItem, AppsListAdapter.AppViewHolder>(AppDiffCallback()) {
        inner class AppViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
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

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val view = themedInflater.inflate(R.layout.item_app_grid, parent, false)
            return AppViewHolder(view)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int, payloads: MutableList<Any>) {
            if (payloads.contains(PAYLOAD_HIGHLIGHT_CHANGE)) {
                holder.applyHighlight(position == previewIndex, previewIndex >= 0)
            } else {
                super.onBindViewHolder(holder, position, payloads)
            }
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val item = getItem(position)
            val cachedBitmap = AppIconCache.get(item.packageName)
            if (cachedBitmap != null && !cachedBitmap.isRecycled) {
                holder.iconView.setImageBitmap(cachedBitmap)
            } else {
                holder.iconView.setImageResource(android.R.drawable.sym_def_app_icon)
                // アイコン未キャッシュ時は非同期で読み込み
                menuScope.launch(Dispatchers.IO) {
                    try {
                        val pm = context.packageManager
                        val appInfo = pm.getApplicationInfo(item.packageName, 0)
                        val icon = appInfo.loadIcon(pm)
                        val iconSizePx = UiHelper.dpToPx(context, 48)
                        AppIconCache.putDrawable(item.packageName, icon, iconSizePx)
                        val bmp = AppIconCache.get(item.packageName)
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

            // 1〜40番まですべてのアプリに数字バッジを確実に表示
            holder.badgeView.visibility = View.VISIBLE
            holder.badgeView.text = (position + 1).toString()

            holder.applyHighlight(position == previewIndex, previewIndex >= 0)

            holder.view.setOnClickListener {
                Vibration.vibrateClick()
                SavedData.addRecentApp(item.packageName)
                hide()
                UiHelper.launchApp(context, item.packageName)
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
