package com.example.onehandcommander.utils

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.example.onehandcommander.R

/**
 * エラー処理を統一管理するクラス
 * ログ出力とユーザーへの通知を一元化
 */
object ErrorHandler {
    /**
     * ログにエラーを記録
     */
    fun logError(message: String, exception: Exception? = null) {
        if (exception != null) {
            Log.e(Constants.LOG_TAG, message, exception)
        } else {
            Log.e(Constants.LOG_TAG, message)
        }
    }
    
    /**
     * 一般的なエラーを処理
     */
    fun handleError(context: Context, message: String, exception: Exception? = null) {
        logError(message, exception)
        showUserMessage(context, message)
    }
    
    /**
     * アプリ起動エラー
     */
    fun handleAppLaunchError(context: Context, packageName: String, exception: Exception? = null) {
        logError("Failed to launch app: $packageName", exception)
        showUserMessage(context, context.getString(R.string.error_app_launch))
    }
    
    /**
     * ファイル操作エラー
     */
    fun handleFileError(context: Context, operation: String, exception: Exception? = null) {
        val messageResId = when (operation) {
            "open" -> R.string.error_file_open
            "delete" -> R.string.error_file_delete
            "share" -> R.string.error_file_share
            else -> R.string.error_file_generic
        }
        logError("File operation failed: $operation", exception)
        showUserMessage(context, context.getString(messageResId))
    }
    
    /**
     * 検索エラー
     */
    fun handleSearchError(context: Context, exception: Exception? = null) {
        logError("Search failed", exception)
        showUserMessage(context, context.getString(R.string.error_search))
    }
    
    /**
     * 権限エラー
     */
    fun handlePermissionError(context: Context, permission: String) {
        logError("Permission denied: $permission", null)
        showUserMessage(context, context.getString(R.string.error_permission))
    }
    
    /**
     * 設定エラー
     */
    fun handleSettingsError(context: Context, exception: Exception? = null) {
        logError("Settings error", exception)
        showUserMessage(context, context.getString(R.string.error_settings))
    }
    
    /**
     * ユーザーにメッセージを表示
     * パフォーマンス配慮: 非メインスレッドからの呼び出しによるクラッシュを防ぐためHandlerでラップ
     */
    private fun showUserMessage(context: Context, message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            Toast.makeText(context, message, Constants.UI.TOAST_DURATION_SHORT).show()
        }
    }
    
    /**
     * 成功メッセージ
     */
    fun showSuccess(context: Context, message: String) {
        Log.i(Constants.LOG_TAG, "Success: $message")
        showUserMessage(context, message)
    }
    
    /**
     * 情報メッセージ
     */
    fun showInfo(context: Context, message: String) {
        showUserMessage(context, message)
    }
}
