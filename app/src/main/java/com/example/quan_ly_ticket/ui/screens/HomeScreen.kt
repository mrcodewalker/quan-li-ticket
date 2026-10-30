package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.BuyTicketRequest
import com.example.quan_ly_ticket.data.model.Event
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.SecurityCheck
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onSelectTicket: (ticket: Ticket) -> Unit,
    onProceedToPayment: (order: com.example.quan_ly_ticket.data.model.CreateOrderData) -> Unit,
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val userRole = remember { securePrefs.getUserRole() ?: "CUSTOMER" }
    val userName = remember { securePrefs.getUserFullName() ?: "Người dùng" }
    val ticketVault = remember { com.example.quan_ly_ticket.data.local.EncryptedTicketVault(context) }

    var selectedTab by remember { mutableIntStateOf(0) }
    var events by remember { mutableStateOf<List<Event>>(emptyList()) }
    var myTickets by remember { mutableStateOf<List<Ticket>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // State cho phòng chờ ảo (Virtual Waiting Room)
    var waitingQueueData by remember { mutableStateOf<com.example.quan_ly_ticket.data.model.QueueData?>(null) }
    var waitingEventId by remember { mutableStateOf<String?>(null) }

    val securityStatus = remember { SecurityCheck.checkDeviceSecurity(context) }

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
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Lỗi tải dữ liệu: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SecureTicket",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "$userName • $userRole",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                },
                actions = {
                    // Security Badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (securityStatus.isSecure) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (securityStatus.isSecure) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = "Security",
                                tint = if (securityStatus.isSecure) Color(0xFF16A34A) else Color(0xFFDC2626),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (securityStatus.isSecure) "An toàn" else "Root/Debug",
                                fontSize = 11.sp,
                                color = if (securityStatus.isSecure) Color(0xFF16A34A) else Color(0xFFDC2626),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = { refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }

                    IconButton(onClick = {
                        securePrefs.clear()
                        onLogout()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Sự kiện") },
                    label = { Text("Sự kiện") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Star, contentDescription = "Ví vé") },
                    label = { Text("Ví vé (${myTickets.size})") }
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onOpenCryptoLab,
                    icon = { Icon(Icons.Default.Lock, contentDescription = "Crypto Lab") },
                    label = { Text("Crypto Lab") }
                )
                if (userRole in listOf("STAFF", "ADMIN", "ORGANIZER")) {
                    NavigationBarItem(
                        selected = false,
                        onClick = onOpenScanner,
                        icon = { Icon(Icons.Default.Search, contentDescription = "Soát vé") },
                        label = { Text("Soát vé") }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && events.isEmpty() && myTickets.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                when (selectedTab) {
                    0 -> EventListTab(
                        events = events,
                        onBuyTicket = { eventId ->
                            scope.launch {
                                try {
                                    val api = ApiClient.getService(context)
                                    // 1. Đăng ký vào phòng chờ (Virtual Waiting Room)
                                    val queueRes = api.joinQueue(com.example.quan_ly_ticket.data.model.JoinQueueRequest(eventId))
                                    if (queueRes.isSuccessful && queueRes.body()?.success == true) {
                                        val qData = queueRes.body()!!.data!!
                                        if (qData.status == "ACTIVE") {
                                            // Đã có slot thanh toán ngay -> Chuyển sang thanh toán VietQR
                                            val orderRes = api.createPaymentOrder(com.example.quan_ly_ticket.data.model.CreateOrderRequest(eventId, qData.passToken))
                                            if (orderRes.isSuccessful && orderRes.body()?.success == true) {
                                                onProceedToPayment(orderRes.body()!!.data!!)
                                            } else {
                                                Toast.makeText(context, orderRes.body()?.error ?: "Lỗi tạo đơn thanh toán", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            // Đang đông người, chuyển vào phòng chờ
                                            waitingEventId = eventId
                                            waitingQueueData = qData
                                        }
                                    } else {
                                        Toast.makeText(context, queueRes.body()?.error ?: "Lỗi vào phòng chờ", Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                    1 -> TicketWalletTab(
                        tickets = myTickets,
                        onSelectTicket = onSelectTicket
                    )
                }
            }
        }
    }

    // Dialog Phòng chờ ảo (Virtual Waiting Room)
    waitingQueueData?.let { qData ->
        LaunchedEffect(qData.queueId) {
            val api = ApiClient.getService(context)
            while (waitingQueueData?.status == "WAITING") {
                kotlinx.coroutines.delay(3000L)
                try {
                    val statusRes = api.getQueueStatus(qData.queueId)
                    if (statusRes.isSuccessful && statusRes.body()?.success == true) {
                        val updated = statusRes.body()!!.data!!
                        waitingQueueData = updated
                        if (updated.status == "ACTIVE" && waitingEventId != null) {
                            val orderRes = api.createPaymentOrder(com.example.quan_ly_ticket.data.model.CreateOrderRequest(waitingEventId!!, updated.passToken))
                            if (orderRes.isSuccessful && orderRes.body()?.success == true) {
                                Toast.makeText(context, "Đã đến lượt bạn! Chuyển sang thanh toán.", Toast.LENGTH_SHORT).show()
                                val ord = orderRes.body()!!.data!!
                                waitingQueueData = null
                                waitingEventId = null
                                onProceedToPayment(ord)
                                break
                            }
                            waitingQueueData = null
                            waitingEventId = null
                            break
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        AlertDialog(
            onDismissRequest = { },
            icon = { Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp)) },
            title = { Text("Phòng chờ Mua vé Ảo", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Hệ thống đang mở bán vé lưu lượng cao. Để đảm bảo công bằng và chống quá tải, bạn đang được xếp hàng vào lượt thanh toán.",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Số thứ tự xếp hàng của bạn:", fontSize = 13.sp)
                    Text("#${qData.queueNumber ?: 1}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Số người đang xếp hàng trước bạn: ${qData.usersAhead ?: 0}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0369A1))
                    Spacer(modifier = Modifier.height(14.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Hệ thống sẽ tự động chuyển sang thanh toán ngay khi đến lượt...", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    waitingQueueData = null
                    waitingEventId = null
                }) {
                    Text("Rời phòng chờ")
                }
            }
        )
    }
}

@Composable
fun EventListTab(
    events: List<Event>,
    onBuyTicket: (eventId: String) -> Unit
) {
    if (events.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Chưa có sự kiện nào đang mở bán.", color = Color.Gray)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(events) { event ->
            Card(
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = event.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = event.description ?: "",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = "Location", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = event.location, fontSize = 12.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Còn lại: ${event.availableTickets}/${event.totalTickets} vé",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (event.availableTickets > 0) Color(0xFF059669) else Color.Red
                        )

                        Button(
                            onClick = { onBuyTicket(event.id) },
                            enabled = event.availableTickets > 0,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Đặt vé ngay")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TicketWalletTab(
    tickets: List<Ticket>,
    onSelectTicket: (ticket: Ticket) -> Unit
) {
    if (tickets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Star, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ví vé trống. Hãy đặt vé từ danh sách sự kiện!", color = Color.Gray)
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(tickets) { ticket ->
            val isActive = ticket.status == "ACTIVE" || ticket.status == null
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectTicket(ticket) },
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) MaterialTheme.colorScheme.surface else Color(0xFFF1F5F9)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (ticket.ticketCode.isNotEmpty()) ticket.ticketCode else "TKT-SECURE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = when (ticket.status ?: "ACTIVE") {
                                "ACTIVE" -> Color(0xFFDCFCE7)
                                "USED" -> Color(0xFFE2E8F0)
                                else -> Color(0xFFFEE2E2)
                            }
                        ) {
                            Text(
                                text = when (ticket.status ?: "ACTIVE") {
                                    "ACTIVE" -> "HỢP LỆ"
                                    "USED" -> "ĐÃ SỬ DỤNG"
                                    "TRANSFERRED" -> "ĐÃ CHUYỂN NHƯỢNG"
                                    else -> ticket.status ?: "ACTIVE"
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (ticket.status ?: "ACTIVE") {
                                    "ACTIVE" -> Color(0xFF16A34A)
                                    "USED" -> Color.Gray
                                    else -> Color(0xFFDC2626)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = ticket.eventTitle ?: "Vé sự kiện SecureTicket", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text(text = ticket.eventLocation ?: "Địa điểm tổ chức", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = "Digital Signature", tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Vé có chữ ký số ECDSA (P-256)", fontSize = 11.sp, color = Color(0xFF0284C7))
                    }
                }
            }
        }
    }
}

