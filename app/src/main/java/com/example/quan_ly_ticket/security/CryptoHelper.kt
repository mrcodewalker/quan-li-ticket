package com.example.quan_ly_ticket.security

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.example.quan_ly_ticket.data.model.Ticket
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {

    /**
     * Khóa công khai ECDSA P-256 chuẩn của Server SecureTicket (SPKI Base64 từ backend/keys/server_public.pem)
     */
    const val DEFAULT_SERVER_PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8cBc42TqLQC9ZMSfa+P4xyACUx425Dob+CzVdXuJ3/WjeYB17Dj7tb5a1Mwpw+E6xzEcYQsCNQZfDELMwaEvPw=="

    /**
     * Xác minh chữ ký số ECDSA P-256 của Server trực tiếp trên Android Client bằng Server Public Key.
     * Cơ chế cốt lõi chống làm giả vé (Anti-Counterfeiting).
     */
    fun verifyServerSignature(
        canonicalString: String,
        signatureBase64: String,
        serverPublicKeyBase64: String = DEFAULT_SERVER_PUBLIC_KEY_BASE64
    ): Boolean {
        if (signatureBase64.isBlank()) return false
        return try {
            val t0 = System.nanoTime()
            val cleanPubKey = serverPublicKeyBase64
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")
            val keyBytes = Base64.decode(cleanPubKey, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance("EC")
            val publicKey = kf.generatePublic(spec)

            val signature = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(publicKey)
                update(canonicalString.toByteArray(Charsets.UTF_8))
            }

            val sigBytes = Base64.decode(signatureBase64, Base64.DEFAULT)
            val verified = signature.verify(sigBytes)
            val ms = ((System.nanoTime() - t0) / 1_000_000L).coerceAtLeast(1L)
            SecurityDebugLog.d("ECDSA-Verify", "Offline server signature verified=$verified in ${ms}ms")
            verified
        } catch (e: Exception) {
            SecurityDebugLog.w("ECDSA-Verify", "Offline verification failed: ${e.message}")
            false
        }
    }

    /**
     * Tạo chuỗi định danh vé chuẩn hóa (Canonical Ticket Payload) khớp với Backend:
     * Định dạng: TICKET_ID|EVENT_ID|OWNER_ID|NONCE|TIMESTAMP_SECONDS
     */
    fun buildTicketCanonicalString(ticket: Ticket, fallbackOwnerId: String = ""): String {
        val ownerId = ticket.currentOwnerId ?: fallbackOwnerId
        val tsSeconds = ticket.issuedTimestamp ?: 1793346577L
        return "${ticket.id}|${ticket.eventId ?: ""}|$ownerId|${ticket.nonce}|$tsSeconds"
    }

    /**
     * Sinh mã TOTP động cục bộ dựa trên hạt giống bí mật qrSecretSeed của vé (HMAC-SHA256 xoay vòng mỗi 30s)
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
