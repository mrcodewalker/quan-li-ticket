package com.example.quan_ly_ticket

import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.CreateOrderData
import com.example.quan_ly_ticket.data.model.Ticket
import com.example.quan_ly_ticket.ui.screens.*
import com.example.quan_ly_ticket.ui.theme.SecureTicketTheme

enum class Screen {
    LOGIN,
    HOME,
    TICKET_DETAIL,
    SCANNER,
    CRYPTO_LAB,
    PAYMENT
}

class MainActivity : FragmentActivity() {

    private lateinit var securePrefs: SecurePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Tính năng bảo mật 4B: Bật FLAG_SECURE để ngăn chặn chụp màn hình và quay video màn hình chứa mã vé
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        securePrefs = SecurePreferences(this)

        setContent {
            SecureTicketTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember {
                        mutableStateOf(
                            if (securePrefs.getAccessToken().isNullOrEmpty()) Screen.LOGIN else Screen.HOME
                        )
                    }
                    var selectedTicket by remember { mutableStateOf<Ticket?>(null) }
                    var activeOrder by remember { mutableStateOf<CreateOrderData?>(null) }

                    when (currentScreen) {
                        Screen.LOGIN -> LoginScreen(
                            onLoginSuccess = { _ ->
                                currentScreen = Screen.HOME
                            },
                            onTriggerBiometric = {
                                showBiometricPrompt {
                                    if (!securePrefs.getAccessToken().isNullOrEmpty()) {
                                        currentScreen = Screen.HOME
                                    } else {
                                        Toast.makeText(this, "Chưa lưu phiên đăng nhập. Vui lòng đăng nhập bằng mật khẩu trước.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
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
                                    onBack = { currentScreen = Screen.HOME }
                                )
                            } ?: run {
                                currentScreen = Screen.HOME
                            }
                        }

                        Screen.SCANNER -> ScannerScreen(
                            onBack = { currentScreen = Screen.HOME }
                        )

                        Screen.CRYPTO_LAB -> CryptoLabScreen(
                            onBack = { currentScreen = Screen.HOME }
                        )
                    }
                }
            }
        }
    }

    /**
     * Xác thực sinh trắc học vân tay / khuôn mặt (AndroidX BiometricPrompt)
     */
    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            Toast.makeText(this, "Thiết bị chưa cài đặt hoặc không hỗ trợ sinh trắc học.", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(this@MainActivity, "Lỗi sinh trắc học: $errString", Toast.LENGTH_SHORT).show()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    Toast.makeText(this@MainActivity, "Không nhận diện được vân tay/khuôn mặt.", Toast.LENGTH_SHORT).show()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Xác thực mở Ví vé SecureTicket")
            .setSubtitle("Sử dụng vân tay hoặc mã khóa màn hình để xác nhận danh tính")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()

        prompt.authenticate(promptInfo)
    }
}

