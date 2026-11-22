package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.*
import com.example.quan_ly_ticket.ui.components.AlertType
import com.example.quan_ly_ticket.ui.components.EventCardSkeleton
import com.example.quan_ly_ticket.ui.components.EventCoverImage
import com.example.quan_ly_ticket.ui.components.FrostedCapsuleBottomNav
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.components.NavTabItem
import com.example.quan_ly_ticket.ui.components.SemanticAlertBanner
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.launch
import java.text.DecimalFormat

/**
 * Cổng Quản Trị Ban Tổ Chức Sự Kiện (RBAC: ORGANIZER / ADMIN)
 * - Tab 1: Tổng quan Doanh thu, Quản lý & Tạo Sự kiện mới, Đóng/Mở bán vé.
 * - Tab 2: Quản lý Vé phát hành & Thu hồi vé gian lận / phe vé tức thì (Yêu cầu Face ID / Vân tay).
 * - Tab 3: Trung tâm Giám sát An ninh (SIEM Audit Logs) theo dõi tấn công Replay, Webhook, Anti-Scalping.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizerDashboardScreen(
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
    onRequireBiometric: (title: String, subtitle: String, onVerified: () -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val formatter = remember { DecimalFormat("#,###") }

    var activeTab by remember { mutableIntStateOf(0) } // 0=Events, 1=Tickets/Revoke, 2=SIEM Audit
    var isLoading by remember { mutableStateOf(true) }
    var dashboard by remember { mutableStateOf(OrganizerDashboardData()) }
    var auditLogs by remember { mutableStateOf<List<SecurityAuditLogItem>>(emptyList()) }
    var severityFilter by remember { mutableStateOf("ALL") }
    var showCreateEventSheet by remember { mutableStateOf(false) }

    fun loadAll() {
        isLoading = true
        scope.launch {
            try {
                val api = ApiClient.getService(context)
                val dashRes = api.getOrganizerDashboard()
                if (dashRes.isSuccessful && dashRes.body()?.success == true && dashRes.body()?.data != null) {
                    dashboard = dashRes.body()!!.data!!
                }
                val auditRes = api.getSecurityAuditLogs()
                if (auditRes.isSuccessful && auditRes.body()?.success == true && auditRes.body()?.data != null) {
                    auditLogs = auditRes.body()!!.data!!
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, "Lỗi tải dữ liệu BTC: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadAll() }

    val orgTabs = remember {
        listOf(
            NavTabItem("Sự kiện", Icons.Default.Event),
            NavTabItem("Thu hồi vé", Icons.Default.GppBad),
            NavTabItem("SIEM An ninh", Icons.Default.Shield)
        )
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFEFF6FF)) {
                            Text(
                                text = "RBAC · ORGANIZER PORTAL",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = securePrefs.getUserFullName() ?: "Ban Tổ Chức",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = TextDark,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color.White)
                                .border(1.dp, BorderLight, RoundedCornerShape(11.dp))
                                .clickable { loadAll() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                            } else {
                                Icon(Icons.Default.Refresh, null, tint = TextDark, modifier = Modifier.size(18.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFFEF2F2))
                                .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(11.dp))
                                .clickable {
                                    scope.launch {
                                        try {
                                            securePrefs.getRefreshToken()?.let {
                                                ApiClient.getService(context).logout(LogoutRequest(it))
                                            }
                                        } catch (_: Exception) { }
                                        securePrefs.clear()
                                        onLogout()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }

                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        val direction = if (targetState >= initialState) 1 else -1
                        (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) {
                                direction * (it / 16)
                            }) togetherWith
                            fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    },
                    label = "org_tab_transition"
                ) { tab ->
                when (tab) {
                    // ================= TAB 0: TỔNG QUAN & QUẢN LÝ SỰ KIỆN =================
                    0 -> {
                        val s = dashboard.summary
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OrgKpiCard("Doanh thu thực", "${formatter.format(s.totalRevenue)} ₫", PrimaryBlue, Modifier.weight(1f))
                            OrgKpiCard("Vé phát hành", "${s.totalIssued} vé", Color(0xFF0F766E), Modifier.weight(1f))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OrgKpiCard("Đã check-in", "${s.checkedInTickets} vé", Color(0xFF15803D), Modifier.weight(1f))
                            OrgKpiCard("Cảnh báo an ninh", "${s.securityAlerts} sự kiện", Color(0xFFDC2626), Modifier.weight(1f))
                        }

                        // Quick Actions Row
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { showCreateEventSheet = true },
                                modifier = Modifier.weight(1.3f).height(44.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Tạo sự kiện 2k", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                            }
                            OutlinedButton(
                                onClick = onOpenScanner,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = TextDark, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Soát vé", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }

                        Text("Sự kiện đang quản lý (${dashboard.events.size})", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)

                        if (isLoading && dashboard.events.isEmpty()) {
                            repeat(3) { EventCardSkeleton() }
                        } else {
                            dashboard.events.forEach { ev ->
                                val style = remember(ev.title) { posterStyleForEvent(ev.title) }
                                val isOpen = ev.status == "UPCOMING" || ev.status == "ONGOING"
                                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        EventCoverImage(
                                            url = style.imageUrl,
                                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(12.dp))
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(ev.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "Giá ${formatter.format(ev.ticketPrice)} ₫ · Đã phát ${ev.issuedCount} · Check-in ${ev.usedCount}",
                                                fontSize = 10.sp, color = TextMuted
                                            )
                                            Text("Còn ${ev.availableTickets}/${ev.totalTickets} vé · ${ev.status}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = if (isOpen) Color(0xFF15803D) else Color(0xFFDC2626))
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val nextStatus = if (isOpen) "CANCELLED" else "UPCOMING"
                                                scope.launch {
                                                    try {
                                                        val res = ApiClient.getService(context)
                                                            .updateEventStatus(ev.id, UpdateEventStatusRequest(nextStatus))
                                                        if (res.isSuccessful) {
                                                            Toast.makeText(context, if (isOpen) "Đã tạm khóa bán vé" else "Đã mở bán lại", Toast.LENGTH_SHORT).show()
                                                            loadAll()
                                                        }
                                                    } catch (_: Exception) { }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(32.dp)
                                        ) {
                                            Text(if (isOpen) "Tạm đóng" else "Mở lại", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOpen) Color(0xFFDC2626) else PrimaryBlue)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ================= TAB 1: QUẢN LÝ VÉ & THU HỒI VÉ GIAN LẬN =================
                    1 -> {
                        SemanticAlertBanner(
                            type = AlertType.ERROR,
                            title = "Thu hồi Vé Tức Thì (Instant Revocation)",
                            message = "Khi phát hiện phe vé hoặc gian lận, Ban tổ chức xác thực sinh trắc học để vô hiệu hóa vé & hủy seed TOTP ngay lập tức. Email khách được che mờ để bảo vệ PII."
                        )

                        dashboard.recentTickets.forEach { t ->
                            val isActive = t.status == "ACTIVE"
                            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(t.ticketCode, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace, color = TextDark)
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = when (t.status) {
                                                    "ACTIVE" -> Color(0xFFDCFCE7)
                                                    "USED" -> Color(0xFFF1F5F9)
                                                    else -> Color(0xFFFEE2E2)
                                                }
                                            ) {
                                                Text(
                                                    text = t.status,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = when (t.status) {
                                                        "ACTIVE" -> Color(0xFF15803D)
                                                        "USED" -> TextMuted
                                                        else -> Color(0xFFDC2626)
                                                    },
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(t.eventTitle ?: "Sự kiện", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextDark, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("Chủ vé: ${t.ownerName ?: "—"} (${t.ownerEmail ?: "***"})", fontSize = 10.sp, color = TextMuted)
                                    }

                                    if (isActive) {
                                        Button(
                                            onClick = {
                                                onRequireBiometric(
                                                    "Xác nhận Thu hồi Vé Gian lận",
                                                    "Vô hiệu hóa vĩnh viễn vé ${t.ticketCode} & hủy mã QR động"
                                                ) {
                                                    scope.launch {
                                                        try {
                                                            val res = ApiClient.getService(context)
                                                                .revokeTicket(t.id, RevokeTicketRequest("BTC thu hồi nghi vấn phe vé / gian lận"))
                                                            if (res.isSuccessful && res.body()?.success == true) {
                                                                Toast.makeText(context, "Đã thu hồi vé ${t.ticketCode}!", Toast.LENGTH_LONG).show()
                                                                loadAll()
                                                            } else {
                                                                Toast.makeText(context, res.body()?.error ?: "Lỗi thu hồi", Toast.LENGTH_SHORT).show()
                                                            }
                                                        } catch (e: Exception) {
                                                            if (e is kotlinx.coroutines.CancellationException) throw e
                                                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp)
                                        ) {
                                            Text("Thu hồi", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ================= TAB 2: TRUNG TÂM GIÁM SÁT AN NINH (SIEM AUDIT LOGS) =================
                    2 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("SIEM Security Audit Logs", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                            TextButton(onClick = onOpenCryptoLab) {
                                Text("Crypto Lab →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("ALL", "CRITICAL", "HIGH", "WARNING", "INFO").forEach { sev ->
                                val active = severityFilter == sev
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (active) PrimaryBlue else Color.White)
                                        .border(1.dp, if (active) PrimaryBlue else BorderLight, RoundedCornerShape(8.dp))
                                        .clickable { severityFilter = sev }
                                        .padding(horizontal = 9.dp, vertical = 5.dp)
                                ) {
                                    Text(sev, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextMuted)
                                }
                            }
                        }

                        val filteredAudit = remember(auditLogs, severityFilter) {
                            if (severityFilter == "ALL") auditLogs else auditLogs.filter { it.severity == severityFilter }
                        }

                        filteredAudit.forEach { log ->
                            val (sevBg, sevFg) = when (log.severity) {
                                "CRITICAL" -> Color(0xFFFEE2E2) to Color(0xFFB91C1C)
                                "HIGH" -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
                                "WARNING" -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                                else -> Color(0xFFEFF6FF) to PrimaryBlue
                            }
                            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = log.action,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextDark,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Surface(shape = RoundedCornerShape(6.dp), color = sevBg) {
                                            Text(
                                                text = log.severity,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = sevFg,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "IP: ${log.ipAddress ?: "N/A"} · ${formatEventTime(log.createdAt) ?: ""}",
                                        fontSize = 10.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                }
                }

                Spacer(Modifier.height(92.dp))
            }

            FrostedCapsuleBottomNav(
                items = orgTabs,
                selectedIndex = activeTab,
                onSelect = { activeTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Bottom Sheet Tạo Sự Kiện Mới (POST /api/events)
    if (showCreateEventSheet) {
        var evTitle by remember { mutableStateOf("") }
        var evLocation by remember { mutableStateOf("Trung tâm Hội nghị Quốc gia, Hà Nội") }
        var evPrice by remember { mutableStateOf("2000") }
        var evQty by remember { mutableStateOf("300") }
        var isCreating by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { if (!isCreating) showCreateEventSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Tạo sự kiện mới (RBAC: Organizer)", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                OutlinedTextField(
                    value = evTitle, onValueChange = { evTitle = it },
                    label = { Text("Tên sự kiện / Concert") },
                    placeholder = { Text("VD: Đêm Nhạc Sinh Viên KMA 2026") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = evLocation, onValueChange = { evLocation = it },
                    label = { Text("Địa điểm tổ chức") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = evPrice, onValueChange = { evPrice = it.filter { c -> c.isDigit() } },
                        label = { Text("Giá vé (VND)") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedTextField(
                        value = evQty, onValueChange = { evQty = it.filter { c -> c.isDigit() } },
                        label = { Text("Tổng số vé") },
                        singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)
                    )
                }
                Button(
                    onClick = {
                        if (evTitle.isBlank() || evLocation.isBlank()) {
                            Toast.makeText(context, "Vui lòng nhập tên và địa điểm sự kiện", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isCreating = true
                        scope.launch {
                            try {
                                val res = ApiClient.getService(context).createEvent(
                                    CreateEventRequest(
                                        title = evTitle.trim(),
                                        description = "[CONCERT] Sự kiện mới phát hành trực tiếp từ cổng Ban Tổ Chức, bảo vệ chống phe vé ECDSA P-256.",
                                        location = evLocation.trim(),
                                        startTime = "2026-12-30T19:30:00",
                                        endTime = "2026-12-30T23:00:00",
                                        totalTickets = evQty.toIntOrNull() ?: 200,
                                        ticketPrice = evPrice.toDoubleOrNull() ?: 2000.0
                                    )
                                )
                                if (res.isSuccessful && res.body()?.success == true) {
                                    Toast.makeText(context, "Đã tạo sự kiện mới thành công!", Toast.LENGTH_LONG).show()
                                    showCreateEventSheet = false
                                    loadAll()
                                } else {
                                    Toast.makeText(context, res.body()?.error ?: "Lỗi tạo sự kiện", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isCreating = false
                            }
                        }
                    },
                    enabled = !isCreating,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isCreating) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                    else Text("Phát hành sự kiện ngay", fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun OrgKpiCard(title: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier = modifier, shape = RoundedCornerShape(15.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextMuted)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
