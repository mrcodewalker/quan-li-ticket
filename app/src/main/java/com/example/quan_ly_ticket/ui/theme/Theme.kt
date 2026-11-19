package com.example.quan_ly_ticket.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

// Clean, bright, modern light palette (no heavy black blocks)
val AppShellBg = Color(0xFFF5F7FB)
val OrbBlue = Color(0xFFE0EAFF)
val OrbTeal = Color(0xFFDCFCE7)

val PrimaryNavy = Color(0xFF1E3A8A)       // Rich royal navy-blue (never black)
val PrimaryNavyDark = Color(0xFF1D4ED8)   // Vibrant cobalt blue
val PrimaryBlue = Color(0xFF2563EB)       // Crisp signature blue
val SecondaryBlue = Color(0xFF3B82F6)

val AccentMint = Color(0xFF10B981)
val AccentTeal = Color(0xFF0D9488)
val MintBg = Color(0xFFECFDF5)
val MintText = Color(0xFF047857)

val PurpleAccent = Color(0xFF7C3AED)
val PurpleDark = Color(0xFF6D28D9)
val PurpleBg = Color(0xFFF5F3FF)
val PurpleText = Color(0xFF6D28D9)

val TextDark = Color(0xFF1E293B)
val TextMuted = Color(0xFF64748B)
val TextSubtle = Color(0xFF94A3B8)
val BorderLight = Color(0xFFE2E8F0)

val ScannerBg = Color(0xFF0F172A)
val ScannerGlow = Color(0xFF1E3A8A)
val ScannerFrame = Color(0xFF60A5FA)
val ScannerSuccess = Color(0xFF34D399)

data class PosterStyle(
    val bgStart: Color,
    val bgEnd: Color,
    val accent: Color,
    val tag: String,
    val imageUrl: String
)

/**
 * Curated real concert/conference photography & solid category colors per event.
 */
fun posterStyleForEvent(title: String?, description: String? = null): PosterStyle {
    val t = ((title ?: "") + " " + (description ?: "")).lowercase()
    return when {
        t.contains("chông gai") -> PosterStyle(
            Color(0xFFDC2626), Color(0xFFB91C1C), Color(0xFFDC2626), "CONCERT",
            "https://images.unsplash.com/photo-1540039155733-5bb30b53aa14?w=600&q=80"
        )
        t.contains("say hi") -> PosterStyle(
            Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF2563EB), "LIVE SHOW",
            "https://images.unsplash.com/photo-1470229722913-7c0e2dbbafd3?w=600&q=80"
        )
        t.contains("hà anh tuấn") -> PosterStyle(
            Color(0xFFB45309), Color(0xFF92400E), Color(0xFFB45309), "ACOUSTIC",
            "https://images.unsplash.com/photo-1501281668745-f7f57925c3b4?w=600&q=80"
        )
        t.contains("đen vâu") || t.contains("đồng âm") -> PosterStyle(
            Color(0xFF047857), Color(0xFF065F46), Color(0xFF047857), "HIP-HOP",
            "https://images.unsplash.com/photo-1459749411175-04bf5292ceea?w=600&q=80"
        )
        t.contains("mỹ tâm") -> PosterStyle(
            Color(0xFFBE185D), Color(0xFF9D174D), Color(0xFFBE185D), "LIVE SHOW",
            "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&q=80"
        )
        t.contains("valorant") || t.contains("esports") -> PosterStyle(
            Color(0xFFE11D48), Color(0xFFBE123C), Color(0xFFE11D48), "ESPORTS",
            "https://images.unsplash.com/photo-1542751371-adc38448a05e?w=600&q=80"
        )
        t.contains("genfest") -> PosterStyle(
            Color(0xFF0284C7), Color(0xFF0369A1), Color(0xFF0284C7), "FESTIVAL",
            "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&q=80"
        )
        t.contains("kma") || t.contains("an toàn") -> PosterStyle(
            Color(0xFF0F766E), Color(0xFF115E59), Color(0xFF0F766E), "SECURITY",
            "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=600&q=80"
        )
        t.contains("securefest") -> PosterStyle(
            Color(0xFF1D4ED8), Color(0xFF1E40AF), Color(0xFF1D4ED8), "SUMMIT",
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&q=80"
        )
        t.contains("google") || t.contains("cloud") -> PosterStyle(
            Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF2563EB), "TECH",
            "https://images.unsplash.com/photo-1505373877841-8d25f7d46678?w=600&q=80"
        )
        else -> PosterStyle(
            Color(0xFF2563EB), Color(0xFF1D4ED8), Color(0xFF2563EB), "EVENT",
            "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&q=80"
        )
    }
}

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
    surfaceVariant = Color(0xFFF1F3F6),
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
 * Clean editorial app canvas without generic blurred gradient blobs.
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
            content = content
        )
    }
}
