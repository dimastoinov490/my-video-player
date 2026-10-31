@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.videoplayer.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Rational
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.R
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.player.VlcPlayerManager
import com.example.videoplayer.ui.theme.ThemeConfigManager
import kotlinx.coroutines.delay
import java.io.File
import java.net.URLDecoder
import java.util.Locale

enum class VideoScaleMode(val label: String) {
    FIT("По размеру экрана"),
    FILL("Заполнение экрана"),
    SIXTEEN_NINE("16:9"),
    FOUR_THREE("4:3")
}

enum class VideoRepeatMode(val label: String) {
    OFF("Повтор выкл"),
    REPEAT_ONE("Повтор одного")
}

fun resolveVideoTitle(context: Context, uriString: String): String {
    try {
        if (uriString.startsWith("sample://")) {
            return when {
                uriString.contains("western_full") -> "Django_osvobojdenn.mp4"
                uriString.contains("western") -> "2026 09 19 172839.mp4"
                uriString.contains("scifi_full") -> "Solo A Star Wars Story.mp4"
                uriString.contains("scifi") -> "Izgoi_Odin_Zvyozdnyy.mp4"
                uriString.contains("nature") -> "Природа_4K.mp4"
                uriString.contains("cyber") -> "Киберпанк.mp4"
                else -> "Демо_видео.mp4"
            }
        }
        if (uriString.startsWith("content://")) {
            val uri = Uri.parse(uriString)
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
            context.contentResolver.query(uri, arrayOf(MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.TITLE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) return name
                    }
                    val titleIdx = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                    if (titleIdx >= 0) {
                        val title = cursor.getString(titleIdx)
                        if (!title.isNullOrBlank()) return title
                    }
                }
            }
        }

        val clean = uriString.substringAfterLast('/').substringBefore('?')
        val decoded = try { URLDecoder.decode(clean, "UTF-8") } catch (_: Exception) { clean }
        if (decoded.isNotBlank() && !decoded.all { it.isDigit() }) {
            return decoded
        }
    } catch (_: Throwable) {}

    try {
        val lastSegment = uriString.substringAfterLast('/')
        if (lastSegment.all { it.isDigit() }) {
            val videos = MediaStoreHelper.getVideos(context)
            val match = videos.find { it.id.toString() == lastSegment || it.uri.toString() == uriString }
            if (match != null) return match.displayName
        }
    } catch (_: Throwable) {}

    val fallback = uriString.substringAfterLast('/').substringBefore('?')
    return if (fallback.isBlank()) "Видео" else fallback
}

