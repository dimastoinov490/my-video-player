package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.player.VlcPlayerManager
import com.example.videoplayer.ui.theme.*
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val playerManager = remember { VlcPlayerManager(context) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Загрузка...") }
    var isControlsLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    BackHandler {
        playerManager.release()
        onBack()
    }

    // Keep screen on
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            playerManager.release()
        }
    }

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isControlsLocked) {
            delay(4000)
            showControls = false
        }
    }

    // Callbacks
    DisposableEffect(playerManager) {
        playerManager.onStatusChanged = { status ->
            statusText = status
            if (status == "Playing") isPlaying = true
            else if (status == "Paused" || status == "Stopped") isPlaying = false
        }
        playerManager.onTimeChanged = { time, length ->
            currentTimeMs = time
            if (length > 0) totalDurationMs = length
        }
        onDispose {}
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!isControlsLocked) {
                    showControls = !showControls
                } else {
                    showControls = true
                }
            }
    ) {
        // VLC Video View
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            playerManager.attachSurface(this@apply)
                            playerManager.playUrl(videoUri)
                        }

                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            playerManager.updateWindowSize(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            playerManager.detachSurface()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            playerManager.release()
                            onBack()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = videoUri.substringAfterLast('/').substringBefore('?'),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    // Share button
                    IconButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, videoUri)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться видео"))
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Поделиться",
                            tint = Color.White
                        )
                    }

                    // Speed button
                    Box {
                        TextButton(
                            onClick = { showSpeedMenu = true }
                        ) {
                            Text(
                                "${currentSpeed}x",
                                color = theme.secondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        DropdownMenu(
                            expanded = showSpeedMenu,
                            onDismissRequest = { showSpeedMenu = false },
                            modifier = Modifier.background(theme.surface)
                        ) {
                            listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "${speed}x",
                                            color = if (currentSpeed == speed) theme.primary else Color.White
                                        )
                                    },
                                    onClick = {
                                        currentSpeed = speed
                                        playerManager.setRate(speed)
                                        showSpeedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                val playBtnSize = when (theme.buttonSize) {
                    ButtonSizeOption.LARGE -> 78.dp
                    ButtonSizeOption.STANDARD -> 66.dp
                    ButtonSizeOption.COMPACT -> 52.dp
                }

                val sideBtnSize = when (theme.buttonSize) {
                    ButtonSizeOption.LARGE -> 56.dp
                    ButtonSizeOption.STANDARD -> 48.dp
                    ButtonSizeOption.COMPACT -> 38.dp
                }

                val playIconSize = when (theme.buttonSize) {
                    ButtonSizeOption.LARGE -> 40.dp
                    ButtonSizeOption.STANDARD -> 34.dp
                    ButtonSizeOption.COMPACT -> 26.dp
                }

                val sideIconSize = when (theme.buttonSize) {
                    ButtonSizeOption.LARGE -> 30.dp
                    ButtonSizeOption.STANDARD -> 26.dp
                    ButtonSizeOption.COMPACT -> 20.dp
                }

                if (theme.playerControlStyle == PlayerControlStyle.MODERN_FLOAT) {
                    // Floating Frosted Pill Bar
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(theme.cornerRadiusDp.dp),
                        color = theme.controlPanelBg ?: theme.surface.copy(alpha = 0.88f),
                        border = BorderStroke(1.5.dp, theme.cardBorder),
                        shadowElevation = 16.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            if (!isControlsLocked) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    IconButton(
                                        onClick = { isControlsLocked = !isControlsLocked },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                            contentDescription = "Блокировка",
                                            tint = if (isControlsLocked) theme.primary else Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Slider(
                                    value = if (totalDurationMs > 0) (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                    onValueChange = { frac ->
                                        val targetTime = (frac * totalDurationMs).toLong()
                                        currentTimeMs = targetTime
                                        playerManager.seekTo(targetTime)
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = theme.primary,
                                        activeTrackColor = theme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { playerManager.seekRelative(-10000L) },
                                        modifier = Modifier
                                            .size(sideBtnSize)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Replay10,
                                            contentDescription = "-10 сек",
                                            tint = Color.White,
                                            modifier = Modifier.size(sideIconSize)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (isPlaying) {
                                                playerManager.pause()
                                                isPlaying = false
                                            } else {
                                                playerManager.resume()
                                                isPlaying = true
                                            }
                                        },
                                        modifier = Modifier
                                            .size(playBtnSize)
                                            .clip(CircleShape)
                                            .background(theme.primary)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Пауза" else "Играть",
                                            tint = Color.Black,
                                            modifier = Modifier.size(playIconSize)
                                        )
                                    }

                                    IconButton(
                                        onClick = { playerManager.seekRelative(10000L) },
                                        modifier = Modifier
                                            .size(sideBtnSize)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Forward10,
                                            contentDescription = "+10 сек",
                                            tint = Color.White,
                                            modifier = Modifier.size(sideIconSize)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Управление заблокировано", color = Color.LightGray, fontSize = 12.sp)
                                    IconButton(onClick = { isControlsLocked = false }) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Разблокировать",
                                            tint = theme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Classic / Minimalist / Cinematic Layout
                    if (!isControlsLocked) {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            IconButton(
                                onClick = { playerManager.seekRelative(-10000L) },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "-10 сек",
                                    tint = Color.White,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (isPlaying) {
                                        playerManager.pause()
                                        isPlaying = false
                                    } else {
                                        playerManager.resume()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier
                                    .size(playBtnSize)
                                    .clip(CircleShape)
                                    .background(theme.primary)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Пауза" else "Играть",
                                    tint = Color.Black,
                                    modifier = Modifier.size(playIconSize)
                                )
                            }

                            IconButton(
                                onClick = { playerManager.seekRelative(10000L) },
                                modifier = Modifier
                                    .size(sideBtnSize)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "+10 сек",
                                    tint = Color.White,
                                    modifier = Modifier.size(sideIconSize)
                                )
                            }
                        }
                    }

                    // Bottom bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${formatTime(currentTimeMs)} / ${formatTime(totalDurationMs)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )

                            IconButton(
                                onClick = { isControlsLocked = !isControlsLocked }
                            ) {
                                Icon(
                                    imageVector = if (isControlsLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                    contentDescription = "Блокировка",
                                    tint = if (isControlsLocked) theme.primary else Color.White
                                )
                            }
                        }

                        if (!isControlsLocked) {
                            Slider(
                                value = if (totalDurationMs > 0) (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                                onValueChange = { frac ->
                                    val targetTime = (frac * totalDurationMs).toLong()
                                    currentTimeMs = targetTime
                                    playerManager.seekTo(targetTime)
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = theme.primary,
                                    activeTrackColor = theme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hour = totalSec / 3600
    return if (hour > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hour, min, sec)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", min, sec)
    }
}
