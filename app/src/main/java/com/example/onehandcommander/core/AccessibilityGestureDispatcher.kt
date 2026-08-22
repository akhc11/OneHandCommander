package com.example.onehandcommander.core

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import com.example.onehandcommander.utils.ErrorHandler

/**
 * AccessibilityService の dispatchGesture API を実装したデフォルトのアダプタ
 */
class AccessibilityGestureDispatcher(
    private val service: AccessibilityService
) : GestureDispatcher {

    override fun dispatchGesture(
        path: Path,
        startTime: Long,
        duration: Long,
        onCompleted: (() -> Unit)?,
        onCancelled: (() -> Unit)?
    ): Boolean {
        return try {
            val stroke = GestureDescription.StrokeDescription(path, startTime, duration.coerceAtLeast(1L))
            val gesture = GestureDescription.Builder()
                .addStroke(stroke)
                .build()

            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        onCompleted?.invoke()
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        onCancelled?.invoke()
                    }
                },
                null
            )
        } catch (e: Exception) {
            ErrorHandler.logError("Failed to dispatch gesture via AccessibilityService", e)
            false
        }
    }
}
