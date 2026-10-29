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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.R
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.player.VlcPlayerManager
import com.example.videoplayer.ui.terminal.TerminalConsoleOverlay
import com.example.videoplayer.ui.theme.ButtonConfig
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

        if (uriString.contains(".decrypted")) {
            val id = uriString.substringAfterLast('/').substringBefore(".decrypted")
            val secureRepo = com.example.videoplayer.security.SecureVaultRepository(context)
            val vaultItem = secureRepo.listItems().find { it.id == id }
            if (vaultItem != null) return vaultItem.displayName
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
    var showTerminalConsole by remember { mutableStateOf(false) }

    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var repeatMode by remember { mutableStateOf(VideoRepeatMode.OFF) }
    var infoFeedbackText by remember { mutableStateOf<String?>(null) }

    var nativeVideoView by remember { mutableStateOf<VideoView?>(null) }

    BackHandler {
        if (showTerminalConsole) {
            showTerminalConsole = false
        } else {
            onBack()
        }
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
        if (showControls && isPlaying && !isControlsLocked && !showTerminalConsole) {
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

    fun getShape(shapeName: String): Shape = when (shapeName) {
        "square" -> RoundedCornerShape(6.dp)
        "rounded", "squircle" -> RoundedCornerShape(12.dp)
        "pill" -> RoundedCornerShape(24.dp)
        else -> CircleShape
    }

    val buttonShapeObj = getShape(config.buttonShape)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(config.backgroundColor)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (!showTerminalConsole) {
                    showControls = !showControls
                }
            }
    ) {
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
                    .padding(top = 64.dp),
                shape = RoundedCornerShape(20.dp),
                color = config.controlPanelBgColor.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, config.secondaryAccentColor),
                shadowElevation = 8.dp
            ) {
                Text(
                    text = info,
                    color = config.textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // 2. OVERLAY CONTROLS
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.50f))
            ) {
                // TOP BAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }

                    // Logo Button / Branding (JSON configured)
                    if (config.logo.visible) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(config.logo.sizeDp.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.6f))
                                .border(
                                    1.5.dp,
                                    Brush.linearGradient(
                                        listOf(config.secondaryAccentColor, config.primaryAccentColor)
                                    ),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Nexora Logo",
                                tint = config.secondaryAccentColor,
                                modifier = Modifier.size((config.logo.sizeDp * 0.65f).dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Nexora",
                            color = config.secondaryAccentColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Title (JSON configured)
                    if (config.title.visible) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = resolvedTitle,
                                color = config.textColor,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                            if (useNativeFallback) {
                                Text(
                                    text = "Android MediaPlayer Engine",
                                    color = config.secondaryAccentColor,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Terminal Console Toggle Button [>_]
                    IconButton(
                        onClick = { showTerminalConsole = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(config.primaryAccentColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                            .border(1.dp, config.secondaryAccentColor, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            ">_",
                            color = config.secondaryAccentColor,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Cast Button (JSON configured)
                    if (config.castButton.visible) {
                        IconButton(
                            onClick = { showCastDialog = true },
                            modifier = Modifier.size(config.castButton.sizeDp.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Cast,
                                contentDescription = "Трансляция (Cast)",
                                tint = config.secondaryAccentColor
                            )
                        }
                    }

                    // Overflow Menu Button (JSON configured)
                    if (config.overflowMenu.visible) {
                        Box {
                            IconButton(
                                onClick = { showTopOverflowMenu = true },
                                modifier = Modifier.size(config.overflowMenu.sizeDp.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Меню",
                                    tint = Color.White
                                )
                            }

                            DropdownMenu(
                                expanded = showTopOverflowMenu,
                                onDismissRequest = { showTopOverflowMenu = false },
                                modifier = Modifier.background(config.controlPanelBgColor)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Терминал и JSON пайплайн", color = config.secondaryAccentColor) },
                                    leadingIcon = { Icon(Icons.Default.Terminal, null, tint = config.secondaryAccentColor) },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        showTerminalConsole = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Информация о видео", color = Color.White) },
                                    leadingIcon = { Icon(Icons.Default.Info, null, tint = Color.White) },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        infoFeedbackText = "Формат: ${videoUri.substringAfterLast('.')} • Длительность: ${formatTime(totalDurationMs)}"
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Сбросить UI к Неону", color = config.primaryAccentColor) },
                                    leadingIcon = { Icon(Icons.Default.Refresh, null, tint = config.primaryAccentColor) },
                                    onClick = {
                                        showTopOverflowMenu = false
                                        ThemeConfigManager.resetToNeon(context)
                                        infoFeedbackText = "Применен стандартный Неон (#9D00FF)"
                                    }
                                )
                            }
                        }
                    }
                }

                // CENTER: BigPlayButton (Чистая центральная кнопка со световым лучом со скриншота)
                if (config.bigPlayButton.visible && !isControlsLocked) {
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center
                    ) {
                        // Вертикальный луч света со скриншота (Spotlight Beam)
                        if (config.spotlightBeamEnabled) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier.size(180.dp, 320.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val path = androidx.compose.ui.graphics.Path().apply {
                                    moveTo(w * 0.5f, h * 0.5f)
                                    lineTo(w * 0.15f, 0f)
                                    lineTo(w * 0.85f, 0f)
                                    close()
                                    moveTo(w * 0.5f, h * 0.5f)
                                    lineTo(w * 0.15f, h)
                                    lineTo(w * 0.85f, h)
                                    close()
                                }
                                drawPath(
                                    path = path,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            config.secondaryAccentColor.copy(alpha = 0.0f),
                                            config.secondaryAccentColor.copy(alpha = 0.22f),
                                            config.primaryAccentColor.copy(alpha = 0.28f),
                                            config.secondaryAccentColor.copy(alpha = 0.22f),
                                            config.secondaryAccentColor.copy(alpha = 0.0f)
                                        )
                                    )
                                )
                            }
                        }

                        // Круглая центральная кнопка с контурным градиентным кольцом (без размытия)
                        Box(
                            modifier = Modifier
                                .size(config.bigPlayButton.sizeDp.dp)
                                .clip(buttonShapeObj)
                                .background(config.backgroundColor.copy(alpha = 0.9f))
                                .border(
                                    width = config.centerPlayBorderWidthDp.dp,
                                    brush = Brush.sweepGradient(
                                        listOf(
                                            config.secondaryAccentColor,
                                            config.primaryAccentColor,
                                            config.secondaryAccentColor
                                        )
                                    ),
                                    shape = buttonShapeObj
                                )
                                .clickable {
                                    if (isPlaying) {
                                        if (!useNativeFallback) playerManager.pause() else nativeVideoView?.pause()
                                        isPlaying = false
                                    } else {
                                        if (!useNativeFallback) playerManager.resume() else nativeVideoView?.start()
                                        isPlaying = true
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Играть",
                                tint = Color.White,
                                modifier = Modifier.size((config.bigPlayButton.sizeDp * 0.50f).dp)
                            )
                        }
                    }
                }

                // BOTTOM CONTROLS PANEL
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(config.cornerRadiusDp.dp),
                    color = config.controlPanelBgColor.copy(alpha = 0.94f),
                    border = BorderStroke(1.5.dp, config.primaryAccentColor.copy(alpha = 0.6f)),
                    shadowElevation = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = config.controlPanelPaddingDp.dp, vertical = 10.dp)
                    ) {
                        if (!isControlsLocked) {
                            // Time Labels Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentTimeMs),
                                    color = config.secondaryAccentColor,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = formatTime(totalDurationMs),
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // GRADIENT PROGRESS BAR (#9D00FF -> #00E5FF)
                            val progressFraction = if (totalDurationMs > 0) {
                                (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                            } else 0f

                            val gradientBrush = Brush.horizontalGradient(
                                colors = listOf(config.progressBarStartColor, config.progressBarEndColor)
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp)
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
                                Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                                    // Background Track
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.15f),
                                        size = size,
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                    // Active Gradient Progress
                                    drawRoundRect(
                                        brush = gradientBrush,
                                        size = Size(width = size.width * progressFraction, height = size.height),
                                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                                    )
                                    // Thumb Circle (clean contour without blur)
                                    drawCircle(
                                        color = config.secondaryAccentColor,
                                        radius = 6.dp.toPx(),
                                        center = Offset(x = size.width * progressFraction, y = size.height / 2)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // BOTTOM BAR BUTTONS (ВСЕ КНОПКИ СО СКРИНШОТА)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 1. SubtitlesListButton (CC / Subtitles)
                                if (config.subtitlesListButton.visible) {
                                    StyledIconButton(
                                        config = config.subtitlesListButton,
                                        shape = buttonShapeObj,
                                        onClick = { showSubtitlesDialog = true }
                                    ) {
                                        Icon(Icons.Default.Subtitles, "Субтитры", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // 2. RepeatButton (Повтор)
                                if (config.repeatButton.visible) {
                                    StyledIconButton(
                                        config = config.repeatButton,
                                        shape = buttonShapeObj,
                                        accentColor = if (repeatMode != VideoRepeatMode.OFF) config.primaryAccentColor else null,
                                        onClick = {
                                            repeatMode = if (repeatMode == VideoRepeatMode.OFF) VideoRepeatMode.REPEAT_ONE else VideoRepeatMode.OFF
                                            infoFeedbackText = repeatMode.label
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (repeatMode == VideoRepeatMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                            contentDescription = "Повтор",
                                            tint = if (repeatMode != VideoRepeatMode.OFF) config.secondaryAccentColor else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // 3. PrevButton (Предыдущее видео / в начало)
                                if (config.prevButton.visible) {
                                    StyledIconButton(
                                        config = config.prevButton,
                                        shape = buttonShapeObj,
                                        onClick = {
                                            if (!useNativeFallback) {
                                                playerManager.seekTo(0)
                                            } else {
                                                nativeVideoView?.seekTo(0)
                                                currentTimeMs = 0L
                                            }
                                            infoFeedbackText = "В начало видео"
                                        }
                                    ) {
                                        Icon(Icons.Default.SkipPrevious, "Назад", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }

                                // 4. PlayPauseButton (Нижняя кнопка Play/Pause)
                                if (config.playPauseButton.visible) {
                                    Box(
                                        modifier = Modifier
                                            .size(config.playPauseButton.sizeDp.dp)
                                            .clip(buttonShapeObj)
                                            .background(config.primaryAccentColor)
                                            .clickable {
                                                if (isPlaying) {
                                                    if (!useNativeFallback) playerManager.pause() else nativeVideoView?.pause()
                                                    isPlaying = false
                                                } else {
                                                    if (!useNativeFallback) playerManager.resume() else nativeVideoView?.start()
                                                    isPlaying = true
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isPlaying) "Пауза" else "Играть",
                                            tint = Color.Black,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                // 5. NextButton (Следующее видео / +10 сек)
                                if (config.nextButton.visible) {
                                    StyledIconButton(
                                        config = config.nextButton,
                                        shape = buttonShapeObj,
                                        onClick = {
                                            if (!useNativeFallback) {
                                                playerManager.seekRelative(10000L)
                                            } else {
                                                val n = ((nativeVideoView?.currentPosition ?: 0) + 10000).coerceAtMost(nativeVideoView?.duration ?: 0)
                                                nativeVideoView?.seekTo(n)
                                                currentTimeMs = n.toLong()
                                            }
                                            infoFeedbackText = "+10 сек"
                                        }
                                    ) {
                                        Icon(Icons.Default.SkipNext, "Вперед", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }

                                // 6. PiPButton (Картинка в картинке)
                                if (config.pipButton.visible) {
                                    StyledIconButton(
                                        config = config.pipButton,
                                        shape = buttonShapeObj,
                                        onClick = {
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
                                        }
                                    ) {
                                        Icon(Icons.Default.PictureInPictureAlt, "Картинка в картинке", tint = config.secondaryAccentColor, modifier = Modifier.size(20.dp))
                                    }
                                }

                                // 7. MoreMenuButton (Дополнительное меню)
                                if (config.moreMenuButton.visible) {
                                    StyledIconButton(
                                        config = config.moreMenuButton,
                                        shape = buttonShapeObj,
                                        onClick = { showMoreBottomSheet = true }
                                    ) {
                                        Icon(Icons.Default.MoreHoriz, "Настройки", tint = Color.White, modifier = Modifier.size(22.dp))
                                    }
                                }
                            }
                        } else {
                            // Locked screen controls
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Экран заблокирован от касаний",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Button(
                                    onClick = { isControlsLocked = false },
                                    colors = ButtonDefaults.buttonColors(containerColor = config.primaryAccentColor),
                                    shape = RoundedCornerShape(8.dp)
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
                        Text("Закрыть", color = config.secondaryAccentColor)
                    }
                },
                containerColor = config.controlPanelBgColor,
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
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = config.secondaryAccentColor)
                        Text("Поиск приемников Google Cast / Miracast в локальной сети...", color = Color.LightGray, fontSize = 13.sp)
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Отмена", color = config.primaryAccentColor)
                    }
                },
                containerColor = config.controlPanelBgColor,
                shape = RoundedCornerShape(16.dp)
            )
        }

        // More Menu Bottom Sheet
        if (showMoreBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreBottomSheet = false },
                containerColor = config.controlPanelBgColor,
                scrimColor = Color.Black.copy(alpha = 0.6f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Параметры воспроизведения", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

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
                                        selectedContainerColor = config.primaryAccentColor,
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
                            colors = ButtonDefaults.buttonColors(containerColor = config.surfaceColor),
                            border = BorderStroke(1.dp, config.secondaryAccentColor)
                        ) {
                            Text(scaleMode.name, color = config.secondaryAccentColor, fontSize = 11.sp)
                        }
                    }

                    // Terminal Console entry
                    Button(
                        onClick = {
                            showMoreBottomSheet = false
                            showTerminalConsole = true
                        },
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = config.primaryAccentColor),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Terminal, null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ОТКРЫТЬ ТЕРМИНАЛ И JSON КОНСОЛЬ", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // 3. INTERNAL TERMINAL CONSOLE OVERLAY
        TerminalConsoleOverlay(
            isVisible = showTerminalConsole,
            onClose = { showTerminalConsole = false }
        )
    }
}

@Composable
private fun StyledIconButton(
    config: ButtonConfig,
    shape: Shape,
    accentColor: Color? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(config.sizeDp.dp)
            .padding(config.paddingDp.dp)
            .clip(shape)
            .background(accentColor?.copy(alpha = 0.2f) ?: Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
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
