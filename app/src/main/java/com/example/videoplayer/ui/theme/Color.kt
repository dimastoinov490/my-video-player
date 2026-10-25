package com.example.videoplayer.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val DarkBackground = Color(0xFF0D0D0D)
val SurfaceCardColor = Color(0xFF1E1E1E)
val NeonOrange = Color(0xFFFF8A00)
val GoldenYellow = Color(0xFFFFAE33)
val GoldenYellowLight = Color(0xFFFF9500)
val GoldenYellowGlow = Color(0x66FF8A00)
val GrayText = Color(0xFF9E9E9E)

val GoldenYellowGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF8A00), Color(0xFFFFAE33))
)

val NeonOrangeGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF9500), Color(0xFFFF8A00))
)
