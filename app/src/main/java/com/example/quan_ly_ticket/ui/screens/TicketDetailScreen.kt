package com.example.quan_ly_ticket.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.data.model.TransferTicketRequest
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.service.SecureTicketHceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    ticket: Ticket,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var currentTotpToken by remember { mutableStateOf("") }
    var isNfcActive by remember { mutableStateOf(false) }

    var showTransferDialog by remember { mutableStateOf(false) }
    var recipientEmail by remember { mutableStateOf("") }
    var isTransferring by remember { mutableStateOf(false) }

    // Kích hoạt vé cho NFC HCE khi màn hình hiển thị
    DisposableEffect(ticket.id) {
        SecureTicketHceService.activeTicketId = ticket.id
        isNfcActive = true
        onDispose {
            SecureTicketHceService.activeTicketId = null
            isNfcActive = false
        }
    }

    // Vòng lặp xoay vòng mã QR động mỗi 30s (TOTP)
    LaunchedEffect(ticket.id, ticket.qrSecretSeed) {
        val seed = ticket.qrSecretSeed ?: "default-seed"
        val tId = if (ticket.id.isNotEmpty()) ticket.id else "TKT-TEMP-ID"
        while (isActive) {
            val (token, remaining) = CryptoHelper.generateLocalRotatingCode(tId, seed)
            currentTotpToken = token
            remainingSeconds = remaining

            val timeSlice = System.currentTimeMillis() / 30000L
            val sig = ticket.serverSignature ?: ""
            val qrPayload = """{"tId":"$tId","ts":$timeSlice,"totp":"$token","sig":"$sig"}"""

            try {
                qrBitmap = CryptoHelper.generateQrBitmap(qrPayload, size = 512)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chi tiết Vé An toàn", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (ticket.status == "ACTIVE" || ticket.status == null) {
                        IconButton(onClick = { showTransferDialog = true }) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chuyển nhượng")
                        }
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
            // Event Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ticket.ticketCode.isNotEmpty()) ticket.ticketCode else "TKT-SECURE",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (ticket.status == "ACTIVE" || ticket.status == null) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                        ) {
                            Text(
                                text = if (ticket.status == "ACTIVE" || ticket.status == null) "HỢP LỆ" else (ticket.status ?: "ACTIVE"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (ticket.status == "ACTIVE" || ticket.status == null) Color(0xFF16A34A) else Color.Red
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = ticket.eventTitle ?: "Vé sự kiện SecureTicket", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(text = ticket.eventLocation ?: "Địa điểm tổ chức", fontSize = 13.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic QR Code Card (Anti-Screenshot / Anti-Copy)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Rotating", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Mã QR Động (Đổi sau ${remainingSeconds}s)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (qrBitmap != null && (ticket.status == "ACTIVE" || ticket.status == null)) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "Dynamic QR Code",
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.White)
                        )
                    } else if (ticket.status != "ACTIVE" && ticket.status != null) {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Vé không còn hiệu lực", color = Color.Gray, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Box(
                            modifier = Modifier.size(240.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { remainingSeconds / 30f },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "TOTP: $currentTotpToken",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NFC HCE Badge Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "NFC",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NFC Host Card Emulation (HCE)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF1E40AF)
                        )
                        Text(
                            text = "Chạm điện thoại vào máy soát vé để xác thực bằng cặp khóa phần cứng Keystore.",
                            fontSize = 11.sp,
                            color = Color(0xFF1E40AF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Cryptographic Details Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Thông tin Mật mã & Bảo mật",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "Thuật toán ký: ECDSA P-256 (SHA-256)", fontSize = 12.sp)
                    val nonceStr = (ticket.nonce ?: "").let { if (it.length >= 16) it.take(16) else it.ifEmpty { "N/A" } }
                    val sigStr = (ticket.serverSignature ?: "").let { if (it.length >= 28) it.take(28) else it.ifEmpty { "N/A" } }
                    Text(text = "Chống Replay (Nonce): $nonceStr...", fontSize = 12.sp)
                    Text(
                        text = "Server Signature: $sigStr...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Ràng buộc thiết bị (Device Bound): ${if (ticket.deviceBoundPublicKey != null) "Đã kích hoạt" else "Chưa ràng buộc"}",
                        fontSize = 12.sp,
                        color = if (ticket.deviceBoundPublicKey != null) Color(0xFF059669) else Color.Gray
                    )
                }
            }
        }
    }

    // Chuyển nhượng vé Dialog
    if (showTransferDialog) {
        AlertDialog(
            onDismissRequest = { showTransferDialog = false },
            title = { Text("Chuyển nhượng vé") },
            text = {
                Column {
                    Text("Nhập email người nhận vé. Khi chuyển nhượng hoàn tất, vé của bạn sẽ bị hủy và một vé có chữ ký số mới sẽ được cấp cho người nhận.", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it },
                        label = { Text("Email người nhận") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (recipientEmail.isBlank()) return@Button
                        isTransferring = true
                        scope.launch {
                            try {
                                val api = ApiClient.getService(context)
                                val res = api.transferTicket(TransferTicketRequest(ticket.id, recipientEmail.trim()))
                                if (res.isSuccessful && res.body()?.success == true) {
                                    Toast.makeText(context, "Chuyển nhượng vé thành công!", Toast.LENGTH_SHORT).show()
                                    showTransferDialog = false
                                    onBack()
                                } else {
                                    Toast.makeText(context, res.body()?.error ?: "Lỗi chuyển nhượng", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isTransferring = false
                            }
                        }
                    },
                    enabled = !isTransferring
                ) {
                    if (isTransferring) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Xác nhận chuyển")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showTransferDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

