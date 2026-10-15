package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.player.VlcPlayerManager
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val playerManager = remember { VlcPlayerManager(context) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerStatus by remember { mutableStateOf("Buffering...") }
    var showControls by remember { mutableStateOf(true) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }

    // Keep screen on and immersive mode
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    // Save progress to SharedPreferences
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }
    DisposableEffect(Unit) {
        prefs.edit().putString("last_played_uri", videoUri).apply()
        onDispose {
            prefs.edit().putLong("last_pos_$videoUri", currentPosition).apply()
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

    LaunchedEffect(videoUri) {
        playerManager.onStatusChanged = { status ->
            playerStatus = status
            isPlaying = status == "Playing"
        }
        playerManager.onTimeChanged = { time, len ->
            currentPosition = time
            duration = len
        }
        playerManager.playUrl(context, videoUri)
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
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
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
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
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
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
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
                        .size(64.dp)
                        .align(Alignment.Center)
                        .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(32.dp))
                ) {
                    if (isPlaying) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(width = 6.dp, height = 24.dp).background(Color.White))
                            Box(modifier = Modifier.size(width = 6.dp, height = 24.dp).background(Color.White))
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = formatDuration(currentPosition), color = Color.White, fontSize = 12.sp)
                        Text(text = formatDuration(duration), color = Color.White, fontSize = 12.sp)
                    }

                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() else 0f,
                        onValueChange = { newValue ->
                            currentPosition = newValue.toLong()
                            playerManager.seekTo(currentPosition)
                        },
                        valueRange = 0f..(if (duration > 0) duration.toFloat() else 1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFF8800),
                            activeTrackColor = Color(0xFFFF8800),
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
