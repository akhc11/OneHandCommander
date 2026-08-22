package com.example.onehandcommander.core

import android.accessibilityservice.GestureDescription
import android.graphics.Path

/**
 * OS の AccessibilityService Gesture API を抽象化し、
 * UI コンポーネントが AccessibilityService 具象クラスに直接依存するのを防ぐインターフェース
 */
interface GestureDispatcher {
    /**
     * 指定された Path と時間パラメータでジェスチャーをディスパッチする
     */
    fun dispatchGesture(
        path: Path,
        startTime: Long = 0,
        duration: Long = 50,
        onCompleted: (() -> Unit)? = null,
        onCancelled: (() -> Unit)? = null
    ): Boolean

    /**
     * 指定されたスクリーン座標 (x, y) をタップする
     */
    fun dispatchTap(
        x: Float,
        y: Float,
        duration: Long = 50,
        onCompleted: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        return dispatchGesture(path, 0, duration, onCompleted)
    }

    /**
     * 指定されたスクリーン座標 (x, y) を長押しタップする
     */
    fun dispatchLongPress(
        x: Float,
        y: Float,
        duration: Long = 500,
        onCompleted: (() -> Unit)? = null
    ): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        return dispatchGesture(path, 0, duration, onCompleted)
    }
}
