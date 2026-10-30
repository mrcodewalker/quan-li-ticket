package com.example.quan_ly_ticket.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PrimaryBlue = Color(0xFF1E3A8A)
val SecondaryTeal = Color(0xFF0D9488)
val AccentGreen = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceCard = Color(0xFFFFFFFF)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = SecondaryTeal,
    tertiary = AccentGreen,
    error = ErrorRed,
    background = BackgroundLight,
    surface = SurfaceCard
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

