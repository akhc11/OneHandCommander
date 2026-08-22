package com.example.onehandcommander.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.onehandcommander.R
import com.example.onehandcommander.utils.ErrorHandler

/**
 * アプリケーション設定画面
 * 責務: パーミッション要求、アクセシビリティ設定画面へのインテント起動、設定項目のバインディング
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }

    private lateinit var settingsContainer: LinearLayout
    private lateinit var viewBinder: SettingsViewBinder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SavedData.init(this)
        viewBinder = SettingsViewBinder(this)

        settingsContainer = findViewById(R.id.layout_commands)
        findViewById<Button>(R.id.btn_open_settings).setOnClickListener {
            openAccessibilitySettings()
        }

        requestNotificationPermissionIfNeeded()
        renderSettings()
    }

    override fun onResume() {
        super.onResume()
        // 戻ってきた際に最新値を再描画
        renderSettings()
    }

    private fun renderSettings() {
        val items = SettingsConfigProvider.getSettingItems(this)
        viewBinder.populateContainer(settingsContainer, items)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return

        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_POST_NOTIFICATIONS
        )
    }

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(intent)
        } catch (e: Exception) {
            ErrorHandler.handleError(this, getString(R.string.error_settings_open), e)
        }
    }
}
