package com.example.quan_ly_ticket.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.example.quan_ly_ticket.data.model.CancelOrderRequest
import com.example.quan_ly_ticket.data.model.CreateOrderRequest
import com.example.quan_ly_ticket.data.model.Event
import com.example.quan_ly_ticket.data.model.JoinQueueRequest
import com.example.quan_ly_ticket.data.model.LeaveQueueRequest
import com.example.quan_ly_ticket.data.model.LogoutRequest
import com.example.quan_ly_ticket.data.model.OrderHistoryItem
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
    currentTab: NavigationTab = NavigationTab.HOME,
    onSelectTab: (NavigationTab) -> Unit = {},
    onSelectEvent: (event: Event) -> Unit = {},
    onSelectTicket: (ticket: Ticket) -> Unit,
    onProceedToPayment: (order: CreateOrderData) -> Unit,
    onOpenScanner: () -> Unit,
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

    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var myTickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var incomingTicketBanner by remember { mutableStateOf<Ticket?>(null) }
    var waitingQueueEventId by remember { mutableStateOf<String?>(null) }
    var waitingQueueData by remember { mutableStateOf<QueueData?>(null) }
    var hasLoadedInitialTickets by remember { mutableStateOf(false) }
    var showOrderHistorySheet by remember { mutableStateOf(false) }
    var orderHistory by remember { mutableStateOf<List<OrderHistoryItem>>(emptyList()) }
    var isLoadingOrders by remember { mutableStateOf(false) }

    fun loadOrderHistory() {
        isLoadingOrders = true
        scope.launch {
            try {
                val res = ApiClient.getService(context).getMyOrders()
                if (res.isSuccessful && res.body()?.success == true) {
                    orderHistory = res.body()!!.data ?: emptyList()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
            } finally {
                isLoadingOrders = false
            }
        }
    }

    // Settings state
    var biometricEnabled by remember { mutableStateOf(securePrefs.isBiometricEnabled()) }
    var securityAlertsEnabled by remember { mutableStateOf(true) }
    var logoutConfirmPending by remember { mutableStateOf(false) }

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
                    withContext(Dispatchers.IO) {
                        ticketVault.saveTicketsEncrypted(list)
                    }
                } else {
                    // Fallback to vault on IO thread
                    val cached = withContext(Dispatchers.IO) { ticketVault.loadTicketsDecrypted() }
                    if (cached.isNotEmpty()) {
                        myTickets = cached
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val cached = withContext(Dispatchers.IO) { ticketVault.loadTicketsDecrypted() }
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
                        withContext(Dispatchers.IO) {
                            ticketVault.saveTicketsEncrypted(freshList)
                        }
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
                        onOpenSettings = { onSelectTab(NavigationTab.SETTINGS) },
                        onOpenTickets = { onSelectTab(NavigationTab.TICKETS) },
                        onOpenOrders = {
                            loadOrderHistory()
                            showOrderHistorySheet = true
                        },
                        onOpenScanner = onOpenScanner,
                        onSelectEvent = onSelectEvent
                    )

                    NavigationTab.TICKETS -> TicketsWalletView(
                        tickets = myTickets,
                        isLoading = isLoading,
                        onRefresh = { refreshData() },
                        onOpenOrders = {
                            loadOrderHistory()
                            showOrderHistorySheet = true
                        },
                        onSelectTicket = { ticket ->
                            onRequireBiometric(
                                "Mở vé điện tử",
                                "Xác thực sinh trắc học để xem mã QR và check-in"
                            ) {
                                onSelectTicket(ticket)
                            }
                        }
                    )

                    NavigationTab.SETTINGS -> SettingsView(
                        userName = userName,
                        userEmail = userEmail,
                        securePrefs = securePrefs,
                        biometricEnabled = biometricEnabled,
                        onToggleBiometric = { enabled ->
                            if (!enabled) {
                                onRequireBiometric(
                                    "Tắt xác thực sinh trắc học",
                                    "Xác nhận danh tính để tắt bảo vệ sinh trắc học"
                                ) {
                                    biometricEnabled = false
                                    securePrefs.setBiometricEnabled(false)
                                }
                            } else {
                                biometricEnabled = true
                                securePrefs.setBiometricEnabled(true)
                            }
                        },
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
                        }
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
                onSelectTab = onSelectTab,
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

    if (showOrderHistorySheet) {
        @OptIn(ExperimentalMaterial3Api::class)
        ModalBottomSheet(
            onDismissRequest = { showOrderHistorySheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Đơn hàng & Biên lai Mật mã", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                    if (isLoadingOrders) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }

                if (orderHistory.isEmpty() && !isLoadingOrders) {
                    Text("Chưa có đơn hàng nào.", fontSize = 12.sp, color = TextMuted, modifier = Modifier.padding(vertical = 12.dp))
                } else {
                    orderHistory.forEach { item ->
                        val isPending = item.status == "PENDING" && item.ttlSeconds > 0
                        val isPaid = item.status == "PAID"
                        GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.eventTitle,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when {
                                            isPaid -> SuccessBg
                                            isPending -> WarningBg
                                            else -> Color(0xFFF1F5F9)
                                        }
                                    ) {
                                        Text(
                                            text = item.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = when {
                                                isPaid -> SuccessFg
                                                isPending -> WarningFg
                                                else -> TextMuted
                                            },
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Đơn #${item.orderCode} · ${item.seatZone ?: "PREM-S"} ${item.seatNumber ?: ""} · ${String.format("%,.0f", item.amount)} ₫",
                                    fontSize = 11.sp,
                                    color = TextMuted
                                )
                                if (isPaid && !item.serverSignatureSnippet.isNullOrBlank()) {
                                    Text(
                                        text = "Vé ${item.ticketCode ?: ""} · ECDSA: ${item.serverSignatureSnippet}",
                                        fontSize = 10.sp,
                                        color = SuccessFg,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                if (isPending) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                showOrderHistorySheet = false
                                                onProceedToPayment(
                                                    CreateOrderData(
                                                        orderId = item.orderId,
                                                        orderCode = item.orderCode,
                                                        eventId = item.eventId,
                                                        eventTitle = item.eventTitle,
                                                        amount = item.amount,
                                                        seatZone = item.seatZone,
                                                        seatNumber = item.seatNumber,
                                                        bankId = "MB",
                                                        bankAccountNo = "0696961698888",
                                                        accountName = "MB BANK",
                                                        transferContent = item.transferContent,
                                                        qrImageUrl = item.qrImageUrl ?: "",
                                                        qrPayload = item.qrPayload,
                                                        expiresAt = item.expiresAt,
                                                        ttlSeconds = item.ttlSeconds
                                                    )
                                                )
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Tiếp tục thanh toán (${item.ttlSeconds}s)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                scope.launch {
                                                    try {
                                                        ApiClient.getService(context).cancelOrder(CancelOrderRequest(item.orderId))
                                                        loadOrderHistory()
                                                    } catch (_: Exception) {}
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text("Hủy", fontSize = 11.sp, color = DangerFg, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * TAB 1: Khám phá sự kiện — Editorial layout với Spotlight Carousel, Shimmer Skeleton & Date Tile
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onOpenOrders: () -> Unit,
    onOpenScanner: () -> Unit,
    onSelectEvent: (Event) -> Unit
) {
    val isStaffOrOrganizer = userRole.uppercase() in listOf("STAFF", "ORGANIZER", "ADMIN")
    var selectedCategory by remember { mutableStateOf("Tất cả") }
    var searchKeyword by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf("Sắp diễn ra") }
    val filteredEvents = remember(events, selectedCategory, searchKeyword, sortMode) {
        val byCat = if (selectedCategory == "Tất cả") events
        else events.filter { ev ->
            val desc = (ev.description ?: "") + " " + ev.title
            when (selectedCategory) {
                "Âm nhạc" -> desc.contains("ÂM NHẠC", true) || desc.contains("LIVE SHOW", true) || desc.contains("LỄ HỘI", true) || desc.contains("Concert", true)
                "An toàn TT" -> desc.contains("AN TOÀN", true) || desc.contains("Security", true) || desc.contains("KMA", true)
                "Esports & Tech" -> desc.contains("ESPORTS", true) || desc.contains("CÔNG NGHỆ", true) || desc.contains("Google", true)
                else -> true
            }
        }
        val bySearch = if (searchKeyword.isBlank()) byCat else byCat.filter { ev ->
            ev.title.contains(searchKeyword, ignoreCase = true) ||
                ev.location.contains(searchKeyword, ignoreCase = true)
        }
        when (sortMode) {
            "Giá thấp → cao" -> bySearch.sortedBy { it.ticketPrice }
            "Còn nhiều vé" -> bySearch.sortedByDescending { it.availableTickets }
            else -> bySearch.sortedBy { it.startTime }
        }
    }

    val initials = remember(userName) {
        userName.split(" ").filter { it.isNotBlank() }.takeLast(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "ST" }
    }

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
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
                                text = if (isStaffOrOrganizer) "Cổng soát vé nhân viên" else "Ví vé điện tử",
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
                            text = if (isStaffOrOrganizer) "NHÂN VIÊN" else "CHÍNH CHỦ",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isStaffOrOrganizer) Color(0xFF15803D) else PrimaryBlue,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }

                if (isStaffOrOrganizer) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
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
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                .background(InfoBg)
                                .border(1.dp, InfoBorder, RoundedCornerShape(11.dp))
                                .clickable { onOpenOrders() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Đơn hàng & Biên lai", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
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
                                .clickable { onSelectEvent(ev) },
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

        // 4. Category Filter Strip + Search + Sort + Full Event Feed
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

            OutlinedTextField(
                value = searchKeyword,
                onValueChange = { searchKeyword = it },
                placeholder = { Text("Tìm sự kiện, địa điểm...", fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf("Sắp diễn ra", "Giá thấp → cao", "Còn nhiều vé").forEach { mode ->
                    val active = sortMode == mode
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) InfoBg else Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) PrimaryBlue else BorderLight),
                        onClick = { sortMode = mode }
                    ) {
                        Text(
                            text = mode,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (active) PrimaryBlue else TextMuted,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                        )
                    }
                }
            }

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
                            .clickable { onSelectEvent(event) },
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
                                val formattedEventDate = remember(event.startTime, style.tag) {
                                    formatEventTime(event.startTime) ?: style.tag
                                }
                                Text(
                                    text = formattedEventDate,
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
                                    .clickable { onSelectEvent(event) }
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
}

/**
 * TAB 2: Ví vé — Boarding-pass ticket stubs với Shimmer Loading Skeleton
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TicketsWalletView(
    tickets: List<Ticket>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenOrders: () -> Unit,
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

    PullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
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
                    text = "Vé của bạn",
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = InfoBg,
                    border = androidx.compose.foundation.BorderStroke(1.dp, InfoBorder),
                    onClick = onOpenOrders
                ) {
                    Text(
                        text = "Đơn hàng",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                    )
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
        val isUpcoming = selectedSegment == "upcoming"
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFE9ECF2))
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / 2
            val indicatorOffset by animateDpAsState(
                targetValue = if (isUpcoming) 0.dp else tabWidth,
                animationSpec = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "wallet_segment_slide"
            )

            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(tabWidth)
                    .height(38.dp)
                    .shadow(2.dp, RoundedCornerShape(10.dp), ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White)
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                val upcomingTint by animateColorAsState(
                    targetValue = if (isUpcoming) TextDark else TextMuted,
                    animationSpec = tween(180),
                    label = "upcoming_tint"
                )
                val historyTint by animateColorAsState(
                    targetValue = if (!isUpcoming) TextDark else TextMuted,
                    animationSpec = tween(180),
                    label = "history_tint"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedSegment = "upcoming" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sắp tới (${activeTickets.size})",
                        fontSize = 12.sp,
                        fontWeight = if (isUpcoming) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = upcomingTint
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { selectedSegment = "history" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Lịch sử (${pastTickets.size})",
                        fontSize = 12.sp,
                        fontWeight = if (!isUpcoming) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = historyTint
                    )
                }
            }
        }

        // Shimmer Loading Skeletons while fetching tickets
        if (isLoading && tickets.isEmpty()) {
            repeat(3) {
                TicketCardSkeleton()
            }
        } else {
            AnimatedContent(
                targetState = selectedSegment,
                transitionSpec = {
                    val direction = if (targetState == "history") 1 else -1
                    (fadeIn(tween(200, easing = FastOutSlowInEasing)) +
                        slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) {
                            direction * (it / 14)
                        }) togetherWith
                        fadeOut(tween(140, easing = FastOutSlowInEasing))
                },
                label = "wallet_segment_content"
            ) { seg ->
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (seg == "upcoming") {
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
                text = "Vé của bạn được bảo vệ và xác thực tự động trên thiết bị này.",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = TextMuted
            )
        }

        Spacer(modifier = Modifier.height(92.dp))
    }
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
    onLogoutClick: () -> Unit
) {
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

        // TÙY CHỌN & THÔNG TIN Section
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column {
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