@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val config = ThemeConfigManager.currentConfig
    val resolvedTitle = remember(videoUri) { resolveVideoTitle(context, videoUri) }

    // Crash-proof player manager
    val playerManager = remember { VlcPlayerManager(context) }
    var useNativeFallback by remember { mutableStateOf(!playerManager.isEngineReady) }

    var isPlaying by remember { mutableStateOf(true) }
    var currentTimeMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Воспроизведение...") }
    var isControlsLocked by remember { mutableStateOf(false) }
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedMenu by remember { mutableStateOf(false) }
    var showTopOverflowMenu by remember { mutableStateOf(false) }
    var showMoreBottomSheet by remember { mutableStateOf(false) }
    var showSubtitlesDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var repeatMode by remember { mutableStateOf(VideoRepeatMode.OFF) }
    var infoFeedbackText by remember { mutableStateOf<String?>(null) }

    var nativeVideoView by remember { mutableStateOf<VideoView?>(null) }

    BackHandler {
        onBack()
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            try {
                playerManager.release()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying && !isControlsLocked) {
            delay(4200)
            showControls = false
        }
    }

    LaunchedEffect(infoFeedbackText) {
        if (infoFeedbackText != null) {
            delay(2200)
            infoFeedbackText = null
        }
    }

    DisposableEffect(playerManager) {
        playerManager.onStatusChanged = { status ->
            statusText = status
            if (status == "Playing") isPlaying = true
            else if (status == "Paused" || status == "Stopped") isPlaying = false
            else if (status == "EndReached") {
                if (repeatMode == VideoRepeatMode.REPEAT_ONE) {
                    playerManager.seekTo(0)
                    playerManager.resume()
                } else {
                    isPlaying = false
                }
            } else if (status.startsWith("Error")) {
                useNativeFallback = true
                infoFeedbackText = "Включен резервный медиа-движок Android"
            }
        }
        playerManager.onTimeChanged = { time, length ->
            currentTimeMs = time
            if (length > 0) totalDurationMs = length
        }
        onDispose {}
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000104))
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                showControls = !showControls
            }
    ) {
        val screenWidthDp = maxWidth
        val uVal = (screenWidthDp.value / 1774f).coerceAtLeast(0.2f)

        // 1. VIDEO VIEWPORT: Pure & uncorrupted
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    when (scaleMode) {
                        VideoScaleMode.FOUR_THREE -> PaddingValues(horizontal = 36.dp)
                        VideoScaleMode.SIXTEEN_NINE -> PaddingValues(vertical = 24.dp)
                        else -> PaddingValues(0.dp)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!useNativeFallback) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    try {
                                        playerManager.attachSurface(this@apply)
                                        playerManager.playUrl(videoUri)
                                    } catch (_: Throwable) {
                                        useNativeFallback = true
                                    }
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
            } else {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            nativeVideoView = this
                            val uri = when {
                                videoUri.startsWith("sample://") -> Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                                videoUri.startsWith("content://") || videoUri.startsWith("file://") || videoUri.startsWith("http") -> Uri.parse(videoUri)
                                else -> Uri.fromFile(File(videoUri))
                            }
                            setVideoURI(uri)
                            setOnPreparedListener { mp ->
                                totalDurationMs = mp.duration.toLong()
                                isPlaying = true
                                mp.start()
                            }
                            setOnCompletionListener {
                                if (repeatMode == VideoRepeatMode.REPEAT_ONE) {
                                    start()
                                } else {
                                    isPlaying = false
                                }
                            }
                            setOnErrorListener { _, _, _ ->
                                Toast.makeText(ctx, "Ошибка воспроизведения", Toast.LENGTH_SHORT).show()
                                true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                LaunchedEffect(useNativeFallback, isPlaying) {
                    while (useNativeFallback && isPlaying) {
                        nativeVideoView?.let { vv ->
                            if (vv.isPlaying) {
                                currentTimeMs = vv.currentPosition.toLong()
                                val dur = vv.duration.toLong()
                                if (dur > 0) totalDurationMs = dur
                            }
                        }
                        delay(500)
                    }
                }
            }
        }

        // Floating feedback badge
        infoFeedbackText?.let { info ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = (80f * uVal).dp.coerceAtLeast(20.dp)),
                shape = RoundedCornerShape(20.dp),
                color = Color(0xEE080C1E),
                border = BorderStroke(1.dp, Color(0xFF00D8FF)),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = info,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 2. OVERLAY CONTROLS (Exact layout from player_screen_v3.html)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // TOP BAR (Anchored to top, statusBarsPadding)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            top = (88f * uVal).dp.coerceAtLeast(8.dp),
                            start = (68f * uVal).dp.coerceAtLeast(12.dp),
                            end = (70f * uVal).dp.coerceAtLeast(12.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nexora Logo
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    infoFeedbackText = "Nexora Player v1.1.0"
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        NexoraLogoCanvas(uVal)
                    }

                    Spacer(modifier = Modifier.width((30f * uVal).dp.coerceAtLeast(6.dp)))

                    // Brand "Nexora"
                    Text(
                        text = "Nexora",
                        style = TextStyle(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF74F0FF), Color(0xFF8FA6FF), Color(0xFF9A5CFF))
                            ),
                            fontSize = (36f * uVal).sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (0.5f * uVal).sp
                        )
                    )

                    Spacer(modifier = Modifier.width((36f * uVal).dp.coerceAtLeast(8.dp)))

                    // Video file title
                    Text(
                        text = resolvedTitle,
                        color = Color(0xFFC4CBDB),
                        fontSize = (29.5f * uVal).sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width((20f * uVal).dp.coerceAtLeast(6.dp)))

                    // Cast Button
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = { showCastDialog = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        CastIconCanvas(uVal)
                    }

                    Spacer(modifier = Modifier.width((34f * uVal).dp.coerceAtLeast(6.dp)))

                    // Top Three Dots Button
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(onClick = { showTopOverflowMenu = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        TopDotsCanvas(uVal)

                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false },
                            modifier = Modifier.background(Color(0xFF0C1014))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Информация о видео", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Info, null, tint = Color(0xFF00D8FF)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    infoFeedbackText = "Формат: ${videoUri.substringAfterLast('.')} • Длительность: ${formatTime(totalDurationMs)}"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Заблокировать экран", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFF5A78FF)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    isControlsLocked = true
                                    infoFeedbackText = "Экран заблокирован от касаний"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сбросить UI к Неону", color = Color(0xFFA63EF6)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Color(0xFFA63EF6)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    ThemeConfigManager.resetToNeon(context)
                                    infoFeedbackText = "Применен стандартный Неон (#9D00FF)"
                                }
                            )
                        }
                    }
                }

                // BOTTOM CONTROLS (Anchored to bottom, navigationBarsPadding)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = (87f * uVal).dp.coerceAtLeast(12.dp))
                ) {
                    if (!isControlsLocked) {
                        // Time Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = (68f * uVal).dp.coerceAtLeast(16.dp),
                                    end = (60f * uVal).dp.coerceAtLeast(16.dp)
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentTimeMs),
                                color = Color(0xFF00D4E5),
                                fontSize = (22f * uVal).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatTime(totalDurationMs),
                                color = Color(0xFFA98CFF),
                                fontSize = (22f * uVal).sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(modifier = Modifier.height((10f * uVal).dp.coerceAtLeast(4.dp)))

                        // Seek Bar
                        val progressFraction = if (totalDurationMs > 0) {
                            (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val seekTrackBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFF00BAF1), Color(0xFF606DF7), Color(0xFFA63EF6))
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = (68f * uVal).dp.coerceAtLeast(16.dp),
                                    end = (60f * uVal).dp.coerceAtLeast(16.dp)
                                )
                                .height((44f * uVal).dp.coerceAtLeast(36.dp))
                                .pointerInput(totalDurationMs) {
                                    detectTapGestures { offset ->
                                        if (totalDurationMs > 0 && size.width > 0) {
                                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                            val target = (fraction * totalDurationMs).toLong()
                                            currentTimeMs = target
                                            if (!useNativeFallback) playerManager.seekTo(target) else nativeVideoView?.seekTo(target.toInt())
                                        }
                                    }
                                }
                                .pointerInput(totalDurationMs) {
                                    detectDragGestures { change, _ ->
                                        change.consume()
                                        if (totalDurationMs > 0 && size.width > 0) {
                                            val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                                            val target = (fraction * totalDurationMs).toLong()
                                            currentTimeMs = target
                                            if (!useNativeFallback) playerManager.seekTo(target) else nativeVideoView?.seekTo(target.toInt())
                                        }
                                    }
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Canvas(modifier = Modifier.fillMaxWidth().height((34f * uVal).dp.coerceAtLeast(24.dp))) {
                                val lineH = (5f * uVal).dp.toPx().coerceAtLeast(3f)
                                val lineY = (size.height - lineH) / 2f
                                val radiusPx = (2.5f * uVal).dp.toPx()

                                // Track filled with gradient
                                drawRoundRect(
                                    brush = seekTrackBrush,
                                    topLeft = Offset(0f, lineY),
                                    size = Size(size.width, lineH),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                                )

                                // Knob
                                val knobRadius = (17f * uVal).dp.toPx().coerceAtLeast(8f)
                                val knobCenterX = size.width * progressFraction
                                val knobCenterY = size.height / 2f

                                // Soft glow
                                drawCircle(
                                    color = Color(0x8C279DFE),
                                    radius = knobRadius + (7f * uVal).dp.toPx().coerceAtLeast(3f),
                                    center = Offset(knobCenterX, knobCenterY)
                                )
                                // Knob circle
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFF6CC0FF), Color(0xFF279DFE)),
                                        center = Offset(knobCenterX - (3f * uVal).dp.toPx(), knobCenterY - (3f * uVal).dp.toPx()),
                                        radius = knobRadius
                                    ),
                                    radius = knobRadius,
                                    center = Offset(knobCenterX, knobCenterY)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height((24f * uVal).dp.coerceAtLeast(8.dp)))

                        // Bottom Row of buttons
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((80f * uVal).dp.coerceAtLeast(48.dp))
                        ) {
                            // 1. Subtitles (b-sub, left 93u, w=52, h=46)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = ((93f * uVal).dp - 24.dp).coerceAtLeast(0.dp))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = { showSubtitlesDialog = true }),
                                contentAlignment = Alignment.Center
                            ) {
                                SubtitlesIconCanvas(uVal)
                            }

                            // 2. Repeat (b-rep, left 227u, w=50, h=46)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .offset(x = ((227f * uVal).dp - 24.dp).coerceAtLeast(0.dp))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = {
                                        repeatMode = if (repeatMode == VideoRepeatMode.OFF) VideoRepeatMode.REPEAT_ONE else VideoRepeatMode.OFF
                                        infoFeedbackText = repeatMode.label
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                RepeatIconCanvas(uVal, repeatMode != VideoRepeatMode.OFF)
                            }

                            // 3. Prev (b-prev, center - 152u, w=40, h=36)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = -((152f * uVal).dp))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = {
                                        if (!useNativeFallback) {
                                            playerManager.seekTo(0)
                                        } else {
                                            nativeVideoView?.seekTo(0)
                                            currentTimeMs = 0L
                                        }
                                        infoFeedbackText = "В начало видео"
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                PrevIconCanvas(uVal)
                            }

                            // 4. Play/Pause (b-play, center, w=80, h=80)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = {
                                        if (isPlaying) {
                                            if (!useNativeFallback) playerManager.pause() else nativeVideoView?.pause()
                                            isPlaying = false
                                        } else {
                                            if (!useNativeFallback) playerManager.resume() else nativeVideoView?.start()
                                            isPlaying = true
                                        }
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                SmallPlayPauseCanvas(uVal, isPlaying)
                            }

                            // 5. Next (b-next, center + 151u, w=40, h=36)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = ((151f * uVal).dp))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = {
                                        if (!useNativeFallback) {
                                            playerManager.seekRelative(10000L)
                                        } else {
                                            val n = ((nativeVideoView?.currentPosition ?: 0) + 10000).coerceAtMost(nativeVideoView?.duration ?: 0)
                                            nativeVideoView?.seekTo(n)
                                            currentTimeMs = n.toLong()
                                        }
                                        infoFeedbackText = "+10 сек"
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                NextIconCanvas(uVal)
                            }

                            // 6. PiP (b-fit, right 216u, w=56, h=46)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset(x = -(((216f * uVal).dp - 24.dp).coerceAtLeast(0.dp)))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = {
                                        val activity = context as? Activity
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                                            try {
                                                val params = PictureInPictureParams.Builder()
                                                    .setAspectRatio(Rational(16, 9))
                                                    .build()
                                                activity.enterPictureInPictureMode(params)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "PiP режим: ${e.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Режим PiP поддерживается на Android 8.0+", Toast.LENGTH_SHORT).show()
                                        }
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                PipIconCanvas(uVal)
                            }

                            // 7. More (b-more, right 85u, w=16, h=48)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .offset(x = -(((85f * uVal).dp - 24.dp).coerceAtLeast(0.dp)))
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(onClick = { showMoreBottomSheet = true }),
                                contentAlignment = Alignment.Center
                            ) {
                                BottomMoreDotsCanvas(uVal)
                            }
                        }
                    } else {
                        // Locked screen unlock indicator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Экран заблокирован от касаний",
                                color = Color(0xFFC4CBDB),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = { isControlsLocked = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D8FF)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.LockOpen, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Разблокировать", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Subtitles and Audio Dialog
        if (showSubtitlesDialog) {
            AlertDialog(
                onDismissRequest = { showSubtitlesDialog = false },
                title = { Text("Субтитры и Аудио", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Звуковая дорожка: По умолчанию (Русский/Оригинал)", color = Color.LightGray, fontSize = 13.sp)
                        Text("Субтитры: Вшитые (UTF-8) / Отключены", color = Color.LightGray, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubtitlesDialog = false }) {
                        Text("Закрыть", color = Color(0xFF00D8FF))
                    }
                },
                containerColor = Color(0xFF0D111A),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // Cast Search Dialog
        if (showCastDialog) {
            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                title = { Text("Беспроводная трансляция", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF00D8FF))
                        Text("Поиск приемников Google Cast / Miracast в локальной сети...", color = Color.LightGray, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Отмена", color = Color(0xFFA63EF6))
                    }
                },
                containerColor = Color(0xFF0D111A),
                shape = RoundedCornerShape(16.dp)
            )
        }

        // More Menu Bottom Sheet
        if (showMoreBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreBottomSheet = false },
                containerColor = Color(0xFF0B0E14),
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Параметры воспроизведения", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Lock screen option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                isControlsLocked = true
                                showMoreBottomSheet = false
                                infoFeedbackText = "Экран заблокирован от касаний"
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Блокировка касаний", color = Color.LightGray, fontSize = 14.sp)
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00D8FF))
                    }

                    // Speed selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Скорость: ${currentSpeed}x", color = Color.LightGray, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                FilterChip(
                                    selected = currentSpeed == spd,
                                    onClick = {
                                        currentSpeed = spd
                                        playerManager.setRate(spd)
                                        infoFeedbackText = "Скорость: ${spd}x"
                                    },
                                    label = { Text("${spd}x", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00D8FF),
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }

                    // Aspect ratio selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Масштаб: ${scaleMode.label}", color = Color.LightGray, fontSize = 14.sp)
                        Button(
                            onClick = {
                                scaleMode = when (scaleMode) {
                                    VideoScaleMode.FIT -> VideoScaleMode.FILL
                                    VideoScaleMode.FILL -> VideoScaleMode.SIXTEEN_NINE
                                    VideoScaleMode.SIXTEEN_NINE -> VideoScaleMode.FOUR_THREE
                                    VideoScaleMode.FOUR_THREE -> VideoScaleMode.FIT
                                }
                                infoFeedbackText = "Масштаб: ${scaleMode.label}"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141923)),
                            border = BorderStroke(1.dp, Color(0xFF00D8FF))
                        ) {
                            Text(scaleMode.name, color = Color(0xFF00D8FF), fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/* =========================================================================
   EXACT SVG VECTOR ICONS FROM player_screen_v3.html
   ========================================================================= */

@Composable
private fun NexoraLogoCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((62f * uVal).dp, (64f * uVal).dp)) {
        val sX = size.width / 62f
        val sY = size.height / 64f
        val strokeW = (4.4f * sX).coerceAtLeast(1.5f)
        val rectX = 2.2f * sX
        val rectY = 2.2f * sY
        val rectW = 57.6f * sX
        val rectH = 59.6f * sY
        val rx = 15f * sX
        val gLogo = Brush.linearGradient(
            colors = listOf(Color(0xFF00D8FF), Color(0xFF5A78FF), Color(0xFF9A45F2)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
        )
        drawRoundRect(
            color = Color(0xFF03071A),
            topLeft = Offset(rectX, rectY),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(rx, rx)
        )
        drawRoundRect(
            brush = gLogo,
            topLeft = Offset(rectX, rectY),
            size = Size(rectW, rectH),
            cornerRadius = CornerRadius(rx, rx),
            style = Stroke(width = strokeW)
        )
        val triPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(23f * sX, 18.5f * sY)
            lineTo(23f * sX, 45.5f * sY)
            lineTo(45f * sX, 32f * sY)
            close()
        }
        drawPath(triPath, color = Color(0xFFABE2FF))
        drawPath(
            triPath,
            color = Color(0xFFABE2FF),
            style = Stroke(
                width = (3f * sX).coerceAtLeast(1.5f),
                join = StrokeJoin.Round
            )
        )
    }
}

