package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.quan_ly_ticket.data.model.LogoutRequest
import com.example.quan_ly_ticket.data.model.ValidationLogItem
import com.example.quan_ly_ticket.data.model.ValidationStats
import com.example.quan_ly_ticket.ui.components.EventCardSkeleton
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Cổng Soát Vé Chuyên Biệt cho Nhân Viên (RBAC: STAFF)
 * Tuân thủ nguyên tắc Đặc quyền tối thiểu (Least Privilege):
 * - Chỉ được quyền quét xác thực QR/NFC và theo dõi nhật ký check-in.
 * - Không truy cập doanh thu, không chỉnh sửa/thu hồi vé, không mua vé hộ.
 */
@Composable
fun StaffDashboardScreen(
    onOpenScanner: () -> Unit,
    onOpenCryptoLab: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val staffName = remember { securePrefs.getUserFullName() ?: "Nhân viên Soát vé" }
    val staffEmail = remember { securePrefs.getUserEmail() ?: "staff@secureticket.com" }

    var isLoading by remember { mutableStateOf(true) }
    var stats by remember { mutableStateOf(ValidationStats()) }
    var logs by remember { mutableStateOf<List<ValidationLogItem>>(emptyList()) }
    var filterMode by remember { mutableStateOf("ALL") } // ALL, SUCCESS, BLOCKED

    fun loadLogs() {
        isLoading = true
        scope.launch {
            try {
                val res = ApiClient.getService(context).getValidationLogs()
                if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                    stats = res.body()!!.data!!.stats
                    logs = res.body()!!.data!!.logs
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, "Không thể tải lịch sử soát vé: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) { loadLogs() }

    val filteredLogs = remember(logs, filterMode) {
        when (filterMode) {
            "SUCCESS" -> logs.filter { it.result == "SUCCESS" }
            "BLOCKED" -> logs.filter { it.result != "SUCCESS" }
            else -> logs
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFFDCFCE7)) {
                            Text(
                                text = "RBAC · STAFF",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF15803D),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        Text(text = staffEmail, fontSize = 11.sp, color = TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        text = staffName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = TextDark,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(1.dp, BorderLight, RoundedCornerShape(12.dp))
                            .clickable { loadLogs() },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Làm mới", tint = TextDark, modifier = Modifier.size(19.dp))
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DangerBg)
                            .border(1.dp, DangerBorder, RoundedCornerShape(12.dp))
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
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Đăng xuất", tint = DangerFg, modifier = Modifier.size(19.dp))
                    }
                }
            }

            // Primary Check-in Action Card
            GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFFEFF6FF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.QrCodeScanner, null, tint = PrimaryBlue, modifier = Modifier.size(25.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Trạm soát vé sự kiện", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                            Text(
                                "Xác thực chữ ký ECDSA P-256, mã động TOTP 30s & Challenge-Response NFC",
                                fontSize = 11.sp,
                                color = TextMuted,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    Button(
                        onClick = onOpenScanner,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Mở Camera Quét QR / NFC Khách", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }

                    OutlinedButton(
                        onClick = onOpenCryptoLab,
                        modifier = Modifier.fillMaxWidth().height(40.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Security, null, tint = PrimaryBlue, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Mở Phòng Lab Kiểm Chứng Mật Mã", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
            }

            // 3 Real-Time Shift Stat Cards
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StaffStatTile(
                    label = "Hợp lệ",
                    value = "${stats.successCount}",
                    accent = SuccessFg,
                    bg = SuccessBg,
                    modifier = Modifier.weight(1f)
                )
                StaffStatTile(
                    label = "Chặn Replay",
                    value = "${stats.replayBlockedCount}",
                    accent = DangerFg,
                    bg = DangerBg,
                    modifier = Modifier.weight(1f)
                )
                StaffStatTile(
                    label = "Tổng quét",
                    value = "${stats.totalScans}",
                    accent = PrimaryBlue,
                    bg = InfoBg,
                    modifier = Modifier.weight(1f)
                )
            }

            // Live Check-in Audit Feed
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nhật ký soát vé gần đây", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = TextDark)
                Text("${filteredLogs.size} lượt", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "Tất cả", "SUCCESS" to "Hợp lệ", "BLOCKED" to "Bị từ chối").forEach { (key, label) ->
                    val active = filterMode == key
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (active) PrimaryBlue else Color.White)
                            .border(1.dp, if (active) PrimaryBlue else BorderLight, RoundedCornerShape(10.dp))
                            .clickable { filterMode = key }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (active) Color.White else TextMuted)
                    }
                }
            }

            if (isLoading && logs.isEmpty()) {
                repeat(3) { EventCardSkeleton() }
            } else if (filteredLogs.isEmpty()) {
                GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.FactCheck, null, tint = TextSubtle, modifier = Modifier.size(32.dp))
                        Text("Chưa có lượt soát vé nào", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                        Text("Bấm 'Mở Camera Quét QR / NFC Khách' để bắt đầu soát vé tại cổng.", fontSize = 11.sp, color = TextMuted)
                    }
                }
            } else {
                filteredLogs.forEach { item ->
                    val isOk = item.result == "SUCCESS"
                    GlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(15.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (isOk) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isOk) Icons.Default.Check else Icons.Default.Block,
                                    contentDescription = null,
                                    tint = if (isOk) Color(0xFF15803D) else Color(0xFFDC2626),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.ticketCode ?: "QR Không hợp lệ",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextDark
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isOk) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                    ) {
                                        Text(
                                            text = when (item.result) {
                                                "SUCCESS" -> "HỢP LỆ"
                                                "FAILED_ALREADY_USED" -> "REPLAY"
                                                "FAILED_REVOKED" -> "ĐÃ THU HỒI"
                                                else -> "TỪ CHỐI"
                                            },
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isOk) Color(0xFF15803D) else Color(0xFFDC2626),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${item.eventTitle ?: "Sự kiện"} · ${item.ownerName ?: "Khách"}",
                                    fontSize = 11.sp,
                                    color = TextMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.method ?: "QR"} · ${formatEventTime(item.scannedAt) ?: ""}" +
                                        if (!item.notes.isNullOrBlank() && !isOk) " · ${item.notes}" else "",
                                    fontSize = 10.sp,
                                    color = TextSubtle,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StaffStatTile(
    label: String,
    value: String,
    accent: Color,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, BorderLight, RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(shape = RoundedCornerShape(8.dp), color = bg) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = accent,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextMuted)
    }
}
