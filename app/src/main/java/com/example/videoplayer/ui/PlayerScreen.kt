package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.R
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
    var videos by remember { mutableStateOf<List<VideoItem>>(MediaStoreHelper.getSampleVideos()) }

    var isPlaying by remember { mutableStateOf(playerManager.mediaPlayer.isPlaying) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playerStatus by remember { mutableStateOf("Buffering...") }
    var showControls by remember { mutableStateOf(true) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var isLooping by remember { mutableStateOf(false) }

    // Neon master gradient matching #00E5FF -> #5F6BF7 -> #B14BF7
    val neonGradient = Brush.horizontalGradient(
        listOf(Color(0xFF00E5FF), Color(0xFF5F6BF7), Color(0xFFB14BF7))
    )

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
    val videoTitle = currentVideoItem?.displayName ?: currentVideoItem?.title
        ?: android.net.Uri.parse(currentVideoUri).lastPathSegment ?: "Video"

    val prevVideo = if (currentIndex > 0) videos[currentIndex - 1] else null
    val nextVideo = if (currentIndex != -1 && currentIndex < videos.size - 1) videos[currentIndex + 1] else null

    // Set landscape orientation and keep screen on in immersive mode matching mockup
    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val uiFlags = (android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        activity?.window?.decorView?.systemUiVisibility = uiFlags

        onDispose {
            activity?.requestedOrientation = originalOrientation
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

    // Auto hide controls after 4 seconds of uninterrupted playback
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Hide gesture indicator after 1.2 second
    LaunchedEffect(gestureIndicatorText) {
        if (gestureIndicatorText != null) {
            delay(1200)
            gestureIndicatorText = null
        }
    }

    LaunchedEffect(currentVideoUri) {
        playerManager.onStatusChanged = { status ->
            playerStatus = status
            isPlaying = status == "Playing"
            if (status == "End Reached") {
                if (isLooping) {
                    playerManager.seekTo(0)
                    playerManager.resume()
                } else if (nextVideo != null) {
                    currentVideoUri = nextVideo.uri.toString()
                }
            }
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
                            currentBrightness -= dragAmount / 400f
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
                        val seekOffset = (dragAmount * 80).toLong()
                        playerManager.seekRelative(seekOffset)
                        gestureIndicatorText = "Перемотка: ${formatDuration(currentPosition)}"
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

        // Gesture indicator pill
        if (gestureIndicatorText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0C0D16).copy(alpha = 0.88f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = gestureIndicatorText!!,
                    color = Color(0xFF00F9FF),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Controls Overlay
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
                                Color.Black.copy(alpha = 0.80f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.90f)
                            )
                        )
                    )
            ) {
                // TOP BAR (Status & Title)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Nexora Logo
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0E0E18))
                                .clickable {
                                    playerManager.release()
                                    onBack()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_nexora),
                                contentDescription = "Nexora Logo",
                                modifier = Modifier.size(42.dp),
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Nexora Gradient Title
                        Text(
                            text = "Nexora",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                brush = Brush.horizontalGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFFB14BF7))
                                )
                            )
                        )

                        Spacer(modifier = Modifier.width(22.dp))

                        // Video Title
                        Text(
                            text = videoTitle,
                            color = Color(0xFFD6E7FB),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Top Right Icons: Cast & More
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    Toast.makeText(context, "Трансляция (Скоро)", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            CastIconNeon(tint = Color(0xFF00D8FF), width = 28.dp, height = 24.dp)
                        }

                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clickable {
                                    Toast.makeText(context, "Опции видео", Toast.LENGTH_SHORT).show()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            VerticalDotsNeon(tint = Color(0xFFB14BF7), width = 6.dp, height = 24.dp)
                        }
                    }
                }

                // CENTER PLAY BUTTON WITH VERTICAL CONICAL SPOTLIGHT BEAM (MATCHING MOCKUP)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(160.dp)
                        .clickable {
                            playerManager.togglePlayback()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Vertical Conical Light Flare beaming upward and downward
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2

                        // Top conical spotlight
                        val topBeam = Path().apply {
                            moveTo(centerX - 16.dp.toPx(), centerY)
                            lineTo(centerX - 60.dp.toPx(), 0f)
                            lineTo(centerX + 60.dp.toPx(), 0f)
                            lineTo(centerX + 16.dp.toPx(), centerY)
                            close()
                        }
                        drawPath(
                            path = topBeam,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF00D8FF).copy(alpha = 0.02f), Color(0xFF00E5FF).copy(alpha = 0.18f)),
                                startY = 0f,
                                endY = centerY
                            )
                        )

                        // Bottom conical spotlight
                        val bottomBeam = Path().apply {
                            moveTo(centerX - 16.dp.toPx(), centerY)
                            lineTo(centerX - 60.dp.toPx(), size.height)
                            lineTo(centerX + 60.dp.toPx(), size.height)
                            lineTo(centerX + 16.dp.toPx(), centerY)
                            close()
                        }
                        drawPath(
                            path = bottomBeam,
                            brush = Brush.verticalGradient(
                                colors = listOf(Color(0xFF7052FF).copy(alpha = 0.18f), Color(0xFFB14BF7).copy(alpha = 0.02f)),
                                startY = centerY,
                                endY = size.height
                            )
                        )

                        // Center radial glow behind the button
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF).copy(alpha = 0.35f),
                                    Color(0xFF5F6BF7).copy(alpha = 0.18f),
                                    Color.Transparent
                                ),
                                center = Offset(centerX, centerY),
                                radius = size.width * 0.45f
                            )
                        )
                    }

                    // Circular Button with Gradient Ring
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF080912).copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 4.dp.toPx()
                            val glowWidth = 9.dp.toPx()

                            // Outer soft neon halo
                            drawCircle(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFFAF5FFF).copy(alpha = 0.35f)),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2)
                                ),
                                radius = (size.minDimension - glowWidth) / 2,
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(width = glowWidth)
                            )

                            // Main crisp neon gradient ring (#00E5FF -> #AF5FFF)
                            drawCircle(
                                brush = Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFFAF5FFF)),
                                    start = Offset(0f, size.height / 2),
                                    end = Offset(size.width, size.height / 2)
                                ),
                                radius = (size.minDimension - strokeWidth) / 2,
                                center = Offset(size.width / 2, size.height / 2),
                                style = Stroke(width = strokeWidth)
                            )
                        }

                        if (playerManager.mediaPlayer.isPlaying) {
                            PauseIconSolid(tint = Color.White, width = 24.dp, height = 28.dp)
                        } else {
                            PlayIconSolid(tint = Color.White, width = 26.dp, height = 28.dp)
                        }
                    }
                }

                // BOTTOM CONTROLS & MODERN GLOWING NEON SEEKBAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 20.dp)
                ) {
                    // Time Labels Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatDuration(currentPosition),
                            color = Color(0xFF00F9FF),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (duration > 0) formatDuration(duration) else "2:45:22",
                            color = Color(0xFFCBAAFF),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // MODERN NEON GLOWING SEEKBAR MATCHING SCREENSHOT
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .pointerInput(duration) {
                                detectTapGestures { offset ->
                                    val totalWidth = size.width.toFloat()
                                    val effectiveDur = if (duration > 0) duration else 9_922_000L
                                    val fraction = (offset.x / totalWidth).coerceIn(0f, 1f)
                                    val newPos = (fraction * effectiveDur).toLong()
                                    currentPosition = newPos
                                    playerManager.seekTo(newPos)
                                }
                            }
                            .pointerInput(duration) {
                                detectHorizontalDragGestures { change, _ ->
                                    val totalWidth = size.width.toFloat()
                                    val effectiveDur = if (duration > 0) duration else 9_922_000L
                                    val fraction = (change.position.x / totalWidth).coerceIn(0f, 1f)
                                    val newPos = (fraction * effectiveDur).toLong()
                                    currentPosition = newPos
                                    playerManager.seekTo(newPos)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        ) {
                            val trackWidth = size.width
                            val centerY = size.height / 2
                            val barThickness = 4.5.dp.toPx()
                            val effectiveDur = if (duration > 0) duration else 9_922_000L
                            val progressFraction = (currentPosition.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f)
                            val thumbX = (trackWidth * progressFraction).coerceIn(10.dp.toPx(), trackWidth - 10.dp.toPx())

                            // 1. Ambient soft glowing halo under entire track
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00F9FF).copy(alpha = 0.25f),
                                        Color(0xFF5F6BF7).copy(alpha = 0.25f),
                                        Color(0xFFB14BF7).copy(alpha = 0.25f)
                                    )
                                ),
                                start = Offset(0f, centerY),
                                end = Offset(trackWidth, centerY),
                                strokeWidth = barThickness + 6.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            // 2. Base full-width luminous neon gradient bar (As seen in the screenshot!)
                            drawLine(
                                brush = Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFF00F9FF),
                                        Color(0xFF4A74FF),
                                        Color(0xFF9B48F7),
                                        Color(0xFFC044FF)
                                    )
                                ),
                                start = Offset(0f, centerY),
                                end = Offset(trackWidth, centerY),
                                strokeWidth = barThickness,
                                cap = StrokeCap.Round
                            )

                            // 3. Glowing Thumb (Neon cyan circle with radiant core)
                            // Outer soft halo
                            drawCircle(
                                color = Color(0xFF00F9FF).copy(alpha = 0.4f),
                                radius = 13.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                            // Main cyan sphere
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xFF00F9FF), Color(0xFF00A2FF)),
                                    center = Offset(thumbX, centerY),
                                    radius = 10.dp.toPx()
                                ),
                                radius = 9.5.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                            // Pure white central glint
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx(),
                                center = Offset(thumbX, centerY)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // BOTTOM CONTROL BUTTONS ROW (PROMINENT, CRISP, PROPORTIONED ICONS)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Left Group: Subtitles & Repeat
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        Toast.makeText(context, "Субтитры", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                SubtitlesIconNeon(tint = Color(0xFF00E5FF), width = 30.dp, height = 24.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        isLooping = !isLooping
                                        Toast.makeText(
                                            context,
                                            if (isLooping) "Повтор включен" else "Повтор выключен",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                RepeatIconNeon(
                                    tint = if (isLooping) Color(0xFF00F9FF) else Color(0xFF4A72FF),
                                    width = 28.dp,
                                    height = 24.dp
                                )
                            }
                        }

                        // Center Group: Prev, Play/Pause Circle, Next
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            // Previous Track
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        prevVideo?.let {
                                            currentVideoUri = it.uri.toString()
                                        } ?: Toast.makeText(context, "Это первое видео", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                PreviousIconNeon(tint = Color(0xFF4E77FF), width = 26.dp, height = 24.dp)
                            }

                            // Center Circle Play/Pause Button
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF090A14))
                                    .clickable {
                                        playerManager.togglePlayback()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 2.8.dp.toPx()
                                    drawCircle(
                                        brush = Brush.linearGradient(
                                            listOf(Color(0xFF00E5FF), Color(0xFFB14BF7))
                                        ),
                                        radius = (size.minDimension - strokeWidth) / 2,
                                        center = Offset(size.width / 2, size.height / 2),
                                        style = Stroke(width = strokeWidth)
                                    )
                                }
                                if (playerManager.mediaPlayer.isPlaying) {
                                    PauseIconSolid(tint = Color(0xFF7585FF), width = 16.dp, height = 18.dp)
                                } else {
                                    PlayIconSolid(tint = Color(0xFF7585FF), width = 18.dp, height = 18.dp)
                                }
                            }

                            // Next Track
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        nextVideo?.let {
                                            currentVideoUri = it.uri.toString()
                                        } ?: Toast.makeText(context, "Это последнее видео", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                NextIconNeon(tint = Color(0xFFAF5FFF), width = 26.dp, height = 24.dp)
                            }
                        }

                        // Right Group: Aspect Ratio & More
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        Toast.makeText(context, "Пропорции экрана", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                AspectRatioIconNeon(tint = Color(0xFF00D8FF), width = 30.dp, height = 24.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clickable {
                                        Toast.makeText(context, "Дополнительно", Toast.LENGTH_SHORT).show()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                VerticalDotsNeon(tint = Color(0xFFB14BF7), width = 6.dp, height = 24.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------
// HIGH-FIDELITY CUSTOM VECTOR ICONS MATCHING SCREENSHOTS
// -----------------------------------------------------

@Composable
fun PlayIconSolid(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val path = Path().apply {
            moveTo(2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(2f, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun PauseIconSolid(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = size.width * 0.34f
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width - barWidth, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx())
        )
    }
}

@Composable
fun PreviousIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.8.dp.toPx()
        // Left vertical bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx())
        )
        // Left pointing triangle
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(barWidth + 3.dp.toPx(), size.height / 2f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun NextIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.8.dp.toPx()
        // Right pointing triangle
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - barWidth - 3.dp.toPx(), size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = tint)
        // Right vertical bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(size.width - barWidth, 0f),
            size = Size(barWidth, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx())
        )
    }
}

@Composable
fun SubtitlesIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        // Outer rounded screen box
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Subtitle line 1
        val line1Y = size.height * 0.44f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.18f, line1Y),
            end = Offset(size.width * 0.82f, line1Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Subtitle line 2
        val line2Y = size.height * 0.70f
        drawLine(
            color = tint,
            start = Offset(size.width * 0.22f, line2Y),
            end = Offset(size.width * 0.65f, line2Y),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun RepeatIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        val path = Path().apply {
            // Top arrow path (goes right and down)
            moveTo(size.width * 0.2f, size.height * 0.35f)
            lineTo(size.width * 0.2f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.45f)

            // Bottom arrow path (goes left and up)
            moveTo(size.width * 0.8f, size.height * 0.65f)
            lineTo(size.width * 0.8f, size.height * 0.82f)
            lineTo(size.width * 0.18f, size.height * 0.82f)
            lineTo(size.width * 0.18f, size.height * 0.55f)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Arrowhead top right
        val arrowTop = Path().apply {
            moveTo(size.width * 0.70f, size.height * 0.33f)
            lineTo(size.width * 0.82f, size.height * 0.48f)
            lineTo(size.width * 0.94f, size.height * 0.33f)
        }
        drawPath(arrowTop, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Arrowhead bottom left
        val arrowBottom = Path().apply {
            moveTo(size.width * 0.06f, size.height * 0.67f)
            lineTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.30f, size.height * 0.67f)
        }
        drawPath(arrowBottom, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun AspectRatioIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        // Outer display screen
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = Size(size.width - stroke, size.height - stroke - 3.5.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Stand
        drawLine(
            color = tint,
            start = Offset(size.width * 0.36f, size.height - 1.dp.toPx()),
            end = Offset(size.width * 0.64f, size.height - 1.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Inner crop markers
        val innerMarginX = size.width * 0.28f
        val innerMarginY = size.height * 0.22f
        drawRect(
            color = tint,
            topLeft = Offset(innerMarginX, innerMarginY),
            size = Size(size.width - innerMarginX * 2, size.height - innerMarginY * 2 - 3.5.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx())
        )
    }
}

@Composable
fun CastIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        // TV display frame
        val tvPath = Path().apply {
            moveTo(size.width * 0.36f, size.height - 3.dp.toPx())
            lineTo(size.width - stroke / 2, size.height - 3.dp.toPx())
            lineTo(size.width - stroke / 2, stroke / 2)
            lineTo(stroke / 2, stroke / 2)
            lineTo(stroke / 2, size.height * 0.55f)
        }
        drawPath(tvPath, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Broadcast arcs
        drawCircle(color = tint, radius = 2.2.dp.toPx(), center = Offset(3.dp.toPx(), size.height - 3.dp.toPx()))
        drawArc(
            color = tint,
            startAngle = 270f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-4.5.dp.toPx(), size.height - 11.dp.toPx()),
            size = Size(15.dp.toPx(), 15.dp.toPx()),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun VerticalDotsNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val radius = size.width / 2f
        drawCircle(color = tint, radius = radius, center = Offset(radius, radius + 2.dp.toPx()))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height / 2f))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height - radius - 2.dp.toPx()))
    }
}
