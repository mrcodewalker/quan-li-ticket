package com.example.quan_ly_ticket.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.model.ValidateQrRequest
import com.example.quan_ly_ticket.data.model.ValidationResultData
import com.example.quan_ly_ticket.security.NfcReaderHelper
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ScannerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? Activity

    var isFlashOn by remember { mutableStateOf(false) }
    var currentMode by remember { mutableStateOf("qr") } // "qr" or "nfc"
    var isValidating by remember { mutableStateOf(false) }
    var validationResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var verifiedData by remember { mutableStateOf<ValidationResultData?>(null) }
    var validationMethod by remember { mutableStateOf("QR") }
    var showManualInput by remember { mutableStateOf(false) }
    var manualQrInput by remember { mutableStateOf("") }

    // NFC Reader Mode Registration
    DisposableEffect(activity, currentMode) {
        if (activity != null && currentMode == "nfc") {
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

    // Animated laser scan line for the camera viewfinder
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserPosition by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    val isSuccess = validationResult?.first == true
    val isError = validationResult?.first == false
    val frameColor = if (isSuccess) ScannerSuccess else ScannerFrame

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ScannerBg)
    ) {
        // Atmospheric radial ambient glow matching preview
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ScannerGlow.copy(alpha = 0.5f), Color.Transparent),
                        radius = 900f
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Header: Back, Title, Flash toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(67.dp)
                    .background(Color(0x61080F1B))
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x9E0A1220))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Đóng máy quét",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Quét vé",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Xác thực QR động",
                        fontSize = 10.sp,
                        color = Color(0xFFBDC9D9),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isFlashOn) Color(0xFFFFF2B8) else Color(0x9E0A1220))
                        .clickable {
                            isFlashOn = !isFlashOn
                            Toast.makeText(context, if (isFlashOn) "Đã bật đèn Flash" else "Đã tắt đèn Flash", Toast.LENGTH_SHORT).show()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Flash",
                        tint = if (isFlashOn) Color(0xFF805F00) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Main Viewport Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Đưa mã QR vào trong khung",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE7EDF6)
                )
                Text(
                    text = "Mã sẽ được nhận diện tự động",
                    fontSize = 10.sp,
                    color = Color(0xFF9DAABC),
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Viewfinder Frame (278x278 dp) with corner brackets and laser line
                Box(
                    modifier = Modifier
                        .size(278.dp)
                        .clickable {
                            // Quick simulation or manual QR paste
                            showManualInput = !showManualInput
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Top-Left bracket
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .align(Alignment.TopStart)
                            .border(
                                width = 4.dp,
                                color = frameColor,
                                shape = RoundedCornerShape(topStart = 22.dp)
                            )
                    )
                    // Top-Right bracket
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .align(Alignment.TopEnd)
                            .border(
                                width = 4.dp,
                                color = frameColor,
                                shape = RoundedCornerShape(topEnd = 22.dp)
                            )
                    )
                    // Bottom-Left bracket
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .align(Alignment.BottomStart)
                            .border(
                                width = 4.dp,
                                color = frameColor,
                                shape = RoundedCornerShape(bottomStart = 22.dp)
                            )
                    )
                    // Bottom-Right bracket
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .align(Alignment.BottomEnd)
                            .border(
                                width = 4.dp,
                                color = frameColor,
                                shape = RoundedCornerShape(bottomEnd = 22.dp)
                            )
                    )

                    // Laser scan line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .fillMaxHeight(laserPosition)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.BottomCenter)
                                .background(frameColor)
                                .shadow(elevation = 12.dp, spotColor = frameColor, ambientColor = frameColor)
                        )
                    }

                    // QR Dot Matrix Placeholder
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(138.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Validation Status Indicators
                if (!isSuccess && !isError) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF6B8CFF))
                                .shadow(6.dp, CircleShape, ambientColor = Color(0xFF6B8CFF), spotColor = Color(0xFF6B8CFF))
                        )
                        Text(
                            text = if (isValidating) "Đang xác thực chữ ký số..." else "Camera đang hoạt động (Chạm để nhập mã)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }

                // Success Result Banner
                if (isSuccess) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xE0146F5B))
                            .border(1.dp, Color(0x6B6EE8C8), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(ScannerSuccess),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color(0xFF0B4A3E),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Vé hợp lệ ($validationMethod)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${verifiedData?.eventTitle ?: "SecureFest"} · ${verifiedData?.ownerName ?: "Khách hàng"}",
                                    fontSize = 10.sp,
                                    color = Color(0xFFC9F8EB),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Error / Replay Attack Result Banner
                if (isError) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color(0xE08B1E2E))
                            .border(1.dp, Color(0x6BFF8A9B), RoundedCornerShape(18.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF6B81)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Từ chối vé ($validationMethod)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = validationResult?.second ?: "Vé không hợp lệ hoặc đã qua sử dụng!",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFFD5DC),
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Manual QR Input Form for testing / emulation
                AnimatedVisibility(visible = showManualInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = manualQrInput,
                            onValueChange = { manualQrInput = it },
                            placeholder = { Text("Dán chuỗi JSON mã QR vé...", fontSize = 11.sp, color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF131D2E),
                                unfocusedContainerColor = Color(0xFF131D2E),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { processValidation(manualQrInput) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                enabled = manualQrInput.isNotBlank() && !isValidating
                            ) {
                                Text("Kiểm tra mã QR", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    // Simulation toggle
                                    if (isSuccess) {
                                        validationResult = null
                                    } else {
                                        validationResult = Pair(true, "Vé hợp lệ!")
                                        verifiedData = ValidationResultData(
                                            ticketCode = "SEC-P12-2026",
                                            eventTitle = "SecureFest Vietnam 2026",
                                            ownerName = "Nguyễn Minh Anh",
                                            checkInTime = "20:04"
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Mô phỏng", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(20.dp))

                // Mode Switcher Pill at bottom: [ QR Camera | NFC ]
                Box(
                    modifier = Modifier
                        .width(245.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0x9E080F1B))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                        .padding(5.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val isQr = currentMode == "qr"

                        // QR Camera Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(43.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isQr) Color.White else Color.Transparent)
                                .clickable { currentMode = "qr" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "QR Camera",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isQr) PrimaryNavy else Color(0xFFAEBACC)
                            )
                        }

                        // NFC Pill
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(43.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (!isQr) Color.White else Color.Transparent)
                                .clickable { currentMode = "nfc" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "NFC",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (!isQr) PrimaryNavy else Color(0xFFAEBACC)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // ================= FULLSCREEN NFC CHALLENGE-RESPONSE OVERLAY =================
        if (currentMode == "nfc") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 67.dp)
                    .background(Color(0xEB080F1B)),
                contentAlignment = Alignment.Center
            ) {
                // Close NFC overlay button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { currentMode = "qr" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "✕",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    // Pulsing NFC Radar container
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(32.dp))
                            .background(Color(0xFF273F9A))
                            .border(18.dp, Color(0x1F4B69D3), RoundedCornerShape(32.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Nfc,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = "Sẵn sàng quét NFC",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Đưa thiết bị của khách đến gần điện thoại để bắt đầu xác thực challenge-response.",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFB9C5D5),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    Button(
                        onClick = { currentMode = "qr" },
                        modifier = Modifier
                            .height(48.dp)
                            .padding(horizontal = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(
                            text = "Quay lại camera",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryNavy
                        )
                    }
                }
            }
        }
    }
}
