package com.example.quan_ly_ticket.ui.screens

import android.widget.Toast
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
import com.example.quan_ly_ticket.data.api.ApiClient
import com.example.quan_ly_ticket.data.local.EncryptedTicketVault
import com.example.quan_ly_ticket.data.local.SecurePreferences
import com.example.quan_ly_ticket.data.model.RefreshTokenRequest
import com.example.quan_ly_ticket.security.CryptoHelper
import com.example.quan_ly_ticket.security.HybridCrypto
import com.example.quan_ly_ticket.security.KeystoreManager
import com.example.quan_ly_ticket.security.SecurityCheck
import com.example.quan_ly_ticket.ui.theme.AppBackground
import com.example.quan_ly_ticket.ui.theme.PrimaryBlue
import com.example.quan_ly_ticket.ui.theme.PrimaryNavy
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
    var tokenStatusMsg by remember { mutableStateOf<String?>(null) }
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
                TopAppBar(
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text("Trung tâm Kiểm chứng Bảo mật (Crypto Lab)", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                            Text("Trực quan hóa Dual-Token · Mã hóa PII · Chống giả mạo vé · RASP", fontSize = 10.sp, color = Color.Gray)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
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
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ================= PHẦN 1: QUẢN TRỊ PHIÊN DUAL-TOKEN (ACCESS & REFRESH TOKEN ROTATION) =================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VpnKey, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "1. Cơ chế Dual-Token & Refresh Token Rotation",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = PrimaryNavy
                            )
                        }
                        Text(
                            text = "Access Token ngắn hạn (15 phút) + Refresh Token (7 ngày) lưu trong EncryptedSharedPreferences. Tự động xoay vòng và phát hiện đánh cắp phiên (Token Reuse Detection).",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF8FAFC),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "• Access Token (JWT 15m): $accessTokenSnippet",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF1E293B)
                                )
                                Text(
                                    text = "• Refresh Token (7d): $refreshTokenSnippet",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF0F766E)
                                )
                                Text(
                                    text = "• Số lần đã xoay vòng (Rotation Count): $rotationCount lần",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                isRotatingToken = true
                                tokenStatusMsg = null
                                scope.launch {
                                    try {
                                        val currentRefresh = securePrefs.getRefreshToken()
                                        if (currentRefresh.isNullOrBlank()) {
                                            tokenStatusMsg = "Chưa có Refresh Token. Hãy đăng nhập trước!"
                                        } else {
                                            val api = ApiClient.getService(context)
                                            val res = api.refreshToken(RefreshTokenRequest(currentRefresh))
                                            if (res.isSuccessful && res.body()?.success == true && res.body()?.data != null) {
                                                val data = res.body()!!.data!!
                                                securePrefs.saveAuthTokens(data.accessToken, data.refreshToken, data.expiresIn)
                                                rotationCount = securePrefs.incrementRefreshRotationCount()
                                                accessTokenSnippet = "${data.accessToken.take(22)}...${data.accessToken.takeLast(10)}"
                                                refreshTokenSnippet = "${data.refreshToken.take(22)}...${data.refreshToken.takeLast(10)}"
                                                tokenStatusMsg = "✓ Đã xoay vòng (Rotate) thành công! Refresh Token cũ đã bị thu hồi trên Server và thay bằng cặp Token mới."
                                            } else {
                                                tokenStatusMsg = "Server từ chối xoay vòng (${res.code()}): ${res.body()?.error ?: "Token đã hết hạn hoặc bị thu hồi"}"
                                            }
                                        }
                                    } catch (e: Exception) {
                                        tokenStatusMsg = "Lỗi kết nối khi xoay vòng token: ${e.message}"
                                    } finally {
                                        isRotatingToken = false
                                    }
                                }
                            },
                            enabled = !isRotatingToken,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isRotatingToken) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                            } else {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Xoay vòng Cặp Token ngay (Test Refresh Token Rotation)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (tokenStatusMsg != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tokenStatusMsg!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (tokenStatusMsg!!.startsWith("✓")) Color(0xFF15803D) else Color(0xFFDC2626)
                            )
                        }
                    }
                }

                // ================= PHẦN 2: MÃ HÓA THÔNG TIN KHÁCH HÀNG (CUSTOMER PII ENCRYPTION) =================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF0F766E))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "2. Mã hóa Thông tin Khách hàng (Customer PII Vault)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F766E)
                            )
                        }
                        Text(
                            text = "Bảo vệ dữ liệu cá nhân (Họ tên, Email, SĐT, CCCD) trong phân vùng Internal Storage bằng khóa phần cứng AndroidKeyStore AES-256-GCM và EncryptedSharedPreferences (AES256-SIV).",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = customerName,
                                onValueChange = { customerName = it },
                                label = { Text("Họ tên KH", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = customerPhone,
                                onValueChange = { customerPhone = it },
                                label = { Text("Số điện thoại", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))
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
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Giải mã từ Keystore", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF0FDF4),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFFBBF7D0), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Bản mã file customer_pii_vault.enc (AES-256-GCM TEE):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = rawPiiCiphertext,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF15803D)
                                )
                                Text(
                                    text = "Bản mã file secure_ticket_prefs.xml (AES256-SIV/GCM):",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF166534)
                                )
                                Text(
                                    text = rawPrefsXmlSample,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF334155)
                                )
                            }
                        }

                        if (decryptedPiiResult != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = decryptedPiiResult!!,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F766E)
                            )
                        }
                    }
                }

                // ================= PHẦN 3: CHỐNG GIẢ MẠO VÉ BẰNG CHỮ KÝ SỐ ECDSA P-256 =================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color(0xFF0284C7))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "3. Chống Giả Mạo Vé: Chữ ký số ECDSA P-256 (secp256r1)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF0284C7)
                            )
                        }
                        Text(
                            text = "Mỗi vé được ký bằng khóa riêng ECDSA P-256. Nếu kẻ gian sửa mã vé hoặc nâng hạng ghế (từ STANDARD lên VIP), chữ ký số lập tức mất hiệu lực.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = ticketCanonicalPayload,
                            onValueChange = { ticketCanonicalPayload = it },
                            label = { Text("Dữ liệu vé chuẩn hóa (Canonical Ticket Payload)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val sig = KeystoreManager.signChallenge(ticketCanonicalPayload.toByteArray(Charsets.UTF_8))
                                    hardwareSignatureBase64 = sig
                                    ecdsaVerifyResult = Pair(
                                        true,
                                        "✓ Đã ký số vé bằng Private Key trong chip AndroidKeyStore TEE!\nChữ ký: ${sig.take(40)}..."
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
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
                                        if (isOk) "✓ XÁC THỰC VÉ THẬT: Chữ ký ECDSA P-256 khớp hoàn toàn với Public Key!"
                                        else "✗ PHÁT HIỆN VÉ GIẢ MẠO: Nội dung vé đã bị chỉnh sửa sau khi ký, chữ ký ECDSA không khớp!"
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                            ) {
                                Text("2. Xác minh Vé", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        FilledTonalButton(
                            onClick = {
                                ticketCanonicalPayload += "|HACKED_VIP_SEAT"
                                ecdsaVerifyResult = Pair(
                                    false,
                                    "✗ CẢNH BÁO GIẢ MẠO VÉ: Kẻ gian vừa sửa nội dung vé thêm '|HACKED_VIP_SEAT'. Bấm '2. Xác minh Vé' để xem thuật toán ECDSA P-256 chặn đứng vé giả!"
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFEE2E2))
                        ) {
                            Text("Giả lập Kẻ gian sửa hạng vé -> Kiểm tra chống giả mạo", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        if (ecdsaVerifyResult != null) {
                            val (isSuccess, msg) = ecdsaVerifyResult!!
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSuccess) Color(0xFFDCFCE7) else Color(0xFFFEE2E2),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = msg,
                                    modifier = Modifier.padding(10.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSuccess) Color(0xFF166534) else Color(0xFFDC2626)
                                )
                            }
                        }
                    }
                }

                // ================= PHẦN 4: MÃ HÓA KHO VÉ ĐỐI XỨNG AES-256-GCM (AEAD) =================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = PrimaryBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "4. Mã hóa Kho vé AES-256-GCM (AEAD & Integrity Tag)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = PrimaryBlue
                            )
                        }
                        Text(
                            text = "Đảm bảo cả Tính bí mật (Confidentiality) lẫn Tính toàn vẹn (Authentication Tag 128-bit).",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(10.dp))

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

                        if (aesResult != null) {
                            Spacer(modifier = Modifier.height(8.dp))
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
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFFEE2E2))
                            ) {
                                Text("Thử tấn công Tamper Ciphertext -> Kiểm tra AEADTag", color = Color(0xFFDC2626), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

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
                                    Text("Ciphertext (Base64): ${aesResult!!.ciphertextBase64.take(36)}...", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PrimaryBlue)
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

                // ================= PHẦN 5: MÃ QR ĐỘNG TOTP & BÁO CÁO RASP THIẾT BỊ =================
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF7C3AED))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "5. QR Động TOTP 30s & Tự vệ RASP (Anti-Tampering)",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                color = Color(0xFF7C3AED)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Mã TOTP hiện tại: $demoTotp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF7C3AED)
                            )
                            Text(
                                text = "Xoay vòng sau ${demoRemainingSec}s",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { demoRemainingSec / 30f },
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF7C3AED),
                            trackColor = Color(0xFFEDE9FE)
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = Color(0xFFE2E8F0))
                        Spacer(modifier = Modifier.height(10.dp))

                        Text(text = "• Kiểm tra Root (RootBeer): ${if (securityStatus.isRooted) "CẢNH BÁO (Đã Root)" else "AN TOÀN"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "• Kiểm tra Hooking Frida/Xposed (/proc/self/maps): ${if (securityStatus.isFridaDetected) "PHÁT HIỆN HOOKING" else "SẠCH (Không có Frida)"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Text(text = "• Kiểm tra Debugger (JDWP): ${if (securityStatus.isDebuggerAttached) "Đang chạy chế độ Debug" else "AN TOÀN"}", fontSize = 11.sp)
                        Text(text = "• Môi trường phần cứng Keystore: ${KeystoreManager.getHardwareSecurityLevel()}", fontSize = 11.sp, color = Color(0xFF059669), fontWeight = FontWeight.Bold)
                        Text(text = "• Chữ ký số APK (SHA-256): ${securityStatus.apkSignatureSha256.take(29)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                        Text(text = "• Kho vé mã hóa (tickets_vault.enc): ${vault.getRawEncryptedVaultSample().take(42)}...", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
