package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EventSeat
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.model.BookingContextData
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.CreateOrderRequest
import com.example.quan_ly_ticket.data.model.Event
import com.example.quan_ly_ticket.data.model.JoinQueueRequest
import com.example.quan_ly_ticket.data.model.LeaveQueueRequest
import com.example.quan_ly_ticket.data.model.QueueData
import com.example.quan_ly_ticket.data.model.ZoneTierInfo
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.ui.components.EventCoverImage
import com.example.quan_ly_ticket.ui.components.FrostedIconButton
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

private fun buildFallbackZones(basePrice: Double): List<ZoneTierInfo> {
    val seatNums = (10..49).map { it.toString() }
    val specs = listOf(
        Triple("STAGE-V", "Stage View 360°", 1.5),
        Triple("VIP-A", "VIP Khu A", 1.4),
        Triple("VIP-B", "VIP Khu B", 1.3),
        Triple("PLATINUM", "Platinum Lounge", 1.2),
        Triple("FANZONE", "Fanzone Sôi động", 1.1),
        Triple("PREM-S", "Standard Tiêu chuẩn", 1.0)
    )
    return specs.map { (code, name, mult) ->
        val computed = ((basePrice * mult) / 100.0).roundToInt() * 100.0
        ZoneTierInfo(
            code = code,
            name = name,
            multiplier = mult,
            price = computed,
            perks = "Vé chính chủ · Mã QR tự động làm mới",
            totalSeats = seatNums.size,
            availableSeats = seatNums.size,
            seatNumbers = seatNums
        )
    }
}

