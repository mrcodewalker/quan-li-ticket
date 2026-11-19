package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onTriggerBiometric: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val ticketVault = remember { EncryptedTicketVault(context) }

    var email by remember { mutableStateOf("customer@secureticket.com") }
    var password by remember { mutableStateOf("SecureTicket@2026") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverHostInput by remember { mutableStateOf(ApiClient.baseUrl) }

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
                text = "Ví Vé Concert Ký Số ECDSA & Định Danh Phần Cứng",
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
                        text = "Dual-Token (15m/7d) · AES-256-GCM PII · StrongBox TEE",
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
                Column(modifier = Modifier.padding(22.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Đăng nhập an toàn",
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
                    Spacer(modifier = Modifier.height(16.dp))

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
                                    val api = ApiClient.getService(context)
                                    val res = api.login(LoginRequest(email.trim(), password))
                                    if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                        val loginData = res.body()!!.data!!

                                        withContext(Dispatchers.IO) {
                                            // 1. Lưu cặp Access Token (15m) & Refresh Token (7d) vào EncryptedSharedPreferences
                                            securePrefs.saveAuthTokens(
                                                accessToken = loginData.accessToken,
                                                refreshToken = loginData.refreshToken,
                                                expiresInSeconds = loginData.expiresIn
                                            )
                                            // 2. Mã hóa thông tin định danh khách hàng (PII) bằng AES-256-GCM
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

                                            // 3. Ràng buộc thiết bị bằng khóa ECDSA P-256 trong AndroidKeyStore TEE
                                            try {
                                                val pubKeyBase64 = KeystoreManager.getDevicePublicKeyBase64()
                                                api.bindDevice(BindDeviceRequest(pubKeyBase64))
                                            } catch (_: Exception) {
                                            }

                                            // 4. Đồng bộ Server Public Key để xác minh chữ ký vé offline
                                            try {
                                                val pubRes = api.getServerPublicKey()
                                                if (pubRes.isSuccessful && pubRes.body()?.data?.publicKeyBase64 != null) {
                                                    securePrefs.saveServerPublicKey(pubRes.body()!!.data!!.publicKeyBase64)
                                                }
                                            } catch (_: Exception) {
                                            }
                                        }

                                        Toast.makeText(
                                            context,
                                            "Đăng nhập thành công! Đã lưu Dual-Token & mã hóa PII.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onLoginSuccess(loginData.user.role)
                                    } else {
                                        errorMessage = res.body()?.error ?: "Đăng nhập thất bại (${res.code()})"
                                    }
                                } catch (e: Exception) {
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
                                text = "Đăng nhập & Khởi tạo Phiên An toàn",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Demo Shortcut Buttons
            Text(
                text = "Tài khoản mẫu kiểm thử nhanh:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextMuted
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        email = "customer@secureticket.com"
                        password = "SecureTicket@2026"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Khách hàng", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        email = "staff@secureticket.com"
                        password = "SecureTicket@2026"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Nhân viên Soát vé", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        email = "organizer@secureticket.com"
                        password = "SecureTicket@2026"
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                ) {
                    Text("Ban tổ chức", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

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
}
