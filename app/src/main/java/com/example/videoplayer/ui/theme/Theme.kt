package com.example.videoplayer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun VideoPlayerTheme(
    content: @Composable () -> Unit
) {
    val theme = ThemeManager.activeTheme

    val colorScheme = darkColorScheme(
        primary = theme.primary,
        onPrimary = Color(0xFF3A1A00),
        secondary = theme.secondary,
        onSecondary = Color(0xFF3A1A00),
        background = theme.background,
        onBackground = Color.White,
        surface = theme.surface,
        onSurface = Color.White,
        surfaceVariant = theme.controlPanelBg ?: DeepVioletCardTop,
        onSurfaceVariant = GrayText,
        outline = theme.cardBorder
    )

    CompositionLocalProvider(LocalAppTheme provides theme) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
