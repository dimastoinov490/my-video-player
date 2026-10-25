package com.example.videoplayer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color.White,
    secondary = GoldenYellow,
    onSecondary = Color.Black,
    background = DarkBackground,
    onBackground = Color.White,
    surface = SurfaceCardColor,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = NeonOrange,
    onPrimary = Color.White,
    secondary = GoldenYellow,
    onSecondary = Color.Black,
    background = Color(0xFFF7F3EE),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFF9F2),
    onSurface = Color(0xFF1A1A1A)
)

@Composable
fun VideoPlayerTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        content = content
    )
}
