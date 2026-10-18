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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
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

    var currentVideoUri by remember { mutableStateOf(videoUri) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerStatus by remember { mutableStateOf("Buffering...") }
    var showControls by remember { mutableStateOf(true) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var isLooping by remember { mutableStateOf(false) }

    // Master horizontal gradient matching #03B6F7 -> #5F6BF7 -> #B14BF7
    val masterGradient = Brush.horizontalGradient(
        listOf(Color(0xFF03B6F7), Color(0xFF5F6BF7), Color(0xFFB14BF7))
    )

    // Helper function to get color based on horizontal fraction (0.0 to 1.0)
    fun colorForFraction(fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return when {
            f < 0.5f -> {
                val t = f * 2f
                Color(
                    red = 0x03 + (0x5F - 0x03) * t / 255f,
                    green = 0xB6 + (0x6F - 0xB6) * t / 255f, // approx
                    blue = 0xF7
                )
            }
            else -> {
                val t = (f - 0.5f) * 2f
                Color(
                    red = 0x5F + (0xB1 - 0x5F) * t / 255f,
                    green = 0x6B + (0x4B - 0x6B) * t / 255f,
                    blue = 0xF7
                )
            }
        }
    }

    // Load videos list for previous/next navigation
    LaunchedEffect(Unit) {
        try {
            videos = MediaStoreHelper.getVideos(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val currentIndex = videos.indexOfFirst { it.uri.toString() == currentVideoUri }
    val currentVideoItem = if (currentIndex != -1) videos[currentIndex] else null
    val videoTitle = currentVideoItem?.displayName ?: currentVideoItem?.title ?: android.net.Uri.parse(currentVideoUri).lastPathSegment ?: "Video"

    val prevVideo = if (currentIndex > 0) videos[currentIndex - 1] else null
    val nextVideo = if (currentIndex != -1 && currentIndex < videos.size - 1) videos[currentIndex + 1] else null

    // Keep screen on and immersive mode
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uiFlags = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
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
        // Video SurfaceView
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

        // Gesture indicator
        if (gestureIndicatorText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xFF0B0B0F).copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = gestureIndicatorText!!, color = Color.White, fontSize = 16.sp)
            }
        }

        // Controls Overlay (No framed panel, direct on video with slight gradient shading)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.6f)
                            )
                        )
                    )
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val screenWidthDp = maxWidth
                    val screenHeightDp = maxHeight

                    // 3) Верхний ряд, центр по вертикали на 13% высоты
                    val topRowY = screenHeightDp * 0.13f

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .absoluteOffset(y = topRowY - 18.dp) // centering roughly 36dp row
                            .padding(horizontal = 33.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Логотип приложения 30dp
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .background(Color(0xFF0B0B0F).copy(alpha = 0.8f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                PlayIconCustom(tint = Color(0xFF03B6F7), modifier = Modifier.size(14.dp))
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // "Nexora" 18sp градиентом
                            Text(
                                text = "Nexora",
                                color = Color(0xFF03B6F7), // Fallback, shader or gradient text if needed
                                fontSize = 18.sp
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Название видео 14sp цветом #D6E7FB
                            Text(
                                text = videoTitle,
                                color = Color(0xFFD6E7FB),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            // Справа значок трансляции 22x18dp (в 48dp кликабельной области)
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                CastIconCustom(tint = Color(0xFFB14BF7), width = 22.dp, height = 18.dp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // и три точки 4x17dp
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable {
                                        Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                VerticalDotsIconCustom(tint = Color(0xFFB14BF7), width = 4.dp, height = 17.dp)
                            }
                        }
                    }

                    // 4) Центр на 47% высоты, по центру ширины: кольцо диаметром 76dp, толщина 3.5dp, градиент от #00D8FF слева до #AF5FFF справа, лёгкое свечение. Внутри треугольник Play 15x18dp или две полоски такого же размера.
                    val centerY = screenHeightDp * 0.47f
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .absoluteOffset(
                                x = (screenWidthDp - 76.dp) / 2,
                                y = centerY - 38.dp
                            )
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
                            val strokeWidth = 3.5.dp.toPx()
                            val radius = (size.minDimension - strokeWidth) / 2
                            val center = Offset(size.width / 2, size.height / 2)

                            // Gradient ring #00D8FF to #AF5FFF
                            drawCircle(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF00D8FF), Color(0xFFAF5FFF)),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2)
                                ),
                                radius = radius,
                                center = center,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        // Inside icon: Play 15x18dp or Pause 15x18dp
                        if (isPlaying) {
                            PauseIconCustom(tint = Color.White, width = 15.dp, height = 18.dp)
                        } else {
                            PlayIconCustom(tint = Color.White, width = 15.dp, height = 18.dp)
                        }
                    }

                    // 5) Полоса перемотки на 75% высоты, от 25dp слева до 29dp справа: толщина 3dp, градиент из п.2, бегунок 16dp. Сделай через Canvas и pointerInput (тап и перетаскивание)
                    val seekbarY = screenHeightDp * 0.75f
                    val seekbarLeftPadding = 25.dp
                    val seekbarRightPadding = 29.dp

                    // Время над полосой слева #00F9FF и справа #CBAAFF, 10sp.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .absoluteOffset(y = seekbarY - 24.dp)
                            .padding(horizontal = seekbarLeftPadding)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = formatDuration(currentPosition), color = Color(0xFF00F9FF), fontSize = 10.sp)
                            Text(text = formatDuration(duration), color = Color(0xFFCBAAFF), fontSize = 10.sp)
                        }
                    }

                    // Seekbar Canvas & Touch
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .absoluteOffset(
                                x = seekbarLeftPadding,
                                y = seekbarY - 24.dp
                            )
                            .padding(horizontal = 0.dp) // handled by width offset
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val totalWidth = size.width.toFloat()
                                    if (totalWidth > 0 && duration > 0) {
                                        val clickFraction = (offset.x / totalWidth).coerceIn(0f, 1f)
                                        val newPos = (clickFraction * duration).toLong()
                                        currentPosition = newPos
                                        playerManager.seekTo(newPos)
                                    }
                                }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures { change, _ ->
                                    val totalWidth = size.width.toFloat()
                                    if (totalWidth > 0 && duration > 0) {
                                        val dragFraction = (change.position.x / totalWidth).coerceIn(0f, 1f)
                                        val newPos = (dragFraction * duration).toLong()
                                        currentPosition = newPos
                                        playerManager.seekTo(newPos)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        ) {
                            val trackWidth = size.width
                            val progressFraction = if (duration > 0) (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
                            val thumbX = trackWidth * progressFraction

                            // Background inactive track
                            drawLine(
                                color = Color.DarkGray.copy(alpha = 0.5f),
                                start = Offset(0f, size.height / 2),
                                end = Offset(trackWidth, size.height / 2),
                                strokeWidth = size.height,
                                cap = StrokeCap.Round
                            )

                            // Active gradient track
                            if (progressFraction > 0f) {
                                drawLine(
                                    brush = masterGradient,
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(thumbX, size.height / 2),
                                    strokeWidth = size.height,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Thumb (16dp diameter)
                            drawCircle(
                                color = Color.White,
                                radius = 8.dp.toPx(),
                                center = Offset(thumbX, size.height / 2)
                            )
                        }
                    }

                    // 6) Нижний ряд на 86% высоты.
                    // Слева субтитры 24x20dp (x 33dp) и повтор 20x19dp (x 100dp).
                    // По центру три элемента с шагом 73dp между центрами: предыдущее 15x15dp, круглая кнопка диаметром 38dp с градиентным кольцом (внутри Play 10x12dp), следующее 15x15dp.
                    // Справа пропорции 25x20dp (x 741dp) и три точки 5x18dp (x 817dp).
                    val bottomRowY = screenHeightDp * 0.86f

                    // Left group: Subtitles (x=33dp), Repeat (x=100dp)
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 33.dp, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SubtitleIconCustom(tint = Color(0xFF03B6F7), width = 24.dp, height = 20.dp)
                    }

                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 100.dp, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                isLooping = !isLooping
                                Toast.makeText(context, if (isLooping) "Повтор включен" else "Повтор выключен", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        RepeatIconCustom(tint = if (isLooping) Color(0xFF03B6F7) else Color(0xFF5F6BF7), width = 20.dp, height = 19.dp)
                    }

                    // Center group: Previous, Circle Play button, Next (step 73dp between centers)
                    // Let's place center group around screen width center.
                    val centerGroupLeft = (screenWidthDp - (73.dp * 2 + 38.dp)) / 2 // approx center calculation

                    // Previous (15x15dp)
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = centerGroupLeft, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                prevVideo?.let {
                                    currentVideoUri = it.uri.toString()
                                } ?: Toast.makeText(context, "Это первое видео", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        PreviousIconCustom(tint = Color(0xFF5F6BF7), width = 15.dp, height = 15.dp)
                    }

                    // Circle Play button (diameter 38dp, gradient ring, inside Play 10x12dp)
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = centerGroupLeft + 73.dp - 5.dp, y = bottomRowY - 24.dp)
                            .size(48.dp)
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
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFF0B0B0F), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 2.dp.toPx()
                                drawCircle(
                                    brush = masterGradient,
                                    radius = (size.minDimension - strokeWidth) / 2,
                                    center = Offset(size.width / 2, size.height / 2),
                                    style = Stroke(width = strokeWidth)
                                )
                            }
                            if (isPlaying) {
                                PauseIconCustom(tint = Color(0xFF5F6BF7), width = 10.dp, height = 12.dp)
                            } else {
                                PlayIconCustom(tint = Color(0xFF5F6BF7), width = 10.dp, height = 12.dp)
                            }
                        }
                    }

                    // Next (15x15dp)
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = centerGroupLeft + 73.dp * 2, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                nextVideo?.let {
                                    currentVideoUri = it.uri.toString()
                                } ?: Toast.makeText(context, "Это последнее видео", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        NextIconCustom(tint = Color(0xFFB14BF7), width = 15.dp, height = 15.dp)
                    }

                    // Right group: Proportions (x 741dp or relative to right) and three dots (x 817dp)
                    // Given screen width 857dp, let's use exact fixed dp positions as specified
                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 741.dp, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        ProportionsIconCustom(tint = Color(0xFFB14BF7), width = 25.dp, height = 20.dp)
                    }

                    Box(
                        modifier = Modifier
                            .absoluteOffset(x = 817.dp, y = bottomRowY - 24.dp)
                            .size(48.dp)
                            .clickable {
                                Toast.makeText(context, "Скоро", Toast.LENGTH_SHORT).show()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        VerticalDotsIconCustom(tint = Color(0xFFB14BF7), width = 5.dp, height = 18.dp)
                    }
                }
            }
        }
    }
}

