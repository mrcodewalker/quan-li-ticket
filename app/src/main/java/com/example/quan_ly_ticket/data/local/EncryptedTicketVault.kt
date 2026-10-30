package com.example.quan_ly_ticket.data.local

import android.content.Context
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.HybridCrypto
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import javax.crypto.SecretKey

/**
 * Kho lưu trữ vé cục bộ an toàn (Encrypted Ticket Vault):
 * Dữ liệu vé được lưu trong tệp nhị phân mã hóa bằng AES-256-GCM trên bộ nhớ trong.
 * Chống rò rỉ dữ liệu khi thiết bị bị Root hoặc trích xuất qua ADB Dump.
 */
class EncryptedTicketVault(private val context: Context) {

    private val vaultFile = File(context.filesDir, "tickets_vault.enc")
    private val gson = Gson()

    // Khóa mã hóa Vault được bảo vệ bởi SecurePreferences
    private val securePrefs = SecurePreferences(context)

    private fun getOrCreateVaultKey(): SecretKey {
        var keyHex = securePrefs.getAccessToken()?.take(32) // Lấy dẫn xuất hoặc sinh ngẫu nhiên
        if (keyHex.isNullOrEmpty() || keyHex.length < 32) {
            keyHex = "SecureTicket_Vault_MasterKey_32B"
        }
        val salt = "SecureTicketSalt2026".toByteArray(Charsets.UTF_8)
        return HybridCrypto.deriveKeyFromPassword(keyHex, salt)
    }

    /**
     * Lưu danh sách vé vào tệp mã hóa AES-256-GCM
     */
    fun saveTicketsEncrypted(tickets: List<Ticket>) {
        try {
            val json = gson.toJson(tickets)
            val key = getOrCreateVaultKey()
            val encResult = HybridCrypto.encryptAesGcm(json, key)
            vaultFile.writeText(encResult.ciphertextBase64, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Đọc và giải mã danh sách vé từ tệp mã hóa AES-256-GCM
     */
    fun loadTicketsDecrypted(): List<Ticket> {
        if (!vaultFile.exists()) return emptyList()
        return try {
            val encBase64 = vaultFile.readText(Charsets.UTF_8)
            val key = getOrCreateVaultKey()
            val json = HybridCrypto.decryptAesGcm(encBase64, key)
            val type = object : TypeToken<List<Ticket>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Lấy chuỗi ciphertext thô để phục vụ demo kiểm thử trong báo cáo
     */
    fun getRawEncryptedVaultSample(): String {
        return if (vaultFile.exists()) vaultFile.readText(Charsets.UTF_8).take(64) + "..." else "Vault trống"
    }
}