@Composable
private fun CastIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((52f * uVal).dp, (42f * uVal).dp)) {
        val sX = size.width / 52f
        val sY = size.height / 42f
        val gAll = Brush.linearGradient(
            colors = listOf(Color(0xFF00D8FF), Color(0xFF5A78FF), Color(0xFFA63EF6)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        // path: M12 3h30a7 7 0 0 1 7 7v22a7 7 0 0 1-7 7H28
        val tvPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sX, 3f * sY)
            lineTo(42f * sX, 3f * sY)
            cubicTo(45.86f * sX, 3f * sY, 49f * sX, 6.13f * sY, 49f * sX, 10f * sY)
            lineTo(49f * sX, 32f * sY)
            cubicTo(49f * sX, 35.86f * sY, 45.86f * sX, 39f * sY, 42f * sX, 39f * sY)
            lineTo(28f * sX, 39f * sY)
        }
        drawPath(tvPath, brush = gAll, style = stroke)

        // wave 1: M3 18 a21 21 0 0 1 21 21
        drawArc(
            brush = gAll,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-18f * sX, 18f * sY),
            size = Size(42f * sX, 42f * sY),
            style = stroke
        )
        // wave 2: M3 28 a11 11 0 0 1 11 11
        drawArc(
            brush = gAll,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-8f * sX, 28f * sY),
            size = Size(22f * sX, 22f * sY),
            style = stroke
        )
        // circle: cx = 4.5, cy = 37.5, r = 1.8
        drawCircle(brush = gAll, radius = (2.2f * sX).coerceAtLeast(1.5f), center = Offset(4.5f * sX, 37.5f * sY))
    }
}

