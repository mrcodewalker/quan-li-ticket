package com.example.quan_ly_ticket

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.security.SecurityCheck
import com.example.quan_ly_ticket.ui.components.BiometricAuthDialog
import com.example.quan_ly_ticket.ui.screens.*
import com.example.quan_ly_ticket.ui.theme.SecureTicketTheme

private data class PendingBiometricRequest(
    val title: String,
    val subtitle: String,
    val onVerified: () -> Unit
)

enum class Screen {
    LOGIN,
    HOME,
    STAFF_DASHBOARD,
    ORGANIZER_DASHBOARD,
    TICKET_DETAIL,
    SCANNER,
    CRYPTO_LAB,
    PAYMENT
}

class MainActivity : FragmentActivity() {

    private lateinit var securePrefs: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tính năng bảo mật 4B: Bật FLAG_SECURE ở tầng WindowManager/SurfaceFlinger
        // để ngăn chặn chụp màn hình, quay video màn hình (MediaProjection) và trình chiếu không chuẩn HDCP
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(false) // Chặn lưu ảnh thu nhỏ màn hình vé trong Recent Apps (Đa nhiệm)
        }

        securePrefs = SecurePreferences(this)
        val securityStatus = SecurityCheck.checkDeviceSecurity(this)

        setContent {
            SecureTicketTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    fun homeScreenForRole(role: String? = securePrefs.getUserRole()): Screen {
                        return when (role?.uppercase()) {
                            "STAFF" -> Screen.STAFF_DASHBOARD
                            "ORGANIZER", "ADMIN" -> Screen.ORGANIZER_DASHBOARD
                            else -> Screen.HOME
                        }
                    }

                    var currentScreen by remember {
                        mutableStateOf(
                            if (securePrefs.getAccessToken().isNullOrEmpty()) Screen.LOGIN else homeScreenForRole()
                        )
                    }
                    var selectedTicket by remember { mutableStateOf<Ticket?>(null) }
                    var activeOrder by remember { mutableStateOf<CreateOrderData?>(null) }
                    var pendingBiometric by remember { mutableStateOf<PendingBiometricRequest?>(null) }
                    val hasHardwareBiometric = remember {
                        BiometricManager.from(this@MainActivity).canAuthenticate(
                            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        ) == BiometricManager.BIOMETRIC_SUCCESS
                    }

                    fun requestBiometricAuth(title: String, subtitle: String, onVerified: () -> Unit) {
                        pendingBiometric = PendingBiometricRequest(title, subtitle, onVerified)
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Phase 3 (FR-19): Adaptive RASP Security Banner khi phát hiện môi trường rủi ro trên bản Release
                        if (securityStatus.isRestrictedMode) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFB91C1C))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GppMaybe,
                                    contentDescription = "Cảnh báo RASP",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "RASP Cảnh báo: Thiết bị có dấu hiệu can thiệp hệ thống (Root/Frida). Các giao dịch nhạy cảm được giám sát.",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            when (currentScreen) {
                                Screen.LOGIN -> LoginScreen(
                                    onLoginSuccess = { role ->
                                        currentScreen = homeScreenForRole(role)
                                    },
                                    onTriggerBiometric = {
                                        requestBiometricAuth(
                                            title = "Xác thực mở Ví vé SecureTicket",
                                            subtitle = "Quét Face ID, Vân tay hoặc dùng cảm biến sinh trắc học thiết bị"
                                        ) {
                                            if (!securePrefs.getAccessToken().isNullOrEmpty()) {
                                                currentScreen = homeScreenForRole()
                                            } else {
                                                Toast.makeText(
                                                    this@MainActivity,
                                                    "Xác thực sinh trắc học thành công! Hãy đăng nhập mật khẩu lần đầu để tạo phiên.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    }
                                )

                                Screen.STAFF_DASHBOARD -> StaffDashboardScreen(
                                    onOpenScanner = { currentScreen = Screen.SCANNER },
                                    onOpenCryptoLab = { currentScreen = Screen.CRYPTO_LAB },
                                    onLogout = { currentScreen = Screen.LOGIN }
                                )

                                Screen.ORGANIZER_DASHBOARD -> OrganizerDashboardScreen(
                                    onOpenScanner = { currentScreen = Screen.SCANNER },
                                    onOpenCryptoLab = { currentScreen = Screen.CRYPTO_LAB },
                                    onRequireBiometric = { title, subtitle, onVerified ->
                                        requestBiometricAuth(title, subtitle, onVerified)
                                    },
                                    onLogout = { currentScreen = Screen.LOGIN }
                                )

                                Screen.HOME -> HomeScreen(
                                    onSelectTicket = { ticket ->
                                        selectedTicket = ticket
                                        currentScreen = Screen.TICKET_DETAIL
                                    },
                                    onProceedToPayment = { order ->
                                        activeOrder = order
                                        currentScreen = Screen.PAYMENT
                                    },
                                    onOpenScanner = {
                                        currentScreen = Screen.SCANNER
                                    },
                                    onOpenCryptoLab = {
                                        currentScreen = Screen.CRYPTO_LAB
                                    },
                                    onRequireBiometric = { title, subtitle, onVerified ->
                                        requestBiometricAuth(title, subtitle, onVerified)
                                    },
                                    onLogout = {
                                        currentScreen = Screen.LOGIN
                                    }
                                )

                                Screen.PAYMENT -> {
                                    activeOrder?.let { order ->
                                        PaymentScreen(
                                            order = order,
                                            onPaymentSuccess = { ticket ->
                                                selectedTicket = ticket
                                                currentScreen = Screen.TICKET_DETAIL
                                            },
                                            onCancel = {
                                                currentScreen = Screen.HOME
                                            }
                                        )
                                    } ?: run {
                                        currentScreen = Screen.HOME
                                    }
                                }

                                Screen.TICKET_DETAIL -> {
                                    selectedTicket?.let { ticket ->
                                        TicketDetailScreen(
                                            ticket = ticket,
                                            onRequireBiometric = { title, subtitle, onVerified ->
                                                requestBiometricAuth(title, subtitle, onVerified)
                                            },
                                            onBack = { currentScreen = Screen.HOME }
                                        )
                                    } ?: run {
                                        currentScreen = Screen.HOME
                                    }
                                }

                                Screen.SCANNER -> ScannerScreen(
                                    onBack = { currentScreen = homeScreenForRole() }
                                )

                                Screen.CRYPTO_LAB -> CryptoLabScreen(
                                    onBack = { currentScreen = homeScreenForRole() }
                                )
                            }
                        }
                    }

                    // Hộp thoại Giả lập Quét Face ID / Vân tay & Biometric hệ thống
                    pendingBiometric?.let { req ->
                        BiometricAuthDialog(
                            title = req.title,
                            subtitle = req.subtitle,
                            hasHardwareBiometric = hasHardwareBiometric,
                            onUseSystemBiometric = {
                                val callback = req.onVerified
                                val t = req.title
                                val s = req.subtitle
                                pendingBiometric = null
                                showBiometricPrompt(
                                    title = t,
                                    subtitle = s,
                                    allowEmulatorFallback = false,
                                    onSuccess = callback
                                )
                            },
                            onSuccess = {
                                val callback = req.onVerified
                                pendingBiometric = null
                                callback()
                            },
                            onDismiss = {
                                pendingBiometric = null
                            }
                        )
                    }
                }
            }
        }
    }

    /**
     * Xác thực sinh trắc học vân tay / khuôn mặt / PIN khóa máy (Phase 2 FR-11: BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
     */
    private fun showBiometricPrompt(
        title: String,
        subtitle: String,
        allowEmulatorFallback: Boolean = true,
        onSuccess: () -> Unit
    ) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            if (allowEmulatorFallback) {
                Toast.makeText(
                    this,
                    "Thiết bị chưa cài đặt Vân tay/PIN — Chuyển sang ký số trực tiếp bằng AndroidKeyStore TEE",
                    Toast.LENGTH_SHORT
                ).show()
                onSuccess()
            } else {
                Toast.makeText(
                    this,
                    "Thiết bị chưa cài đặt vân tay hoặc mã khóa màn hình.",
                    Toast.LENGTH_SHORT
                ).show()
            }
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    Toast.makeText(this@MainActivity, "Xác thực sinh trắc học thành công!", Toast.LENGTH_SHORT).show()
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    Toast.makeText(this@MainActivity, "Hủy/Lỗi sinh trắc học: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Không nhận diện được vân tay/khuôn mặt.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}
