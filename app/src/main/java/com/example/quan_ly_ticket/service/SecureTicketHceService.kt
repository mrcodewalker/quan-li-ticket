package com.example.quan_ly_ticket.service

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import com.example.quan_ly_ticket.security.KeystoreManager

/**
 * Dịch vụ giả lập thẻ NFC (Host Card Emulation - HCE)
 * Phục vụ cơ chế Challenge-Response chống sao chép và tấn công Relay khi soát vé qua NFC.
 */
class SecureTicketHceService : HostApduService() {

    companion object {
        private const val TAG = "SecureTicketHce"

        // APDU Status Words
        private val STATUS_SUCCESS = byteArrayOf(0x90.toByte(), 0x00.toByte())
        private val STATUS_FAILED = byteArrayOf(0x6F.toByte(), 0x00.toByte())
        private val CLA_CUSTOM = 0x80.toByte()
        private val INS_GET_CHALLENGE_RESPONSE = 0x10.toByte()

        // Vé đang được kích hoạt để xuất trình qua NFC
        var activeTicketId: String? = null
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null || commandApdu.size < 4) {
            return STATUS_FAILED
        }

        Log.d(TAG, "Received APDU: ${commandApdu.joinToString(" ") { "%02X".format(it) }}")

        // 1. Kiểm tra lệnh SELECT AID: 00 A4 04 00 [Length] [AID]
        if (commandApdu[0] == 0x00.toByte() && commandApdu[1] == 0xA4.toByte()) {
            Log.i(TAG, "APDU SELECT AID matched. Responding OK.")
            return STATUS_SUCCESS
        }

        // 2. Kiểm tra lệnh CHALLENGE - RESPONSE: 80 10 00 00 [Length] [Challenge Bytes]
        if (commandApdu[0] == CLA_CUSTOM && commandApdu[1] == INS_GET_CHALLENGE_RESPONSE) {
            try {
                val challengeLength = commandApdu[4].toInt() and 0xFF
                val challengeBytes = commandApdu.copyOfRange(5, 5 + challengeLength)

                Log.i(TAG, "Signing challenge using Android Keystore Private Key...")
                val signatureBase64 = KeystoreManager.signChallenge(challengeBytes)

                val responsePayload = (activeTicketId ?: "NO_ACTIVE_TICKET") + "|" + signatureBase64
                val responseBytes = responsePayload.toByteArray(Charsets.UTF_8)

                // Ghép payload phản hồi với mã trạng thái 0x9000
                return responseBytes + STATUS_SUCCESS
            } catch (e: Exception) {
                Log.e(TAG, "Error generating challenge response: ${e.message}")
                return STATUS_FAILED
            }
        }

        return STATUS_FAILED
    }

    override fun onDeactivated(reason: Int) {
        Log.i(TAG, "NFC HCE link deactivated. Reason: $reason")
    }
}

