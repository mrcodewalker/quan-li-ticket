package com.example.quan_ly_ticket.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Lưu trữ phiên đăng nhập và dữ liệu nhạy cảm được mã hóa an toàn bằng EncryptedSharedPreferences (AES-256)
 */
class SecurePreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_ticket_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAuthTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .apply()
    }

    fun getAccessToken(): String? = prefs.getString("access_token", null)

    fun saveUser(id: String, email: String, fullName: String, role: String) {
        prefs.edit()
            .putString("user_id", id)
            .putString("user_email", email)
            .putString("user_full_name", fullName)
            .putString("user_role", role)
            .apply()
    }

    fun getUserRole(): String? = prefs.getString("user_role", null)
    fun getUserEmail(): String? = prefs.getString("user_email", null)
    fun getUserFullName(): String? = prefs.getString("user_full_name", null)

    fun saveServerPublicKey(keyBase64: String) {
        prefs.edit().putString("server_pubkey", keyBase64).apply()
    }

    fun getServerPublicKey(): String? = prefs.getString("server_pubkey", null)

    fun clear() {
        prefs.edit().clear().apply()
    }
}

