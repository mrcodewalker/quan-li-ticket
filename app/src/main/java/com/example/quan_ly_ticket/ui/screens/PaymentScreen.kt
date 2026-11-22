package com.example.quan_ly_ticket.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.model.CancelOrderRequest
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.ui.components.FrostedIconButton
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
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
    var isPollingManual by remember { mutableStateOf(false) }
    var paymentStatusText by remember { mutableStateOf("Đang chờ thanh toán chuyển khoản...") }
    var successTicket by remember { mutableStateOf<Ticket?>(null) }
    var successVia by remember { mutableStateOf("Chuyển khoản Napas 247 · SePay") }

    fun enrichTicket(raw: Ticket): Ticket {
        return raw.copy(
            eventTitle = raw.eventTitle ?: order.eventTitle,
            eventId = raw.eventId ?: order.eventId,
            seatZone = raw.seatZone ?: order.seatZone,
            seatNumber = raw.seatNumber ?: order.seatNumber
        )
    }

    suspend fun persistTicketToVault(ticket: Ticket) {
        withContext(Dispatchers.IO) {
            try {
                val existing = ticketVault.loadTicketsDecrypted().toMutableList()
                existing.removeAll { it.id == ticket.id }
                existing.add(0, ticket)
                ticketVault.saveTicketsEncrypted(existing)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

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
                if (e is kotlinx.coroutines.CancellationException) throw e
                try {
                    qrBitmap = CryptoHelper.generateQrBitmap(order.qrPayload ?: order.transferContent, 512)
                } catch (_: Exception) { }
            }
        }
    }

    // 2. Vòng lặp đếm ngược thời gian thanh toán (5 phút)
    LaunchedEffect(Unit) {
        while (remainingSeconds > 0 && successTicket == null && isActive) {
            delay(1000L)
            remainingSeconds--
        }
    }

    // 3. Tự động thăm dò (Polling) trạng thái đơn hàng mỗi 2.5 giây
    LaunchedEffect(order.orderId) {
        val api = ApiClient.getService(context)
        while (remainingSeconds > 0 && successTicket == null && isActive) {
            delay(2500L)
            try {
                val statusRes = api.getOrderStatus(order.orderId)
                if (statusRes.isSuccessful && statusRes.body()?.success == true) {
                    val statusData = statusRes.body()!!.data!!
                    if (statusData.status == "PAID" && statusData.ticket != null) {
                        paymentStatusText = "Thanh toán thành công! Đang chuyển đến vé..."
                        var fullTicket = statusData.ticket

                        if (fullTicket.eventTitle.isNullOrEmpty()) {
                            try {
                                val myTicketsRes = api.getMyTickets()
                                if (myTicketsRes.isSuccessful && myTicketsRes.body()?.success == true) {
                                    val matched = myTicketsRes.body()!!.data?.find {
                                        it.id == fullTicket.id || (it.ticketCode.isNotEmpty() && it.ticketCode == fullTicket.ticketCode)
                                    }
                                    if (matched != null) fullTicket = matched
                                }
                            } catch (_: Exception) {}
                        }
                        fullTicket = enrichTicket(fullTicket)
                        persistTicketToVault(fullTicket)

                        successVia = "Chuyển khoản Napas 247 · SePay"
                        successTicket = fullTicket
                        break
                    } else if (statusData.status == "EXPIRED") {
                        Toast.makeText(context, "Đơn hàng đã hết thời hạn thanh toán.", Toast.LENGTH_SHORT).show()
                        onCancel()
                        break
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrostedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onCancel
                    )
                    Text(
                        text = "Thanh toán",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (remainingSeconds < 60) DangerBg else Color.White,
                        modifier = Modifier.border(
                            1.dp,
                            if (remainingSeconds < 60) DangerBorder else BorderLight,
                            RoundedCornerShape(20.dp)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (remainingSeconds < 60) DangerFg else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "${remainingSeconds / 60}:${String.format("%02d", remainingSeconds % 60)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = if (remainingSeconds < 60) DangerFg else TextDark
                            )
                        }
                    }
                }
            }
        ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Order & Amount Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = order.eventTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${formatter.format(order.amount)} ₫",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.8).sp,
                    style = TextStyle(fontFeatureSettings = "tnum"),
                    color = TextDark
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Đơn #${order.orderCode}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = TextSubtle
                )
                if (!order.seatZone.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = InfoBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, InfoBorder)
                    ) {
                        Text(
                            text = "Ghế đã giữ: ${order.seatZone} · Ghế ${order.seatNumber ?: "--"}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = InfoFg,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Unified VietQR Checkout Sheet
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(AccentMint)
                        )
                        Text(
                            text = "Quét mã VietQR bằng app Ngân hàng",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pure white QR container with >= 14dp quiet zone
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .border(1.dp, BorderLight, RoundedCornerShape(18.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap!!.asImageBitmap(),
                                contentDescription = "VietQR Code",
                                modifier = Modifier
                                    .size(216.dp)
                                    .background(Color.White)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(216.dp)
                                    .background(AppShellBg, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 2.5.dp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(modifier = Modifier.height(14.dp))

                    // Clean Bank Transfer Rows
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Ngân hàng", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = order.bankId,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Số tài khoản", fontSize = 12.sp, color = TextMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable { copyToClipboard("Số tài khoản", order.bankAccountNo) }
                            ) {
                                Text(
                                    text = order.bankAccountNo,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = TextDark
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Sao chép số tài khoản",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Chủ tài khoản", fontSize = 12.sp, color = TextMuted)
                            Text(
                                text = order.accountName,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextDark
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Nội dung CK", fontSize = 12.sp, color = TextMuted)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.clickable { copyToClipboard("Nội dung chuyển khoản", order.transferContent) }
                            ) {
                                Text(
                                    text = order.transferContent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = PrimaryBlue
                                )
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Sao chép nội dung",
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Giữ nguyên nội dung chuyển khoản để nhận vé tự động ngay sau khi thanh toán.",
                        fontSize = 11.sp,
                        color = TextMuted,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 4-Stage Live Cryptographic Payment Pipeline
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Tiến trình Thanh toán",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark
                    )
                    val stage3Done = successTicket != null
                    listOf(
                        Triple("1. Xác thực & giữ ghế", "Đã khóa chỗ an toàn cho bạn", true),
                        Triple("2. Tạo mã VietQR", "Sẵn sàng quét bằng app ngân hàng", true),
                        Triple("3. Chờ xác nhận thanh toán", paymentStatusText, stage3Done),
                        Triple("4. Phát hành vé điện tử", "Tự động đưa vé vào ví của bạn", stage3Done)
                    ).forEach { (title, sub, done) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (done) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                contentDescription = null,
                                tint = if (done) AccentMint else PrimaryBlue,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(title, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text(sub, fontSize = 10.sp, color = TextMuted)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Primary Action: Poll order status immediately
            Button(
                onClick = {
                    isPollingManual = true
                    scope.launch {
                        try {
                            val api = ApiClient.getService(context)
                            val statusRes = api.getOrderStatus(order.orderId)
                            if (statusRes.isSuccessful && statusRes.body()?.success == true) {
                                val data = statusRes.body()!!.data!!
                                if (data.status == "PAID" && data.ticket != null) {
                                    val enriched = enrichTicket(data.ticket)
                                    persistTicketToVault(enriched)
                                    successVia = "Chuyển khoản Napas 247 · SePay"
                                    successTicket = enriched
                                } else {
                                    Toast.makeText(context, "Trạng thái hiện tại: ${data.status}. Hệ thống vẫn đang lắng nghe Webhook.", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, statusRes.body()?.error ?: "Không thể kiểm tra đơn hàng", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                        } finally {
                            isPollingManual = false
                        }
                    }
                },
                enabled = !isPollingManual,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                if (isPollingManual) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Kiểm tra trạng thái giao dịch ngay", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            TextButton(
                onClick = {
                    scope.launch {
                        try { ApiClient.getService(context).cancelOrder(CancelOrderRequest(order.orderId)) } catch (_: Exception) {}
                        onCancel()
                    }
                },
                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
            ) {
                Text("Hủy giao dịch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

    // Overlay ăn mừng thanh toán thành công (thay cho Toast)
    androidx.compose.animation.AnimatedVisibility(
        visible = successTicket != null,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300))
    ) {
        successTicket?.let { t ->
            PaymentSuccessOverlay(
                ticket = t,
                amount = order.amount,
                orderCode = order.orderCode,
                paidVia = successVia,
                onViewTicket = { onPaymentSuccess(t) },
                onGoHome = onCancel
            )
        }
    }
}

