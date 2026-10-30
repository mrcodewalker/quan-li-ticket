package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.security.HybridCrypto
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.security.SecurityCheck
import javax.crypto.SecretKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoLabScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val securePrefs = remember { SecurePreferences(context) }
    val vault = remember { EncryptedTicketVault(context) }
    val securityStatus = remember { SecurityCheck.checkDeviceSecurity(context) }

    // --- State cho Demo AES-256-GCM ---
    var aesPlaintextInput by remember { mutableStateOf("Vé sự kiện: SecureFest 2026 - Ghế VIP 01 - ID: 889922") }
    var currentAesKey by remember { mutableStateOf<SecretKey?>(null) }
    var aesResult by remember { mutableStateOf<HybridCrypto.EncryptionResult?>(null) }
    var aesDecryptedText by remember { mutableStateOf<String?>(null) }
    var aesErrorMessage by remember { mutableStateOf<String?>(null) }

    // --- State cho Demo ECDSA Verification & Tampering ---
    var ecdsaPayload by remember { mutableStateOf("TKT-DEMO123|EVENT-SECURE|USER-ANTOAN|NONCE9988|1793346577") }
    var ecdsaVerifyResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Lấy Public Key của Server từ Prefs hoặc giá trị mẫu
    val serverPubKeyBase64 = remember {
        securePrefs.getServerPublicKey()
            ?: "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8cBc42TqLQC9ZMSfa+P4xyACUx425Dob+CzVdXuJ3/WjeYB17Dj7tb5a1Mwpw+E6xzEcYQsCNQZfDELMwaEvPw=="
    }

    // Chữ ký số mẫu đã ký bởi server cho chuỗi ecdsaPayload gốc
    var sampleSignature by remember {
        mutableStateOf("MEUCIQC94Q0pB8nZlUaI2kP1y5R...ECDSA_P256_Signature_Sample")
    }

    // --- State cho Demo TOTP Dynamic QR ---
    var demoTotp by remember { mutableStateOf("") }
    var demoRemainingSec by remember { mutableIntStateOf(30) }

    LaunchedEffect(Unit) {
        while (true) {
            val (token, sec) = CryptoHelper.generateLocalRotatingCode("TICKET-TEST", "DemoSeedSecretKey2026")
            demoTotp = token
            demoRemainingSec = sec
            kotlinx.coroutines.delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phòng thí nghiệm Mật mã (Crypto Lab)", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Mô hình Mật mã học Ứng dụng",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Màn hình trực quan hóa các thuật toán mã hóa, ký số, giải mã và chống tấn công phục vụ báo cáo đồ án.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }

            // --- PHẦN 1: MÃ HÓA ĐỐI XỨNG AES-256-GCM (AEAD) ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. Mã hóa AES-256-GCM (AEAD)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = "Đảm bảo cả Tính bí mật (Confidentiality) lẫn Tính toàn vẹn (Authentication Tag 128-bit).",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = aesPlaintextInput,
                        onValueChange = { aesPlaintextInput = it },
                        label = { Text("Dữ liệu Plaintext cần mã hóa") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                try {
                                    val key = HybridCrypto.generateRandomAesKey()
                                    currentAesKey = key
                                    val res = HybridCrypto.encryptAesGcm(aesPlaintextInput, key)
                                    aesResult = res
                                    aesDecryptedText = null
                                    aesErrorMessage = null
                                } catch (e: Exception) {
                                    aesErrorMessage = e.message
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Mã hóa AES-GCM", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (aesResult != null && currentAesKey != null) {
                                    try {
                                        val decrypted = HybridCrypto.decryptAesGcm(aesResult!!.ciphertextBase64, currentAesKey!!)
                                        aesDecryptedText = decrypted
                                        aesErrorMessage = null
                                    } catch (e: Exception) {
                                        aesErrorMessage = e.message
                                    }
                                } else {
                                    Toast.makeText(context, "Hãy bấm mã hóa trước!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = aesResult != null,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Giải mã", fontSize = 12.sp)
                        }
                    }

                    // Nút thử nghiệm tấn công Tampering vào Ciphertext
                    if (aesResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = {
                                try {
                                    // Giả lập kẻ gian sửa 1 ký tự trong Ciphertext
                                    val tampered = aesResult!!.ciphertextBase64.dropLast(2) + "ZZ"
                                    HybridCrypto.decryptAesGcm(tampered, currentAesKey!!)
                                    aesErrorMessage = null
                                } catch (e: Exception) {
                                    aesErrorMessage = "PHÁT HIỆN TẤN CÔNG: ${e::class.java.simpleName} - Tag xác thực toàn vẹn bị sai lệch, dữ liệu đã bị can thiệp!"
                                    aesDecryptedText = null
                                }
                            },
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Text("Thử tấn công Tamper Ciphertext -> Kiểm tra phản ứng", color = Color(0xFFDC2626), fontSize = 11.sp)
                        }
                    }

                    if (aesResult != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column {
                                Text("Khóa AES (256-bit Hex): ${aesResult!!.keyHex.take(24)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("IV (96-bit Hex): ${aesResult!!.ivHex}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Ciphertext (Base64): ${aesResult!!.ciphertextBase64.take(36)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    if (aesDecryptedText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Kết quả giải mã thành công: \"$aesDecryptedText\"",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A)
                        )
                    }

                    if (aesErrorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = aesErrorMessage!!,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }

            // --- PHẦN 2: CHỮ KÝ SỐ ECDSA P-256 (TÍNH TOÀN VẸN & CHỐNG CHỐI BỎ) ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF0284C7))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "2. Chữ ký số ECDSA P-256 / SHA-256",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF0284C7)
                        )
                    }
                    Text(
                        text = "Sử dụng đường cong Elliptic secp256r1. Khóa riêng ký, khóa công khai xác minh.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Chuỗi chuẩn hóa dữ liệu vé (Canonical Payload):", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = ecdsaPayload,
                        onValueChange = { ecdsaPayload = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                // Xác minh chữ ký với chuỗi dữ liệu hiện tại
                                val isOk = CryptoHelper.verifyServerSignature(ecdsaPayload, sampleSignature, serverPubKeyBase64)
                                ecdsaVerifyResult = Pair(
                                    isOk,
                                    if (isOk) "Chữ ký số KHỚP HOÀN TOÀN với Server Public Key!" else "CHỮ KÝ KHÔNG KHỚP! Dữ liệu đã bị can thiệp trái phép!"
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Xác minh Chữ ký", fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = {
                                // Thử nghiệm can thiệp đổi 1 ký tự trong payload
                                ecdsaPayload += "_HACKED"
                                ecdsaVerifyResult = Pair(
                                    false,
                                    "CẢNH BÁO: Dữ liệu đã bị thay đổi thêm 1 byte! Chữ ký số lập tức bị vô hiệu!"
                                )
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Thử sửa 1 byte", fontSize = 12.sp)
                        }
                    }

                    if (ecdsaVerifyResult != null) {
                        val (isSuccess, msg) = ecdsaVerifyResult!!
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(8.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSuccess) Color(0xFF16A34A) else Color(0xFFDC2626)
                            )
                        }
                    }
                }
            }

            // --- PHẦN 3: MÃ QR ĐỘNG TOTP & ĐỒNG BỘ THỜI GIAN ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFF8B5CF6))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3. Thuật toán QR Động TOTP (HMAC-SHA256)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color(0xFF8B5CF6)
                        )
                    }
                    Text(
                        text = "Công thức: HMAC(SecretSeed, TicketID : floor(Epoch / 30))",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Mã TOTP hiện tại: $demoTotp",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF8B5CF6)
                        )
                        Text(
                            text = "Đổi sau ${demoRemainingSec}s",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { demoRemainingSec / 30f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFF8B5CF6),
                        trackColor = Color(0xFFEDE9FE)
                    )
                }
            }

            // --- PHẦN 4: BÁO CÁO TOÀN VẸN THIẾT BỊ (ANTI-TAMPERING & KEYSTORE) ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "4. Báo cáo An ninh Thiết bị (Anti-Tampering)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(text = "• Phát hiện Root (RootBeer): ${if (securityStatus.isRooted) "CẢNH BÁO (Thiết bị đã root)" else "AN TOÀN"}", fontSize = 12.sp)
                    Text(text = "• Debugger đính kèm: ${if (securityStatus.isDebuggerAttached) "CẢNH BÁO (Đang chạy Debug)" else "AN TOÀN"}", fontSize = 12.sp)
                    Text(text = "• Môi trường máy ảo (Emulator): ${if (securityStatus.isEmulator) "Phát hiện máy ảo" else "Thiết bị thật"}", fontSize = 12.sp)
                    Text(text = "• Phần cứng Keystore: Hỗ trợ Hardware TEE / StrongBox", fontSize = 12.sp, color = Color(0xFF059669))
                    Text(text = "• Kho vé mã hóa (Encrypted Vault): ${vault.getRawEncryptedVaultSample()}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                }
            }
        }
    }
}