@Composable
private fun TopDotsCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((16f * uVal).dp, (52f * uVal).dp)) {
        val sX = size.width / 16f
        val sY = size.height / 52f
        val r = (4.4f * sX).coerceAtLeast(2.5f)
        val cx = 8f * sX
        drawCircle(color = Color(0xFF8092FA), radius = r, center = Offset(cx, 7f * sY))
        drawCircle(color = Color(0xFF8A78FA), radius = r, center = Offset(cx, 26f * sY))
        drawCircle(color = Color(0xFF9560F9), radius = r, center = Offset(cx, 45f * sY))
    }
}

@Composable
private fun SubtitlesIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((52f * uVal).dp, (46f * uVal).dp)) {
        val sX = size.width / 52f
        val sY = size.height / 46f
        val gCyan = Brush.linearGradient(
            colors = listOf(Color(0xFF00DCFF), Color(0xFF3D8BFF)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            brush = gCyan,
            topLeft = Offset(2f * sX, 3f * sY),
            size = Size(48f * sX, 40f * sY),
            cornerRadius = CornerRadius(5f * sX, 5f * sY),
            style = stroke
        )
        val sw = (3.6f * sX).coerceAtLeast(1.5f)
        drawLine(brush = gCyan, start = Offset(10f * sX, 15f * sY), end = Offset(42f * sX, 15f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gCyan, start = Offset(10f * sX, 24f * sY), end = Offset(42f * sX, 24f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gCyan, start = Offset(10f * sX, 33f * sY), end = Offset(23f * sX, 33f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gCyan, start = Offset(29f * sX, 33f * sY), end = Offset(42f * sX, 33f * sY), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
private fun RepeatIconCanvas(uVal: Float, isActive: Boolean) {
    Canvas(modifier = Modifier.size((50f * uVal).dp, (46f * uVal).dp)) {
        val sX = size.width / 50f
        val sY = size.height / 46f
        val gBlue = if (isActive) {
            Brush.linearGradient(
                colors = listOf(Color(0xFF00E2FF), Color(0xFF74F0FF)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color(0xFF3FB0FF), Color(0xFF8A62FF)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        }
        val stroke = Stroke(
            width = (3.8f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val p1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(7f * sX, 21f * sY)
            cubicTo(8.5f * sX, 13f * sY, 15.5f * sX, 7f * sY, 24.5f * sX, 7f * sY)
            cubicTo(30f * sX, 7f * sY, 34f * sX, 9f * sY, 37f * sX, 12.5f * sY)
        }
        drawPath(p1, brush = gBlue, style = stroke)

        val a1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(38f * sX, 3f * sY)
            lineTo(38f * sX, 13.5f * sY)
            lineTo(27.5f * sX, 13.5f * sY)
        }
        drawPath(a1, brush = gBlue, style = stroke)

        val p2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(43f * sX, 25f * sY)
            cubicTo(41.5f * sX, 33f * sY, 34.5f * sX, 39f * sY, 25.5f * sX, 39f * sY)
            cubicTo(20f * sX, 39f * sY, 16f * sX, 37f * sY, 13f * sX, 33.5f * sY)
        }
        drawPath(p2, brush = gBlue, style = stroke)

        val a2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sX, 43f * sY)
            lineTo(12f * sX, 32.5f * sY)
            lineTo(22.5f * sX, 32.5f * sY)
        }
        drawPath(a2, brush = gBlue, style = stroke)
    }
}

@Composable
private fun PrevIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((40f * uVal).dp, (36f * uVal).dp)) {
        val sX = size.width / 40f
        val sY = size.height / 36f
        val gCyan = Brush.linearGradient(
            colors = listOf(Color(0xFF00DCFF), Color(0xFF3D8BFF)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.8f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            brush = gCyan,
            topLeft = Offset(3f * sX, 3f * sY),
            size = Size(4f * sX, 30f * sY),
            cornerRadius = CornerRadius(2f * sX, 2f * sY)
        )
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(34f * sX, 5.5f * sY)
            lineTo(34f * sX, 30.5f * sY)
            lineTo(12f * sX, 18f * sY)
            close()
        }
        drawPath(tri, brush = gCyan, style = stroke)
    }
}

@Composable
private fun SmallPlayPauseCanvas(uVal: Float, isPlaying: Boolean) {
    Canvas(modifier = Modifier.size((80f * uVal).dp, (80f * uVal).dp)) {
        val sX = size.width / 80f
        val sY = size.height / 80f
        val gRing = Brush.linearGradient(
            colors = listOf(Color(0xFF00E2FF), Color(0xFF4F8DFF), Color(0xFFA24EE6)),
            start = Offset(0f, size.height * 0.25f),
            end = Offset(size.width, size.height * 0.75f)
        )
        // Gradient Ring without glow or shadow
        drawCircle(
            brush = gRing,
            radius = 37f * sX,
            center = Offset(40f * sX, 40f * sY),
            style = Stroke(width = (4f * sX).coerceAtLeast(2f))
        )
        if (!isPlaying) {
            val playPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(33f * sX, 27f * sY)
                lineTo(33f * sX, 53f * sY)
                lineTo(55f * sX, 40f * sY)
                close()
            }
            drawPath(playPath, color = Color.White)
            drawPath(
                playPath,
                color = Color.White,
                style = Stroke(
                    width = (3f * sX).coerceAtLeast(1.5f),
                    join = StrokeJoin.Round
                )
            )
        } else {
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(29f * sX, 27f * sY),
                size = Size(8f * sX, 26f * sY),
                cornerRadius = CornerRadius(2f * sX, 2f * sY)
            )
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(43f * sX, 27f * sY),
                size = Size(8f * sX, 26f * sY),
                cornerRadius = CornerRadius(2f * sX, 2f * sY)
            )
        }
    }
}

