package com.example.quan_ly_ticket.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.quan_ly_ticket.ui.theme.PrimaryBlue
import com.example.quan_ly_ticket.ui.theme.PrimaryNavy
import com.example.quan_ly_ticket.ui.theme.TextDark
import com.example.quan_ly_ticket.ui.theme.TextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class BiometricMode {
    FACE_ID,
    FINGERPRINT
}

private enum class ScanState {
    IDLE,
    SCANNING,
    SUCCESS,
    FAILED
}

/**
 * Hộp thoại xác thực sinh trắc học hỗ trợ cả:
 * 1. Giả lập quét Face ID (hiệu ứng khung quét khuôn mặt + tia laser + vòng xoay)
 * 2. Giả lập quét Vân tay (cảm biến vân tay dưới màn hình + sóng radar)
 * 3. Gọi cảm biến Android BiometricPrompt thật của hệ thống (nếu thiết bị có hỗ trợ)
 * 4. Nút giả lập quét sai để demo cho giảng viên thấy cơ chế từ chối giao dịch
 */
@Composable
fun BiometricAuthDialog(
    title: String,
    subtitle: String,
    hasHardwareBiometric: Boolean,
    onUseSystemBiometric: () -> Unit,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(BiometricMode.FACE_ID) }
    var scanState by remember { mutableStateOf(ScanState.IDLE) }
    var statusText by remember { mutableStateOf("Chạm vào biểu tượng bên dưới để bắt đầu quét") }

    fun vibrate(success: Boolean) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val pattern = if (success) longArrayOf(0, 35, 50, 60) else longArrayOf(0, 80, 60, 80)
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
            }
        } catch (_: Exception) { }
    }

    fun startScan(shouldSucceed: Boolean) {
        if (scanState == ScanState.SCANNING || scanState == ScanState.SUCCESS) return
        scanState = ScanState.SCANNING
        statusText = if (mode == BiometricMode.FACE_ID) {
            "Đang quét đặc điểm khuôn mặt 3D (Face ID)..."
        } else {
            "Đang đọc cảm biến vân tay phần cứng..."
        }

        scope.launch {
            delay(1350L)
            if (shouldSucceed) {
                scanState = ScanState.SUCCESS
                statusText = if (mode == BiometricMode.FACE_ID) {
                    "Nhận diện Face ID thành công! Đã mở khóa AndroidKeyStore TEE."
                } else {
                    "Xác thực vân tay khớp! Đã mở khóa AndroidKeyStore TEE."
                }
                vibrate(true)
                delay(650L)
                onSuccess()
            } else {
                scanState = ScanState.FAILED
                statusText = if (mode == BiometricMode.FACE_ID) {
                    "Khuôn mặt không khớp! Từ chối ký số bảo mật."
                } else {
                    "Vân tay không khớp! Từ chối truy cập khóa TEE."
                }
                vibrate(false)
            }
        }
    }

    val infinite = rememberInfiniteTransition(label = "bio_scan")
    val laserPos by infinite.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "laser"
    )
    val pulseScale by infinite.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.28f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing)),
        label = "pulse"
    )

    val accentColor by animateColorAsState(
        targetValue = when (scanState) {
            ScanState.SUCCESS -> Color(0xFF10B981)
            ScanState.FAILED -> Color(0xFFEF4444)
            ScanState.SCANNING -> Color(0xFF38BDF8)
            ScanState.IDLE -> PrimaryBlue
        },
        label = "accent"
    )

    Dialog(
        onDismissRequest = { if (scanState != ScanState.SCANNING) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = Color.White,
            shadowElevation = 14.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "BIOMETRIC TEE AUTH",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlue,
                            letterSpacing = 1.sp
                        )
                    }
                    IconButton(
                        onClick = { if (scanState != ScanState.SCANNING) onDismiss() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Đóng", tint = TextMuted, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(Modifier.height(6.dp))

                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = TextMuted,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(16.dp))

                // Mode Switcher: Face ID | Vân tay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF1F5F9))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val isFace = mode == BiometricMode.FACE_ID
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isFace) PrimaryBlue else Color.Transparent)
                            .clickable(enabled = scanState != ScanState.SCANNING) {
                                mode = BiometricMode.FACE_ID
                                scanState = ScanState.IDLE
                                statusText = "Chạm vào khung Face ID để quét khuôn mặt"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Face, null, tint = if (isFace) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                            Text("Face ID", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isFace) Color.White else TextMuted)
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (!isFace) PrimaryBlue else Color.Transparent)
                            .clickable(enabled = scanState != ScanState.SCANNING) {
                                mode = BiometricMode.FINGERPRINT
                                scanState = ScanState.IDLE
                                statusText = "Chạm vào cảm biến vân tay để xác thực"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Default.Fingerprint, null, tint = if (!isFace) Color.White else TextMuted, modifier = Modifier.size(16.dp))
                            Text("Vân tay", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (!isFace) Color.White else TextMuted)
                        }
                    }
                }

                Spacer(Modifier.height(22.dp))

                // Interactive Biometric Scanner Viewport
                Box(
                    modifier = Modifier
                        .size(144.dp)
                        .clickable(enabled = scanState != ScanState.SCANNING) { startScan(true) },
                    contentAlignment = Alignment.Center
                ) {
                    // Sonar Pulse Ring when scanning
                    if (scanState == ScanState.SCANNING) {
                        Box(
                            modifier = Modifier
                                .size(116.dp)
                                .scale(pulseScale)
                                .border(2.dp, accentColor.copy(alpha = 0.45f), CircleShape)
                        )
                    }

                    // Frame / Sensor Circle
                    Box(
                        modifier = Modifier
                            .size(116.dp)
                            .clip(if (mode == BiometricMode.FACE_ID) RoundedCornerShape(28.dp) else CircleShape)
                            .background(accentColor.copy(alpha = 0.14f))
                            .border(
                                width = 2.5.dp,
                                color = accentColor,
                                shape = if (mode == BiometricMode.FACE_ID) RoundedCornerShape(28.dp) else CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Face ID corner brackets or Fingerprint icon
                        if (scanState == ScanState.SUCCESS) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Thành công",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(56.dp)
                            )
                        } else if (scanState == ScanState.FAILED) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Thất bại",
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(56.dp)
                            )
                        } else {
                            Icon(
                                imageVector = if (mode == BiometricMode.FACE_ID) Icons.Default.Face else Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(62.dp)
                            )
                        }

                        // Scanning laser bar overlay
                        if (scanState == ScanState.SCANNING) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val y = size.height * laserPos
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        listOf(Color.Transparent, Color(0xFF38BDF8), Color.Transparent)
                                    ),
                                    start = Offset(8f, y),
                                    end = Offset(size.width - 8f, y),
                                    strokeWidth = 6f,
                                    cap = StrokeCap.Round
                                )
                                drawRoundRect(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.12f),
                                    topLeft = Offset(0f, 0f),
                                    size = Size(size.width, y),
                                    cornerRadius = CornerRadius(12f, 12f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (scanState) {
                        ScanState.SUCCESS -> Color(0xFF059669)
                        ScanState.FAILED -> Color(0xFFDC2626)
                        else -> TextMuted
                    },
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(Modifier.height(18.dp))

                // Primary Scan Button
                Button(
                    onClick = { startScan(true) },
                    enabled = scanState != ScanState.SCANNING && scanState != ScanState.SUCCESS,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Icon(
                        imageVector = if (mode == BiometricMode.FACE_ID) Icons.Default.Face else Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (mode == BiometricMode.FACE_ID) "Quét Face ID ngay" else "Quét Vân tay ngay",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Secondary Row: System BiometricPrompt (if available) + Simulate Fail (for Security Demo)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (hasHardwareBiometric) {
                        OutlinedButton(
                            onClick = onUseSystemBiometric,
                            enabled = scanState != ScanState.SCANNING,
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                        ) {
                            Text("Cảm biến thật", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                        }
                    }

                    OutlinedButton(
                        onClick = { startScan(false) },
                        enabled = scanState != ScanState.SCANNING && scanState != ScanState.SUCCESS,
                        modifier = Modifier.weight(1f).height(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5))
                    ) {
                        Text("Thử quét sai", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                    }
                }
            }
        }
    }
}
