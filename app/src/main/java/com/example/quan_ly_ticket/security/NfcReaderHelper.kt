package com.example.quan_ly_ticket.security

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.util.Log
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.model.ValidationResultData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Trình quản lý chế độ Reader NFC cho máy nhân viên soát vé
 * Gửi lệnh APDU tới điện thoại khách hàng (đang chạy HCE) để thực hiện giao thức Challenge-Response
 */
object NfcReaderHelper {
    private const val TAG = "NfcReaderHelper"

    // AID của SecureTicket (phải trùng với apduservice.xml)
    private val AID_SECURE_TICKET = byteArrayOf(
        0xF0.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte()
    )

    // APDU SELECT AID command: 00 A4 04 00 07 F0 01 02 03 04 05 06 00
    private fun createSelectAidApdu(): ByteArray {
        val header = byteArrayOf(0x00.toByte(), 0xA4.toByte(), 0x04.toByte(), 0x00.toByte(), AID_SECURE_TICKET.size.toByte())
        return header + AID_SECURE_TICKET + byteArrayOf(0x00.toByte())
    }

    // APDU CHALLENGE command: 80 10 00 00 [len] [challengeBytes] 00
    private fun createChallengeApdu(challengeBytes: ByteArray): ByteArray {
        val header = byteArrayOf(0x80.toByte(), 0x10.toByte(), 0x00.toByte(), 0x00.toByte(), challengeBytes.size.toByte())
        return header + challengeBytes + byteArrayOf(0x00.toByte())
    }

    /**
     * Bật chế độ quét NFC Reader trên Activity của máy soát vé
     */
    fun enableNfcReader(
        activity: Activity,
        scope: CoroutineScope,
        onResult: (isSuccess: Boolean, message: String, data: ValidationResultData?) -> Unit
    ) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return

        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK
        nfcAdapter.enableReaderMode(
            activity,
            { tag ->
                scope.launch(Dispatchers.IO) {
                    processNfcTag(activity, tag, onResult)
                }
            },
            flags,
            null
        )
        Log.i(TAG, "NFC Reader Mode enabled on staff device.")
    }

    /**
     * Tắt chế độ quét NFC Reader khi rời màn hình soát vé
     */
    fun disableNfcReader(activity: Activity) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(activity) ?: return
        nfcAdapter.disableReaderMode(activity)
        Log.i(TAG, "NFC Reader Mode disabled.")
    }

    private suspend fun processNfcTag(
        activity: Activity,
        tag: Tag,
        onResult: (isSuccess: Boolean, message: String, data: ValidationResultData?) -> Unit
    ) {
        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            withContext(Dispatchers.Main) {
                onResult(false, "Thẻ hoặc thiết bị không hỗ trợ giao thức ISO-DEP.", null)
            }
            return
        }

        try {
            isoDep.connect()
            isoDep.timeout = 5000

            // 1. Gửi lệnh SELECT AID để chọn ứng dụng SecureTicket HCE trên máy khách
            val selectResponse = isoDep.transceive(createSelectAidApdu())
            if (!isApduSuccess(selectResponse)) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Không tìm thấy ứng dụng vé số SecureTicket trên thiết bị khách.", null)
                }
                return
            }

            // 2. Gọi Backend xin Challenge ngẫu nhiên (chống Replay và Relay)
            val api = ApiClient.getService(activity)
            val challengeRes = api.getNfcChallenge()
            if (!challengeRes.isSuccessful || challengeRes.body()?.success != true) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Không thể lấy Challenge từ Server.", null)
                }
                return
            }

            val challengeData = challengeRes.body()!!.data!!
            val challengeBytes = challengeData.challenge.toByteArray(Charsets.UTF_8)

            // 3. Gửi Challenge sang điện thoại khách qua NFC APDU
            val responseApdu = isoDep.transceive(createChallengeApdu(challengeBytes))
            if (!isApduSuccess(responseApdu)) {
                withContext(Dispatchers.Main) {
                    onResult(false, "Điện thoại khách từ chối ký Challenge.", null)
                }
                return
            }

            // 4. Giải mã payload trả về từ khách: TicketID | DeviceSignature
            val responseBytes = responseApdu.copyOfRange(0, responseApdu.size - 2)
            val responseString = String(responseBytes, Charsets.UTF_8)
            val parts = responseString.split("|")

            if (parts.size < 2 || parts[0] == "NO_ACTIVE_TICKET") {
                withContext(Dispatchers.Main) {
                    onResult(false, "Khách hàng chưa mở chi tiết vé trên màn hình để xuất trình.", null)
                }
                return
            }

            val ticketId = parts[0]
            val deviceSignature = parts[1]

            // 5. Gửi lên Backend để kiểm tra chữ ký Keystore và hoàn tất check-in
            val validateRes = api.validateNfc(
                com.example.quan_ly_ticket.data.model.ValidateNfcRequest(
                    ticketId = ticketId,
                    challengeId = challengeData.challengeId,
                    deviceSignature = deviceSignature
                )
            )

            withContext(Dispatchers.Main) {
                if (validateRes.isSuccessful && validateRes.body()?.success == true) {
                    onResult(true, "Xác thực NFC HCE thành công!", validateRes.body()?.data)
                } else {
                    val errMsg = validateRes.body()?.error ?: "Xác thực NFC thất bại (${validateRes.code()})"
                    onResult(false, errMsg, null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi trao đổi NFC: ${e.message}")
            withContext(Dispatchers.Main) {
                onResult(false, "Lỗi kết nối NFC: ${e.message}", null)
            }
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    private fun isApduSuccess(response: ByteArray): Boolean {
        if (response.size < 2) return false
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        return sw1 == 0x90 && sw2 == 0x00
    }
}

