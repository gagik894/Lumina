package com.lumina.app.util

import android.util.Log

/**
 * Utility class for logging camera lifecycle events.
 * Helps debug and verify that transient camera operations work correctly.
 */
object CameraLifecycleLogger {
    private const val TAG = "CameraLifecycle"

    fun logCameraActivated(mode: String) {
        Log.d(TAG, "🟢 Camera ACTIVATED for: $mode")
    }

    fun logCameraDeactivated(reason: String) {
        Log.d(TAG, "🔴 Camera DEACTIVATED after: $reason")
    }

    fun logTransientOperationStart(operation: String) {
        Log.d(TAG, "⚡ Transient operation STARTED: $operation")
    }

    fun logTransientOperationEnd(operation: String) {
        Log.d(TAG, "✅ Transient operation COMPLETED: $operation")
    }
}
