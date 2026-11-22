package com.example.quan_ly_ticket.security

import android.util.Log
import com.example.quan_ly_ticket.BuildConfig

/**
 * Centralized debug-only Logcat telemetry for cryptographic, vault, RASP, and auth events.
 * Filter in Android Studio Logcat with tag: SecureTicketSec
 */
object SecurityDebugLog {
    private const val TAG = "SecureTicketSec"

    fun d(category: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "[$category] $message")
        }
    }

    fun w(category: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.w(TAG, "[$category] $message")
        }
    }
}
