package com.example.quan_ly_ticket.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.SimulatePaymentRequest
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.CryptoHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentScreen(
    order: CreateOrderData,
    onPaymentSuccess: (Ticket) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val ticketVault = remember { EncryptedTicketVault(context) }
    val formatter = remember { DecimalFormat("#,###") }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(order.ttlSeconds) }
    var isSimulating by remember { mutableStateOf(false) }
    var paymentStatusText by remember { mutableStateOf("Đang chờ thanh toán chuyển khoản...") }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "Đã sao chép $label", Toast.LENGTH_SHORT).show()
    }

    // 1. Sinh mã QR chuẩn ngân hàng EMVCo Napas 247 hoặc tải ảnh VietQR
    LaunchedEffect(order.orderId, order.qrPayload) {
        withContext(Dispatchers.IO) {
            try {
                // Nếu backend trả về chuỗi EMVCo Napas 247, sinh trực tiếp QR chuẩn quét bằng mọi app ngân hàng (MB, VCB, TCB,...)
                if (!order.qrPayload.isNullOrEmpty()) {
                    qrBitmap = CryptoHelper.generateQrBitmap(order.qrPayload, 512)
                } else {
                    val url = URL(order.qrImageUrl)
                    val connection = url.openConnection()
                    connection.connectTimeout = 8000
                    connection.readTimeout = 8000
                    val inputStream = connection.getInputStream()
                    val loadedBitmap = BitmapFactory.decodeStream(inputStream)
                    if (loadedBitmap != null) {
                        qrBitmap = loadedBitmap
                    } else {
                        qrBitmap = CryptoHelper.generateQrBitmap(order.transferContent, 512)
                    }
                }
            } catch (e: Exception) {
                try {
                    qrBitmap = CryptoHelper.generateQrBitmap(order.qrPayload ?: order.transferContent, 512)
                } catch (_: Exception) { }
            }
        }
    }

    // 2. Vòng lặp đếm ngược thời gian thanh toán (5 phút)
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && isActive) {
            delay(1000L)
            remainingSeconds--
        }
    }

    // 3. Tự động thăm dò (Polling) trạng thái đơn hàng mỗi 2.5 giây
    LaunchedEffect(order.orderId) {
        val api = ApiClient.getService(context)
        while (remainingSeconds > 0 && isActive) {
            delay(2500L)
            try {
                val statusRes = api.getOrderStatus(order.orderId)
                if (statusRes.isSuccessful && statusRes.body()?.success == true) {
                    val statusData = statusRes.body()!!.data!!
                    if (statusData.status == "PAID" && statusData.ticket != null) {
                        paymentStatusText = "Thanh toán thành công! Đang chuyển đến vé..."
                        var fullTicket = statusData.ticket
                        
                        // Đảm bảo thông tin sự kiện đầy đủ cho vé
                        if (fullTicket.eventTitle.isNullOrEmpty()) {
                            try {
                                val myTicketsRes = api.getMyTickets()
                                if (myTicketsRes.isSuccessful && myTicketsRes.body()?.success == true) {
                                    val matched = myTicketsRes.body()!!.data?.find { it.id == fullTicket.id || (it.ticketCode.isNotEmpty() && it.ticketCode == fullTicket.ticketCode) }
                                    if (matched != null) fullTicket = matched
                                }
                            } catch (_: Exception) {}
                        }
                        if (fullTicket.eventTitle.isNullOrEmpty()) {
                            fullTicket = fullTicket.copy(
                                eventTitle = order.eventTitle,
                                eventId = order.eventId
                            )
                        }

                        // Lưu vé vào Encrypted Vault an toàn
                        try {
                            val existing = ticketVault.loadTicketsDecrypted().toMutableList()
                            existing.removeAll { it.id == fullTicket.id }
                            existing.add(0, fullTicket)
                            ticketVault.saveTicketsEncrypted(existing)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }

                        Toast.makeText(context, "Thanh toán thành công! Vé đã được ký số.", Toast.LENGTH_LONG).show()
                        delay(800L)
                        onPaymentSuccess(fullTicket)
                        break
                    } else if (statusData.status == "EXPIRED") {
                        Toast.makeText(context, "Đơn hàng đã hết thời hạn thanh toán.", Toast.LENGTH_SHORT).show()
                        onCancel()
                        break
                    }
                }
            } catch (e: Exception) {
                // Tiếp tục thử lại ở chu kỳ tiếp theo
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Thanh toán vé sự kiện", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
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
            // Card Thông tin Đơn hàng & Giá tiền
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
                            text = "Mã đơn: #${order.orderCode}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Gray
                        )
                        Text(
                            text = "${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (remainingSeconds < 60) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = order.eventTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Tổng thanh toán:", fontSize = 14.sp, color = Color.DarkGray)
                        Text(
                            text = "${formatter.format(order.amount)} VND",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card Mã VietQR & Hướng dẫn Chuyển khoản
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
                    Text(
                        text = "Quét mã VietQR bằng app Ngân hàng bất kỳ",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Ảnh mã VietQR
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "VietQR Code",
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color.White)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .background(Color(0xFFF8FAFC), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { remainingSeconds.toFloat() / order.ttlSeconds.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color(0xFFE2E8F0)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Bảng chi tiết chuyển khoản
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            // Ngân hàng
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Ngân hàng:", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = "${order.bankId} (Napas 247)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Số tài khoản + Nút sao chép
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Số tài khoản:", fontSize = 12.sp, color = Color.Gray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = order.bankAccountNo,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sao chép",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Chủ tài khoản
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Chủ tài khoản:", fontSize = 12.sp, color = Color.Gray)
                                Text(
                                    text = order.accountName,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Nội dung chuyển khoản + Nút sao chép
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Nội dung CK:", fontSize = 12.sp, color = Color.Gray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = order.transferContent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E),
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Sao chép",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(4.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ Vui lòng giữ nguyên nội dung chuyển khoản để hệ thống tự động gạch nợ và xuất vé ngay lập tức.",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Sandbox Demo / Giả lập thanh toán tức thì
            Button(
                onClick = {
                    isSimulating = true
                    scope.launch {
                        try {
                            val api = ApiClient.getService(context)
                            val simRes = api.simulatePayment(SimulatePaymentRequest(orderId = order.orderId))
                            if (simRes.isSuccessful && simRes.body()?.success == true) {
                                Toast.makeText(context, "⚡ [Sandbox] Thanh toán thành công! Đang cấp vé...", Toast.LENGTH_SHORT).show()
                                var ticket = simRes.body()!!.data
                                if (ticket != null) {
                                    if (ticket.eventTitle.isNullOrEmpty()) {
                                        try {
                                            val myTicketsRes = api.getMyTickets()
                                            if (myTicketsRes.isSuccessful && myTicketsRes.body()?.success == true) {
                                                val matched = myTicketsRes.body()!!.data?.find { it.id == ticket.id || (it.ticketCode.isNotEmpty() && it.ticketCode == ticket.ticketCode) }
                                                if (matched != null) ticket = matched
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    if (ticket.eventTitle.isNullOrEmpty()) {
                                        ticket = ticket.copy(
                                            eventTitle = order.eventTitle,
                                            eventId = order.eventId
                                        )
                                    }

                                    try {
                                        val existing = ticketVault.loadTicketsDecrypted().toMutableList()
                                        existing.removeAll { it.id == ticket.id }
                                        existing.add(0, ticket)
                                        ticketVault.saveTicketsEncrypted(existing)
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }

                                    delay(500L)
                                    onPaymentSuccess(ticket)
                                }
                            } else {
                                Toast.makeText(context, simRes.body()?.error ?: "Lỗi giả lập", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isSimulating = false
                        }
                    }
                },
                enabled = !isSimulating,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSimulating) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ Giả lập Quét mã & Thanh toán (Test Sandbox)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Hủy giao dịch")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Thẻ giải thích bảo mật môn học
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF334155), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cơ chế bảo mật thanh toán (Napas 247 & Webhook)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF1E293B)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "• Webhook biến động số dư ngân hàng được xác thực bằng chữ ký số HMAC-SHA256 (timing-safe comparison chống Timing Attack).\n" +
                                "• Vé chỉ được cấp phát khi chữ ký số ngân hàng hợp lệ và số tiền khớp đơn hàng.\n" +
                                "• Vé điện tử sau đó được ký số độc lập bằng ECDSA P-256 trên phần cứng máy chủ.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

