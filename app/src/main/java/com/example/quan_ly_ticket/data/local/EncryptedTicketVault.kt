package com.example.quan_ly_ticket.data.local

import android.content.Context
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.KeystoreManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Kho lưu trữ vé & hồ sơ khách hàng cục bộ an toàn (Hardware-Backed Encrypted Vault - Phase 2):
 * - Dữ liệu vé được lưu trong tệp nhị phân/Base64 `tickets_vault.enc` tại Internal Storage Sandbox.
 * - Mã hóa trực tiếp bằng khóa đối xứng AES-256-GCM sinh bên trong phần cứng AndroidKeyStore (TEE/StrongBox).
 * - Độc lập hoàn toàn với JWT Access Token: Mở vé mượt mà khi bật Chế độ máy bay (Offline)
 *   và chống trích xuất khi thiết bị bị Root hoặc ADB Dump.
 */
class EncryptedTicketVault(context: Context) {

    private val vaultFile = File(context.filesDir, "tickets_vault.enc")
    private val customerPiiFile = File(context.filesDir, "customer_pii_vault.enc")
    private val gson = Gson()

    data class CustomerPiiRecord(
        val userId: String,
        val fullName: String,
        val email: String,
        val phone: String,
        val identityNumberMasked: String,
        val encryptedAt: Long = System.currentTimeMillis()
    )

    /**
     * Lưu danh sách vé vào tệp mã hóa AES-256-GCM bằng khóa phần cứng AndroidKeyStore TEE
     */
    @Synchronized
    fun saveTicketsEncrypted(tickets: List<Ticket>) {
        try {
            val json = gson.toJson(tickets)
            val ciphertextBase64 = KeystoreManager.encryptWithHardwareAes(
                plaintext = json,
                alias = KeystoreManager.KEY_ALIAS_VAULT_AES
            )
            vaultFile.writeText(ciphertextBase64, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Đọc và giải mã danh sách vé từ tệp mã hóa AES-256-GCM bằng khóa phần cứng AndroidKeyStore TEE.
     * Nếu file bị sửa đổi dù chỉ 1 bit, hàm sẽ ném AEADBadTagException và từ chối nạp vé giả.
     */
    @Synchronized
    fun loadTicketsDecrypted(): List<Ticket> {
        if (!vaultFile.exists()) return emptyList()
        return try {
            val encBase64 = vaultFile.readText(Charsets.UTF_8)
            val json = KeystoreManager.decryptWithHardwareAes(
                encryptedBase64 = encBase64,
                alias = KeystoreManager.KEY_ALIAS_VAULT_AES
            )
            val type = object : TypeToken<List<Ticket>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Mã hóa thông tin cá nhân nhạy cảm của khách hàng (PII: Họ tên, Email, SĐT, CCCD)
     * bằng khóa AES-256-GCM riêng biệt trong AndroidKeyStore (KEY_ALIAS_PII_AES)
     */
    @Synchronized
    fun saveCustomerPiiEncrypted(record: CustomerPiiRecord) {
        try {
            val json = gson.toJson(record)
            val ciphertextBase64 = KeystoreManager.encryptWithHardwareAes(
                plaintext = json,
                alias = KeystoreManager.KEY_ALIAS_PII_AES
            )
            customerPiiFile.writeText(ciphertextBase64, Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Giải mã thông tin cá nhân khách hàng (PII) từ tệp `customer_pii_vault.enc`
     */
    @Synchronized
    fun loadCustomerPiiDecrypted(): CustomerPiiRecord? {
        if (!customerPiiFile.exists()) return null
        return try {
            val encBase64 = customerPiiFile.readText(Charsets.UTF_8)
            val json = KeystoreManager.decryptWithHardwareAes(
                encryptedBase64 = encBase64,
                alias = KeystoreManager.KEY_ALIAS_PII_AES
            )
            gson.fromJson(json, CustomerPiiRecord::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Lấy mẫu bản mã (Ciphertext) thô của kho vé trên đĩa để phục vụ kiểm chứng trong Crypto Lab
     */
    fun getRawEncryptedVaultSample(): String {
        return if (vaultFile.exists()) {
            vaultFile.readText(Charsets.UTF_8).take(72) + "..."
        } else {
            "Chưa có vé lưu trong Vault"
        }
    }

    /**
     * Lấy mẫu bản mã (Ciphertext) thô của hồ sơ khách hàng (PII) trên đĩa
     */
    fun getRawEncryptedCustomerPiiSample(): String {
        return if (customerPiiFile.exists()) {
            customerPiiFile.readText(Charsets.UTF_8).take(72) + "..."
        } else {
            "Chưa có hồ sơ PII mã hóa"
        }
    }
}
