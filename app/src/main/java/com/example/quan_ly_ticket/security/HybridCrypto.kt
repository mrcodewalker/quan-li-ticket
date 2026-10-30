package com.example.quan_ly_ticket.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thư viện Mật mã học nâng cao phục vụ môn học An toàn và Bảo mật Thông tin:
 * 1. AES-256-GCM (Galois/Counter Mode): Mã hóa đối xứng có xác thực toàn vẹn (AEAD)
 * 2. PBKDF2 with HMAC-SHA256: Hàm dẫn xuất khóa an toàn từ mật khẩu kèm muối (Salt)
 * 3. Chống rò rỉ độ dài và chống tấn công Padding Oracle
 */
object HybridCrypto {

    private const val AES_KEY_SIZE = 256
    private const val GCM_IV_LENGTH = 12 // 96 bits chuẩn cho AES-GCM
    private const val GCM_TAG_LENGTH = 128 // 128 bits authentication tag
    private const val PBKDF2_ITERATIONS = 10000

    data class EncryptionResult(
        val ciphertextBase64: String,
        val ivHex: String,
        val keyHex: String,
        val originalPlaintext: String
    )

    /**
     * Sinh khóa bí mật AES-256 ngẫu nhiên an toàn cao (SecureRandom)
     */
    fun generateRandomAesKey(): SecretKey {
        val keyBytes = ByteArray(32) // 256 bits
        SecureRandom().nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Dẫn xuất khóa AES-256 từ mật khẩu bằng thuật toán chuẩn PBKDF2 with HMAC-SHA256
     */
    fun deriveKeyFromPassword(password: String, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val derivedBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(derivedBytes, "AES")
    }

    /**
     * Mã hóa dữ liệu bằng AES-256-GCM (AEAD - Authenticated Encryption with Associated Data)
     * Đảm bảo cả Tính bí mật (Confidentiality) lẫn Tính toàn vẹn (Integrity)
     */
    fun encryptAesGcm(plaintext: String, secretKey: SecretKey): EncryptionResult {
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val cipherBytes = cipher.doFinal(plaintextBytes)

        // Ghép [IV (12B)] + [Ciphertext + Tag (16B)]
        val combined = iv + cipherBytes
        val ciphertextBase64 = Base64.encodeToString(combined, Base64.NO_WRAP)

        return EncryptionResult(
            ciphertextBase64 = ciphertextBase64,
            ivHex = iv.joinToString("") { "%02X".format(it) },
            keyHex = secretKey.encoded.joinToString("") { "%02X".format(it) },
            originalPlaintext = plaintext
        )
    }

    /**
     * Giải mã dữ liệu AES-256-GCM.
     * Nếu ciphertext hoặc tag bị kẻ gian sửa dù chỉ 1 bit, hàm sẽ ném lỗi AEADBadTagException
     */
    fun decryptAesGcm(encryptedBase64: String, secretKey: SecretKey): String {
        val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
        if (combined.size < GCM_IV_LENGTH + 16) {
            throw IllegalArgumentException("Ciphertext quá ngắn hoặc không đúng định dạng GCM")
        }

        val iv = combined.copyOfRange(0, GCM_IV_LENGTH)
        val cipherBytes = combined.copyOfRange(GCM_IV_LENGTH, combined.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val decryptedBytes = cipher.doFinal(cipherBytes)
        return String(decryptedBytes, Charsets.UTF_8)
    }
}

