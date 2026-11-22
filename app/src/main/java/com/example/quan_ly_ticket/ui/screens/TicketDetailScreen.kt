package com.example.quan_ly_ticket.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.data.model.TransferTicketRequest
import com.example.quan_ly_ticket.data.model.RecipientVerifyData
import com.example.quan_ly_ticket.data.model.VerifyRecipientRequest
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.service.SecureTicketHceService
import com.example.quan_ly_ticket.ui.components.AlertType
import com.example.quan_ly_ticket.ui.components.CryptoTerminalBox
import com.example.quan_ly_ticket.ui.components.EventCoverImage
import com.example.quan_ly_ticket.ui.components.FrostedIconButton
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.components.SemanticAlertBanner
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TicketDetailScreen(
    ticket: Ticket,
    onRequireBiometric: (title: String, subtitle: String, onVerified: () -> Unit) -> Unit = { _, _, ok -> ok() },
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }

    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var remainingSeconds by remember { mutableIntStateOf(30) }
    var currentTotpToken by remember { mutableStateOf("648291") }
    var isNfcActive by remember { mutableStateOf(true) }
    var showTransferModal by remember { mutableStateOf(false) }
    var isQrShielded by remember { mutableStateOf(false) }
    var shieldReason by remember { mutableStateOf("Đã khóa hiển thị QR để chống chụp lén / pass vé") }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Chống Pass Vé 1: Phát hiện trình chiếu màn hình phụ / quay màn hình (DisplayManager) & Tự khóa QR khi rời ứng dụng (ON_PAUSE)
    DisposableEffect(lifecycleOwner) {
        val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val hasMirrorDisplay = (displayManager?.displays?.size ?: 1) > 1
        if (hasMirrorDisplay) {
            isQrShielded = true
            shieldReason = "Phát hiện đang chia sẻ / phản chiếu màn hình phụ! Mã QR đã bị khóa."
            SecureTicketHceService.activeTicketId = null
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                // Khi kéo thanh trạng thái quay màn hình hoặc chuyển tab -> khóa mã QR ngay lập tức
                isQrShielded = true
                shieldReason = "Ứng dụng vừa mất tiêu điểm (nghi vấn chụp/quay màn hình). Chạm xác thực để mở lại."
                SecureTicketHceService.activeTicketId = null
                isNfcActive = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Kích hoạt vé cho NFC HCE
    DisposableEffect(ticket.id) {
        SecureTicketHceService.activeTicketId = ticket.id
        isNfcActive = true
        onDispose {
            SecureTicketHceService.activeTicketId = null
            isNfcActive = false
        }
    }

    // Dynamic QR & TOTP countdown loop (only regenerate QR bitmap when payload changes every 30s)
    LaunchedEffect(ticket.id, ticket.qrSecretSeed) {
        val seed = ticket.qrSecretSeed ?: "secure-seed-${ticket.id.ifEmpty { "demo" }}"
        val tId = if (ticket.id.isNotEmpty()) ticket.id else "TKT-PREMIUM-001"
        val sig = ticket.serverSignature.ifEmpty { "MEYCIQC3vXf09N4..." }
        var lastQrPayload: String? = null

        while (isActive) {
            val (token, remaining) = CryptoHelper.generateLocalRotatingCode(tId, seed)
            currentTotpToken = token
            remainingSeconds = remaining

            val timeSlice = System.currentTimeMillis() / 30000L
            val qrPayload = """{"tId":"$tId","ts":$timeSlice,"totp":"$token","sig":"$sig"}"""

            if (qrPayload != lastQrPayload) {
                lastQrPayload = qrPayload
                try {
                    qrBitmap = withContext(Dispatchers.Default) {
                        CryptoHelper.generateQrBitmap(qrPayload, size = 512)
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    e.printStackTrace()
                }
            }

            delay(1000L)
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
                        .padding(top = 10.dp)
                        .height(64.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrostedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Vé Điện Tử Ký Số",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark
                        )
                        Text(
                            text = "Chống chụp màn hình",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessFg
                        )
                    }

                    FrostedIconButton(
                        icon = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Chuyển nhượng vé",
                        onClick = { showTransferModal = true },
                        tint = PrimaryBlue
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ================= TICKET PASS (Top Navy Header + Bottom Perforated QR Section) =================
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(25.dp),
                            ambientColor = Color(0x24243154),
                            spotColor = Color(0x24243154)
                        )
                ) {
                    // Top Card Section (Real event cover photo + subtle readability scrim)
                    val ticketStyle = remember(ticket.eventTitle) { posterStyleForEvent(ticket.eventTitle) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 8.dp, bottomEnd = 8.dp))
                    ) {
                        EventCoverImage(
                            url = ticketStyle.imageUrl,
                            fallbackColor = PrimaryNavy,
                            modifier = Modifier.matchParentSize()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        listOf(Color(0x660F172A), Color(0xCC0F172A))
                                    )
                                )
                        )
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = (ticket.eventTitle ?: "SECUREFEST VIETNAM 2026").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD7E0FF),
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF10B981).copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "VÉ HỢP LỆ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFF6EE7B7),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (ticket.ticketCode.isNotBlank()) "Vé ${ticket.ticketCode}" else "Premium Access",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(17.dp))
                            val formattedTime = remember(ticket.eventStartTime) {
                                formatEventTime(ticket.eventStartTime) ?: "14.11.2026 · 18:30"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formattedTime,
                                    fontSize = 11.sp,
                                    color = Color(0xFFD7E0FF),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = ticket.eventLocation ?: "SECC · Hall A",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD7E0FF),
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Bottom Perforated QR Section with Cutout Semicircles
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomStart = 25.dp, bottomEnd = 25.dp))
                            .background(Color.White)
                            .padding(vertical = 16.dp, horizontal = 18.dp)
                    ) {
                        // Left & Right Semicircular notches
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopStart)
                                .offset(x = (-27).dp, y = (-25).dp)
                                .clip(CircleShape)
                                .background(AppShellBg)
                        )
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .align(Alignment.TopEnd)
                                .offset(x = 27.dp, y = (-25).dp)
                                .clip(CircleShape)
                                .background(AppShellBg)
                        )

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Live TOTP status indicator
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF29B594))
                                )
                                Text(
                                    text = "MÃ QR TỰ ĐỘNG LÀM MỚI SAU $remainingSeconds GIÂY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF166B5B)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive QR Code Box with Anti-Pass Privacy Shield & Live Hologram
                            Box(
                                modifier = Modifier
                                    .size(216.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (isQrShielded) Color(0xFF0F172A) else Color.White)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isQrShielded) Color(0xFFEF4444) else Color(0xFFE5E8EF),
                                        shape = RoundedCornerShape(18.dp)
                                    )
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isQrShielded) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = "Locked QR",
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(34.dp)
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "CHỐNG PASS VÉ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = shieldReason,
                                            fontSize = 9.sp,
                                            color = Color(0xFFCBD5E1),
                                            lineHeight = 12.sp,
                                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                                        )
                                        Button(
                                            onClick = {
                                                onRequireBiometric(
                                                    "Mở khóa Mã QR Vé",
                                                    "Xác nhận chủ sở hữu vé để hiển thị lại mã QR động"
                                                ) {
                                                    isQrShielded = false
                                                    SecureTicketHceService.activeTicketId = ticket.id
                                                    isNfcActive = true
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Xác thực Mở lại", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else if (qrBitmap != null) {
                                    Image(
                                        bitmap = qrBitmap!!.asImageBitmap(),
                                        contentDescription = "Dynamic Secure QR Code",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    CircularProgressIndicator(
                                        color = PrimaryBlue,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Smooth Progress Bar for 30s countdown
                            val smoothProgress by animateFloatAsState(
                                targetValue = (remainingSeconds / 30f).coerceIn(0f, 1f),
                                animationSpec = if (remainingSeconds >= 29) {
                                    tween(durationMillis = 250, easing = FastOutSlowInEasing)
                                } else {
                                    tween(durationMillis = 1000, easing = LinearEasing)
                                },
                                label = "qr_countdown_progress"
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE7EAF0))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(smoothProgress)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryBlue)
                                )
                            }
                        }
                    }
                }

                // ================= NFC STATUS CARD =================
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isNfcActive = !isNfcActive },
                    shape = RoundedCornerShape(18.dp),
                    backgroundColor = if (isNfcActive) Color(0xFFEAF4FF) else Color(0xFFF0F2F6)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Nfc,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isNfcActive) "Check-in không chạm NFC đang bật" else "NFC đã tạm dừng",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF234C85)
                            )
                            Text(
                                text = "Chạm điện thoại vào máy soát vé để check-in",
                                fontSize = 10.sp,
                                color = Color(0xFF234C85).copy(alpha = 0.8f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.White.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = if (isNfcActive) "SẴN SÀNG" else "BẬT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ================= TRANSFER MODAL: 3 BƯỚC XÁC THỰC (Verify recipient -> Confirm -> Biometric + TEE signature) =================
    if (showTransferModal) {
        SecureTransferSheet(
            ticket = ticket,
            onDismiss = { showTransferModal = false },
            onRequireBiometric = onRequireBiometric,
            onTransferred = {
                showTransferModal = false
                onBack()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecureTransferSheet(
    ticket: Ticket,
    onDismiss: () -> Unit,
    onRequireBiometric: (title: String, subtitle: String, onVerified: () -> Unit) -> Unit,
    onTransferred: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(1) }
    var recipientEmail by remember { mutableStateOf("") }
    var recipient by remember { mutableStateOf<RecipientVerifyData?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var confirmNoPayment by remember { mutableStateOf(false) }
    var confirmIdentity by remember { mutableStateOf(false) }
    var typedCode by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    val expectedCode = ticket.ticketCode.takeLast(4).uppercase()
    val transferCount = ticket.transferCount ?: 0

    ModalBottomSheet(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = PrimaryBlue)
                Text("Chuyển nhượng vé an toàn", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
            }

            // Step indicator
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Xác minh", "Xác nhận", "Ký số").forEachIndexed { idx, label ->
                    val active = step >= idx + 1
                    Column(modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (active) PrimaryBlue else Color(0xFFE2E8F0))
                        )
                        Text(
                            "${idx + 1}. $label", fontSize = 10.sp,
                            fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                            color = if (active) PrimaryBlue else TextMuted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            // Anti-scalping info
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFFFF7ED),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text(
                    "Vé ${ticket.ticketCode} đã chuyển $transferCount/2 lần. Mỗi vé chỉ được chuyển tối đa 2 lần để chống phe vé. " +
                        "Sau khi chuyển, mã QR & NFC trên máy bạn sẽ hết hiệu lực ngay lập tức.",
                    fontSize = 10.sp, color = Color(0xFF9A3412), lineHeight = 14.sp,
                    modifier = Modifier.padding(10.dp)
                )
            }

            if (errorText != null) {
                Surface(
                    shape = RoundedCornerShape(10.dp), color = Color(0xFFFEE2E2),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text(errorText!!, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFB91C1C), modifier = Modifier.padding(10.dp))
                }
            }

            when (step) {
                // ---------- BƯỚC 1: Tra cứu người nhận ----------
                1 -> {
                    Text("Email tài khoản người nhận", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF6F7788), modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                    OutlinedTextField(
                        value = recipientEmail,
                        onValueChange = { recipientEmail = it; errorText = null },
                        placeholder = { Text("vd: friend@example.com") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Hoàng Nam" to "friend@example.com",
                            "Khánh Linh" to "linh.pham@secureticket.com",
                            "Minh Khoa" to "minhkhoa.le@gmail.com"
                        ).forEach { (label, mail) ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (recipientEmail.equals(mail, true)) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                                modifier = Modifier.clickable { recipientEmail = mail; errorText = null }
                            ) {
                                Text(
                                    label, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = if (recipientEmail.equals(mail, true)) PrimaryBlue else TextDark,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val email = recipientEmail.trim().lowercase()
                            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                                errorText = "Email không hợp lệ."
                                return@Button
                            }
                            isBusy = true
                            scope.launch {
                                try {
                                    val res = ApiClient.getService(context)
                                        .verifyRecipient(VerifyRecipientRequest(email, ticket.id))
                                    val body = res.body()
                                    if (res.isSuccessful && body?.success == true && body.data != null) {
                                        if (!body.data.canTransfer) {
                                            errorText = "Vé đã đạt giới hạn chuyển nhượng (${body.data.maxTransfers} lần)."
                                        } else {
                                            recipient = body.data
                                            step = 2
                                        }
                                    } else {
                                        errorText = parseError(res.errorBody()?.string()) ?: body?.error
                                            ?: "Không tìm thấy người nhận (${res.code()})"
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    errorText = "Lỗi kết nối: ${e.message}"
                                } finally {
                                    isBusy = false
                                }
                            }
                        },
                        enabled = !isBusy && recipientEmail.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        if (isBusy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        else Text("Tra cứu & xác minh người nhận", fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ---------- BƯỚC 2: Xác nhận danh tính + cảnh báo lừa đảo ----------
                2 -> {
                    val r = recipient!!
                    GlassCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                        shape = RoundedCornerShape(16.dp),
                        backgroundColor = Color(0xFFF0F7FF)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape).background(PrimaryBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    r.fullName.split(" ").filter { it.isNotBlank() }.takeLast(2)
                                        .joinToString("") { it.first().uppercase() },
                                    color = Color.White, fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(r.fullName, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                                Text(r.email, fontSize = 11.sp, color = TextMuted)
                                Text("SĐT: ${r.phoneMasked ?: "Ẩn"}", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        Row(
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            StatusChip(if (r.kycVerified) "Tài khoản đã xác minh" else "Chưa xác minh", r.kycVerified)
                            StatusChip(if (r.isDeviceBound) "Thiết bị đã xác minh" else "Chưa xác minh thiết bị", r.isDeviceBound)
                        }
                    }

                    SemanticAlertBanner(
                        type = AlertType.ERROR,
                        title = "Cảnh báo lừa đảo",
                        message = "SecureTicket KHÔNG BAO GIỜ yêu cầu bạn chuyển vé để \"xác minh\", " +
                            "\"nhận thưởng\" hay \"hoàn tiền\". Chỉ chuyển vé cho người bạn quen biết. Giao dịch không thể hoàn tác.",
                        modifier = Modifier.padding(top = 12.dp)
                    )

                    CheckRow(confirmIdentity, { confirmIdentity = it },
                        "Tôi xác nhận đúng người nhận là ${r.fullName}")
                    CheckRow(confirmNoPayment, { confirmNoPayment = it },
                        "Tôi không bị ai yêu cầu chuyển vé để nhận tiền/thưởng")

                    Text("Nhập 4 ký tự cuối mã vé ($expectedCode) để xác nhận", fontSize = 11.sp,
                        fontWeight = FontWeight.Bold, color = Color(0xFF6F7788),
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
                    OutlinedTextField(
                        value = typedCode,
                        onValueChange = { typedCode = it.uppercase().take(4) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { step = 1; recipient = null; typedCode = ""; confirmIdentity = false; confirmNoPayment = false },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(15.dp)
                        ) { Text("Quay lại") }
                        Button(
                            onClick = { errorText = null; step = 3 },
                            enabled = confirmIdentity && confirmNoPayment && typedCode == expectedCode,
                            modifier = Modifier.weight(1.6f).height(48.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) { Text("Tiếp tục", fontWeight = FontWeight.ExtraBold) }
                    }
                }

                // ---------- BƯỚC 3: Sinh trắc học + ký ECDSA trong TEE ----------
                3 -> {
                    val r = recipient!!
                    if (resultMessage != null) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier.size(72.dp).clip(CircleShape).background(Color(0xFFDCFCE7)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFF15803D), modifier = Modifier.size(40.dp))
                            }
                            Text("Đã chuyển vé cho ${r.fullName}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                                color = TextDark, modifier = Modifier.padding(top = 12.dp))
                            Text(resultMessage!!, fontSize = 11.sp, color = TextMuted,
                                modifier = Modifier.padding(top = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Spacer(Modifier.height(18.dp))
                            Button(
                                onClick = onTransferred,
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(15.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) { Text("Xong", fontWeight = FontWeight.ExtraBold) }
                        }
                    } else {
                        Text(
                            "Giao dịch sẽ được xác nhận an toàn trên thiết bị này " +
                                "kèm nonce dùng-một-lần và timestamp ±60s để chống phát lại.",
                            fontSize = 11.sp, color = TextMuted, lineHeight = 16.sp,
                            modifier = Modifier.padding(top = 14.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(10.dp), color = Color(0xFFF8FAFC),
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                        ) {
                            Text(
                                "TRANSFER:${ticket.id.take(8)}…:${r.email}:<nonce>:<ts>",
                                fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF334155),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                onRequireBiometric(
                                    "Xác nhận chuyển nhượng vé",
                                    "Ký lệnh chuyển vé ${ticket.ticketCode} cho ${r.fullName}"
                                ) {
                                    isBusy = true
                                    errorText = null
                                    scope.launch {
                                        try {
                                            val clientNonce = UUID.randomUUID().toString().replace("-", "")
                                            val timestamp = System.currentTimeMillis() / 1000L
                                            val sig = withContext(Dispatchers.Default) {
                                                KeystoreManager.signTransferPayload(ticket.id, r.email, clientNonce, timestamp)
                                            }
                                            val res = ApiClient.getService(context).transferTicket(
                                                TransferTicketRequest(
                                                    ticketId = ticket.id,
                                                    recipientEmail = r.email.lowercase(),
                                                    clientNonce = clientNonce,
                                                    timestamp = timestamp,
                                                    deviceSignature = sig
                                                )
                                            )
                                            if (res.isSuccessful && res.body()?.success == true) {
                                                SecureTicketHceService.activeTicketId = null
                                                // Xóa vé khỏi kho mã hóa cục bộ
                                                try {
                                                    val vault = com.example.quan_ly_ticket.data.local.EncryptedTicketVault(context)
                                                    vault.saveTicketsEncrypted(vault.loadTicketsDecrypted().filter { it.id != ticket.id })
                                                } catch (_: Exception) { }
                                                resultMessage = "Chuyển nhượng vé thành công! Mã QR cũ trên máy bạn đã hết hiệu lực."
                                            } else {
                                                errorText = parseError(res.errorBody()?.string()) ?: res.body()?.error
                                                    ?: "Lỗi chuyển nhượng (${res.code()})"
                                            }
                                        } catch (e: Exception) {
                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                            errorText = "Lỗi: ${e.message}"
                                        } finally {
                                            isBusy = false
                                        }
                                    }
                                }
                            },
                            enabled = !isBusy,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            if (isBusy) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                            else {
                                Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Xác thực vân tay & ký chuyển vé", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                        TextButton(onClick = { step = 2 }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                            Text("Quay lại")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, ok: Boolean) {
    Surface(shape = RoundedCornerShape(8.dp), color = if (ok) Color(0xFFDCFCE7) else Color(0xFFFEF3C7)) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            color = if (ok) Color(0xFF15803D) else Color(0xFF92400E),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, fontSize = 11.sp, color = TextDark)
    }
}

private fun parseError(raw: String?): String? = try {
    if (raw.isNullOrBlank()) null
    else com.google.gson.JsonParser.parseString(raw).asJsonObject.get("error")?.asString
} catch (_: Exception) { null }
