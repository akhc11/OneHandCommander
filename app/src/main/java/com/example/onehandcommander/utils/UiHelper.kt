package com.example.onehandcommander.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Build
import android.util.TypedValue
import com.example.onehandcommander.R
import java.io.File

/**
 * UI関連のユーティリティ関数を提供
 * Toast表示、クリップボード、ファイル操作、アプリ起動などを統一化
 */
object UiHelper {
    
    /**
     * dpをpxに変換
     */
    fun dpToPx(context: Context, dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 
            dp.toFloat(), 
            context.resources.displayMetrics
        ).toInt()
    }

    /**
     * FileProviderを使ってcontent:// URIを生成
     * なぜ: Uri.fromFile()はAPI 24+でFileUriExposedExceptionを発生させるため
     */
    fun getContentUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    /**
     * トーストメッセージを表示
     * パフォーマンス配慮: 非メインスレッドからの直接の呼び出しでもクラッシュしないよう保護
     */
    fun showToast(context: Context, msg: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, msg, Constants.UI.TOAST_DURATION_SHORT).show()
        }
    }

    /**
     * クリップボードにテキストをコピー
     */
    fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText("FilePath", text))
            
            // Android 13未満のみトースト表示（Android 13以降は自動表示）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                ErrorHandler.showSuccess(context, context.getString(R.string.toast_copy_success))
            }
        } catch (e: Exception) {
            ErrorHandler.handleError(context, context.getString(R.string.toast_copy_failed), e)
        }
    }

    /**
     * ファイルを共有 (File指定)
     */
    fun shareFile(context: Context, file: File) {
        try {
            val uri = getContentUri(context, file)
            shareUri(context, uri, "*/*")
        } catch (e: Exception) {
            ErrorHandler.handleFileError(context, "share", e)
        }
    }

    /**
     * Uri指定でファイルを共有
     */
    fun shareUri(context: Context, uri: Uri, mimeType: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                Intent.createChooser(intent, "共有")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            ErrorHandler.handleFileError(context, "share", e)
        }
    }

    /**
     * Uri指定でファイルを開く (ACTION_VIEW)
     */
    fun openUri(context: Context, uri: Uri, mimeType: String? = null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType ?: "*/*")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // VIEWで開けない場合は共有ダイアログを試行
            try {
                shareUri(context, uri, mimeType)
            } catch (shareEx: Exception) {
                ErrorHandler.handleError(context, "ファイルを開けません", e)
            }
        }
    }

    /**
     * ファイルを削除
     */
    fun deleteFile(context: Context, file: File) {
        try {
            if (file.delete()) {
                ErrorHandler.showSuccess(context, context.getString(R.string.toast_delete_success))
            } else {
                ErrorHandler.handleFileError(context, "delete")
            }
        } catch (e: Exception) {
            ErrorHandler.handleFileError(context, "delete", e)
        }
    }

    /**
     * アプリを起動
     */
    fun launchApp(context: Context, packageName: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                ErrorHandler.handleAppLaunchError(context, packageName)
            }
        } catch (e: Exception) {
            ErrorHandler.handleAppLaunchError(context, packageName, e)
        }
    }

    /**
     * パーセント値(0-100)をアルファ値(0.0-1.0)に変換
     */
    fun percentToAlpha(percent: Int): Float {
        return percent / Constants.ALPHA_PERCENTAGE_DIVISOR
    }
}
