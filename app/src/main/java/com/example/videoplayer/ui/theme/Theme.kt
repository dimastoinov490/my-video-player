package com.example.videoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun VideoPlayerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
