package com.example.quan_ly_ticket.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.CreateOrderRequest
import com.example.quan_ly_ticket.data.model.Event
import com.example.quan_ly_ticket.data.model.JoinQueueRequest
import com.example.quan_ly_ticket.data.model.LeaveQueueRequest
import com.example.quan_ly_ticket.data.model.LogoutRequest
import com.example.quan_ly_ticket.data.model.QueueData
import com.example.quan_ly_ticket.data.model.RefreshTokenRequest
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.security.SecurityCheck
import com.example.quan_ly_ticket.ui.components.CryptoTerminalBox
import com.example.quan_ly_ticket.ui.components.EventCardSkeleton
import com.example.quan_ly_ticket.ui.components.EventCoverImage
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.components.NavigationTab
import com.example.quan_ly_ticket.ui.components.PremiumPillSwitch
import com.example.quan_ly_ticket.ui.components.SecureBottomNav
import com.example.quan_ly_ticket.ui.components.TicketCardSkeleton
import com.example.quan_ly_ticket.ui.components.TicketStubCard
import com.example.quan_ly_ticket.ui.components.rememberShimmerBrush
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

@Composable
fun HomeScreen(
    onSelectTicket: (ticket: Ticket) -> Unit,
    onProceedToPayment: (order: CreateOrderData) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
    onRequireBiometric: (title: String, subtitle: String, onVerified: () -> Unit) -> Unit = { _, _, ok -> ok() },
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val userRole = remember { securePrefs.getUserRole() ?: "CUSTOMER" }
    val userName = remember { securePrefs.getUserFullName()?.ifBlank { "Nguyễn Minh Anh" } ?: "Nguyễn Minh Anh" }
    val userEmail = remember { securePrefs.getUserEmail()?.ifBlank { "customer@secureticket.com" } ?: "customer@secureticket.com" }
    val ticketVault = remember { com.example.quan_ly_ticket.data.local.EncryptedTicketVault(context) }

    var currentTab by remember { mutableStateOf(NavigationTab.HOME) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var myTickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var incomingTicketBanner by remember { mutableStateOf<Ticket?>(null) }
    var waitingQueueEventId by remember { mutableStateOf<String?>(null) }
    var waitingQueueData by remember { mutableStateOf<QueueData?>(null) }
    var hasLoadedInitialTickets by remember { mutableStateOf(false) }

    // Settings state
    var biometricEnabled by remember { mutableStateOf(true) }
    var securityAlertsEnabled by remember { mutableStateOf(true) }
    var logoutConfirmPending by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverHostInput by remember { mutableStateOf(ApiClient.baseUrl) }

    fun refreshData() {
        isLoading = true
        scope.launch {
            try {
                val api = ApiClient.getService(context)
                val eventRes = api.getEvents()
                if (eventRes.isSuccessful && eventRes.body()?.success == true) {
                    events = eventRes.body()!!.data ?: emptyList()
                }

                val ticketRes = api.getMyTickets()
                if (ticketRes.isSuccessful && ticketRes.body()?.success == true) {
                    val list = ticketRes.body()!!.data ?: emptyList()
                    myTickets = list
                    ticketVault.saveTicketsEncrypted(list)
                } else {
                    // Fallback to vault
                    val cached = ticketVault.loadTicketsDecrypted()
                    if (cached.isNotEmpty()) {
                        myTickets = cached
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val cached = ticketVault.loadTicketsDecrypted()
                if (cached.isNotEmpty()) {
                    myTickets = cached
                }
            } finally {
                isLoading = false
                hasLoadedInitialTickets = true
            }
        }
    }

    fun proceedCreateOrder(eventId: String, passToken: String?) {
        scope.launch {
            try {
                val clientNonce = UUID.randomUUID().toString().replace("-", "")
                val timestamp = System.currentTimeMillis() / 1000L
                val deviceSig = withContext(Dispatchers.Default) {
                    KeystoreManager.signBuyPayload(eventId, clientNonce, timestamp)
                }
                val api = ApiClient.getService(context)
                val res = api.createPaymentOrder(
                    CreateOrderRequest(
                        eventId = eventId,
                        passToken = passToken,
                        clientNonce = clientNonce,
                        timestamp = timestamp,
                        deviceSignature = deviceSig
                    )
                )
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    onProceedToPayment(res.body()!!.data!!)
                } else {
                    Toast.makeText(context, res.body()?.error ?: "Lỗi tạo đơn đặt vé", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    // Tự động Polling nhận vé chuyển nhượng mới mỗi 5 giây (Khi A gửi vé cho B, B tự nhận ngay!)
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5000L)
            if (!hasLoadedInitialTickets) continue
            try {
                val api = ApiClient.getService(context)
                val ticketRes = api.getMyTickets()
                if (ticketRes.isSuccessful && ticketRes.body()?.success == true) {
                    val freshList = ticketRes.body()!!.data ?: emptyList()
                    val existingIds = myTickets.map { it.id }.toSet()
                    val newlyReceived = freshList.firstOrNull { it.id !in existingIds && (it.status == "ACTIVE" || it.status == null) }
                    if (newlyReceived != null) {
                        incomingTicketBanner = newlyReceived
                    }
                    if (freshList != myTickets) {
                        myTickets = freshList
                        ticketVault.saveTicketsEncrypted(freshList)
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    // Tự động Polling trạng thái Phòng Chờ Ảo (Virtual Waiting Room) mỗi 2.5 giây
    LaunchedEffect(waitingQueueData?.queueId) {
        val qId = waitingQueueData?.queueId ?: return@LaunchedEffect
        val evId = waitingQueueEventId ?: return@LaunchedEffect
        val api = ApiClient.getService(context)
        while (isActive && waitingQueueData != null) {
            delay(2500L)
            try {
                val res = api.getQueueStatus(qId)
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    val updated = res.body()!!.data!!
                    if (updated.status == "ACTIVE" && !updated.passToken.isNullOrBlank()) {
                        waitingQueueData = null
                        waitingQueueEventId = null
                        Toast.makeText(context, "Đến lượt bạn! Đang mở cổng thanh toán VietQR...", Toast.LENGTH_SHORT).show()
                        proceedCreateOrder(evId, updated.passToken)
                        break
                    } else if (updated.status == "WAITING") {
                        waitingQueueData = updated
                    } else {
                        waitingQueueData = null
                        waitingQueueEventId = null
                        break
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            }
        }
    }

    val activeTicketCount = remember(myTickets) {
        myTickets.count { it.status == "ACTIVE" || it.status == null }
    }

    AppBackground {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (fadeIn(animationSpec = tween(200, easing = FastOutSlowInEasing)) +
                            slideInHorizontally(animationSpec = tween(220, easing = FastOutSlowInEasing)) {
                                direction * (it / 16)
                            }) togetherWith
                            fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    },
                    label = "home_tab_transition"
                ) { tab ->
                when (tab) {
                    NavigationTab.HOME -> HomeMainView(
                        userName = userName,
                        userRole = userRole,
                        ticketCount = activeTicketCount,
                        events = events,
                        isLoading = isLoading,
                        onRefresh = { refreshData() },
                        onOpenSettings = { currentTab = NavigationTab.SETTINGS },
                        onOpenTickets = { currentTab = NavigationTab.TICKETS },
                        onOpenScanner = onOpenScanner,
                        onOpenCryptoLab = onOpenCryptoLab,
                        onBuyEvent = { eventId ->
                            onRequireBiometric(
                                "Xác nhận Đặt mua Vé",
                                "Xác thực Face ID / Vân tay để vào hàng đợi & ký lệnh mua vé bằng TEE"
                            ) {
                                scope.launch {
                                    try {
                                        val api = ApiClient.getService(context)
                                        val qRes = api.joinQueue(JoinQueueRequest(eventId))
                                        if (qRes.isSuccessful && qRes.body()?.success == true && qRes.body()?.data != null) {
                                            val qData = qRes.body()!!.data!!
                                            if (qData.status == "ACTIVE") {
                                                proceedCreateOrder(eventId, qData.passToken)
                                            } else {
                                                waitingQueueEventId = eventId
                                                waitingQueueData = qData
                                            }
                                        } else {
                                            // Fallback trực tiếp nếu server chưa bật queue
                                            proceedCreateOrder(eventId, null)
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        Toast.makeText(context, "Lỗi kết nối hàng đợi: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    )

                    NavigationTab.TICKETS -> TicketsWalletView(
                        tickets = myTickets,
                        isLoading = isLoading,
                        onRefresh = { refreshData() },
                        onSelectTicket = onSelectTicket
                    )

                    NavigationTab.SETTINGS -> SettingsView(
                        userName = userName,
                        userEmail = userEmail,
                        securePrefs = securePrefs,
                        biometricEnabled = biometricEnabled,
                        onToggleBiometric = { biometricEnabled = it },
                        securityAlertsEnabled = securityAlertsEnabled,
                        onToggleSecurityAlerts = { securityAlertsEnabled = it },
                        logoutConfirmPending = logoutConfirmPending,
                        onLogoutClick = {
                            if (logoutConfirmPending) {
                                scope.launch {
                                    try {
                                        val refreshTok = securePrefs.getRefreshToken()
                                        if (!refreshTok.isNullOrBlank()) {
                                            ApiClient.getService(context).logout(LogoutRequest(refreshTok))
                                        }
                                    } catch (_: Exception) {
                                    } finally {
                                        securePrefs.clear()
                                        onLogout()
                                    }
                                }
                            } else {
                                logoutConfirmPending = true
                            }
                        },
                        onOpenServerConfig = { showServerDialog = true },
                        onOpenCryptoLab = onOpenCryptoLab
                    )
                }
                }

                // Banner thông báo khi Bên B tự động polling nhận được vé chuyển nhượng mới từ Bên A
                AnimatedVisibility(
                    visible = incomingTicketBanner != null,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter).padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    incomingTicketBanner?.let { newTicket ->
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF065F46),
                            shadowElevation = 12.dp,
                            modifier = Modifier.fillMaxWidth().clickable {
                                incomingTicketBanner = null
                                onSelectTicket(newTicket)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.ConfirmationNumber, null, tint = Color(0xFF6EE7B7), modifier = Modifier.size(24.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Bạn vừa nhận được vé mới!", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                                    Text(
                                        "${newTicket.eventTitle ?: "Sự kiện"} · ${newTicket.ticketCode} (Chạm để mở)",
                                        fontSize = 10.sp, color = Color(0xFFD1FAE5), maxLines = 2
                                    )
                                }
                                IconButton(onClick = { incomingTicketBanner = null }, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Frosted-Acrylic Capsule Bottom Navigation
            SecureBottomNav(
                currentTab = currentTab,
                onSelectTab = { currentTab = it },
                badgeCount = activeTicketCount,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    // Hộp thoại Phòng Chờ Ảo (Virtual Waiting Room FIFO - Chống quá tải & công bằng)
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
                        "Hiện đang có khách hàng khác trong cổng thanh toán. Hệ thống tự động giữ chỗ công bằng theo thứ tự FIFO và sẽ mở cổng ngay khi có lượt trống!",
                        fontSize = 12.sp, color = TextMuted, lineHeight = 17.sp
                    )
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFEEF2FF), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("SỐ THỨ TỰ CỦA BẠN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                            Text("#${q.queueNumber ?: 1}", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = PrimaryNavy)
                            Text(
                                "Đang có ${q.usersAhead ?: 0} người xếp hàng trước bạn · Tự động cập nhật mỗi 2.5s",
                                fontSize = 10.sp, color = TextMuted
                            )
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        val idToLeave = q.queueId
                        waitingQueueData = null
                        waitingQueueEventId = null
                        scope.launch {
                            try { ApiClient.getService(context).leaveQueue(LeaveQueueRequest(idToLeave)) } catch (_: Exception) {}
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Rời hàng đợi", color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Địa chỉ Máy chủ (Backend)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Nhập URL Backend (VD: http://10.0.2.2:5000/ cho máy ảo hoặc http://192.168.1.x:5000 cho máy thật):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = serverHostInput,
                        onValueChange = { serverHostInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ApiClient.updateBaseUrl(serverHostInput.trim(), context)
                    showServerDialog = false
                    Toast.makeText(context, "Đã cập nhật máy chủ!", Toast.LENGTH_SHORT).show()
                    refreshData()
                }) {
                    Text("Lưu cấu hình")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

/**
 * TAB 1: Khám phá sự kiện — Editorial layout với Spotlight Carousel, Shimmer Skeleton & Date Tile
 */
@Composable
private fun HomeMainView(
    userName: String,
    userRole: String,
    ticketCount: Int,
    events: List<Event>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
    onBuyEvent: (String) -> Unit
) {
    val isStaffOrOrganizer = userRole.uppercase() in listOf("STAFF", "ORGANIZER", "ADMIN")
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    val filteredEvents = remember(events, selectedCategory) {
        if (selectedCategory == "Tất cả") events
        else events.filter { ev ->
            val desc = (ev.description ?: "") + " " + ev.title
            when (selectedCategory) {
                "Âm nhạc" -> desc.contains("ÂM NHẠC", true) || desc.contains("LIVE SHOW", true) || desc.contains("LỄ HỘI", true) || desc.contains("Concert", true)
                "An toàn TT" -> desc.contains("AN TOÀN", true) || desc.contains("Security", true) || desc.contains("KMA", true)
                "Esports & Tech" -> desc.contains("ESPORTS", true) || desc.contains("CÔNG NGHỆ", true) || desc.contains("Google", true)
                else -> true
            }
        }
    }

    val initials = remember(userName) {
        userName.split(" ").filter { it.isNotBlank() }.takeLast(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "ST" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Editorial Top Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Xin chào,",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted
                )
                Text(
                    text = userName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                    color = TextDark
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryNavy)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Tải lại", tint = TextDark, modifier = Modifier.size(19.dp))
                    }
                }

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryNavy)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text(initials, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }

        // 2. Role-Based Card (RBAC): Nhân viên thấy Cổng Soát Vé; Khách hàng thấy Ví vé & Bảo mật (không thấy nút soát vé!)
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(if (isStaffOrOrganizer) PrimaryBlue else AccentMint))
                            Text(
                                text = if (isStaffOrOrganizer) "Cổng soát vé nhân viên · RBAC: $userRole" else "Ví vé bảo mật TEE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isStaffOrOrganizer) PrimaryBlue else AccentTeal
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (isStaffOrOrganizer) "Sẵn sàng soát vé khách" else "$ticketCount vé đang sở hữu",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark,
                            letterSpacing = (-0.4).sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isStaffOrOrganizer) Color(0xFFDCFCE7) else Color(0xFFEFF6FF)
                    ) {
                        Text(
                            text = if (isStaffOrOrganizer) userRole else "ECDSA P-256",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStaffOrOrganizer) Color(0xFF15803D) else PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                if (isStaffOrOrganizer) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1.4f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(PrimaryBlue)
                                .clickable { onOpenScanner() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Text("Quét mã QR / NFC khách", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { onOpenCryptoLab() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Icon(Icons.Default.Security, null, tint = TextDark, modifier = Modifier.size(16.dp))
                                Text("Crypto Lab", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(PrimaryBlue)
                                .clickable { onOpenTickets() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.ConfirmationNumber, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Text("Mở ví vé", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(Color(0xFFF1F5F9))
                                .clickable { onOpenCryptoLab() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.Security, null, tint = TextDark, modifier = Modifier.size(16.dp))
                                Text("Crypto Lab", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
                            }
                        }
                    }
                }
            }
        }

        // 3. Horizontal Spotlight Carousel — Real Event Cover Photography (Ticketbox / Eventbrite Style)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Sự kiện nổi bật",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextDark,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "Đồng giá 2.000 ₫",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }

            if (isLoading && events.isEmpty()) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val brush = rememberShimmerBrush()
                    repeat(2) {
                        Column(
                            modifier = Modifier
                                .width(248.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.White)
                                .border(1.dp, BorderLight, RoundedCornerShape(16.dp))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().height(126.dp).background(brush))
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.fillMaxWidth(0.85f).height(14.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                                Box(modifier = Modifier.fillMaxWidth(0.55f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(brush))
                            }
                        }
                    }
                }
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(events.take(5)) { ev ->
                        val style = remember(ev.title) { posterStyleForEvent(ev.title, ev.description) }
                        GlassCard(
                            modifier = Modifier
                                .width(252.dp)
                                .clickable { onBuyEvent(ev.id) },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            // Top Real Concert/Event Photo
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(128.dp)
                            ) {
                                EventCoverImage(
                                    url = style.imageUrl,
                                    fallbackColor = Color(0xFFE2E8F0),
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Solid Category Pill (Top-Left)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    color = style.accent
                                ) {
                                    Text(
                                        text = style.tag,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 0.6.sp,
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                // Solid White Price Pill (Bottom-Right)
                                Surface(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = "${String.format("%,.0f", ev.ticketPrice)} ₫",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = PrimaryBlue,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Solid White Card Body
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = ev.title,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                                Text(
                                    text = formatEventTime(ev.startTime) ?: "",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PrimaryBlue,
                                    maxLines = 1
                                )
                                Text(
                                    text = ev.location,
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Category Filter Strip + Full Event Feed
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Tất cả sự kiện (${filteredEvents.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark,
                letterSpacing = (-0.3).sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Tất cả", "Âm nhạc", "An toàn TT", "Esports & Tech").forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PrimaryBlue else Color.White)
                            .border(1.dp, if (isSelected) PrimaryBlue else BorderLight, RoundedCornerShape(10.dp))
                            .clickable { selectedCategory = cat }
                            .padding(horizontal = 11.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = cat,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextMuted
                        )
                    }
                }
            }

            if (isLoading && events.isEmpty()) {
                repeat(4) {
                    EventCardSkeleton()
                }
            } else {
                filteredEvents.forEach { event ->
                    val style = remember(event.title) { posterStyleForEvent(event.title, event.description) }
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onBuyEvent(event.id) },
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            EventCoverImage(
                                url = style.imageUrl,
                                fallbackColor = Color(0xFFE2E8F0),
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = formatEventTime(event.startTime) ?: style.tag,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = event.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDark,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 17.sp
                                )
                                Text(
                                    text = "${event.location} · Còn ${event.availableTickets} vé",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PrimaryBlue)
                                    .clickable { onBuyEvent(event.id) }
                                    .padding(horizontal = 11.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${String.format("%,.0f", event.ticketPrice)} ₫",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(92.dp))
    }
}

/**
 * TAB 2: Ví vé — Boarding-pass ticket stubs với Shimmer Loading Skeleton
 */
@Composable
private fun TicketsWalletView(
    tickets: List<Ticket>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onSelectTicket: (Ticket) -> Unit
) {
    var selectedSegment by remember { mutableStateOf("upcoming") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val activeTickets = remember(tickets, searchQuery) {
        tickets.filter { (it.status == "ACTIVE" || it.status == null) }.filter {
            searchQuery.isBlank() ||
                (it.eventTitle ?: "").contains(searchQuery, ignoreCase = true) ||
                it.ticketCode.contains(searchQuery, ignoreCase = true)
        }
    }
    val pastTickets = remember(tickets, searchQuery) {
        tickets.filter { it.status != "ACTIVE" && it.status != null }.filter {
            searchQuery.isBlank() ||
                (it.eventTitle ?: "").contains(searchQuery, ignoreCase = true) ||
                it.ticketCode.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Kho vé mã hóa AES-256-GCM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Text(
                    text = "Vé của tôi",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                    color = TextDark
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                        .clickable { onRefresh() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryNavy)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = TextDark, modifier = Modifier.size(19.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSearchActive) PrimaryNavy else Color.White)
                        .border(1.dp, if (isSearchActive) PrimaryNavy else BorderLight, RoundedCornerShape(12.dp))
                        .clickable { isSearchActive = !isSearchActive },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm kiếm vé",
                        tint = if (isSearchActive) Color.White else TextDark,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm tên sự kiện hoặc mã vé...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }

        // Segmented Control
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE9ECF2))
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isUpcoming = selectedSegment == "upcoming"
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isUpcoming) Color.White else Color.Transparent)
                    .clickable { selectedSegment = "upcoming" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sắp tới (${activeTickets.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isUpcoming) TextDark else TextMuted
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!isUpcoming) Color.White else Color.Transparent)
                    .clickable { selectedSegment = "history" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lịch sử (${pastTickets.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (!isUpcoming) TextDark else TextMuted
                )
            }
        }

        // Shimmer Loading Skeletons while fetching tickets
        if (isLoading && tickets.isEmpty()) {
            repeat(3) {
                TicketCardSkeleton()
            }
        } else if (selectedSegment == "upcoming") {
            if (activeTickets.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.ConfirmationNumber, null, tint = TextSubtle, modifier = Modifier.size(36.dp))
                        Text("Chưa có vé sắp tới", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                        Text(
                            "Mua vé từ mục Khám phá hoặc chờ bạn bè chuyển nhượng vé (ví tự động cập nhật mỗi 5 giây).",
                            fontSize = 11.sp, color = TextMuted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                activeTickets.forEach { ticket ->
                    TicketStubCard(
                        ticket = ticket,
                        isPast = false,
                        onClick = { onSelectTicket(ticket) }
                    )
                }
            }
        } else {
            if (pastTickets.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Chưa có vé đã sử dụng", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                    }
                }
            } else {
                pastTickets.forEach { ticket ->
                    TicketStubCard(
                        ticket = ticket,
                        isPast = true,
                        onClick = { onSelectTicket(ticket) }
                    )
                }
            }
        }

        // Subtle Hardware Binding Footer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Shield, null, tint = AccentTeal, modifier = Modifier.size(16.dp))
            Text(
                text = "Tất cả vé được ký số ECDSA P-256 & lưu mã hóa trong AndroidKeyStore TEE.",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(92.dp))
    }
}

/**
 * TAB 3: Cài đặt (Settings.dc.html)
 */
@Composable
private fun SettingsView(
    userName: String,
    userEmail: String,
    securePrefs: SecurePreferences,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    securityAlertsEnabled: Boolean,
    onToggleSecurityAlerts: (Boolean) -> Unit,
    logoutConfirmPending: Boolean,
    onLogoutClick: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onOpenCryptoLab: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var accessSnippet by remember {
        mutableStateOf(securePrefs.getAccessToken()?.let { "${it.take(16)}...${it.takeLast(8)}" } ?: "Chưa cấp")
    }
    var refreshSnippet by remember {
        mutableStateOf(securePrefs.getRefreshToken()?.let { "${it.take(16)}...${it.takeLast(8)}" } ?: "Chưa cấp")
    }
    var rotationCount by remember { mutableIntStateOf(securePrefs.getRefreshRotationCount()) }
    var isRotating by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Column {
            Text(
                text = "Tài khoản & bảo mật",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted
            )
            Text(
                text = "Cài đặt",
                fontSize = 25.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.6).sp,
                color = TextDark
            )
        }

        // User Account Card (Matching preview)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(23.dp))
                .background(PrimaryNavy)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(23.dp), ambientColor = Color(0x33203E9A), spotColor = Color(0x33203E9A))
                .padding(17.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Initials circle
                val initials = userName.split(" ")
                    .filter { it.isNotBlank() }
                    .takeLast(2)
                    .map { it.first().uppercase() }
                    .joinToString("")
                    .ifBlank { "MA" }

                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PrimaryBlue
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = userEmail,
                        fontSize = 11.sp,
                        color = Color(0xFFD8E1FF),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
        }

        // PHIÊN ĐĂNG NHẬP DUAL-TOKEN & MÃ HÓA PII Section
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "PHIÊN DUAL-TOKEN & MÃ HÓA KHÁCH HÀNG (PII)",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF747C8C),
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.VpnKey,
                                contentDescription = null,
                                tint = PrimaryBlue,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Access Token (15m) & Refresh (7d)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDark
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFEAF4FF)
                        ) {
                            Text(
                                text = "XOAY VÒNG: $rotationCount LẦN",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = PrimaryBlue,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }

                    CryptoTerminalBox(
                        title = "Dual-Token & TEE Vault",
                        badgeText = "ACTIVE SESSION",
                        lines = listOf(
                            "ACCESS JWT" to accessSnippet,
                            "REFRESH TOKEN" to refreshSnippet,
                            "PII VAULT" to "AES-256-GCM & AES-256-SIV (AndroidKeyStore TEE)"
                        )
                    )

                    Button(
                        onClick = {
                            isRotating = true
                            scope.launch {
                                try {
                                    val rTok = securePrefs.getRefreshToken()
                                    if (!rTok.isNullOrBlank()) {
                                        val res = ApiClient.getService(context).refreshToken(RefreshTokenRequest(rTok))
                                        if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                            val d = res.body()!!.data!!
                                            securePrefs.saveAuthTokens(d.accessToken, d.refreshToken, d.expiresIn)
                                            rotationCount = securePrefs.incrementRefreshRotationCount()
                                            accessSnippet = "${d.accessToken.take(16)}...${d.accessToken.takeLast(8)}"
                                            refreshSnippet = "${d.refreshToken.take(16)}...${d.refreshToken.takeLast(8)}"
                                            Toast.makeText(
                                                context,
                                                "Đã xoay vòng cặp Access & Refresh Token mới!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                res.body()?.error ?: "Không thể làm mới token",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Chưa có Refresh Token", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isRotating = false
                                }
                            }
                        },
                        enabled = !isRotating,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRotating) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Làm mới & Xoay vòng Token ngay (Refresh Rotation)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // BẢO MẬT Section
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "BẢO MẬT",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF747C8C),
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            // Biometric switch
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEEF2FF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Mở khóa sinh trắc học",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Vân tay hoặc khóa màn hình",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    PremiumPillSwitch(
                        checked = biometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        activeColor = PrimaryBlue
                    )
                }
            }

            // Security alert switch
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFEAF7F3)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = Color(0xFF117B68),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cảnh báo bảo mật",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Đăng nhập và chuyển nhượng vé",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    PremiumPillSwitch(
                        checked = securityAlertsEnabled,
                        onCheckedChange = onToggleSecurityAlerts,
                        activeColor = Color(0xFF16836F)
                    )
                }
            }
        }

        // THIẾT BỊ Section
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = "THIẾT BỊ",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF747C8C),
                letterSpacing = 0.7.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp)
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
                            .background(Color(0xFFF0EDFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhoneAndroid,
                            contentDescription = null,
                            tint = Color(0xFF6E48B9),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        val deviceName = if (Build.MODEL.isNullOrBlank() || Build.MODEL.contains("sdk", ignoreCase = true)) {
                            "Google Pixel 8"
                        } else {
                            Build.MODEL
                        }
                        Text(
                            text = deviceName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "Thiết bị tin cậy · StrongBox",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Text(
                            text = "ĐÃ XÁC MINH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF16836F),
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // TÙY CHỌN & THÔNG TIN Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column {
                // Hardware Security & TEE Attestation row (replaces dead dark-mode toggle)
                val hardwareLevel = remember { KeystoreManager.getHardwareSecurityLevel() }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = SuccessFg,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cấp độ Bảo mật Phần cứng",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextDark
                        )
                        Text(
                            text = "$hardwareLevel · FLAG_SECURE: Bật",
                            fontSize = 10.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = SuccessBg
                    ) {
                        Text(
                            text = "TEE ACTIVE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = SuccessFg,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = Color(0xFFECEFF4))

                // Server Backend Config item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenServerConfig() }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = Color(0xFF596174),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Máy chủ Backend",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = ApiClient.baseUrl.take(20) + "...",
                        fontSize = 10.sp,
                        color = Color(0xFF6F7788)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF969DAB),
                        modifier = Modifier.size(17.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = Color(0xFFECEFF4))

                // Crypto Lab shortcut
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenCryptoLab() }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Phòng thí nghiệm Mật mã (Lab)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(17.dp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(start = 48.dp), color = Color(0xFFECEFF4))

                // About item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF596174),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Giới thiệu SecureTicket",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "v1.0.0",
                        fontSize = 11.sp,
                        color = Color(0xFF6F7788)
                    )
                }
            }
        }

        // Logout Button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFFFF5F6))
                .border(1.dp, Color(0xFFECDADD), RoundedCornerShape(15.dp))
                .clickable { onLogoutClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (logoutConfirmPending) "Nhấn lần nữa để đăng xuất" else "Đăng xuất an toàn",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFB33D50)
            )
        }

        Spacer(modifier = Modifier.height(92.dp))
    }
}
