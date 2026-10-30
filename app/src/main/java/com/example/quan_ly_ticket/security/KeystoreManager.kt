package com.example.quan_ly_ticket.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Quản lý cặp khóa bất đối xứng trong Android Keystore (Hardware-Backed / StrongBox)
 * Khóa bí mật (Private Key) được lưu trong phần cứng bảo mật (TEE/SE), không bao giờ bị xuất ra ngoài.
 */
object KeystoreManager {
    private const val TAG = "KeystoreManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "SecureTicket_DeviceBindingKey"

    /**
     * Khởi tạo hoặc lấy cặp khóa ECDSA P-256 từ Android Keystore
     */
    fun getOrCreateDeviceKeyPair(): PublicKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateDeviceKeyPair(useStrongBox = true)
        }

        return keyStore.getCertificate(KEY_ALIAS).publicKey
    }

    private fun generateDeviceKeyPair(useStrongBox: Boolean) {
        try {
            val kpg = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                ANDROID_KEYSTORE
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                // Yêu cầu xác thực người dùng nếu cần thiết
                .setUserAuthenticationRequired(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && useStrongBox) {
                try {
                    builder.setIsStrongBoxBacked(true)
                    kpg.initialize(builder.build())
                    kpg.generateKeyPair()
                    Log.i(TAG, "Device ECDSA keypair generated inside StrongBox Keymaster.")
                    return
                } catch (e: StrongBoxUnavailableException) {
                    Log.w(TAG, "StrongBox not available on device, falling back to standard TEE Keystore.")
                }
            }

            builder.setIsStrongBoxBacked(false)
            kpg.initialize(builder.build())
            kpg.generateKeyPair()
            Log.i(TAG, "Device ECDSA keypair generated inside Android Keystore TEE.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore keypair: ${e.message}")
            throw e
        }
    }

    /**
     * Xuất Public Key của thiết bị dạng Base64 (X.509 format) để gửi lên Server
     */
    fun getDevicePublicKeyBase64(): String {
        val pubKey = getOrCreateDeviceKeyPair()
        return Base64.encodeToString(pubKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Ký một challenge ngẫu nhiên (dùng trong xác thực NFC HCE và chống Replay)
     * Ký bằng Private Key nằm trong Keystore
     */
    fun signChallenge(challengeBytes: ByteArray): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Keystore Private Key not found!")

        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(challengeBytes)
        }

        val signedBytes = signature.sign()
        return Base64.encodeToString(signedBytes, Base64.NO_WRAP)
    }
}

