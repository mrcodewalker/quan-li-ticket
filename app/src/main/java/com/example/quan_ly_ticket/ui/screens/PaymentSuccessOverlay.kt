package com.example.quan_ly_ticket.ui.screens

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.model.Ticket
import kotlinx.coroutines.delay
import java.text.DecimalFormat
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private data class ConfettiPiece(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color,
    val spin: Float,
    val isCircle: Boolean
)

/**
 * Màn hình ăn mừng thanh toán thành công (sau khi SePay webhook xác nhận / polling trả về PAID).
 * Hiệu ứng: nền gradient, vòng sóng lan tỏa, dấu tick vẽ dần, confetti bắn ra, thẻ vé trượt lên.
 */
@Composable
fun PaymentSuccessOverlay(
    ticket: Ticket,
    amount: Double,
    orderCode: String,
    paidVia: String,
    onViewTicket: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { DecimalFormat("#,###") }

    // Rung nhẹ báo thành công
    LaunchedEffect(Unit) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 60, 80), -1))
            }
        } catch (_: Exception) { }
    }

    // Timeline animation
    val circleScale = remember { Animatable(0f) }
    val checkProgress = remember { Animatable(0f) }
    val confettiProgress = remember { Animatable(0f) }
    val cardOffset = remember { Animatable(120f) }
    val cardAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        circleScale.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow))
    }
    LaunchedEffect(Unit) {
        delay(250)
        checkProgress.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(350)
        confettiProgress.animateTo(1f, tween(2200, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(600)
        cardAlpha.animateTo(1f, tween(400))
    }
    LaunchedEffect(Unit) {
        delay(600)
        cardOffset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow))
    }

    val infinite = rememberInfiniteTransition(label = "success_pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "pulse"
    )
    val shimmer by infinite.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "shimmer"
    )

    val confetti = remember {
        val palette = listOf(
            Color(0xFF6EE7B7), Color(0xFFFCD34D), Color(0xFF93C5FD),
            Color(0xFFF9A8D4), Color(0xFFC4B5FD), Color.White
        )
        List(70) {
            ConfettiPiece(
                angle = Random.nextFloat() * 360f,
                speed = 0.45f + Random.nextFloat() * 0.75f,
                size = 6f + Random.nextFloat() * 10f,
                color = palette.random(),
                spin = Random.nextFloat() * 720f - 360f,
                isCircle = Random.nextBoolean()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0B3D2E), Color(0xFF0F5F48), Color(0xFF152759))
                )
            )
    ) {
        // Confetti layer
        Canvas(modifier = Modifier.fillMaxSize()) {
            val origin = Offset(size.width / 2f, size.height * 0.28f)
            val p = confettiProgress.value
            if (p > 0f && p < 1f) {
                confetti.forEach { c ->
                    val rad = Math.toRadians(c.angle.toDouble())
                    val dist = size.minDimension * 0.75f * c.speed * p
                    val gravity = size.height * 0.45f * p * p
                    val x = origin.x + (cos(rad) * dist).toFloat()
                    val y = origin.y + (sin(rad) * dist).toFloat() + gravity
                    val alpha = (1f - p).coerceIn(0f, 1f)
                    rotate(c.spin * p, pivot = Offset(x, y)) {
                        if (c.isCircle) {
                            drawCircle(c.color.copy(alpha = alpha), radius = c.size / 2f, center = Offset(x, y))
                        } else {
                            drawRect(
                                c.color.copy(alpha = alpha),
                                topLeft = Offset(x - c.size / 2f, y - c.size / 4f),
                                size = Size(c.size, c.size / 2f)
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(56.dp))

            // Ripple rings + check circle
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(190.dp)) {
                listOf(0f, 0.5f).forEach { phase ->
                    val t = (pulse + phase) % 1f
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(1f + t * 0.6f)
                            .alpha((1f - t) * 0.5f * circleScale.value)
                            .border(2.dp, Color(0xFF6EE7B7), CircleShape)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(circleScale.value)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(58.dp)) {
                        val path = Path().apply {
                            moveTo(size.width * 0.12f, size.height * 0.52f)
                            lineTo(size.width * 0.40f, size.height * 0.78f)
                            lineTo(size.width * 0.90f, size.height * 0.24f)
                        }
                        val measure = androidx.compose.ui.graphics.PathMeasure()
                        measure.setPath(path, false)
                        val partial = Path()
                        measure.getSegment(0f, measure.length * checkProgress.value, partial, true)
                        drawPath(
                            partial, Color.White,
                            style = Stroke(width = size.width * 0.13f, cap = StrokeCap.Round)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Thanh toán thành công!",
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                modifier = Modifier.alpha(checkProgress.value)
            )
            Text(
                "${formatter.format(amount)} ₫ · $paidVia",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFA7F3D0),
                modifier = Modifier.padding(top = 6.dp).alpha(checkProgress.value)
            )
            Text(
                "Mã đơn #$orderCode · Vé đã được ký số ECDSA P-256",
                fontSize = 11.sp, color = Color(0xFFCBD5E1),
                modifier = Modifier.padding(top = 4.dp).alpha(checkProgress.value)
            )

            Spacer(modifier = Modifier.height(28.dp))

            // Ticket card sliding up
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = cardOffset.value.dp)
                    .alpha(cardAlpha.value)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.linearGradient(listOf(Color(0xFF203E9A), Color(0xFF7B4FD6))))
                            .padding(18.dp)
                    ) {
                        // Shimmer sweep
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val x = size.width * shimmer
                            drawRect(
                                Brush.linearGradient(
                                    listOf(Color.Transparent, Color.White.copy(alpha = 0.18f), Color.Transparent),
                                    start = Offset(x - 120f, 0f), end = Offset(x + 120f, size.height)
                                )
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ConfirmationNumber, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("VÉ ĐIỆN TỬ CỦA BẠN", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD7E0FF), letterSpacing = 1.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                ticket.eventTitle ?: "Sự kiện SecureTicket",
                                fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                listOfNotNull(formatEventTime(ticket.eventStartTime), ticket.eventLocation).joinToString(" · "),
                                fontSize = 11.sp, color = Color(0xFFD7E0FF)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("MÃ VÉ", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF858D9D))
                            Text(
                                ticket.ticketCode.ifBlank { "—" },
                                fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace, color = Color(0xFF18202F)
                            )
                        }
                        Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFDCFCE7)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.VerifiedUser, null, tint = Color(0xFF15803D), modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("ĐÃ KÝ SỐ", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF15803D))
                            }
                        }
                    }
                    Text(
                        "Vé đã lưu vào kho mã hóa AES-256-GCM trên thiết bị và ràng buộc với khóa phần cứng TEE.",
                        fontSize = 10.sp, color = Color(0xFF6A7283), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onViewTicket,
                modifier = Modifier.fillMaxWidth().height(52.dp).alpha(cardAlpha.value),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF34D399), contentColor = Color(0xFF064E3B))
            ) {
                Text("Xem vé & mã QR ngay", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            }
            TextButton(
                onClick = onGoHome,
                modifier = Modifier.padding(bottom = 16.dp).alpha(cardAlpha.value)
            ) {
                Text("Về trang chủ", color = Color(0xFFCBD5E1), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** "2026-11-28T12:30:00.000Z" hoặc "2026-11-28 19:30:00" -> "28/11 · 19:30" (giờ VN) */
fun formatEventTime(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val vn = java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        val parsed: java.util.Date? = if (raw.contains("T")) {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(raw.take(19))
        } else {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).apply {
                timeZone = vn
            }.parse(raw.take(19))
        }
        val out = java.text.SimpleDateFormat("EEE, dd/MM · HH:mm", java.util.Locale("vi", "VN")).apply { timeZone = vn }
        if (parsed != null) out.format(parsed) else raw
    } catch (_: Exception) {
        raw
    }
}
