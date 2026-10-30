package com.example.quan_ly_ticket.security

import android.content.Context
import android.os.Build
import android.os.Debug
import com.scottyab.rootbeer.RootBeer

/**
 * Kiểm tra tính toàn vẹn và môi trường chạy của ứng dụng (Anti-Tampering)
 */
object SecurityCheck {

    data class SecurityStatus(
        val isRooted: Boolean,
        val isDebuggerAttached: Boolean,
        val isEmulator: Boolean,
        val isSecure: Boolean
    )

    fun checkDeviceSecurity(context: Context): SecurityStatus {
        val rootBeer = RootBeer(context)
        val isRooted = rootBeer.isRooted

        val isDebugger = Debug.isDebuggerConnected() || Debug.waitingForDebugger()
        val isEmulator = checkIsEmulator()

        val isSecure = !isRooted && !isDebugger

        return SecurityStatus(
            isRooted = isRooted,
            isDebuggerAttached = isDebugger,
            isEmulator = isEmulator,
            isSecure = isSecure
        )
    }

    private fun checkIsEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HARDWARE.contains("goldfish")
                || Build.HARDWARE.contains("ranchu")
                || Build.PRODUCT.contains("sdk_gphone")
                || Build.PRODUCT.contains("sdk_google"))
    }
}

