package com.example.onehandcommander.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.onehandcommander.R
import com.example.onehandcommander.core.MainService
import com.example.onehandcommander.utils.ErrorHandler

/**
 * アプリケーション設定画面
 * 責務: パーミッション要求、アクセシビリティ設定画面へのインテント起動、設定項目のバインディング、
 * サービスステータスの動的表示および設定リセット機能
 */
class SettingsActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_POST_NOTIFICATIONS = 2001
    }

    private lateinit var settingsContainer: LinearLayout
    private lateinit var viewBinder: SettingsViewBinder
    private lateinit var tvServiceStatusBadge: TextView
    private lateinit var tvServiceStatusDesc: TextView
    private lateinit var btnOpenSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SavedData.init(this)
        viewBinder = SettingsViewBinder(this)

        settingsContainer = findViewById(R.id.layout_commands)
        tvServiceStatusBadge = findViewById(R.id.tv_service_status_badge)
        tvServiceStatusDesc = findViewById(R.id.tv_service_status_desc)
        btnOpenSettings = findViewById(R.id.btn_open_settings)

        btnOpenSettings.setOnClickListener {
            openAccessibilitySettings()
        }

        findViewById<Button>(R.id.btn_reset_settings).setOnClickListener {
            showResetConfirmationDialog()
        }

        requestNotificationPermissionIfNeeded()
        renderSettings()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        renderSettings()
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled()
        if (isEnabled) {
            tvServiceStatusBadge.text = getString(R.string.service_status_active)
            tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            tvServiceStatusBadge.background = ContextCompat.getDrawable(this, R.drawable.bg_status_badge_active)
            tvServiceStatusDesc.text = getString(R.string.service_status_desc_active)
        } else {
            tvServiceStatusBadge.text = getString(R.string.service_status_inactive)
            tvServiceStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            tvServiceStatusBadge.background = ContextCompat.getDrawable(this, R.drawable.bg_status_badge_inactive)
            tvServiceStatusDesc.text = getString(R.string.service_status_desc_inactive)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponentName = "${packageName}/${MainService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { 
            it.equals(expectedComponentName, ignoreCase = true) ||
            it.contains(packageName) && it.contains("MainService")
        }
    }

    private fun showResetConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_reset_confirm_title)
            .setMessage(R.string.dialog_reset_confirm_message)
            .setPositiveButton(R.string.dialog_btn_reset) { _, _ ->
                SavedData.resetToDefaults()
                renderSettings()
                Toast.makeText(this, R.string.toast_reset_completed, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.dialog_btn_cancel, null)
            .show()
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

