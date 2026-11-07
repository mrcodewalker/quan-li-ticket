package com.example.quan_ly_ticket.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Colors extracted from designs/secureticket-premium
val AppShellBg = Color(0xFFEEF2F8)
val OrbBlue = Color(0xFFDBE5FF)
val OrbTeal = Color(0xFFD9F0EB)

val PrimaryNavy = Color(0xFF203E9A)
val PrimaryNavyDark = Color(0xFF152759)
val PrimaryBlue = Color(0xFF2D54C7)
val SecondaryBlue = Color(0xFF3C61CA)

val AccentMint = Color(0xFF69E3C2)
val AccentTeal = Color(0xFF00B8A9)
val MintBg = Color(0xFFE9F7F3)
val MintText = Color(0xFF126C5F)

val PurpleAccent = Color(0xFF8F5CFF)
val PurpleDark = Color(0xFF774AD8)
val PurpleBg = Color(0xFFF1ECFF)
val PurpleText = Color(0xFF6743B6)

val TextDark = Color(0xFF18202F)
val TextMuted = Color(0xFF6A7283)
val TextSubtle = Color(0xFF8A92A3)
val BorderLight = Color(0xFFE5E9F2)

val ScannerBg = Color(0xFF09101E)
val ScannerGlow = Color(0xFF365273)
val ScannerFrame = Color(0xFF8BA7FF)
val ScannerSuccess = Color(0xFF6EE8C8)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = AccentTeal,
    onSecondary = Color.White,
    tertiary = PurpleAccent,
    background = AppShellBg,
    onBackground = TextDark,
    surface = Color.White,
    onSurface = TextDark,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = TextMuted,
    outline = BorderLight
)

@Composable
fun SecureTicketTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * Reusable ambient background matching the preview .app-shell with soft gradient orbs
 */
@Composable
fun AppBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(AppShellBg)
    ) {
        // Decorative top-right orb
        Box(
            modifier = Modifier
                .size(240.dp)
                .offset(x = 180.dp, y = 80.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OrbBlue.copy(alpha = 0.85f), OrbBlue.copy(alpha = 0f))
                    )
                )
        )

        // Decorative bottom-left orb
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = (-100).dp, y = 500.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(OrbTeal.copy(alpha = 0.85f), OrbTeal.copy(alpha = 0f))
                    )
                )
        )

        content()
    }
}
