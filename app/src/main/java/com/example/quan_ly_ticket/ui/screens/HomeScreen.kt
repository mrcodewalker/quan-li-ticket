package com.example.quan_ly_ticket.ui.screens

import android.os.Build
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.CreateOrderRequest
import com.example.quan_ly_ticket.data.model.Event
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.SecurityCheck
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.components.NavigationTab
import com.example.quan_ly_ticket.ui.components.PremiumPillSwitch
import com.example.quan_ly_ticket.ui.components.SecureBottomNav
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onSelectTicket: (ticket: Ticket) -> Unit,
    onProceedToPayment: (order: CreateOrderData) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
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
    var showAllEvents by remember { mutableStateOf(false) }

    // Settings state
    var biometricEnabled by remember { mutableStateOf(true) }
    var securityAlertsEnabled by remember { mutableStateOf(true) }
    var isDarkMode by remember { mutableStateOf(false) }
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
                val cached = ticketVault.loadTicketsDecrypted()
                if (cached.isNotEmpty()) {
                    myTickets = cached
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                SecureBottomNav(
                    currentTab = currentTab,
                    onSelectTab = { currentTab = it }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (currentTab) {
                    NavigationTab.HOME -> HomeMainView(
                        userName = userName,
                        ticketCount = myTickets.count { it.status == "ACTIVE" || it.status == null },
                        events = events,
                        showAllEvents = showAllEvents,
                        onToggleShowAll = { showAllEvents = !showAllEvents },
                        onOpenSettings = { currentTab = NavigationTab.SETTINGS },
                        onOpenTickets = { currentTab = NavigationTab.TICKETS },
                        onOpenScanner = onOpenScanner,
                        onSelectFeaturedEvent = {
                            val activeTicket = myTickets.firstOrNull { it.status == "ACTIVE" || it.status == null }
                            if (activeTicket != null) {
                                onSelectTicket(activeTicket)
                            } else {
                                val feat = events.firstOrNull()
                                if (feat != null) {
                                    scope.launch {
                                        try {
                                            val api = ApiClient.getService(context)
                                            val res = api.createPaymentOrder(CreateOrderRequest(feat.id))
                                            if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                                onProceedToPayment(res.body()!!.data!!)
                                            } else {
                                                Toast.makeText(context, res.body()?.error ?: "Lỗi tạo đơn đặt vé", Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    // Default preview ticket
                                    onSelectTicket(
                                        Ticket(
                                            id = "TKT-PREMIUM-001",
                                            ticketCode = "SEC-P12-2026",
                                            eventTitle = "SecureFest Vietnam 2026",
                                            eventLocation = "SECC · Hall A",
                                            eventStartTime = "14.11.2026 · 18:30",
                                            status = "ACTIVE"
                                        )
                                    )
                                }
                            }
                        },
                        onBuyEvent = { eventId ->
                            scope.launch {
                                try {
                                    val api = ApiClient.getService(context)
                                    val res = api.createPaymentOrder(CreateOrderRequest(eventId))
                                    if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                        onProceedToPayment(res.body()!!.data!!)
                                    } else {
                                        Toast.makeText(context, res.body()?.error ?: "Lỗi tạo đơn đặt vé", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )

                    NavigationTab.TICKETS -> TicketsWalletView(
                        tickets = myTickets,
                        onSelectTicket = onSelectTicket
                    )

                    NavigationTab.SETTINGS -> SettingsView(
                        userName = userName,
                        userEmail = userEmail,
                        biometricEnabled = biometricEnabled,
                        onToggleBiometric = { biometricEnabled = it },
                        securityAlertsEnabled = securityAlertsEnabled,
                        onToggleSecurityAlerts = { securityAlertsEnabled = it },
                        isDarkMode = isDarkMode,
                        onToggleDarkMode = { isDarkMode = it },
                        logoutConfirmPending = logoutConfirmPending,
                        onLogoutClick = {
                            if (logoutConfirmPending) {
                                securePrefs.clear()
                                onLogout()
                            } else {
                                logoutConfirmPending = true
                            }
                        },
                        onOpenServerConfig = { showServerDialog = true },
                        onOpenCryptoLab = onOpenCryptoLab
                    )
                }
            }
        }
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
 * TAB 1: Trang chủ (Main.dc.html)
 */
@Composable
private fun HomeMainView(
    userName: String,
    ticketCount: Int,
    events: List<Event>,
    showAllEvents: Boolean,
    onToggleShowAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenTickets: () -> Unit,
    onOpenScanner: () -> Unit,
    onSelectFeaturedEvent: () -> Unit,
    onBuyEvent: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status bar space filler
        Spacer(modifier = Modifier.height(6.dp))

        // Header: Greeting + Avatar Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Xin chào,",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Text(
                    text = userName,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    color = TextDark
                )
            }

            // Avatar / Settings button
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.85f))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                    .clickable { onOpenSettings() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Cài đặt",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Section: VÍ VÉ AN TOÀN Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(25.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(PrimaryNavy, Color(0xFF2644A8))
                    )
                )
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(25.dp), ambientColor = Color(0x3D203E9A), spotColor = Color(0x3D203E9A))
                .padding(20.dp)
        ) {
            // Background decorative circles matching preview
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .offset(x = 180.dp, y = (-78).dp)
                    .clip(CircleShape)
                    .background(SecondaryBlue.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .offset(x = 130.dp, y = 50.dp)
                    .border(18.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "VÍ VÉ AN TOÀN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD8E1FF),
                            letterSpacing = 0.7.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (ticketCount > 0) "$ticketCount vé" else "2 vé",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "Đã mã hóa trên thiết bị",
                            fontSize = 12.sp,
                            color = Color(0xFFD8E1FF)
                        )
                    }

                    // Ticket icon in glass container
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = "Ticket",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Verification indicator pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AccentMint)
                            .shadow(4.dp, CircleShape, ambientColor = AccentMint, spotColor = AccentMint)
                    )
                    Text(
                        text = "Thiết bị đã được xác thực",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFEEF2FF)
                    )
                }
            }
        }

        // Section: Sự kiện nổi bật Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sự kiện nổi bật",
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextDark
            )
            Text(
                text = if (showAllEvents) "Thu gọn" else "Xem tất cả",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue,
                modifier = Modifier
                    .clickable { onToggleShowAll() }
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )
        }

        // Featured Event Card (matching preview exactly)
        GlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelectFeaturedEvent() },
            shape = RoundedCornerShape(22.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Event graphic banner
                Box(
                    modifier = Modifier
                        .size(width = 99.dp, height = 112.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF0C1938)),
                    contentAlignment = Alignment.Center
                ) {
                    // Overlapping glowing gradient orbs
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .offset(x = 24.dp, y = (-24).dp)
                            .clip(CircleShape)
                            .background(PurpleAccent)
                    )
                    Box(
                        modifier = Modifier
                            .size(73.dp)
                            .offset(x = (-24).dp, y = 24.dp)
                            .clip(CircleShape)
                            .background(AccentTeal)
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(4.dp)
                    ) {
                        Text(
                            text = "SECURE",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = (-0.7).sp
                        )
                        Text(
                            text = "FEST 2026",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB9C7EA),
                            letterSpacing = 2.sp
                        )
                    }
                }

                // Event info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFEAF0FF)
                        ) {
                            Text(
                                text = "CÔNG NGHỆ",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF244AB7),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color(0xFF8A92A3),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "SecureFest Vietnam 2026",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "Th 7, 14 thg 11 · 18:30",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                        Text(
                            text = "SECC · Quận 7, TP.HCM",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextMuted
                        )
                    }
                }
            }
        }

        // Expandable events list
        AnimatedVisibility(
            visible = showAllEvents,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Secondary sample event matching preview
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Android Makers Night",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDark
                            )
                            Text(
                                text = "22 thg 11 · The Sentry",
                                fontSize = 10.sp,
                                color = TextMuted,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        Text(
                            text = "320.000 ₫",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = PrimaryBlue
                        )
                    }
                }

                // Dynamic events from Backend
                events.forEach { event ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextDark
                                )
                                Text(
                                    text = "${event.location} • Còn ${event.availableTickets} vé",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Button(
                                onClick = { onBuyEvent(event.id) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text(
                                    text = "${String.format("%,.0f", event.ticketPrice)} ₫",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Quick Actions Grid (Vé của tôi & Quét vé)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // "Vé của tôi" shortcut
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(69.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(MintBg)
                    .clickable { onOpenTickets() }
                    .padding(13.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ConfirmationNumber,
                            contentDescription = null,
                            tint = MintText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Vé của tôi",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MintText
                    )
                }
            }

            // "Quét vé" shortcut
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(69.dp)
                    .clip(RoundedCornerShape(19.dp))
                    .background(PurpleBg)
                    .clickable { onOpenScanner() }
                    .padding(13.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = PurpleText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Quét vé",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = PurpleText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
    }
}

/**
 * TAB 2: Vé của tôi (Tickets.dc.html)
 */
@Composable
private fun TicketsWalletView(
    tickets: List<Ticket>,
    onSelectTicket: (Ticket) -> Unit
) {
    var selectedSegment by remember { mutableStateOf("upcoming") }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    val activeTickets = remember(tickets) {
        tickets.filter { it.status == "ACTIVE" || it.status == null }
    }
    val pastTickets = remember(tickets) {
        tickets.filter { it.status != "ACTIVE" && it.status != null }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Header: Title + Search button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Ví cá nhân",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextMuted
                )
                Text(
                    text = "Vé của tôi",
                    fontSize = 25.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.6).sp,
                    color = TextDark
                )
            }

            Box(
                modifier = Modifier
                    .size(45.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color.White.copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(15.dp))
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(15.dp))
                    .clickable { isSearchActive = !isSearchActive },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Tìm kiếm vé",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Search Input if active
        AnimatedVisibility(visible = isSearchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Tìm theo tên sự kiện hoặc mã vé...", fontSize = 12.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )
        }

        // Segmented Control Tabs (Sắp tới vs Lịch sử)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0xFFE9ECF3))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isUpcoming = selectedSegment == "upcoming"
            val upcomingCount = if (activeTickets.isNotEmpty()) activeTickets.size else 2
            val pastCount = if (pastTickets.isNotEmpty()) pastTickets.size else 3

            // Upcoming tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isUpcoming) Color.White else Color.Transparent)
                    .clickable { selectedSegment = "upcoming" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Sắp tới · $upcomingCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isUpcoming) Color(0xFF244AB7) else Color(0xFF777F8F)
                )
            }

            // History tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isUpcoming) Color.White else Color.Transparent)
                    .clickable { selectedSegment = "history" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lịch sử · $pastCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (!isUpcoming) Color(0xFF244AB7) else Color(0xFF777F8F)
                )
            }
        }

        // Segment Content
        if (selectedSegment == "upcoming") {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Featured Ticket Card (Matches preview's SecureFest Premium Access card)
                val firstTicket = activeTickets.firstOrNull()
                val featuredTicket = firstTicket ?: Ticket(
                    id = "TKT-PREMIUM-001",
                    ticketCode = "SEC-P12-2026",
                    eventTitle = "SecureFest Vietnam 2026",
                    eventLocation = "SECC · Hall A",
                    eventStartTime = "14.11 · 18:30",
                    status = "ACTIVE"
                )

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTicket(featuredTicket) },
                    shape = RoundedCornerShape(23.dp)
                ) {
                    Column {
                        // Top navy header with decorative shape
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(94.dp)
                                .clip(RoundedCornerShape(topStart = 23.dp, topEnd = 23.dp))
                                .background(PrimaryNavyDark)
                                .padding(17.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(130.dp)
                                    .offset(x = 160.dp, y = (-57).dp)
                                    .clip(CircleShape)
                                    .background(PurpleDark)
                            )

                            Column {
                                Text(
                                    text = (featuredTicket.eventTitle ?: "SECUREFEST 2026").uppercase(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFCBD6FF),
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Vé Premium Access",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }

                            // HỢP LỆ badge
                            Surface(
                                modifier = Modifier.align(Alignment.BottomEnd),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF62D8BA)
                            ) {
                                Text(
                                    text = "HỢP LỆ",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF0D4D41),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }

                        // Bottom info columns
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(15.dp, 17.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "THỜI GIAN",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF858D9D)
                                )
                                Text(
                                    text = featuredTicket.eventStartTime ?: "14.11 · 18:30",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextDark,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "KHU VỰC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF858D9D)
                                )
                                Text(
                                    text = featuredTicket.eventLocation ?: "Premium · P12",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextDark,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Subsequent active tickets or secondary mock card
                if (activeTickets.size > 1) {
                    activeTickets.drop(1).forEach { ticket ->
                        GlassCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectTicket(ticket) },
                            shape = RoundedCornerShape(21.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(15.dp),
                                horizontalArrangement = Arrangement.spacedBy(13.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp, 65.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(Color(0xFFEAF0FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ConfirmationNumber,
                                        contentDescription = null,
                                        tint = PrimaryBlue,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = ticket.eventTitle ?: "Vé sự kiện SecureTicket",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextDark
                                    )
                                    Text(
                                        text = ticket.eventStartTime ?: "Thời gian thông báo sau",
                                        fontSize = 10.sp,
                                        color = TextMuted,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                    Text(
                                        text = ticket.ticketCode.ifBlank { "General Admission" },
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = Color(0xFF9299A8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                } else {
                    // Secondary mockup matching preview
                    val secondTicketMock = Ticket(
                        id = "TKT-PREVIEW-002",
                        ticketCode = "GA-ADM-092",
                        eventTitle = "Android Makers Night",
                        eventLocation = "The Sentry",
                        eventStartTime = "22 thg 11 · 19:00",
                        status = "ACTIVE"
                    )
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTicket(secondTicketMock) },
                        shape = RoundedCornerShape(21.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(15.dp),
                            horizontalArrangement = Arrangement.spacedBy(13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(58.dp, 65.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(Color(0xFFEAF0FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Android Makers Night",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextDark
                                )
                                Text(
                                    text = "22 thg 11 · 19:00",
                                    fontSize = 10.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Text(
                                    text = "General Admission",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF9299A8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // History segment
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                val historyList: List<Pair<String, String>> = if (pastTickets.isNotEmpty()) {
                    pastTickets.map { (it.eventTitle ?: "Sự kiện đã tham dự") to (it.eventStartTime ?: "Trước đây") }
                } else {
                    listOf(
                        "Cloud Native Day" to "12 tháng 8, 2026",
                        "DevSecOps Summit" to "03 tháng 6, 2026",
                        "Kotlin Saigon Meetup" to "18 tháng 4, 2026"
                    )
                }

                historyList.forEach { (title, date) ->
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(15.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFF0F2F6)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = Color(0xFF697184),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextDark
                                )
                                Text(
                                    text = date,
                                    fontSize = 10.sp,
                                    color = Color(0xFF7A8292),
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    text = "ĐÃ DÙNG",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF6E7687),
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Security Notice Banner at bottom
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFFEEF6FF))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Mọi vé đều được ký số và ràng buộc với thiết bị này.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF31547F),
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

/**
 * TAB 3: Cài đặt (Settings.dc.html)
 */
@Composable
private fun SettingsView(
    userName: String,
    userEmail: String,
    biometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    securityAlertsEnabled: Boolean,
    onToggleSecurityAlerts: (Boolean) -> Unit,
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    logoutConfirmPending: Boolean,
    onLogoutClick: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onOpenCryptoLab: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

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
                // Theme item
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDarkMode(!isDarkMode) }
                        .padding(horizontal = 15.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DarkMode,
                        contentDescription = null,
                        tint = Color(0xFF596174),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Giao diện",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextDark,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (isDarkMode) "Tối" else "Theo hệ thống",
                        fontSize = 11.sp,
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

        Spacer(modifier = Modifier.height(14.dp))
    }
}
