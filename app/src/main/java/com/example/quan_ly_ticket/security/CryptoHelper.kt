package com.example.quan_ly_ticket.security

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    /**
     * Xác minh chữ ký số của Server trực tiếp trên Android Client bằng Server Public Key
     */
    fun verifyServerSignature(
        canonicalString: String,
        signatureBase64: String,
        serverPublicKeyBase64: String
    ): Boolean {
        return try {
            val keyBytes = Base64.decode(serverPublicKeyBase64, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("EC")
            val publicKey = kf.generatePublic(spec)

            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(canonicalString.toByteArray(Charsets.UTF_8))
            }

            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            signature.verify(sigBytes)
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Sinh mã TOTP động cục bộ dựa trên seed vé (HMAC-SHA256 xoay vòng mỗi 30s)
     */
    fun generateLocalRotatingCode(ticketId: String, qrSecretSeed: String): Pair<String, Int> {
        val now = System.currentTimeMillis()
        val timeSlice = now / 30000L
        val remainingSeconds = (30 - ((now / 1000L) % 30)).toInt()

        val mac = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(qrSecretSeed.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKey)

        val dataToMac = "$ticketId:$timeSlice".toByteArray(Charsets.UTF_8)
        val hash = mac.doFinal(dataToMac)
        val hex = hash.joinToString("") { "%02x".format(it) }.substring(0, 16)

        return Pair(hex, remainingSeconds)
    }

    /**
     * Tạo hình ảnh Bitmap mã QR từ chuỗi nội dung (sử dụng ZXing)
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}

