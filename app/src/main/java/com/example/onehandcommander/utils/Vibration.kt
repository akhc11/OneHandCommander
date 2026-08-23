package com.example.onehandcommander.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.onehandcommander.settings.SavedData

object Vibration {
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun vibrateTick() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(10)
        }
    }

    fun vibrateClick() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(30)
        }
    }

    /**
     * 上下左右（2, 4, 6, 8）用: 軽微なパルス（微小クリック / 10ms）
     */
    fun vibrateOrthogonal() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(8, 80))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(8)
        }
    }

    /**
     * 斜め（1, 3, 7, 9）用: しっかりしたクリック（斜め判定を指先に確実に伝達）
     */
    fun vibrateDiagonal() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(24, 220))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(24)
        }
    }

    /**
     * 中心タップ（5）用: ダブルマイクロパルス
     */
    fun vibrateCenterTap() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timings = longArrayOf(0, 8, 25, 8)
            val amplitudes = intArrayOf(0, 100, 0, 100)
            v.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(longArrayOf(0, 8, 25, 8), -1)
        }
    }

    /**
     * 長押し（0）用: 重厚な確定フィードバック
     */
    fun vibrateLongPress() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(45, 255))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(45)
        }
    }

    /**
     * 2桁確定／実行成功用パルス
     */
    fun vibrateSuccess() {
        if (!SavedData.isVibrationEnabled()) return
        val v = vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(18, 180))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(18)
        }
    }
}