@Composable
private fun NextIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((40f * uVal).dp, (36f * uVal).dp)) {
        val sX = size.width / 40f
        val sY = size.height / 36f
        val gPurple = Brush.linearGradient(
            colors = listOf(Color(0xFF6A8BFF), Color(0xFFB05CFF)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.8f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            brush = gPurple,
            topLeft = Offset(33f * sX, 3f * sY),
            size = Size(4f * sX, 30f * sY),
            cornerRadius = CornerRadius(2f * sX, 2f * sY)
        )
        val tri = androidx.compose.ui.graphics.Path().apply {
            moveTo(6f * sX, 5.5f * sY)
            lineTo(6f * sX, 30.5f * sY)
            lineTo(28f * sX, 18f * sY)
            close()
        }
        drawPath(tri, brush = gPurple, style = stroke)
    }
}

@Composable
private fun PipIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((56f * uVal).dp, (46f * uVal).dp)) {
        val sX = size.width / 56f
        val sY = size.height / 46f
        val gCyan = Brush.linearGradient(
            colors = listOf(Color(0xFF00DCFF), Color(0xFF3D8BFF)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        // Monitor screen: rect x=2, y=2, w=52, h=33, rx=4
        drawRoundRect(
            brush = gCyan,
            topLeft = Offset(2f * sX, 2f * sY),
            size = Size(52f * sX, 33f * sY),
            cornerRadius = CornerRadius(4f * sX, 4f * sY),
            style = stroke
        )
        // Stand: M20 43h16M28 35v8
        val sw = (3.6f * sX).coerceAtLeast(1.5f)
        drawLine(brush = gCyan, start = Offset(20f * sX, 43f * sY), end = Offset(36f * sX, 43f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gCyan, start = Offset(28f * sX, 35f * sY), end = Offset(28f * sX, 43f * sY), strokeWidth = sw, cap = StrokeCap.Round)

        // Corner brackets:
        val pTL = androidx.compose.ui.graphics.Path().apply {
            moveTo(10f * sX, 14f * sY)
            lineTo(10f * sX, 10f * sY)
            lineTo(14f * sX, 10f * sY)
        }
        drawPath(pTL, brush = gCyan, style = stroke)

        val pTR = androidx.compose.ui.graphics.Path().apply {
            moveTo(46f * sX, 14f * sY)
            lineTo(46f * sX, 10f * sY)
            lineTo(42f * sX, 10f * sY)
        }
        drawPath(pTR, brush = gCyan, style = stroke)

        val pBL = androidx.compose.ui.graphics.Path().apply {
            moveTo(10f * sX, 23f * sY)
            lineTo(10f * sX, 27f * sY)
            lineTo(14f * sX, 27f * sY)
        }
        drawPath(pBL, brush = gCyan, style = stroke)

        val pBR = androidx.compose.ui.graphics.Path().apply {
            moveTo(46f * sX, 23f * sY)
            lineTo(46f * sX, 27f * sY)
            lineTo(42f * sX, 27f * sY)
        }
        drawPath(pBR, brush = gCyan, style = stroke)
    }
}

@Composable
private fun BottomMoreDotsCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size((16f * uVal).dp, (48f * uVal).dp)) {
        val sX = size.width / 16f
        val sY = size.height / 48f
        val r = (4.4f * sX).coerceAtLeast(2.5f)
        val cx = 8f * sX
        drawCircle(color = Color(0xFF969AF7), radius = r, center = Offset(cx, 6f * sY))
        drawCircle(color = Color(0xFF9C86F8), radius = r, center = Offset(cx, 24f * sY))
        drawCircle(color = Color(0xFFA070F9), radius = r, center = Offset(cx, 42f * sY))
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val sec = totalSec % 60
    val min = (totalSec / 60) % 60
    val hour = totalSec / 3600
    return if (hour > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hour, min, sec)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }
}
