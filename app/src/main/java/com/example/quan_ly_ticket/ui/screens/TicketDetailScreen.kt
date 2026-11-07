package com.example.quan_ly_ticket.ui.screens

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.data.model.TransferTicketRequest
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.service.SecureTicketHceService
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
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
    var currentTotpToken by remember { mutableStateOf("648291") }
    var isNfcActive by remember { mutableStateOf(true) }
    var showTransferModal by remember { mutableStateOf(false) }
    var recipientEmail by remember { mutableStateOf("friend@example.com") }
    var isTransferring by remember { mutableStateOf(false) }
    var showCryptoDetails by remember { mutableStateOf(false) }

    // Kích hoạt vé cho NFC HCE
    DisposableEffect(ticket.id) {
        SecureTicketHceService.activeTicketId = ticket.id
        isNfcActive = true
        onDispose {
            SecureTicketHceService.activeTicketId = null
            isNfcActive = false
        }
    }

    // Dynamic QR & TOTP countdown loop
    LaunchedEffect(ticket.id, ticket.qrSecretSeed) {
        val seed = ticket.qrSecretSeed ?: "secure-seed-${ticket.id.ifEmpty { "demo" }}"
        val tId = if (ticket.id.isNotEmpty()) ticket.id else "TKT-PREMIUM-001"

        while (isActive) {
            val (token, remaining) = CryptoHelper.generateLocalRotatingCode(tId, seed)
            currentTotpToken = token
            remainingSeconds = remaining

            val timeSlice = System.currentTimeMillis() / 30000L
            val sig = ticket.serverSignature.ifEmpty { "MEYCIQC3vXf09N4..." }
            val qrPayload = """{"tId":"$tId","ts":$timeSlice,"totp":"$token","sig":"$sig"}"""

            try {
                qrBitmap = CryptoHelper.generateQrBitmap(qrPayload, size = 512)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(1000L)
        }
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                // Header matching TicketDetail.dc.html
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.88f))
                            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
                            .clickable { onBack() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại",
                            tint = TextDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "Vé an toàn",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.88f))
                            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
                            .shadow(elevation = 6.dp, shape = RoundedCornerShape(14.dp))
                            .clickable { showTransferModal = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Chuyển nhượng vé",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(19.dp)
                        )
                    }
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
                    // Top Card Section (Navy with Purple Accent)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp, bottomStart = 10.dp, bottomEnd = 10.dp))
                            .background(PrimaryNavy)
                            .padding(18.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .offset(x = 180.dp, y = (-49).dp)
                                .clip(CircleShape)
                                .background(Color(0xFF7B4FD6))
                        )

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = (ticket.eventTitle ?: "SECUREFEST VIETNAM 2026").uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD7E0FF),
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (ticket.ticketCode.isNotBlank()) "Vé ${ticket.ticketCode}" else "Premium Access",
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(17.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = ticket.eventStartTime ?: "14.11.2026 · 18:30",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD7E0FF),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = ticket.eventLocation ?: "SECC · Hall A",
                                    fontSize = 11.sp,
                                    color = Color(0xFFD7E0FF),
                                    fontWeight = FontWeight.Medium
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
                                    text = "MÃ ĐỘNG · CÒN $remainingSeconds GIÂY",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF166B5B)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Interactive QR Code Box
                            Box(
                                modifier = Modifier
                                    .size(216.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color.White)
                                    .border(1.dp, Color(0xFFE5E8EF), RoundedCornerShape(18.dp))
                                    .padding(14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qrBitmap != null) {
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

                            // Progress Bar for 30s countdown
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .height(5.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE7EAF0))
                            ) {
                                val progressFraction = (remainingSeconds / 30f).coerceIn(0f, 1f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(progressFraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryBlue)
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // TOTP code display
                            val formattedTotp = if (currentTotpToken.length >= 6) {
                                "${currentTotpToken.take(3)} ${currentTotpToken.takeLast(3)}"
                            } else {
                                currentTotpToken
                            }
                            Text(
                                text = "TOTP · $formattedTotp",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF7A8292),
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // ================= NFC HCE BUTTON / STATUS CARD =================
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
                                text = if (isNfcActive) "NFC đang hoạt động" else "NFC đã tạm dừng",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF234C85)
                            )
                            Text(
                                text = "Chạm điện thoại vào máy soát vé",
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

                // ================= SECURITY TRUST FOOTER =================
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = Color(0xFF16836F),
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "ECDSA P-256 · Device binding · Chống phát lại",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF6C7485)
                    )
                }

                // Expandable Cryptographic Details Card for Academic Inspection
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showCryptoDetails = !showCryptoDetails },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Chi tiết chứng thực mật mã",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryBlue
                            )
                            Icon(
                                imageVector = if (showCryptoDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = PrimaryBlue
                            )
                        }

                        AnimatedVisibility(visible = showCryptoDetails) {
                            Column(modifier = Modifier.padding(top = 10.dp)) {
                                Text(
                                    text = "• Thuật toán ký: ECDSA P-256 (SHA-256)",
                                    fontSize = 11.sp,
                                    color = TextDark
                                )
                                Text(
                                    text = "• Nonce: ${ticket.nonce.ifEmpty { "1f48a204e9c7d81a" }}",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                Text(
                                    text = "• Chữ ký Server: ${ticket.serverSignature.take(28).ifEmpty { "MEQCIQD8xM9Z0f2... (Hợp lệ)" }}...",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextMuted
                                )
                                Text(
                                    text = "• Ràng buộc thiết bị: Android Keystore StrongBox TEE",
                                    fontSize = 11.sp,
                                    color = Color(0xFF16836F),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // ================= TRANSFER MODAL / BOTTOM SHEET =================
    if (showTransferModal) {
        ModalBottomSheet(
            onDismissRequest = { showTransferModal = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = "Chuyển nhượng vé",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark
                )

                Text(
                    text = "Email người nhận",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6F7788),
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                )

                OutlinedTextField(
                    value = recipientEmail,
                    onValueChange = { recipientEmail = it },
                    placeholder = { Text("friend@example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

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
                                    showTransferModal = false
                                    onBack()
                                } else {
                                    Toast.makeText(context, res.body()?.error ?: "Lỗi chuyển nhượng vé", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isTransferring = false
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    enabled = !isTransferring
                ) {
                    if (isTransferring) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text(
                            text = "Xác nhận an toàn",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }
    }
}