@Composable
fun EventBookingScreen(
    event: Event,
    onProceedToPayment: (CreateOrderData) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var bookingContext by remember { mutableStateOf<BookingContextData?>(null) }
    var isLoadingContext by remember { mutableStateOf(true) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorBanner by remember { mutableStateOf<String?>(null) }

    val zones = remember(bookingContext, event.ticketPrice) {
        bookingContext?.zones?.takeIf { it.isNotEmpty() } ?: buildFallbackZones(event.ticketPrice)
    }
    var selectedZoneCode by remember { mutableStateOf("VIP-A") }
    val activeZone = remember(zones, selectedZoneCode) {
        zones.firstOrNull { it.code == selectedZoneCode } ?: zones.first()
    }

    val soldSet = remember(bookingContext) { bookingContext?.soldSeats?.toSet() ?: emptySet() }
    val heldSet = remember(bookingContext) { bookingContext?.heldSeats?.toSet() ?: emptySet() }

    var selectedSeat by remember { mutableStateOf<String?>(null) }
    var waitingQueueData by remember { mutableStateOf<QueueData?>(null) }

    // Auto-select first available seat whenever zone or occupancy changes
    LaunchedEffect(activeZone.code, soldSet, heldSet) {
        val currentKey = selectedSeat?.let { "${activeZone.code}$it" }
        if (selectedSeat == null || currentKey in soldSet || currentKey in heldSet) {
            val seats = activeZone.seatNumbers.ifEmpty { (10..49).map { it.toString() } }
            selectedSeat = seats.firstOrNull { s ->
                val k = "${activeZone.code}$s"
                k !in soldSet && k !in heldSet
            }
        }
    }

    fun loadContext() {
        scope.launch {
            isLoadingContext = true
            try {
                val res = ApiClient.getService(context).getEventBookingContext(event.id)
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    bookingContext = res.body()!!.data
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                isLoadingContext = false
            }
        }
    }

    LaunchedEffect(event.id) {
        loadContext()
    }

    fun submitBookingOrder(passToken: String?) {
        val seat = selectedSeat
        if (seat == null) {
            Toast.makeText(context, "Khu vực này đã hết ghế trống, vui lòng chọn khu khác.", Toast.LENGTH_SHORT).show()
            return
        }
        isSubmitting = true
        errorBanner = null
        scope.launch {
            try {
                // Always generate a fresh clientNonce, timestamp (seconds), and TEE signature per attempt
                val clientNonce = UUID.randomUUID().toString().replace("-", "")
                val timestamp = System.currentTimeMillis() / 1000L
                val deviceSig = withContext(Dispatchers.Default) {
                    KeystoreManager.signBuyPayload(event.id, clientNonce, timestamp)
                }
                val res = ApiClient.getService(context).createPaymentOrder(
                    CreateOrderRequest(
                        eventId = event.id,
                        passToken = passToken,
                        clientNonce = clientNonce,
                        timestamp = timestamp,
                        deviceSignature = deviceSig,
                        seatZone = activeZone.code,
                        seatNumber = seat
                    )
                )
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    onProceedToPayment(res.body()!!.data!!)
                } else {
                    val rawErr = res.errorBody()?.string()
                    val parsedMsg = try {
                        if (!rawErr.isNullOrBlank()) JSONObject(rawErr).optString("error") else null
                    } catch (_: Exception) {
                        null
                    }
                    errorBanner = parsedMsg?.ifBlank { null } ?: res.body()?.error ?: "Không thể giữ ghế (${res.code()})"
                    if (res.code() == 409) {
                        loadContext()
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                errorBanner = "Lỗi kết nối: ${e.message}"
            } finally {
                isSubmitting = false
            }
        }
    }

    // Poll FIFO queue if user is placed in waiting room
    LaunchedEffect(waitingQueueData?.queueId) {
        val qId = waitingQueueData?.queueId ?: return@LaunchedEffect
        val api = ApiClient.getService(context)
        while (isActive && waitingQueueData != null) {
            delay(2500L)
            try {
                val res = api.getQueueStatus(qId)
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    val updated = res.body()!!.data!!
                    if (updated.status == "ACTIVE" && !updated.passToken.isNullOrBlank()) {
                        waitingQueueData = null
                        submitBookingOrder(updated.passToken)
                        break
                    } else if (updated.status == "WAITING") {
                        waitingQueueData = updated
                    } else {
                        waitingQueueData = null
                        break
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    val style = remember(event.title, event.description) { posterStyleForEvent(event.title, event.description) }
    val userTotal = bookingContext?.userTotalActive ?: 0
    val maxQuota = bookingContext?.maxTicketsPerEvent ?: 6
    val quotaReached = userTotal >= maxQuota

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 110.dp)
            ) {
                // 1. Event Cover Hero
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    EventCoverImage(
                        url = style.imageUrl,
                        fallbackColor = style.bgStart,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color(0x660F172A), Color(0xCC0F172A))
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FrostedIconButton(
                            onClick = onBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Quay lại"
                        )
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xCC0F172A),
                            border = BorderStroke(1.dp, Color(0x4DFFFFFF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(Icons.Default.VerifiedUser, null, tint = TerminalTextEmerald, modifier = Modifier.size(13.dp))
                                Text("Vé chính chủ · Chống đầu cơ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(shape = RoundedCornerShape(6.dp), color = style.accent) {
                            Text(
                                text = style.tag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                        Text(
                            text = event.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.Schedule, null, tint = Color(0xFF93C5FD), modifier = Modifier.size(12.dp))
                                Text(formatEventTime(event.startTime) ?: event.startTime, fontSize = 11.sp, color = Color(0xFFE2E8F0))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.LocationOn, null, tint = Color(0xFF93C5FD), modifier = Modifier.size(12.dp))
                                Text(event.location, fontSize = 11.sp, color = Color(0xFFE2E8F0), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 2. Personal Anti-Scalping Quota Bar
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Default.Shield, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                                    Text("Hạn mức chống đầu cơ", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (quotaReached) DangerBg else InfoBg
                                ) {
                                    Text(
                                        text = "$userTotal / $maxQuota vé",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (quotaReached) DangerFg else InfoFg,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            LinearProgressIndicator(
                                progress = { (userTotal.toFloat() / maxQuota.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(7.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (quotaReached) DangerFg else PrimaryBlue,
                                trackColor = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "Mỗi tài khoản chỉ giữ/sở hữu tối đa $maxQuota vé cho sự kiện này · Dynamic 30s TOTP.",
                                fontSize = 10.sp,
                                color = TextMuted
                            )
                        }
                    }

                    // 3. Visual Stage & Arena Zone Map
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = "Sơ đồ Sân khấu & Phân khu (Chạm để chọn)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDark
                            )

                            // Main Stage Banner
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(PrimaryNavy, PrimaryBlue))
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "SÂN KHẤU CHÍNH 360°",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                            }

                            // Row 1: STAGE-V
                            ArenaZoneBlock(
                                zone = zones.firstOrNull { it.code == "STAGE-V" } ?: activeZone,
                                isSelected = activeZone.code == "STAGE-V",
                                onClick = { selectedZoneCode = "STAGE-V" },
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Row 2: VIP-A & VIP-B
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                zones.firstOrNull { it.code == "VIP-A" }?.let { z ->
                                    ArenaZoneBlock(
                                        zone = z,
                                        isSelected = activeZone.code == z.code,
                                        onClick = { selectedZoneCode = z.code },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                zones.firstOrNull { it.code == "VIP-B" }?.let { z ->
                                    ArenaZoneBlock(
                                        zone = z,
                                        isSelected = activeZone.code == z.code,
                                        onClick = { selectedZoneCode = z.code },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Row 3: PLATINUM & FANZONE
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                zones.firstOrNull { it.code == "PLATINUM" }?.let { z ->
                                    ArenaZoneBlock(
                                        zone = z,
                                        isSelected = activeZone.code == z.code,
                                        onClick = { selectedZoneCode = z.code },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                zones.firstOrNull { it.code == "FANZONE" }?.let { z ->
                                    ArenaZoneBlock(
                                        zone = z,
                                        isSelected = activeZone.code == z.code,
                                        onClick = { selectedZoneCode = z.code },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Row 4: PREM-S
                            zones.firstOrNull { it.code == "PREM-S" }?.let { z ->
                                ArenaZoneBlock(
                                    zone = z,
                                    isSelected = activeZone.code == z.code,
                                    onClick = { selectedZoneCode = z.code },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    // 4. Interactive Seat Grid Picker (40 seats: 10..49)
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Chọn ghế khu ${activeZone.code}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = "${activeZone.name} · ${String.format("%,.0f", activeZone.price)} ₫",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PrimaryBlue
                                    )
                                }
                                if (isLoadingContext) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text(
                                        text = "Còn ${activeZone.availableSeats}/${activeZone.totalSeats}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessFg
                                    )
                                }
                            }

                            // Legend
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SeatLegendChip("Trống", Color(0xFFF8FAFC), BorderLight, TextDark)
                                SeatLegendChip("Đang chọn", PrimaryBlue, PrimaryBlue, Color.White)
                                SeatLegendChip("Giữ 5p", WarningBg, WarningBorder, WarningFg)
                                SeatLegendChip("Đã bán", Color(0xFFE2E8F0), Color(0xFFCBD5E1), TextSubtle)
                            }

                            val seats = activeZone.seatNumbers.ifEmpty { (10..49).map { it.toString() } }
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                seats.chunked(8).forEach { rowSeats ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        rowSeats.forEach { seatNum ->
                                            val key = "${activeZone.code}$seatNum"
                                            val isSold = key in soldSet
                                            val isHeld = key in heldSet
                                            val isSelected = selectedSeat == seatNum && !isSold && !isHeld
                                            val bg = when {
                                                isSelected -> PrimaryBlue
                                                isSold -> Color(0xFFE2E8F0)
                                                isHeld -> WarningBg
                                                else -> Color(0xFFF8FAFC)
                                            }
                                            val border = when {
                                                isSelected -> PrimaryBlue
                                                isSold -> Color(0xFFCBD5E1)
                                                isHeld -> WarningBorder
                                                else -> BorderLight
                                            }
                                            val fg = when {
                                                isSelected -> Color.White
                                                isSold -> TextSubtle
                                                isHeld -> WarningFg
                                                else -> TextDark
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(32.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(bg)
                                                    .border(1.dp, border, RoundedCornerShape(8.dp))
                                                    .clickable(enabled = !isSold && !isHeld) {
                                                        selectedSeat = seatNum
                                                        errorBanner = null
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = seatNum,
                                                    fontSize = 10.sp,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    color = fg
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (errorBanner != null) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = DangerBg,
                            border = BorderStroke(1.dp, DangerBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorBanner!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DangerFg,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }

            // 5. Sticky Bottom Booking Bar
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                color = Color.White,
                shadowElevation = 14.dp,
                border = BorderStroke(1.dp, BorderLight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (selectedSeat != null) "${activeZone.code} · Ghế $selectedSeat" else "${activeZone.code} · Hết ghế",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark
                        )
                        Text(
                            text = "${String.format("%,.0f", activeZone.price)} ₫",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = PrimaryBlue
                        )
                    }

                    Button(
                        onClick = {
                            if (quotaReached) {
                                errorBanner = "Bạn đã đạt hạn mức tối đa $maxQuota vé cho sự kiện này."
                                return@Button
                            }
                            isSubmitting = true
                            errorBanner = null
                            scope.launch {
                                try {
                                    val api = ApiClient.getService(context)
                                    val qRes = api.joinQueue(JoinQueueRequest(event.id))
                                    if (qRes.isSuccessful && qRes.body()?.success == true && qRes.body()?.data != null) {
                                        val qData = qRes.body()!!.data!!
                                        if (qData.status == "ACTIVE") {
                                            submitBookingOrder(qData.passToken)
                                        } else {
                                            isSubmitting = false
                                            waitingQueueData = qData
                                        }
                                    } else {
                                        submitBookingOrder(null)
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    isSubmitting = false
                                    errorBanner = "Lỗi phòng chờ: ${e.message}"
                                }
                            }
                        },
                        enabled = !isSubmitting && selectedSeat != null && !quotaReached,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        modifier = Modifier.height(48.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Lock, null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Đặt vé & Giữ ghế",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    waitingQueueData?.let { q ->
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.5.dp, color = PrimaryBlue)
                    Text("Phòng Chờ Ảo (FIFO)", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = TextDark)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Hệ thống đang giữ chỗ công bằng theo thứ tự FIFO và sẽ tự động khóa ghế ${activeZone.code} · $selectedSeat ngay khi đến lượt.",
                        fontSize = 12.sp, color = TextMuted
                    )
                    Surface(shape = RoundedCornerShape(14.dp), color = InfoBg, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SỐ THỨ TỰ", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            Text("#${q.queueNumber ?: 1}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryNavy)
                            Text("Còn ${q.usersAhead ?: 0} người phía trước", fontSize = 10.sp, color = TextMuted)
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val idToLeave = q.queueId
                        waitingQueueData = null
                        scope.launch {
                            try { ApiClient.getService(context).leaveQueue(LeaveQueueRequest(idToLeave)) } catch (_: Exception) {}
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rời hàng đợi", color = DangerFg, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun ArenaZoneBlock(
    zone: ZoneTierInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
        border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) PrimaryBlue else BorderLight),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.code,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) PrimaryBlue else TextDark
                )
                Text(
                    text = zone.name,
                    fontSize = 9.sp,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${String.format("%,.0f", zone.price)} ₫",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) PrimaryBlue else TextDark
                )
                Text(
                    text = "${zone.availableSeats} trống",
                    fontSize = 9.sp,
                    color = if (zone.availableSeats > 0) SuccessFg else DangerFg,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun SeatLegendChip(
    label: String,
    bg: Color,
    border: Color,
    textColor: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg)
                .border(1.dp, border, RoundedCornerShape(3.dp))
        )
        Text(label, fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Medium)
    }
}
