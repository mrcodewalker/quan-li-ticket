package com.example.quan_ly_ticket.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/**
 * Quản lý khóa mật mã trong Android Keystore (Hardware-Backed TEE / StrongBox SE):
 * 1. Cặp khóa bất đối xứng ECDSA P-256 (secp256r1) cho Device Binding, Ký giao dịch Mua/Chuyển vé và NFC HCE.
 * 2. Khóa đối xứng AES-256-GCM nằm trong phần cứng TEE/StrongBox để mã hóa Kho vé (EncryptedTicketVault)
 *    và Thông tin cá nhân khách hàng (Customer PII) độc lập hoàn toàn với phiên mạng.
 */
object KeystoreManager {
    private const val TAG = "KeystoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_ECDSA = "SecureTicket_DeviceBindingKey"
    const val KEY_ALIAS_VAULT_AES = "SecureTicket_VaultMasterAesKey"
    const val KEY_ALIAS_PII_AES = "SecureTicket_CustomerPiiAesKey"

    private const val GCM_IV_LENGTH = 12
    private const val GCM_TAG_LENGTH = 128

    var isStrongBoxActive: Boolean = false
        private set

    /**
     * Khởi tạo hoặc lấy cặp khóa ECDSA P-256 từ Android Keystore
     */
    fun getOrCreateDeviceKeyPair(): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS_ECDSA)) {
            generateDeviceKeyPair(useStrongBox = true)
        }

        return keyStore.getCertificate(KEY_ALIAS_ECDSA).publicKey
    }

    private fun generateDeviceKeyPair(useStrongBox: Boolean) {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS_ECDSA,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setUserAuthenticationRequired(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
                try {
                    builder.setIsStrongBoxBacked(true)
                    kpg.initialize(builder.build())
                    kpg.generateKeyPair()
                    isStrongBoxActive = true
                    Log.i(TAG, "Device ECDSA keypair generated inside StrongBox Keymaster.")
                    return
                } catch (e: StrongBoxUnavailableException) {
                    Log.w(TAG, "StrongBox not available on device, falling back to standard TEE Keystore.")
                } catch (e: Exception) {
                    Log.w(TAG, "StrongBox fallback to TEE: ${e.message}")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(false)
            }
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            isStrongBoxActive = false
            Log.i(TAG, "Device ECDSA keypair generated inside Android Keystore TEE.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore keypair: ${e.message}")
            throw e
        }
    }

    /**
     * Khởi tạo hoặc lấy khóa đối xứng AES-256-GCM lưu trực tiếp trong phần cứng AndroidKeyStore (TEE/StrongBox).
     * Khóa này không bao giờ rời khỏi chip bảo mật và không phụ thuộc vào JWT Token.
     */
    @Synchronized
    fun getOrCreateHardwareAesKey(alias: String = KEY_ALIAS_VAULT_AES): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existingKey = keyStore.getKey(alias, null) as? SecretKey
        if (existingKey != null) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                builder.setIsStrongBoxBacked(true)
                keyGenerator.init(builder.build())
                return keyGenerator.generateKey()
            } catch (_: Exception) {
                builder.setIsStrongBoxBacked(false)
            }
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /**
     * Mã hóa dữ liệu bằng khóa AES-256-GCM bên trong AndroidKeyStore (TEE/StrongBox)
     */
    fun encryptWithHardwareAes(plaintext: String, alias: String = KEY_ALIAS_VAULT_AES): String {
        val t0 = System.nanoTime()
        val secretKey = getOrCreateHardwareAesKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey) // Keystore tự sinh IV ngẫu nhiên an toàn 12 bytes
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        // Ghép [1 byte độ dài IV] + [IV] + [Ciphertext + 128-bit GCM Tag]
        val combined = ByteArray(1 + iv.size + cipherBytes.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(cipherBytes, 0, combined, 1 + iv.size, cipherBytes.size)

        val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        SecurityDebugLog.d("AES-256-GCM", "Encrypted ${plaintext.length}B with alias=$alias ivLen=${iv.size}B in ${ms}ms")
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Giải mã dữ liệu bằng khóa AES-256-GCM bên trong AndroidKeyStore (TEE/StrongBox)
     */
    fun decryptWithHardwareAes(encryptedBase64: String, alias: String = KEY_ALIAS_VAULT_AES): String {
        val t0 = System.nanoTime()
        val combined = Base64.decode(encryptedBase64, Base64.DEFAULT)
        require(combined.size > 1 + GCM_IV_LENGTH + 16) { "Ciphertext không hợp lệ hoặc bị cắt cụt" }

        val ivLength = combined[0].toInt() and 0xFF
        val iv = combined.copyOfRange(1, 1 + ivLength)
        val cipherBytes = combined.copyOfRange(1 + ivLength, combined.size)

        val secretKey = getOrCreateHardwareAesKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)

        val plainBytes = cipher.doFinal(cipherBytes)
        val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        SecurityDebugLog.d("AES-256-GCM", "Decrypted ${plainBytes.size}B with alias=$alias in ${ms}ms")
        return String(plainBytes, Charsets.UTF_8)
    }

    /**
     * Xuất Public Key của thiết bị dạng Base64 (X.509 SPKI format) để gửi lên Server
     */
    fun getDevicePublicKeyBase64(): String {
        val pubKey = getOrCreateDeviceKeyPair()
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Ký một mảng byte bất kỳ bằng Private Key ECDSA P-256 nằm trong Keystore
     */
    fun signChallenge(challengeBytes: ByteArray): String {
        val t0 = System.nanoTime()
        getOrCreateDeviceKeyPair()
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(KEY_ALIAS_ECDSA, null) as? PrivateKey
            ?: throw IllegalStateException("Keystore Private Key not found!")

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(challengeBytes)
        }

        val signedBytes = signature.sign()
        val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
        val preview = String(challengeBytes, Charsets.UTF_8).take(40)
        SecurityDebugLog.d("Keystore-ECDSA", "Signed (${if (isStrongBoxActive) "StrongBox" else "TEE"}) payload='$preview...' in ${ms}ms")
        return Base64.encodeToString(signedBytes, Base64.NO_WRAP)
    }

    /**
     * Ký số xác nhận giao dịch Chuyển nhượng vé (Phase 2 - Chống cướp vé khi lộ JWT Token)
     * Chuỗi chuẩn hóa: TRANSFER:<ticketId>:<recipientEmail>:<nonce>:<timestamp>
     */
    fun signTransferPayload(
        ticketId: String,
        recipientEmail: String,
        nonce: String,
        timestamp: Long
    ): String {
        val canonical = "TRANSFER:${ticketId.trim()}:${recipientEmail.trim().lowercase()}:$nonce:$timestamp"
        return signChallenge(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Ký số xác nhận giao dịch Mua vé / Đặt lệnh thanh toán (Phase 2 - Chống Bot cURL gom vé)
     * Chuỗi chuẩn hóa: BUY:<eventId>:<nonce>:<timestamp>
     */
    fun signBuyPayload(
        eventId: String,
        nonce: String,
        timestamp: Long
    ): String {
        val canonical = "BUY:${eventId.trim()}:$nonce:$timestamp"
        return signChallenge(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Ký số xác nhận giao dịch Hoàn trả / Hủy vé (Hardware-Signed Refund)
     * Chuỗi chuẩn hóa: REFUND:<ticketId>:<nonce>:<timestamp>
     */
    fun signRefundPayload(
        ticketId: String,
        nonce: String,
        timestamp: Long
    ): String {
        val canonical = "REFUND:${ticketId.trim()}:$nonce:$timestamp"
        return signChallenge(canonical.toByteArray(Charsets.UTF_8))
    }

    /**
     * Kiểm tra khóa có thực sự nằm trong phần cứng bảo mật (TEE / StrongBox) hay không
     */
    fun getHardwareSecurityLevel(): String {
        return try {
            getOrCreateDeviceKeyPair()
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val privateKey = keyStore.getKey(KEY_ALIAS_ECDSA, null) as? PrivateKey ?: return "Software Keystore"
            val factory = KeyFactory.getInstance(privateKey.algorithm, ANDROID_KEYSTORE)
            val keyInfo = factory.getKeySpec(privateKey, KeyInfo::class.java)
            @Suppress("DEPRECATION")
            if (keyInfo.isInsideSecureHardware) {
                if (isStrongBoxActive) "StrongBox Hardware SE" else "Hardware-Backed TEE (TrustZone)"
            } else {
                "Android Keystore (Virtual/Emulator TEE)"
            }
        } catch (e: Exception) {
            "Android Keystore TEE"
        }
    }
}
