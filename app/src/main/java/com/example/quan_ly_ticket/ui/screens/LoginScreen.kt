package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.BindDeviceRequest
import com.example.quan_ly_ticket.data.model.LoginRequest
import com.example.quan_ly_ticket.security.KeystoreManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (role: String) -> Unit,
    onTriggerBiometric: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }

    var email by remember { mutableStateOf("customer@secureticket.com") }
    var password by remember { mutableStateOf("SecureTicket@2026") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverHostInput by remember { mutableStateOf(ApiClient.baseUrl) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Header
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "SecureTicket",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Hệ thống Quản lý Vé An toàn & Xác thực Số",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Card form
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Đăng nhập",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Mật khẩu") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = errorMessage!!, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        isLoading = true
                        errorMessage = null
                        scope.launch {
                            try {
                                val api = ApiClient.getService(context)
                                val res = api.login(LoginRequest(email.trim(), password.trim()))
                                if (res.isSuccessful && res.body()?.success == true) {
                                    val loginData = res.body()!!.data!!
                                    securePrefs.saveAuthTokens(loginData.accessToken, loginData.refreshToken)
                                    securePrefs.saveUser(
                                        loginData.user.id,
                                        loginData.user.email,
                                        loginData.user.fullName,
                                        loginData.user.role
                                    )

                                    // Tự động bind khóa Keystore nếu chưa bind
                                    if (!loginData.user.isDeviceBound) {
                                        try {
                                            val pubKeyBase64 = KeystoreManager.getDevicePublicKeyBase64()
                                            api.bindDevice(BindDeviceRequest(pubKeyBase64))
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }

                                    Toast.makeText(context, "Đăng nhập thành công!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess(loginData.user.role)
                                } else {
                                    errorMessage = res.body()?.error ?: "Đăng nhập thất bại (${res.code()})"
                                }
                            } catch (e: Exception) {
                                errorMessage = "Lỗi kết nối: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                    } else {
                        Text("Đăng nhập")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Demo Shortcut Buttons
        Text("Tài khoản mẫu thử nghiệm:", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            FilledTonalButton(
                onClick = {
                    email = "customer@secureticket.com"
                    password = "SecureTicket@2026"
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Khách hàng", fontSize = 12.sp)
            }

            FilledTonalButton(
                onClick = {
                    email = "staff@secureticket.com"
                    password = "SecureTicket@2026"
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Soát vé (Staff)", fontSize = 12.sp)
            }

            FilledTonalButton(
                onClick = {
                    email = "organizer@secureticket.com"
                    password = "SecureTicket@2026"
                },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Ban tổ chức", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Biometric & Settings
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onTriggerBiometric) {
                Icon(Icons.Default.Lock, contentDescription = "Biometric", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Vân tay / Sinh trắc học", fontSize = 13.sp)
            }

            IconButton(onClick = { showServerDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Cấu hình Server")
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("Địa chỉ Máy chủ (Backend)") },
            text = {
                Column {
                    Text("Nhập URL Backend (VD: http://10.0.2.2:5000/ cho máy ảo hoặc http://192.168.1.x:5000 cho máy thật):", fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = serverHostInput,
                        onValueChange = { serverHostInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
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