// 7) Значки только контуром через Canvas/Path, толщина линии около 2dp, Icons.Default.* не использовать.
@Composable
fun PlayIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, size.height / 2)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun PauseIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = size.width / 3f
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(barWidth, size.height))
        drawRect(color = tint, topLeft = Offset(size.width - barWidth, 0f), size = Size(barWidth, size.height))
    }
}

@Composable
fun PreviousIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.dp.toPx()
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(barWidth, size.height))
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(barWidth + 2f, size.height / 2)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun NextIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - barWidth - 2f, size.height / 2)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = tint)
        drawRect(color = tint, topLeft = Offset(size.width - barWidth, 0f), size = Size(barWidth, size.height))
    }
}

@Composable
fun CastIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.dp.toPx()
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(size.width, size.height - 4f), style = Stroke(width = stroke))
        val path = Path().apply {
            moveTo(2f, size.height)
            quadraticBezierTo(size.width / 2, size.height - 8f, size.width - 2f, size.height)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun SubtitleIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.dp.toPx()
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(size.width, size.height), style = Stroke(width = stroke))
        val lineY = size.height * 0.6f
        drawLine(color = tint, start = Offset(size.width * 0.2f, lineY), end = Offset(size.width * 0.8f, lineY), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(size.width * 0.2f, lineY + 4.dp.toPx()), end = Offset(size.width * 0.5f, lineY + 4.dp.toPx()), strokeWidth = stroke)
    }
}

@Composable
fun RepeatIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.dp.toPx()
        val path = Path().apply {
            moveTo(4f, 4f)
            lineTo(size.width - 4f, 4f)
            moveTo(size.width - 8f, 1f)
            lineTo(size.width - 4f, 4f)
            lineTo(size.width - 8f, 7f)

            moveTo(size.width - 4f, size.height - 4f)
            lineTo(4f, size.height - 4f)
            moveTo(8f, size.height - 7f)
            lineTo(4f, size.height - 4f)
            lineTo(8f, size.height - 1f)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun ProportionsIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.dp.toPx()
        drawRect(color = tint, topLeft = Offset(0f, 0f), size = Size(size.width, size.height), style = Stroke(width = stroke))
        // Inner dashed or framing rectangle representing aspect ratio resize
        drawRect(color = tint, topLeft = Offset(size.width * 0.25f, size.height * 0.25f), size = Size(size.width * 0.5f, size.height * 0.5f), style = Stroke(width = stroke * 0.75f))
    }
}

@Composable
fun VerticalDotsIconCustom(tint: Color, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val radius = size.width / 2f
        drawCircle(color = tint, radius = radius, center = Offset(radius, radius))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height / 2f))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height - radius))
    }
}
