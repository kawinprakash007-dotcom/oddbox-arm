package com.oddbox.armcontroller.data.ble

import android.util.Log

/**
 * Centralized Logcat wrapper for tag [BleConstants.LOG_TAG] ("ODDBOX_BLE").
 * Also supports an optional test sink so JVM unit tests execute cleanly without
 * hitting unmocked android.util.Log failures.
 */
object BleDebugLog {
    @Volatile
    var testListener: ((String) -> Unit)? = null

    fun d(message: String) {
        testListener?.invoke(message)
        try {
            Log.d(BleConstants.LOG_TAG, message)
        } catch (_: Throwable) {
            // Safe fallback under local JVM unit tests where android.util.Log is stubbed.
        }
    }

    fun w(message: String) {
        testListener?.invoke("WARN: $message")
        try {
            Log.w(BleConstants.LOG_TAG, message)
        } catch (_: Throwable) {
        }
    }
}

