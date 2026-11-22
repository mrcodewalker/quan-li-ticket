package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.RefreshTokenRequest
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.security.HybridCrypto
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.security.SecurityCheck
import com.example.quan_ly_ticket.ui.components.AlertType
import com.example.quan_ly_ticket.ui.components.CryptoTerminalBox
import com.example.quan_ly_ticket.ui.components.FrostedIconButton
import com.example.quan_ly_ticket.ui.components.GlassCard
import com.example.quan_ly_ticket.ui.components.SemanticAlertBanner
import com.example.quan_ly_ticket.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.Signature
import javax.crypto.SecretKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CryptoLabScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val securePrefs = remember { SecurePreferences(context) }
    val vault = remember { EncryptedTicketVault(context) }
    val securityStatus = remember { SecurityCheck.checkDeviceSecurity(context) }

    // --- PHẦN 1: State cho Dual-Token (Access Token & Refresh Token Rotation) ---
    var accessTokenSnippet by remember {
        mutableStateOf(securePrefs.getAccessToken()?.let { "${it.take(22)}...${it.takeLast(10)}" } ?: "Chưa đăng nhập")
    }
    var refreshTokenSnippet by remember {
        mutableStateOf(securePrefs.getRefreshToken()?.let { "${it.take(22)}...${it.takeLast(10)}" } ?: "Chưa có Refresh Token")
    }
    var rotationCount by remember { mutableIntStateOf(securePrefs.getRefreshRotationCount()) }
    var tokenStatusState by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var isRotatingToken by remember { mutableStateOf(false) }

    // --- PHẦN 2: State cho Mã hóa Thông tin Khách hàng (Customer PII Encryption) ---
    var customerName by remember { mutableStateOf(securePrefs.getUserFullName() ?: "Nguyễn Minh Anh") }
    var customerEmail by remember { mutableStateOf(securePrefs.getUserEmail() ?: "customer@secureticket.com") }
    var customerPhone by remember { mutableStateOf(securePrefs.getUserPhone() ?: "0988.765.432") }
    var customerCccd by remember { mutableStateOf("079204008899") }
    var rawPiiCiphertext by remember { mutableStateOf(vault.getRawEncryptedCustomerPiiSample()) }
    var rawPrefsXmlSample by remember { mutableStateOf(securePrefs.getRawEncryptedXmlSample()) }
    var decryptedPiiResult by remember { mutableStateOf<String?>(null) }

    // --- PHẦN 3: State cho Chữ ký số ECDSA P-256 Chống Giả Mạo Vé ---
    var ticketCanonicalPayload by remember {
        mutableStateOf("TKT-VIP-2026|EVT-CONCERT-01|USR-MINHANH|NONCE-9F8A|1794966700")
    }
    var hardwareSignatureBase64 by remember { mutableStateOf("") }
    var ecdsaVerifyResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    // Khởi tạo chữ ký phần cứng thật ngay khi mở màn hình
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            try {
                val sig = KeystoreManager.signChallenge(ticketCanonicalPayload.toByteArray(Charsets.UTF_8))
                hardwareSignatureBase64 = sig
            } catch (_: Exception) {
            }
        }
    }

    // --- PHẦN 4: State cho AES-256-GCM (AEAD) & Tampering ---
    var aesPlaintextInput by remember { mutableStateOf("Vé Concert: SecureFest 2026 - Hạng VIP 01 - Chủ vé: Nguyễn Minh Anh") }
    var currentAesKey by remember { mutableStateOf<SecretKey?>(null) }
    var aesResult by remember { mutableStateOf<HybridCrypto.EncryptionResult?>(null) }
    var aesDecryptedText by remember { mutableStateOf<String?>(null) }
    var aesErrorMessage by remember { mutableStateOf<String?>(null) }

    // --- PHẦN 5: State cho QR Động TOTP ---
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

    AppBackground {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FrostedIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        onClick = onBack
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Crypto Lab Console",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = TextDark
                        )
                        Text(
                            text = "Dual-Token · PII Vault · ECDSA P-256 · AES-GCM · RASP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted
                        )
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ================= PHẦN 1: QUẢN TRỊ PHIÊN DUAL-TOKEN (ACCESS & REFRESH TOKEN ROTATION) =================
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CryptoModuleHeader(
                            number = "01",
                            title = "Dual-Token & Refresh Rotation",
                            subtitle = "Access Token (15m) + Refresh Token (7d) lưu trong EncryptedSharedPreferences với Token Reuse Detection.",
                            accent = PrimaryBlue,
                            icon = Icons.Default.VpnKey
                        )

                        CryptoTerminalBox(
                            title = "Session Token State",
                            badgeText = "ROTATIONS: $rotationCount",
                            lines = listOf(
                                "ACCESS JWT (15M)" to accessTokenSnippet,
                                "REFRESH TOKEN (7D)" to refreshTokenSnippet
                            )
                        )

                        Button(
                            onClick = {
                                isRotatingToken = true
                                tokenStatusState = null
                                scope.launch {
                                    try {
                                        val currentRefresh = securePrefs.getRefreshToken()
                                        if (currentRefresh.isNullOrBlank()) {
                                            tokenStatusState = false to "Chưa có Refresh Token. Hãy đăng nhập trước!"
                                        } else {
                                            val api = ApiClient.getService(context)
                                            val res = api.refreshToken(RefreshTokenRequest(currentRefresh))
                                            if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                                val data = res.body()!!.data!!
                                                securePrefs.saveAuthTokens(data.accessToken, data.refreshToken, data.expiresIn)
                                                rotationCount = securePrefs.incrementRefreshRotationCount()
                                                accessTokenSnippet = "${data.accessToken.take(22)}...${data.accessToken.takeLast(10)}"
                                                refreshTokenSnippet = "${data.refreshToken.take(22)}...${data.refreshToken.takeLast(10)}"
                                                tokenStatusState = true to "Đã xoay vòng (Rotate) thành công! Refresh Token cũ đã bị thu hồi trên Server và thay bằng cặp Token mới."
                                            } else {
                                                tokenStatusState = false to "Server từ chối xoay vòng (${res.code()}): ${res.body()?.error ?: "Token đã hết hạn hoặc bị thu hồi"}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        if (e is kotlinx.coroutines.CancellationException) throw e
                                        tokenStatusState = false to "Lỗi kết nối khi xoay vòng token: ${e.message}"
                                    } finally {
                                        isRotatingToken = false
                                    }
                                }
                            },
                            enabled = !isRotatingToken,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp)
                        ) {
                            if (isRotatingToken) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xoay vòng Cặp Token ngay (Test Refresh Rotation)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        tokenStatusState?.let { (ok, msg) ->
                            SemanticAlertBanner(
                                type = if (ok) AlertType.SUCCESS else AlertType.ERROR,
                                message = msg
                            )
                        }
                    }
                }

                // ================= PHẦN 2: MÃ HÓA THÔNG TIN KHÁCH HÀNG (CUSTOMER PII ENCRYPTION) =================
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CryptoModuleHeader(
                            number = "02",
                            title = "Customer PII Vault (TEE)",
                            subtitle = "Bảo vệ dữ liệu cá nhân trong Internal Storage bằng AndroidKeyStore AES-256-GCM và AES256-SIV.",
                            accent = AccentTeal,
                            icon = Icons.Default.Badge
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Họ tên KH", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Số điện thoại", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    securePrefs.saveUser(
                                        id = securePrefs.getUserId() ?: "USR-2026",
                                        email = customerEmail,
                                        fullName = customerName,
                                        role = securePrefs.getUserRole() ?: "CUSTOMER",
                                        phone = customerPhone
                                    )
                                    vault.saveCustomerPiiEncrypted(
                                        EncryptedTicketVault.CustomerPiiRecord(
                                            userId = securePrefs.getUserId() ?: "USR-2026",
                                            fullName = customerName,
                                            email = customerEmail,
                                            phone = customerPhone,
                                            identityNumberMasked = customerCccd
                                        )
                                    )
                                    rawPiiCiphertext = vault.getRawEncryptedCustomerPiiSample()
                                    rawPrefsXmlSample = securePrefs.getRawEncryptedXmlSample()
                                    decryptedPiiResult = null
                                    Toast.makeText(context, "Đã mã hóa PII bằng khóa phần cứng TEE!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal)
                            ) {
                                Text("Mã hóa lưu xuống đĩa", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val rec = vault.loadCustomerPiiDecrypted()
                                    decryptedPiiResult = if (rec != null) {
                                        "Giải mã từ TEE thành công: ${rec.fullName} | SĐT: ${rec.phone} | Email: ${rec.email} | CCCD: ${rec.identityNumberMasked}"
                                    } else {
                                        "Hãy bấm 'Mã hóa lưu xuống đĩa' trước!"
                                    }
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Giải mã từ Keystore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        CryptoTerminalBox(
                            title = "Encrypted Storage Dump",
                            badgeText = "AES-256-GCM",
                            lines = listOf(
                                "customer_pii_vault.enc" to rawPiiCiphertext,
                                "secure_ticket_prefs.xml" to rawPrefsXmlSample
                            )
                        )

                        if (decryptedPiiResult != null) {
                            SemanticAlertBanner(
                                type = if (decryptedPiiResult!!.startsWith("Giải mã")) AlertType.SUCCESS else AlertType.INFO,
                                message = decryptedPiiResult!!
                            )
                        }
                    }
                }

                // ================= PHẦN 3: CHỐNG GIẢ MẠO VÉ BẰNG CHỮ KÝ SỐ ECDSA P-256 =================
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CryptoModuleHeader(
                            number = "03",
                            title = "ECDSA P-256 Ticket Signature",
                            subtitle = "Mỗi vé được ký bằng khóa riêng ECDSA P-256 (secp256r1). Mọi chỉnh sửa payload lập tức làm mất hiệu lực chữ ký.",
                            accent = InfoFg,
                            icon = Icons.Default.VerifiedUser
                        )

                        OutlinedTextField(
                            value = ticketCanonicalPayload,
                            onValueChange = { ticketCanonicalPayload = it },
                            label = { Text("Dữ liệu vé chuẩn hóa (Canonical Ticket Payload)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val sig = KeystoreManager.signChallenge(ticketCanonicalPayload.toByteArray(Charsets.UTF_8))
                                    hardwareSignatureBase64 = sig
                                    ecdsaVerifyResult = Pair(
                                        true,
                                        "Đã ký số vé bằng Private Key trong chip AndroidKeyStore TEE!\nChữ ký: ${sig.take(40)}..."
                                    )
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                            ) {
                                Text("1. Ký số Vé (TEE)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    val isOk = try {
                                        val pubKey = KeystoreManager.getOrCreateDeviceKeyPair()
                                        val verifier = Signature.getInstance("SHA256withECDSA").apply {
                                            initVerify(pubKey)
                                            update(ticketCanonicalPayload.toByteArray(Charsets.UTF_8))
                                        }
                                        verifier.verify(android.util.Base64.decode(hardwareSignatureBase64, android.util.Base64.DEFAULT))
                                    } catch (_: Exception) {
                                        false
                                    }
                                    ecdsaVerifyResult = Pair(
                                        isOk,
                                        if (isOk) "XÁC THỰC VÉ THẬT: Chữ ký ECDSA P-256 khớp hoàn toàn với Public Key!"
                                        else "PHÁT HIỆN VÉ GIẢ MẠO: Nội dung vé đã bị chỉnh sửa sau khi ký, chữ ký ECDSA không khớp!"
                                    )
                                },
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = InfoFg)
                            ) {
                                Text("2. Xác minh Vé", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        FilledTonalButton(
                            onClick = {
                                ticketCanonicalPayload += "|HACKED_VIP_SEAT"
                                ecdsaVerifyResult = Pair(
                                    false,
                                    "CẢNH BÁO GIẢ MẠO VÉ: Kẻ gian vừa sửa nội dung vé thêm '|HACKED_VIP_SEAT'. Bấm '2. Xác minh Vé' để xem thuật toán ECDSA P-256 chặn đứng vé giả!"
                                )
                            },
                            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = DangerBg)
                        ) {
                            Text("Giả lập Kẻ gian sửa hạng vé -> Kiểm tra chống giả mạo", color = DangerFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (ecdsaVerifyResult != null) {
                            val (isSuccess, msg) = ecdsaVerifyResult!!
                            SemanticAlertBanner(
                                type = if (isSuccess) AlertType.SUCCESS else AlertType.ERROR,
                                message = msg
                            )
                        }
                    }
                }

                // ================= PHẦN 4: MÃ HÓA KHO VÉ ĐỐI XỨNG AES-256-GCM (AEAD) =================
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CryptoModuleHeader(
                            number = "04",
                            title = "AES-256-GCM (AEAD Integrity)",
                            subtitle = "Đảm bảo cả Tính bí mật (Confidentiality) lẫn Tính toàn vẹn (Authentication Tag 128-bit).",
                            accent = PrimaryBlue,
                            icon = Icons.Default.Lock
                        )

                        OutlinedTextField(
                            value = aesPlaintextInput,
                            onValueChange = { aesPlaintextInput = it },
                            label = { Text("Dữ liệu Plaintext cần mã hóa") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            shape = RoundedCornerShape(12.dp)
                        )

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
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
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
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 44.dp)
                            ) {
                                Text("Giải mã", fontSize = 12.sp)
                            }
                        }

                        if (aesResult != null) {
                            FilledTonalButton(
                                onClick = {
                                    try {
                                        val tampered = aesResult!!.ciphertextBase64.dropLast(2) + "ZZ"
                                        HybridCrypto.decryptAesGcm(tampered, currentAesKey!!)
                                        aesErrorMessage = null
                                    } catch (e: Exception) {
                                        aesErrorMessage = "PHÁT HIỆN TẤN CÔNG: ${e::class.java.simpleName} - Nhãn toàn vẹn 128-bit GCM Tag bị sai lệch, từ chối giải mã!"
                                        aesDecryptedText = null
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = DangerBg)
                            ) {
                                Text("Thử tấn công Tamper Ciphertext -> Kiểm tra AEADTag", color = DangerFg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            CryptoTerminalBox(
                                title = "AES-256-GCM Output",
                                badgeText = "128-BIT TAG",
                                lines = listOf(
                                    "KEY (256-BIT HEX)" to "${aesResult!!.keyHex.take(28)}...",
                                    "IV (96-BIT HEX)" to aesResult!!.ivHex,
                                    "CIPHERTEXT (B64)" to "${aesResult!!.ciphertextBase64.take(40)}..."
                                )
                            )
                        }

                        if (aesDecryptedText != null) {
                            SemanticAlertBanner(
                                type = AlertType.SUCCESS,
                                message = "Kết quả giải mã thành công: \"$aesDecryptedText\""
                            )
                        }

                        if (aesErrorMessage != null) {
                            SemanticAlertBanner(
                                type = AlertType.ERROR,
                                message = aesErrorMessage!!
                            )
                        }
                    }
                }

                // ================= PHẦN 5: MÃ QR ĐỘNG TOTP & BÁO CÁO RASP THIẾT BỊ =================
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    elevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        CryptoModuleHeader(
                            number = "05",
                            title = "TOTP 30s & RASP Anti-Tampering",
                            subtitle = "Giám sát mã QR xoay vòng 30s, Root, Hooking Frida/Xposed, Debugger và toàn vẹn APK.",
                            accent = PurpleAccent,
                            icon = Icons.Default.Shield
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mã TOTP: $demoTotp",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(fontFeatureSettings = "tnum"),
                                color = PurpleAccent
                            )
                            Surface(shape = RoundedCornerShape(8.dp), color = PurpleBg) {
                                Text(
                                    text = "Xoay vòng sau ${demoRemainingSec}s",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(fontFeatureSettings = "tnum"),
                                    color = PurpleText,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        LinearProgressIndicator(
                            progress = { demoRemainingSec / 30f },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = PurpleAccent,
                            trackColor = PurpleBg
                        )

                        CryptoTerminalBox(
                            title = "RASP & Integrity Telemetry",
                            badgeText = if (securityStatus.isRestrictedMode) "RISK DETECTED" else "SECURE",
                            badgeColor = if (securityStatus.isRestrictedMode) Color(0xFFF87171) else TerminalTextEmerald,
                            lines = listOf(
                                "ROOT (ROOTBEER)" to if (securityStatus.isRooted) "CẢNH BÁO (Đã Root)" else "AN TOÀN",
                                "FRIDA / XPOSED" to if (securityStatus.isFridaDetected) "PHÁT HIỆN HOOKING" else "SẠCH (Không có Frida)",
                                "DEBUGGER (JDWP)" to if (securityStatus.isDebuggerAttached) "Đang chạy chế độ Debug" else "AN TOÀN",
                                "KEYSTORE TEE" to KeystoreManager.getHardwareSecurityLevel(),
                                "APK SHA-256" to "${securityStatus.apkSignatureSha256.take(32)}...",
                                "TICKETS VAULT" to "${vault.getRawEncryptedVaultSample().take(42)}..."
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun CryptoModuleHeader(
    number: String,
    title: String,
    subtitle: String,
    accent: Color,
    icon: ImageVector
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = number,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = accent
                )
            }
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 14.sp,
                color = TextDark
            )
        }
        Text(
            text = subtitle,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = TextMuted
        )
    }
}
