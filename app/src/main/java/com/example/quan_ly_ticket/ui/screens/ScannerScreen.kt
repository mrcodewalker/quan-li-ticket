package com.example.quan_ly_ticket.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.model.ValidateQrRequest
import com.example.quan_ly_ticket.data.model.ValidationResultData
import com.example.quan_ly_ticket.security.NfcReaderHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var qrInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var verifiedData by remember { mutableStateOf<ValidationResultData?>(null) }
    var validationMethod by remember { mutableStateOf("QR") }

    // Bật chế độ quét NFC Reader khi nhân viên mở màn hình Soát vé
    DisposableEffect(activity) {
        if (activity != null) {
            NfcReaderHelper.enableNfcReader(activity, scope) { isSuccess, message, data ->
                isValidating = false
                validationMethod = "NFC_HCE"
                validationResult = Pair(isSuccess, message)
                verifiedData = data
            }
        }
        onDispose {
            if (activity != null) {
                NfcReaderHelper.disableNfcReader(activity)
            }
        }
    }

    fun processValidation(qrString: String) {
        if (qrString.isBlank()) return
        isValidating = true
        validationMethod = "QR"
        validationResult = null
        verifiedData = null

        scope.launch {
            try {
                val api = ApiClient.getService(context)
                val res = api.validateQr(ValidateQrRequest(qrString.trim()))
                if (res.isSuccessful && res.body()?.success == true) {
                    validationResult = Pair(true, res.body()?.message ?: "Vé hợp lệ!")
                    verifiedData = res.body()?.data
                } else {
                    val errMsg = res.body()?.error ?: "Xác thực thất bại (${res.code()})"
                    validationResult = Pair(false, errMsg)
                }
            } catch (e: Exception) {
                validationResult = Pair(false, "Lỗi kết nối: ${e.message}")
            } finally {
                isValidating = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chế độ Soát vé (Staff)", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // NFC Tap Card Active Prompt
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "NFC",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NFC Reader Đang Bật",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1E40AF),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Chạm mặt lưng vào điện thoại của khách hàng để nhận vé tức thì qua NFC HCE Challenge-Response.",
                            fontSize = 12.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scanner instruction card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Kiểm tra Chữ ký số & Chống Replay",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Mã QR động được kiểm tra chữ ký ECDSA P-256 từ Server và mã TOTP 30s. Quét lần 2 sẽ bị phát hiện là Replay Attack.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR Input field
            OutlinedTextField(
                value = qrInput,
                onValueChange = { qrInput = it },
                label = { Text("Chuỗi mã QR (Quét bằng camera hoặc dán mã)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { processValidation(qrInput) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                enabled = !isValidating && qrInput.isNotBlank()
            ) {
                if (isValidating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Kiểm tra QR")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Kết quả xác thực
            validationResult?.let { (isSuccess, message) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Close,
                            contentDescription = null,
                            tint = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier.size(56.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isSuccess) "XÁC THỰC THÀNH CÔNG (${validationMethod})" else "TỪ CHỐI VÉ (${validationMethod})!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = message,
                            fontSize = 13.sp,
                            color = if (isSuccess) Color(0xFF166534) else Color(0xFF991B1B)
                        )

                        if (isSuccess && verifiedData != null) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFBBF7D0))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(text = "Mã vé: ${verifiedData?.ticketCode}", fontWeight = FontWeight.Bold)
                            Text(text = "Sự kiện: ${verifiedData?.eventTitle}", fontSize = 13.sp)
                            Text(text = "Khách hàng: ${verifiedData?.ownerName}", fontSize = 13.sp)
                            Text(text = "Thời gian: ${verifiedData?.checkInTime}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}
