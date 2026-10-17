package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.player.VlcPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val playerManager = remember { VlcPlayerManager(context) }

    var currentVideoUri by remember { mutableStateOf(videoUri) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerStatus by remember { mutableStateOf("Buffering...") }
    var showControls by remember { mutableStateOf(true) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableStateOf(1f) }
    var isFullscreen by remember { mutableStateOf(false) }

    // Load videos list for previous/next navigation
    LaunchedEffect(Unit) {
        try {
            videos = MediaStoreHelper.getVideos(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val currentIndex = videos.indexOfFirst { it.uri.toString() == currentVideoUri }
    val prevVideo = if (currentIndex > 0) videos[currentIndex - 1] else null
    val nextVideo = if (currentIndex != -1 && currentIndex < videos.size - 1) videos[currentIndex + 1] else null

    // Keep screen on and immersive mode
    DisposableEffect(activity, isFullscreen) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uiFlags = if (isFullscreen) {
            android.view.View.SYSTEM_UI_FLAG_VISIBLE
        } else {
            (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
        activity?.window?.decorView?.systemUiVisibility = uiFlags

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // Save progress to SharedPreferences
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }
    DisposableEffect(currentVideoUri) {
        prefs.edit().putString("last_played_uri", currentVideoUri).apply()
        onDispose {
            prefs.edit().putLong("last_pos_$currentVideoUri", currentPosition).apply()
        }
    }

    // Auto hide controls after 3 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }

    // Hide gesture indicator after 1 second
    LaunchedEffect(gestureIndicatorText) {
        if (gestureIndicatorText != null) {
            delay(1000)
            gestureIndicatorText = null
        }
    }

    LaunchedEffect(currentVideoUri) {
        playerManager.onStatusChanged = { status ->
            playerStatus = status
            isPlaying = status == "Playing"
        }
        playerManager.onTimeChanged = { time, len ->
            currentPosition = time
            duration = len
        }
        playerManager.playUrl(context, currentVideoUri)
    }

    BackHandler {
        playerManager.release()
        onBack()
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            playerManager.seekRelative(-10000)
                            gestureIndicatorText = "-10 сек"
                        } else {
                            playerManager.seekRelative(10000)
                            gestureIndicatorText = "+10 сек"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2
                        if (isLeft) {
                            val lp = activity?.window?.attributes
                            var currentBrightness = lp?.screenBrightness ?: 0.5f
                            if (currentBrightness < 0f) currentBrightness = 0.5f
                            currentBrightness -= dragAmount / 500f
                            currentBrightness = currentBrightness.coerceIn(0.01f, 1f)
                            lp?.screenBrightness = currentBrightness
                            activity?.window?.attributes = lp
                            gestureIndicatorText = "Яркость: ${(currentBrightness * 100).toInt()}%"
                        } else {
                            val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                            val changeVol = if (dragAmount < 0) 1 else -1
                            val newVol = (currentVol + changeVol).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            gestureIndicatorText = "Громкость: ${((newVol.toFloat() / maxVolume) * 100).toInt()}%"
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        val seekOffset = (dragAmount * 50).toLong()
                        playerManager.seekRelative(seekOffset)
                        gestureIndicatorText = "Перемотка"
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            playerManager.attachSurface(this@apply)
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

        if (gestureIndicatorText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF0B0B0F).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = gestureIndicatorText!!, color = Color.White, fontSize = 16.sp)
            }
        }

        val showBuffering = prefs.getBoolean("show_buffering_status", false)

        if (playerStatus.startsWith("Error") || (showBuffering && playerStatus.contains("Buffering"))) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF0B0B0F).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                    .border(1.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = playerStatus, color = Color.White, fontSize = 14.sp)
            }
        }

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
                val progressFraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f

                // Center large round Play/Pause button with gradient progress ring
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(100.dp)
                        .clickable {
                            if (isPlaying) {
                                playerManager.pause()
                                isPlaying = false
                            } else {
                                playerManager.resume()
                                isPlaying = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 6.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2, strokeWidth / 2)
                        val sizeObj = androidx.compose.ui.geometry.Size(diameter, diameter)

                        // Background ring track
                        drawArc(
                            color = Color.DarkGray.copy(alpha = 0.4f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = sizeObj,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // Gradient progress ring (#00D4FF to #9B30FF)
                        val sweep = progressFraction * 360f
                        if (sweep > 0f) {
                            drawArc(
                                brush = Brush.sweepGradient(
                                    listOf(Color(0xFF00D4FF), Color(0xFF9B30FF), Color(0xFF00D4FF))
                                ),
                                startAngle = -90f,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = sizeObj,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.size(76.dp),
                        shape = CircleShape,
                        color = Color(0xFF0B0B0F).copy(alpha = 0.85f),
                        border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (isPlaying) {
                                PauseIconCustom(tint = Color.White, modifier = Modifier.size(32.dp))
                            } else {
                                PlayIconCustom(tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    }
                }

                // Glassmorphic Control Panel at bottom (24dp corners, gradient border #00D4FF to #9B30FF)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF0B0B0F).copy(alpha = 0.88f))
                        .border(1.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))), RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Time indicators
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatDuration(currentPosition), color = Color(0xFF00D4FF), fontSize = 12.sp)
                            Text(text = formatDuration(duration), color = Color.White, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Seekbar with gradient fill feel
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() else 0f,
                            onValueChange = { newValue ->
                                currentPosition = newValue.toLong()
                                playerManager.seekTo(currentPosition)
                            },
                            valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFF00D4FF),
                                activeTrackColor = Color(0xFF00D4FF),
                                inactiveTrackColor = Color.DarkGray
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Row of control buttons: Fullscreen, Previous, Play/Pause, Next, Three dots (speed)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Fullscreen button
                            IconButton(onClick = { isFullscreen = !isFullscreen }) {
                                FullscreenIconCustom(tint = Color.White)
                            }

                            // Previous video button
                            IconButton(
                                onClick = {
                                    prevVideo?.let {
                                        currentVideoUri = it.uri.toString()
                                    } ?: Toast.makeText(context, "Это первое видео", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                PreviousIconCustom(tint = if (prevVideo != null) Color.White else Color.Gray)
                            }

                            // Play/Pause button in panel
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
                                    .size(48.dp)
                                    .background(Color(0xFF1E1E2C), CircleShape)
                                    .border(1.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))), CircleShape)
                            ) {
                                if (isPlaying) {
                                    PauseIconCustom(tint = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    PlayIconCustom(tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }

                            // Next video button
                            IconButton(
                                onClick = {
                                    nextVideo?.let {
                                        currentVideoUri = it.uri.toString()
                                    } ?: Toast.makeText(context, "Это последнее видео", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                NextIconCustom(tint = if (nextVideo != null) Color.White else Color.Gray)
                            }

                            // Three dots menu (playback speed)
                            Box {
                                IconButton(onClick = { showSpeedMenu = true }) {
                                    SpeedIconCustom(tint = Color.White)
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false },
                                    modifier = Modifier.background(Color(0xFF0B0B0F)).border(1.dp, Brush.linearGradient(listOf(Color(0xFF00D4FF), Color(0xFF9B30FF))), RoundedCornerShape(8.dp))
                                ) {
                                    listOf(0.5f, 1f, 1.5f, 2f).forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "${speed}x" + if (currentSpeed == speed) " (активно)" else "",
                                                    color = if (currentSpeed == speed) Color(0xFF00D4FF) else Color.White
                                                )
                                            },
                                            onClick = {
                                                currentSpeed = speed
                                                playerManager.setRate(speed)
                                                showSpeedMenu = false
                                                Toast.makeText(context, "Скорость: ${speed}x", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom Icon Composables drawn via Canvas (No Icons.Default.Pause/SkipNext/SkipPrevious/Fullscreen)
@Composable
fun PlayIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(6f, 4f)
            lineTo(20f, 12f)
            lineTo(6f, 20f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun PauseIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(5f, 3f), size = androidx.compose.ui.geometry.Size(4f, 18f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(15f, 3f), size = androidx.compose.ui.geometry.Size(4f, 18f))
    }
}

@Composable
fun PreviousIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(4f, 4f), size = androidx.compose.ui.geometry.Size(3f, 16f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(20f, 4f)
            lineTo(9f, 12f)
            lineTo(20f, 20f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun NextIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(4f, 4f)
            lineTo(15f, 12f)
            lineTo(4f, 20f)
            close()
        }
        drawPath(path, color = tint)
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(17f, 4f), size = androidx.compose.ui.geometry.Size(3f, 16f))
    }
}

@Composable
fun FullscreenIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, 2f), end = androidx.compose.ui.geometry.Offset(8f, 2f), strokeWidth = stroke)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, 2f), end = androidx.compose.ui.geometry.Offset(2f, 8f), strokeWidth = stroke)

        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(w - 2f, 2f), end = androidx.compose.ui.geometry.Offset(w - 8f, 2f), strokeWidth = stroke)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(w - 2f, 2f), end = androidx.compose.ui.geometry.Offset(w - 2f, 8f), strokeWidth = stroke)

        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, h - 2f), end = androidx.compose.ui.geometry.Offset(8f, h - 2f), strokeWidth = stroke)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, h - 2f), end = androidx.compose.ui.geometry.Offset(2f, h - 8f), strokeWidth = stroke)

        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(w - 2f, h - 2f), end = androidx.compose.ui.geometry.Offset(w - 8f, h - 2f), strokeWidth = stroke)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(w - 2f, h - 2f), end = androidx.compose.ui.geometry.Offset(w - 2f, h - 8f), strokeWidth = stroke)
    }
}

@Composable
fun SpeedIconCustom(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 5f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 12f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 19f))
    }
}
