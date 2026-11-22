    package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.BindDeviceRequest
import com.example.quan_ly_ticket.data.model.LoginRequest
import com.example.quan_ly_ticket.data.model.RegisterRequest
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onTriggerBiometric: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val ticketVault = remember { EncryptedTicketVault(context) }

    var isRegisterMode by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("customer@secureticket.com") }
    var password by remember { mutableStateOf("SecureTicket@2026") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var showEvaluatorSheet by remember { mutableStateOf(false) }
    var serverHostInput by remember { mutableStateOf(ApiClient.baseUrl) }

    suspend fun performLoginAndBind(loginEmail: String, loginPassword: String) {
        val api = ApiClient.getService(context)
        val res = api.login(LoginRequest(loginEmail, loginPassword))
        if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
            val loginData = res.body()!!.data!!

            withContext(Dispatchers.IO) {
                securePrefs.saveAuthTokens(
                    accessToken = loginData.accessToken,
                    refreshToken = loginData.refreshToken,
                    expiresInSeconds = loginData.expiresIn
                )
                securePrefs.saveUser(
                    id = loginData.user.id,
                    email = loginData.user.email,
                    fullName = loginData.user.fullName,
                    role = loginData.user.role,
                    phone = loginData.user.phone ?: "0988.765.432"
                )
                ticketVault.saveCustomerPiiEncrypted(
                    EncryptedTicketVault.CustomerPiiRecord(
                        userId = loginData.user.id,
                        fullName = loginData.user.fullName,
                        email = loginData.user.email,
                        phone = loginData.user.phone ?: "0988.765.432",
                        identityNumberMasked = "079204***889"
                    )
                )
                try {
                    val pubKeyBase64 = KeystoreManager.getDevicePublicKeyBase64()
                    api.bindDevice(BindDeviceRequest(pubKeyBase64))
                } catch (_: Exception) {
                }
                try {
                    val pubRes = api.getServerPublicKey()
                    if (pubRes.isSuccessful && pubRes.body()?.data?.publicKeyBase64 != null) {
                        securePrefs.saveServerPublicKey(pubRes.body()!!.data!!.publicKeyBase64)
                    }
                } catch (_: Exception) {
                }
            }

            Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
            onLoginSuccess(loginData.user.role)
        } else {
            errorMessage = res.body()?.error ?: "Đăng nhập thất bại (${res.code()})"
        }
    }

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // App Shield Badge with Gradient
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(PrimaryNavy, PrimaryBlue)
                        )
                    )
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0x33203E9A),
                        spotColor = Color(0x33203E9A)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = "SecureTicket",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "SecureTicket",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = TextDark
            )
            Text(
                text = "Ví Vé Sự Kiện & Check-in Thông Minh",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Security Architecture Pill
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFEAF4FF),
                modifier = Modifier.border(1.dp, Color(0xFFD0E4FF), RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Vé điện tử chính chủ · Chống giả mạo & Chuyển nhượng an toàn",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryNavy
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Login Glass Form Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = 12.dp
            ) {
                Column(
                    modifier = Modifier
                        .padding(22.dp)
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = 0.85f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRegisterMode) "Đăng ký tài khoản" else "Đăng nhập an toàn",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextDark
                        )
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Smooth Sliding Segmented Toggle: Đăng nhập | Đăng ký mới
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF1F5F9))
                            .padding(4.dp)
                    ) {
                        val tabWidth = maxWidth / 2
                        val indicatorOffset by animateDpAsState(
                            targetValue = if (!isRegisterMode) 0.dp else tabWidth,
                            animationSpec = spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "login_tab_slide"
                        )

                        Box(
                            modifier = Modifier
                                .offset(x = indicatorOffset)
                                .width(tabWidth)
                                .height(36.dp)
                                .shadow(1.dp, RoundedCornerShape(9.dp), ambientColor = Color(0x140F172A), spotColor = Color(0x140F172A))
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White)
                        )

                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf(false to "Đăng nhập", true to "Đăng ký mới").forEach { (reg, label) ->
                                val active = isRegisterMode == reg
                                val textColor by animateColorAsState(
                                    targetValue = if (active) PrimaryBlue else TextMuted,
                                    animationSpec = tween(180),
                                    label = "login_tab_tint"
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(9.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            isRegisterMode = reg
                                            errorMessage = null
                                            if (reg && email == "customer@secureticket.com") {
                                                email = ""
                                                password = ""
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 12.sp,
                                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = textColor
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    AnimatedVisibility(
                        visible = isRegisterMode,
                        enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(140))
                    ) {
                        Column {
                            OutlinedTextField(
                                value = fullName,
                                onValueChange = { fullName = it },
                                label = { Text("Họ và tên", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedTextField(
                                value = phone,
                                onValueChange = { phone = it },
                                label = { Text("Số điện thoại", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email khách hàng / nhân viên", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu", fontSize = 12.sp) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFEE2E2),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = errorMessage!!,
                                color = Color(0xFFB91C1C),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    if (isRegisterMode) {
                                        if (fullName.isBlank() || email.isBlank() || password.length < 6) {
                                            errorMessage = "Vui lòng nhập đầy đủ Họ tên, Email và Mật khẩu (>= 6 ký tự)."
                                            return@launch
                                        }
                                        val api = ApiClient.getService(context)
                                        val regRes = api.register(
                                            RegisterRequest(
                                                email = email.trim(),
                                                password = password,
                                                fullName = fullName.trim(),
                                                phone = phone.trim().ifBlank { null }
                                            )
                                        )
                                        if (regRes.isSuccessful && regRes.body()?.success == true) {
                                            performLoginAndBind(email.trim(), password)
                                        } else {
                                            errorMessage = regRes.body()?.error ?: "Đăng ký thất bại (${regRes.code()})"
                                        }
                                    } else {
                                        performLoginAndBind(email.trim(), password)
                                    }
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    errorMessage = "Lỗi kết nối máy chủ: ${e.message}"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                        enabled = !isLoading
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = if (isRegisterMode) "Đăng ký tài khoản" else "Đăng nhập",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Discreet Evaluator Role Switcher Chip (opens ModalBottomSheet)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFFF1F5F9),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                onClick = { showEvaluatorSheet = true }
            ) {
                Text(
                    text = "Chuyển nhanh tài khoản kiểm thử (Evaluator)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Biometric quick auth & Server config button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onTriggerBiometric,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Sinh trắc học",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mở khóa bằng Vân tay / PIN máy",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }

                IconButton(onClick = { showServerDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Cấu hình Server",
                        tint = TextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Địa chỉ Máy chủ (Backend)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Nhập URL Backend (VD: http://42.96.5.186:5000/ cho VPS Cloud hoặc http://10.0.2.2:5000/ cho máy ảo local):",
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverHostInput,
                        onValueChange = { serverHostInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    ApiClient.updateBaseUrl(serverHostInput.trim(), context)
                    showServerDialog = false
                    Toast.makeText(context, "Đã cập nhật máy chủ!", Toast.LENGTH_SHORT).show()
                }) {
                    Text("Lưu")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    if (showEvaluatorSheet) {
        val personas = listOf(
            Triple("KH A · Minh Anh", "customer@secureticket.com", "CUSTOMER"),
            Triple("KH B · Hoàng Nam", "friend@example.com", "CUSTOMER"),
            Triple("KH C · Khánh Linh", "linh.pham@secureticket.com", "CUSTOMER"),
            Triple("Nhân viên Soát vé", "staff@secureticket.com", "STAFF"),
            Triple("Ban Tổ Chức", "organizer@secureticket.com", "ORGANIZER")
        )
        ModalBottomSheet(
            onDismissRequest = { showEvaluatorSheet = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Evaluator Role Switcher",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextDark
                    )
                    Surface(shape = RoundedCornerShape(8.dp), color = InfoBg) {
                        Text(
                            text = "Pass: SecureTicket@2026",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = InfoFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                personas.forEach { (label, personaEmail, role) ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF8FAFC),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        onClick = {
                            isRegisterMode = false
                            email = personaEmail
                            password = "SecureTicket@2026"
                            errorMessage = null
                            showEvaluatorSheet = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextDark)
                                Text(personaEmail, fontSize = 11.sp, color = TextMuted)
                            }
                            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFEFF6FF)) {
                                Text(
                                    text = role,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryBlue,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
