@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.videoplayer.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Rational
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.videoplayer.R
import com.example.videoplayer.archive.ArchivePlaybackSpec
import com.example.videoplayer.archive.ArchiveSource
import com.example.videoplayer.archive.ArchiveStreamServer
import com.example.videoplayer.model.AudioItem
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.player.EqualizerState
import com.example.videoplayer.player.PlaybackPreferences
import com.example.videoplayer.player.PlaybackTrack
import com.example.videoplayer.player.GoogleCastManager
import com.example.videoplayer.player.CastStartResult
import com.example.videoplayer.player.SubtitleStyleState
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.mediarouter.app.MediaRouteButton
import com.example.videoplayer.player.VlcPlayerManager
import com.example.videoplayer.ui.theme.DynamicPlayerConfig
import com.example.videoplayer.ui.theme.ThemeConfigManager
import com.example.videoplayer.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.roundToInt

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

private fun playbackProgressKey(uri: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
    return "position_" + digest.joinToString("") { "%02x".format(it) }
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

private fun isPrivateIpv4Host(host: String): Boolean {
    val parts = host.split('.')
    if (parts.size != 4) return false
    val octets = parts.map { it.toIntOrNull() ?: return false }
    if (octets.any { it !in 0..255 }) return false
    val b0 = octets[0]
    val b1 = octets[1]
    return b0 == 10 || (b0 == 172 && b1 in 16..31) || (b0 == 192 && b1 == 168)
}

private fun extractLanMonitorBeatEndpoint(videoUri: String): String? {
    if (!videoUri.startsWith("http://", ignoreCase = true) && !videoUri.startsWith("https://", ignoreCase = true)) {
        return null
    }
    return try {
        val uri = Uri.parse(videoUri)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val isLocal = host == "localhost" || host == "127.0.0.1" || isPrivateIpv4Host(host)
        if (!isLocal) return null
        val port = uri.port
        val authority = if (port > 0) "$host:$port" else host
        "$scheme://$authority/api/monitor/beat"
    } catch (_: Throwable) {
        null
    }
}

private fun isAudioMedia(context: Context, uriString: String, archiveSpec: ArchivePlaybackSpec?): Boolean {
    val audioExts = setOf("mp3", "m4a", "aac", "flac", "ogg", "wav", "opus", "wma")
    val checkName = archiveSpec?.entryName ?: uriString.substringBefore('?')
    val ext = checkName.substringAfterLast('.', "").lowercase()
    if (ext in audioExts) return true
    if (uriString.contains("media/external/audio") || uriString.startsWith("sample://audio")) return true
    if (uriString.startsWith("content://")) {
        try {
            val mime = context.contentResolver.getType(Uri.parse(uriString))
            if (mime != null && mime.startsWith("audio/")) return true
        } catch (_: Throwable) {}
    }
    return false
}

@Composable
fun PlayerScreen(
    videoUri: String,
    archiveSpec: ArchivePlaybackSpec? = null,
    isInPipMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val config = ThemeConfigManager.currentConfig
    var activeMediaUri by remember(videoUri) { mutableStateOf(videoUri) }
    var resolvedTitle by remember(activeMediaUri, archiveSpec) { mutableStateOf(archiveSpec?.entryName ?: "Видео") }
    var audioArtistSubtitle by remember(activeMediaUri) { mutableStateOf("") }
    var isAudioMode by remember(activeMediaUri, archiveSpec) {
        mutableStateOf(isAudioMedia(context, activeMediaUri, archiveSpec))
    }
    var audioQueue by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var isShuffle by remember { mutableStateOf(false) }
    var isFavorite by remember(activeMediaUri) {
        mutableStateOf(
            PlaylistManager.getPlaylists(context).find { it.id == "pl_favorites" }
                ?.itemUris?.contains(activeMediaUri) == true
        )
    }

    val progressPrefs = remember { context.getSharedPreferences("playback_progress", Context.MODE_PRIVATE) }
    val progressKey = remember(activeMediaUri) { playbackProgressKey(activeMediaUri) }
    var savedResumePositionMs by remember(activeMediaUri) { mutableLongStateOf(progressPrefs.getLong(progressKey, 0L)) }
    var savedResumeDurationMs by remember(activeMediaUri) { mutableLongStateOf(progressPrefs.getLong("${progressKey}_duration", 0L)) }
    var resumePositionApplied by remember(activeMediaUri) { mutableStateOf(false) }

    // Crash-proof player manager
    val playerManager = remember { VlcPlayerManager(context) }
    val castManager = remember { GoogleCastManager(context) }
    var useNativeFallback by remember { mutableStateOf(!playerManager.isEngineReady) }
    var activeArchiveSessionId by remember(activeMediaUri, archiveSpec) { mutableStateOf<String?>(null) }
    var activeArchiveStreamUrl by remember(activeMediaUri, archiveSpec) { mutableStateOf<String?>(null) }
    var triedDirectZip by remember(activeMediaUri, archiveSpec) { mutableStateOf(false) }
    val latestArchiveSessionId by rememberUpdatedState(activeArchiveSessionId)

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
    var showEqualizerDialog by remember { mutableStateOf(false) }
    var eqState by remember { mutableStateOf(PlaybackPreferences.getEqualizerState(context)) }
    var subStyle by remember { mutableStateOf(PlaybackPreferences.getSubtitleStyle(context)) }
    var audioTracks by remember { mutableStateOf(emptyList<PlaybackTrack>()) }
    var subtitleTracks by remember { mutableStateOf(emptyList<PlaybackTrack>()) }
    var selectedAudioTrack by remember { mutableIntStateOf(-1) }
    var selectedSubtitleTrack by remember { mutableIntStateOf(-1) }
    var showCastDialog by remember { mutableStateOf(false) }
    var castStatusText by remember { mutableStateOf("Выберите устройство Google Cast") }
    var scaleMode by remember { mutableStateOf(VideoScaleMode.FIT) }
    var repeatMode by remember { mutableStateOf(VideoRepeatMode.OFF) }
    var infoFeedbackText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        audioQueue = withContext(Dispatchers.IO) {
            MediaStoreHelper.getAudios(context)
        }
    }

    // ContentResolver queries and the fallback media scan can block; resolve the
    // title off the main thread so opening a media file never stalls the player UI.
    LaunchedEffect(activeMediaUri, archiveSpec, audioQueue) {
        if (archiveSpec != null) {
            resolvedTitle = archiveSpec.entryName
            audioArtistSubtitle = "Из архива"
        } else {
            val audioMatch = audioQueue.find { it.uri.toString() == activeMediaUri }
            if (audioMatch != null) {
                resolvedTitle = audioMatch.title
                audioArtistSubtitle = "${audioMatch.artist} • ${audioMatch.album}"
            } else {
                resolvedTitle = withContext(Dispatchers.IO) {
                    resolveVideoTitle(context, activeMediaUri)
                }
                audioArtistSubtitle = if (isAudioMode) "Аудиодорожка" else ""
            }
        }
    }

    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }

    val initialWindowBrightness = remember {
        activity?.window?.attributes?.screenBrightness ?: -1f
    }

    val systemBrightness = remember {
        try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (_: Throwable) {
            0.5f
        }
    }

    var currentBrightness by remember {
        val b = activity?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (b in 0.01f..1.0f) b else systemBrightness.coerceIn(0.01f, 1.0f))
    }

    var currentVolumeFloat by remember {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        mutableFloatStateOf(cur.toFloat())
    }

    var showBrightnessGauge by remember { mutableStateOf(false) }
    var showVolumeGauge by remember { mutableStateOf(false) }
    var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
    val coroutineScope = rememberCoroutineScope()
    var gaugeDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val latestCurrentTimeMs by rememberUpdatedState(currentTimeMs)
    val latestTotalDurationMs by rememberUpdatedState(totalDurationMs)
    val latestRepeatMode by rememberUpdatedState(repeatMode)
    val latestActiveUri by rememberUpdatedState(activeMediaUri)
    val latestResolvedTitle by rememberUpdatedState(resolvedTitle)
    val latestAudioSub by rememberUpdatedState(audioArtistSubtitle)
    val latestIsAudio by rememberUpdatedState(isAudioMode)

    var nativeVideoView by remember { mutableStateOf<VideoView?>(null) }
    var nativeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var hasStartedVlcPlayback by remember { mutableStateOf(false) }

    fun switchAudioTrackByStep(step: Int) {
        if (audioQueue.isEmpty()) return
        val curIdx = audioQueue.indexOfFirst { it.uri.toString() == activeMediaUri }
        val nextIdx = if (isShuffle && audioQueue.size > 1) {
            val candidates = audioQueue.indices.filter { it != curIdx }
            candidates.random()
        } else if (curIdx < 0) {
            0
        } else {
            (curIdx + step + audioQueue.size) % audioQueue.size
        }
        val nextItem = audioQueue[nextIdx]
        activeMediaUri = nextItem.uri.toString()
        resolvedTitle = nextItem.title
        audioArtistSubtitle = "${nextItem.artist} • ${nextItem.album}"
        currentTimeMs = 0L
        totalDurationMs = nextItem.duration
        if (!useNativeFallback) {
            playerManager.playUrl(activeMediaUri)
        } else {
            nativeVideoView?.setVideoURI(nextItem.uri)
            nativeVideoView?.start()
        }
    }

    BackHandler {
        onBack()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(Unit) {
        val window = activity?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.let { act ->
                val lp = act.window.attributes
                lp.screenBrightness = initialWindowBrightness
                act.window.attributes = lp
                val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
            val position = latestCurrentTimeMs
            val duration = latestTotalDurationMs
            PlaybackPreferences.recordRecentMedia(
                context = context,
                uri = latestActiveUri,
                title = latestResolvedTitle,
                subtitle = latestAudioSub,
                isAudio = latestIsAudio,
                positionMs = position,
                durationMs = duration
            )
            if (position > 0L && (duration <= 0L || position < duration - 10_000L)) {
                progressPrefs.edit()
                    .putLong(playbackProgressKey(latestActiveUri), position)
                    .putLong("${playbackProgressKey(latestActiveUri)}_duration", duration)
                    .apply()
            }
            try {
                ArchiveStreamServer.stopSession(latestArchiveSessionId)
            } catch (_: Throwable) {}
            try {
                playerManager.release()
            } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(isLandscape, isInPipMode) {
        activity?.let { act ->
            val ctrl = WindowCompat.getInsetsController(act.window, act.window.decorView)
            ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isLandscape && !isInPipMode) {
                ctrl.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                ctrl.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(isInPipMode) {
        if (isInPipMode) {
            showControls = false
            showBrightnessGauge = false
            showVolumeGauge = false
            showSpeedMenu = false
            showTopOverflowMenu = false
            showMoreBottomSheet = false
        }
    }

    LaunchedEffect(scaleMode) {
        if (!useNativeFallback) {
            playerManager.setVideoScale(scaleMode)
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

    // Persist progress periodically so playback can resume after leaving the player or closing the app.
    LaunchedEffect(isPlaying, activeMediaUri, resolvedTitle) {
        if (resolvedTitle.isNotBlank()) {
            PlaybackPreferences.recordRecentMedia(
                context = context,
                uri = activeMediaUri,
                title = resolvedTitle,
                subtitle = audioArtistSubtitle,
                isAudio = isAudioMode,
                positionMs = latestCurrentTimeMs,
                durationMs = latestTotalDurationMs
            )
        }
        if (!isPlaying) {
            val position = latestCurrentTimeMs
            val duration = latestTotalDurationMs
            if (position > 0L && (duration <= 0L || position < duration - 10_000L)) {
                progressPrefs.edit()
                    .putLong(progressKey, position)
                    .putLong("${progressKey}_duration", duration)
                    .apply()
                savedResumePositionMs = position
                savedResumeDurationMs = duration
            } else if (duration > 0L && position >= duration - 10_000L) {
                progressPrefs.edit().remove(progressKey).remove("${progressKey}_duration").apply()
                savedResumePositionMs = 0L
                savedResumeDurationMs = 0L
            }
            return@LaunchedEffect
        }
        while (isPlaying) {
            delay(5000L)
            val position = latestCurrentTimeMs
            val duration = latestTotalDurationMs
            PlaybackPreferences.recordRecentMedia(
                context = context,
                uri = activeMediaUri,
                title = resolvedTitle,
                subtitle = audioArtistSubtitle,
                isAudio = isAudioMode,
                positionMs = position,
                durationMs = duration
            )
            if (position > 0L && (duration <= 0L || position < duration - 10_000L)) {
                progressPrefs.edit()
                    .putLong(progressKey, position)
                    .putLong("${progressKey}_duration", duration)
                    .apply()
                savedResumePositionMs = position
                savedResumeDurationMs = duration
            } else if (duration > 0L && position >= duration - 10_000L) {
                progressPrefs.edit().remove(progressKey).remove("${progressKey}_duration").apply()
                savedResumePositionMs = 0L
                savedResumeDurationMs = 0L
            }
        }
    }

    val beatPosMs by rememberUpdatedState(currentTimeMs)
    val beatDurMs by rememberUpdatedState(totalDurationMs)
    val beatTitle by rememberUpdatedState(resolvedTitle)
    val monitorBeatUrl = remember(videoUri, archiveSpec) {
        if (archiveSpec != null || ArchiveStreamServer.isArchiveStreamUrl(videoUri)) null
        else extractLanMonitorBeatEndpoint(videoUri)
    }

    LaunchedEffect(isPlaying, monitorBeatUrl) {
        val endpoint = monitorBeatUrl ?: return@LaunchedEffect
        if (!isPlaying) return@LaunchedEffect
        delay(1000L)
        while (isPlaying) {
            withContext(Dispatchers.IO) {
                var conn: HttpURLConnection? = null
                try {
                    val payload = JSONObject().apply {
                        put("file", beatTitle)
                        put("pos", beatPosMs / 1000.0)
                        put("dur", beatDurMs / 1000.0)
                        put("kind", "video")
                    }.toString().toByteArray(Charsets.UTF_8)
                    conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 2000
                        readTimeout = 2000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                        setRequestProperty("X-Nexora", "1")
                        setFixedLengthStreamingMode(payload.size)
                    }
                    conn.outputStream.use { it.write(payload) }
                    conn.responseCode
                } catch (_: Throwable) {
                } finally {
                    try { conn?.disconnect() } catch (_: Throwable) {}
                }
            }
            delay(5000L)
        }
    }

    DisposableEffect(playerManager) {
        playerManager.onVideoAspectChanged = { ar ->
            if (ar > 0.1f && ar < 10f) {
                videoAspect = ar
            }
        }
        playerManager.onStatusChanged = { status ->
            statusText = status
            if (status == "Playing") {
                isPlaying = true
                if (!resumePositionApplied) {
                    resumePositionApplied = true
                    val resumeAt = savedResumePositionMs
                    val savedDuration = savedResumeDurationMs
                    if (resumeAt > 15_000L && (savedDuration <= 0L || resumeAt < savedDuration - 10_000L)) {
                        playerManager.seekTo(resumeAt)
                        currentTimeMs = resumeAt
                        infoFeedbackText = "Продолжение с ${formatTime(resumeAt)}"
                    }
                }
            } else if (status == "Paused" || status == "Stopped") isPlaying = false
            else if (status == "EndReached") {
                if (latestRepeatMode == VideoRepeatMode.REPEAT_ONE) {
                    playerManager.seekTo(0)
                    playerManager.resume()
                } else {
                    isPlaying = false
                    progressPrefs.edit().remove(progressKey).remove("${progressKey}_duration").apply()
                    savedResumePositionMs = 0L
                    savedResumeDurationMs = 0L
                }
            } else if (status.startsWith("Error")) {
                val spec = archiveSpec
                if (spec != null && triedDirectZip && activeArchiveSessionId == null) {
                    triedDirectZip = false
                    infoFeedbackText = "Переключение на потоковый мост архива..."
                    coroutineScope.launch {
                        try {
                            val handle = withContext(Dispatchers.IO) {
                                ArchiveStreamServer.startSession(context, spec)
                            }
                            activeArchiveSessionId = handle.sessionId
                            activeArchiveStreamUrl = handle.streamUrl
                            playerManager.playUrl(handle.streamUrl)
                        } catch (t: Throwable) {
                            infoFeedbackText = t.message ?: "Ошибка чтения видео из архива"
                        }
                    }
                } else {
                    val fallbackPosition = latestCurrentTimeMs
                    val fallbackDuration = latestTotalDurationMs
                    if (fallbackPosition > 0L) {
                        savedResumePositionMs = fallbackPosition
                        savedResumeDurationMs = fallbackDuration
                        progressPrefs.edit()
                            .putLong(progressKey, fallbackPosition)
                            .putLong("${progressKey}_duration", fallbackDuration)
                            .apply()
                    }
                    resumePositionApplied = false
                    // Stop the failed VLC session before mounting VideoView to avoid
                    // retaining decoder resources or overlapping audio during fallback.
                    coroutineScope.launch {
                        playerManager.stop()
                        useNativeFallback = true
                    }
                    infoFeedbackText = "Включен резервный медиа-движок Android"
                }
            }
        }
        playerManager.onTracksChanged = {
            coroutineScope.launch {
                audioTracks = playerManager.getAudioTracks()
                subtitleTracks = playerManager.getSubtitleTracks()
                selectedAudioTrack = playerManager.getSelectedAudioTrack()
                selectedSubtitleTrack = playerManager.getSelectedSubtitleTrack()
            }
        }
        playerManager.onTimeChanged = { time, length ->
            currentTimeMs = time
            if (length > 0) totalDurationMs = length
        }
        onDispose {
            playerManager.onTracksChanged = null
            playerManager.onStatusChanged = null
            playerManager.onTimeChanged = null
            playerManager.onVideoAspectChanged = null
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF030208))
            .pointerInput(isControlsLocked) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    }
                )
            }
            .pointerInput(isControlsLocked) {
                if (isControlsLocked) return@pointerInput
                var isLeftHalf = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isLeftHalf = offset.x < size.width / 2f
                        gaugeDismissJob?.cancel()
                        if (isLeftHalf) {
                            showBrightnessGauge = true
                            showVolumeGauge = false
                        } else {
                            showVolumeGauge = true
                            showBrightnessGauge = false
                        }
                    },
                    onDragEnd = {
                        gaugeDismissJob = coroutineScope.launch {
                            delay(900)
                            showBrightnessGauge = false
                            showVolumeGauge = false
                        }
                    },
                    onDragCancel = {
                        gaugeDismissJob = coroutineScope.launch {
                            delay(900)
                            showBrightnessGauge = false
                            showVolumeGauge = false
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        val totalHeight = size.height.toFloat()
                        if (totalHeight > 0) {
                            val deltaFraction = -dragAmount / (0.80f * totalHeight)
                            if (isLeftHalf) {
                                currentBrightness = (currentBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                                activity?.let { act ->
                                    val lp = act.window.attributes
                                    lp.screenBrightness = currentBrightness
                                    act.window.attributes = lp
                                }
                            } else {
                                currentVolumeFloat = (currentVolumeFloat + deltaFraction * maxVolume).coerceIn(0f, maxVolume.toFloat())
                                val targetVol = currentVolumeFloat.roundToInt().coerceIn(0, maxVolume)
                                try {
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                )
            }
    ) {
        val screenWidthDp = maxWidth
        val uVal = (screenWidthDp.value / 1774f).coerceAtLeast(0.2f)

        // 1. VIDEO VIEWPORT: Pure & uncorrupted
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds(),
            contentAlignment = Alignment.Center
        ) {
            val containerRatio = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 16f / 9f
            val surfaceModifier = when (scaleMode) {
                VideoScaleMode.FILL -> {
                    val (w, h) = if (videoAspect > containerRatio) {
                        Pair(maxHeight * videoAspect, maxHeight)
                    } else {
                        Pair(maxWidth, maxWidth / videoAspect)
                    }
                    Modifier.requiredSize(w, h)
                }
                else -> Modifier.fillMaxSize()
            }

            if (!useNativeFallback) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    try {
                                        playerManager.attachSurface(this@apply)
                                        // SurfaceView can be recreated on rotation or window changes.
                                        // Reattach the existing VLC output without resetting the current media.
                                        if (!hasStartedVlcPlayback) {
                                            hasStartedVlcPlayback = true
                                            val spec = archiveSpec
                                            if (spec != null) {
                                                if (ArchiveStreamServer.shouldPreferDirectZip(spec)) {
                                                    triedDirectZip = true
                                                    val path = (spec.source as ArchiveSource.FilePath).path
                                                    playerManager.playArchiveZip(path, spec.entryPath)
                                                } else {
                                                    infoFeedbackText = "Потоковая распаковка из архива..."
                                                    coroutineScope.launch {
                                                        try {
                                                            val handle = withContext(Dispatchers.IO) {
                                                                ArchiveStreamServer.startSession(context, spec)
                                                            }
                                                            activeArchiveSessionId = handle.sessionId
                                                            activeArchiveStreamUrl = handle.streamUrl
                                                            playerManager.playUrl(handle.streamUrl)
                                                        } catch (t: Throwable) {
                                                            infoFeedbackText = t.message ?: "Не удалось открыть видео из архива"
                                                            statusText = "Error: ${t.message}"
                                                        }
                                                    }
                                                }
                                            } else {
                                                playerManager.playUrl(activeMediaUri)
                                            }
                                        }
                                    } catch (_: Throwable) {
                                        coroutineScope.launch {
                                            playerManager.stop()
                                            useNativeFallback = true
                                        }
                                    }
                                }

                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                    playerManager.updateWindowSize(width, height)
                                    playerManager.setVideoScale(scaleMode)
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    playerManager.detachSurface()
                                }
                            })
                        }
                    },
                    modifier = surfaceModifier
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            nativeVideoView = this
                            val targetUriStr = activeArchiveStreamUrl ?: videoUri
                            val uri = when {
                                targetUriStr.startsWith("sample://") -> Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                                targetUriStr.startsWith("content://") || targetUriStr.startsWith("file://") || targetUriStr.startsWith("http") -> Uri.parse(targetUriStr)
                                else -> Uri.fromFile(File(targetUriStr))
                            }
                            setVideoURI(uri)
                            setOnPreparedListener { mp ->
                                nativeMediaPlayer = mp
                                totalDurationMs = mp.duration.toLong()
                                if (!resumePositionApplied) {
                                    resumePositionApplied = true
                                    val resumeAt = savedResumePositionMs
                                    val savedDuration = savedResumeDurationMs
                                    if (resumeAt > 15_000L && mp.duration > 0 &&
                                        (savedDuration <= 0L || resumeAt < savedDuration - 10_000L) && resumeAt < mp.duration - 10_000L
                                    ) {
                                        mp.seekTo(resumeAt.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                                        currentTimeMs = resumeAt
                                        infoFeedbackText = "Продолжение с ${formatTime(resumeAt)}"
                                    }
                                }
                                try {
                                    mp.playbackParams = mp.playbackParams.setSpeed(currentSpeed)
                                } catch (e: Exception) {
                                    Log.w("PlayerScreen", "Playback speed is unavailable in native fallback", e)
                                }
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
                            setOnErrorListener { _, what, extra ->
                                isPlaying = false
                                statusText = "Ошибка воспроизведения ($what/$extra)"
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

        // Floating feedback badge (Obsidian Info Toast style)
        infoFeedbackText?.let { info ->
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = (80f * uVal).dp.coerceAtLeast(20.dp)),
                shape = RoundedCornerShape(22.dp),
                color = Color(0xF00B1A4A),
                border = BorderStroke(1.5.dp, Color(0x886E64DC)),
                shadowElevation = 10.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3E7BFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("i", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        text = info,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 2. DEDICATED FULL-SCREEN AUDIO PLAYER (Artboard 4) OR VIDEO OVERLAY CONTROLS
        if (isAudioMode && !isInPipMode) {
            AudioPlayerOverlay(
                title = resolvedTitle,
                subtitle = audioArtistSubtitle,
                isPlaying = isPlaying,
                isFavorite = isFavorite,
                isShuffle = isShuffle,
                repeatMode = repeatMode,
                currentTimeMs = currentTimeMs,
                totalDurationMs = totalDurationMs,
                hasQueueNavigation = audioQueue.size > 1,
                onBack = onBack,
                onToggleFavorite = {
                    if (isFavorite) {
                        PlaylistManager.removeItemFromPlaylist(context, "pl_favorites", activeMediaUri)
                        isFavorite = false
                        infoFeedbackText = "Удалено из «Избранного»"
                    } else {
                        PlaylistManager.addItemToPlaylist(context, "pl_favorites", activeMediaUri)
                        isFavorite = true
                        infoFeedbackText = "Добавлено в «Избранное»"
                    }
                },
                onOpenEqualizer = { showEqualizerDialog = true },
                onOpenMore = { showMoreBottomSheet = true },
                onToggleShuffle = {
                    isShuffle = !isShuffle
                    infoFeedbackText = if (isShuffle) "Перемешивание включено" else "Перемешивание выключено"
                },
                onPreviousTrack = { switchAudioTrackByStep(-1) },
                onTogglePlayPause = {
                    if (isPlaying) {
                        if (!useNativeFallback) playerManager.pause() else nativeVideoView?.pause()
                        isPlaying = false
                    } else {
                        if (!useNativeFallback) playerManager.resume() else nativeVideoView?.start()
                        isPlaying = true
                    }
                },
                onNextTrack = { switchAudioTrackByStep(+1) },
                onToggleRepeat = {
                    repeatMode = if (repeatMode == VideoRepeatMode.OFF) VideoRepeatMode.REPEAT_ONE else VideoRepeatMode.OFF
                    infoFeedbackText = repeatMode.label
                },
                onSeekTo = { target ->
                    currentTimeMs = target
                    if (!useNativeFallback) playerManager.seekTo(target) else nativeVideoView?.seekTo(target.toInt())
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = showControls && !isInPipMode && !isAudioMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xBF05030A),
                                Color(0x3D030208),
                                Color(0xCC05030A)
                            )
                        )
                    )
            ) {
                // TOP BAR (Anchored to top, statusBarsPadding)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(
                            top = (36f * uVal).dp.coerceAtLeast(8.dp),
                            start = (40f * uVal).dp.coerceAtLeast(10.dp),
                            end = (40f * uVal).dp.coerceAtLeast(10.dp)
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back Arrow Button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("player_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Nexora Logo
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("player_brand_logo")
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

                    Spacer(modifier = Modifier.width(6.dp))

                    // Brand "Nexora" with fiery orange 'N'
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFFFF9A1A), fontWeight = FontWeight.Bold)) {
                                append("N")
                            }
                            withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) {
                                append("exora")
                            }
                        },
                        fontSize = (32f * uVal).coerceIn(15f, 22f).sp,
                        letterSpacing = 0.3.sp
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Video file title
                    Text(
                        text = resolvedTitle,
                        color = Color(0xFFE4E2F0),
                        fontSize = (26f * uVal).coerceIn(13f, 18f).sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Cast Button
                    IconButton(
                        onClick = { showCastDialog = true },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("player_cast_button")
                    ) {
                        CastIconCanvas(uVal)
                    }

                    // Picture-in-Picture quick toggle button
                    IconButton(
                        onClick = {
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
                        },
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("player_top_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Картинка в картинке",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Top Three Dots Button
                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .testTag("player_top_menu_button")
                            .clickable(onClick = { showTopOverflowMenu = true }),
                        contentAlignment = Alignment.Center
                    ) {
                        TopDotsCanvas(uVal)

                        DropdownMenu(
                            expanded = showTopOverflowMenu,
                            onDismissRequest = { showTopOverflowMenu = false },
                            modifier = Modifier.background(Color(0xFF14121C))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Информация о видео", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Info, null, tint = Color(0xFFFF9A1A)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    infoFeedbackText = "Формат: ${activeMediaUri.substringAfterLast('.')} • Длительность: ${formatTime(totalDurationMs)}"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Эквалайзер", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Tune, null, tint = Color(0xFFFF9A1A)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    showEqualizerDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Заблокировать экран", color = Color.White) },
                                leadingIcon = { Icon(Icons.Default.Lock, null, tint = Color(0xFFFFA31A)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    isControlsLocked = true
                                    infoFeedbackText = "Экран заблокирован от касаний"
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Сбросить UI к Обсидиан-Неону", color = Color(0xFFFF9A1A)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Color(0xFFFF6A00)) },
                                onClick = {
                                    showTopOverflowMenu = false
                                    ThemeManager.resetToOriginalNeon(context)
                                    ThemeConfigManager.applyConfig(DynamicPlayerConfig.obsidianOrangePreset(), context)
                                    infoFeedbackText = "Применен стиль Nexora Обсидиан Оранж"
                                }
                            )
                        }
                    }
                }

                // CENTER OVERLAY CONTROLS (-10s | Frosted Play/Pause | +10s)
                if (!isControlsLocked) {
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(28.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // -10s
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 54.dp, minHeight = 54.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (!useNativeFallback) {
                                        playerManager.seekRelative(-10_000L)
                                    } else {
                                        val n = ((nativeVideoView?.currentPosition ?: 0) - 10_000).coerceAtLeast(0)
                                        nativeVideoView?.seekTo(n)
                                        currentTimeMs = n.toLong()
                                    }
                                    infoFeedbackText = "-10 сек"
                                }
                                .testTag("player_seek_back_10"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay10,
                                contentDescription = "Назад на 10 секунд",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }

                        // Frosted Circular Play / Pause
                        Box(
                            modifier = Modifier
                                .size(76.dp)
                                .clip(CircleShape)
                                .background(Color(0x29FFFFFF))
                                .border(1.5.dp, Color(0x45FFFFFF), CircleShape)
                                .clickable {
                                    if (isPlaying) {
                                        if (!useNativeFallback) playerManager.pause() else nativeVideoView?.pause()
                                        isPlaying = false
                                    } else {
                                        if (!useNativeFallback) playerManager.resume() else nativeVideoView?.start()
                                        isPlaying = true
                                    }
                                }
                                .testTag("player_center_play_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                                tint = Color(0xFFF6F6F8),
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // +10s
                        Box(
                            modifier = Modifier
                                .sizeIn(minWidth = 54.dp, minHeight = 54.dp)
                                .clip(CircleShape)
                                .clickable {
                                    if (!useNativeFallback) {
                                        playerManager.seekRelative(10_000L)
                                    } else {
                                        val dur = nativeVideoView?.duration ?: 0
                                        val n = ((nativeVideoView?.currentPosition ?: 0) + 10_000).let {
                                            if (dur > 0) it.coerceAtMost(dur) else it
                                        }
                                        nativeVideoView?.seekTo(n)
                                        currentTimeMs = n.toLong()
                                    }
                                    infoFeedbackText = "+10 сек"
                                }
                                .testTag("player_seek_forward_10"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Forward10,
                                contentDescription = "Вперёд на 10 секунд",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // BOTTOM CONTROLS (Time Row, Neon-Orange Seek Bar, Bottom Actions Bar)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = (44f * uVal).dp.coerceAtLeast(16.dp),
                            end = (44f * uVal).dp.coerceAtLeast(16.dp),
                            bottom = 14.dp
                        )
                ) {
                    if (!isControlsLocked) {
                        // 1. Time Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentTimeMs),
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = formatTime(totalDurationMs),
                                color = Color(0xFFA9A7C4),
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // 2. Seek Bar (Background + Fiery Orange Filled Track + Glowing Knob)
                        val progressFraction = if (totalDurationMs > 0) {
                            (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        } else 0f

                        val seekTrackBrush = Brush.horizontalGradient(
                            colors = listOf(Color(0xFFFF9A1A), Color(0xFFFF6A00))
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
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
                            Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
                                val lineH = 4.dp.toPx()
                                val lineY = (size.height - lineH) / 2f
                                val radiusPx = 2.dp.toPx()

                                // Unfilled track
                                drawRoundRect(
                                    color = Color(0x33FFFFFF),
                                    topLeft = Offset(0f, lineY),
                                    size = Size(size.width, lineH),
                                    cornerRadius = CornerRadius(radiusPx, radiusPx)
                                )

                                // Active orange gradient progress track
                                val filledWidth = (size.width * progressFraction).coerceIn(0f, size.width)
                                if (filledWidth > 0f) {
                                    drawRoundRect(
                                        brush = seekTrackBrush,
                                        topLeft = Offset(0f, lineY),
                                        size = Size(filledWidth, lineH),
                                        cornerRadius = CornerRadius(radiusPx, radiusPx)
                                    )
                                }

                                // Knob
                                val knobRadius = 7.dp.toPx()
                                val knobCenterX = filledWidth
                                val knobCenterY = size.height / 2f

                                // Warm amber glow
                                drawCircle(
                                    color = Color(0x8CFF8C00),
                                    radius = knobRadius + 4.dp.toPx(),
                                    center = Offset(knobCenterX, knobCenterY)
                                )
                                // Knob circle
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(Color(0xFFFFC04D), Color(0xFFFF8A00)),
                                        center = Offset(knobCenterX - 1.5.dp.toPx(), knobCenterY - 1.5.dp.toPx()),
                                        radius = knobRadius
                                    ),
                                    radius = knobRadius,
                                    center = Offset(knobCenterX, knobCenterY)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // 3. Bottom Action Row (Lock, Subtitles, Speed Pill, Prev/Play/Next, Repeat, Scale, More)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Lock
                            IconButton(
                                onClick = {
                                    isControlsLocked = true
                                    infoFeedbackText = "Экран заблокирован от касаний"
                                },
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("player_lock_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Блокировка касаний",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Subtitles / Audio
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("player_subtitles_button")
                                    .clickable(onClick = { showSubtitlesDialog = true }),
                                contentAlignment = Alignment.Center
                            ) {
                                SubtitlesIconCanvas(uVal)
                            }

                            // Speed Pill ("1.0x" from mockup)
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 54.dp, minHeight = 40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xB80F0C1A))
                                    .border(1.dp, Color(0x24FFFFFF), RoundedCornerShape(12.dp))
                                    .clickable { showSpeedMenu = true }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("player_speed_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${currentSpeed}x",
                                    color = if (currentSpeed != 1.0f) Color(0xFFFF9A1A) else Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Repeat Mode
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("player_repeat_button")
                                    .clickable(onClick = {
                                        repeatMode = if (repeatMode == VideoRepeatMode.OFF) VideoRepeatMode.REPEAT_ONE else VideoRepeatMode.OFF
                                        infoFeedbackText = repeatMode.label
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                RepeatIconCanvas(uVal, repeatMode != VideoRepeatMode.OFF)
                            }

                            // Video Scale / Fit Mode
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("player_scale_button")
                                    .clickable(onClick = {
                                        scaleMode = when (scaleMode) {
                                            VideoScaleMode.FIT -> VideoScaleMode.FILL
                                            VideoScaleMode.FILL -> VideoScaleMode.SIXTEEN_NINE
                                            VideoScaleMode.SIXTEEN_NINE -> VideoScaleMode.FOUR_THREE
                                            VideoScaleMode.FOUR_THREE -> VideoScaleMode.FIT
                                        }
                                        infoFeedbackText = "Масштаб: ${scaleMode.label}"
                                    }),
                                contentAlignment = Alignment.Center
                            ) {
                                PipIconCanvas(uVal)
                            }

                            // More Bottom Sheet
                            Box(
                                modifier = Modifier
                                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("player_more_button")
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
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Экран заблокирован от касаний",
                                color = Color(0xFFE4E2F0),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Button(
                                onClick = { isControlsLocked = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9A1A),
                                    contentColor = Color(0xFF3A1A00)
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("player_unlock_button")
                            ) {
                                Icon(Icons.Default.LockOpen, null, tint = Color(0xFF3A1A00), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Разблокировать", color = Color(0xFF3A1A00), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Styled Speed Selector Dialog (Obsidian-Violet & Warm Orange highlight)
        if (showSpeedMenu) {
            Dialog(onDismissRequest = { showSpeedMenu = false }) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, Color(0x809664AA)),
                    shadowElevation = 24.dp,
                    modifier = Modifier.widthIn(min = 240.dp, max = 320.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF0F0C18), Color(0xFF050309))
                                )
                            )
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = "Выбор скорости",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        HorizontalDivider(color = Color(0x26FFFFFF))

                        listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                            val isSelected = currentSpeed == spd
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFF8A3D10), Color(0xFF5C2A12), Color(0xFF6A3010))
                                                )
                                            )
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        if (useNativeFallback) {
                                            val nativePlayer = nativeMediaPlayer
                                            if (nativePlayer == null) {
                                                infoFeedbackText = "Плеер ещё загружается"
                                            } else {
                                                try {
                                                    nativePlayer.playbackParams = nativePlayer.playbackParams.setSpeed(spd)
                                                    currentSpeed = spd
                                                    infoFeedbackText = "Скорость: ${spd}x"
                                                } catch (e: Exception) {
                                                    Log.w("PlayerScreen", "Native playback speed failed", e)
                                                    infoFeedbackText = "Эта скорость не поддерживается"
                                                }
                                            }
                                        } else {
                                            if (playerManager.setRate(spd)) {
                                                currentSpeed = spd
                                                infoFeedbackText = "Скорость: ${spd}x"
                                            } else {
                                                infoFeedbackText = "Не удалось изменить скорость"
                                            }
                                        }
                                        showSpeedMenu = false
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("player_speed_option_$spd"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) {
                                                Brush.linearGradient(listOf(Color(0xFFFF9A1A), Color(0xFFE86400)))
                                            } else {
                                                Brush.linearGradient(listOf(Color(0x14FFFFFF), Color(0x14FFFFFF)))
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Speed,
                                        contentDescription = null,
                                        tint = if (isSelected) Color.White else Color(0xFF8D8AA8),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "${spd}x",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Real VLC audio, subtitle track & styling (Artboard 12).
        if (showSubtitlesDialog) {
            LaunchedEffect(showSubtitlesDialog, useNativeFallback) {
                if (showSubtitlesDialog && !useNativeFallback) {
                    delay(350)
                    audioTracks = playerManager.getAudioTracks()
                    subtitleTracks = playerManager.getSubtitleTracks()
                    selectedAudioTrack = playerManager.getSelectedAudioTrack()
                    selectedSubtitleTrack = playerManager.getSelectedSubtitleTrack()
                }
            }
            SubtitlesCustomizationDialog(
                subStyle = subStyle,
                useNativeFallback = useNativeFallback,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioTrack = selectedAudioTrack,
                selectedSubtitleTrack = selectedSubtitleTrack,
                onStyleChanged = { nextStyle ->
                    subStyle = nextStyle
                    PlaybackPreferences.saveSubtitleStyle(context, nextStyle)
                    infoFeedbackText = "Стиль субтитров обновлён (${nextStyle.fontSizeSp} sp)"
                },
                onSelectAudioTrack = { id ->
                    if (playerManager.selectAudioTrack(id)) {
                        selectedAudioTrack = id
                        infoFeedbackText = "Звуковая дорожка изменена"
                    } else {
                        infoFeedbackText = "Не удалось переключить дорожку"
                    }
                },
                onSelectSubtitleTrack = { id ->
                    if (playerManager.selectSubtitleTrack(id)) {
                        selectedSubtitleTrack = id
                        infoFeedbackText = if (id == -1) "Субтитры отключены" else "Субтитры включены"
                    } else {
                        infoFeedbackText = "Не удалось изменить субтитры"
                    }
                },
                onDismiss = { showSubtitlesDialog = false }
            )
        }

        // Artboard 9: 5-Band Interactive Equalizer Dialog
        if (showEqualizerDialog) {
            EqualizerDialog(
                state = eqState,
                onStateChanged = { nextEq ->
                    eqState = nextEq
                    PlaybackPreferences.saveEqualizerState(context, nextEq)
                    playerManager.applyEqualizer(nextEq.enabled, nextEq.bands)
                },
                onDismiss = { showEqualizerDialog = false }
            )
        }

        // Google Cast device picker and remote playback.
        if (showCastDialog) {
            AlertDialog(
                onDismissRequest = { showCastDialog = false },
                title = { Text("Трансляция на телевизор", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            "Выберите Chromecast или телевизор с Google Cast:",
                            color = Color(0xFFE4E2F0),
                            fontSize = 13.sp
                        )
                        AndroidView(
                            factory = { viewContext ->
                                MediaRouteButton(viewContext).apply {
                                    try {
                                        CastButtonFactory.setUpMediaRouteButton(viewContext.applicationContext, this)
                                    } catch (e: Exception) {
                                        Log.w("PlayerScreen", "Unable to initialize Google Cast device picker", e)
                                    }
                                }
                            },
                            modifier = Modifier.size(52.dp)
                        )
                        Text(castStatusText, color = Color(0xFF8D8AA8), fontSize = 12.sp)
                        Button(
                            onClick = {
                                when (castManager.castVideo(videoUri, resolvedTitle) { success ->
                                    castStatusText = if (success) {
                                        "Видео передано на устройство"
                                    } else {
                                        "Устройство не приняло видео. Проверьте формат и доступность ссылки."
                                    }
                                }) {
                                    CastStartResult.STARTED -> castStatusText = "Отправка видео на устройство…"
                                    CastStartResult.NOT_CONNECTED -> castStatusText = "Сначала выберите устройство в списке выше"
                                    CastStartResult.UNSUPPORTED_URI -> castStatusText = "Для Google Cast нужна HTTP(S)-ссылка, доступная телевизору. Локальные файлы пока не поддерживаются."
                                    CastStartResult.UNAVAILABLE -> castStatusText = "Google Cast недоступен. Проверьте Google Play Services."
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF9A1A),
                                contentColor = Color(0xFF3A1A00)
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Транслировать текущее видео", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCastDialog = false }) {
                        Text("Закрыть", color = Color(0xFFFF9A1A), fontWeight = FontWeight.SemiBold)
                    }
                },
                containerColor = Color(0xFF14121C),
                shape = RoundedCornerShape(22.dp)
            )
        }

        // More Menu Bottom Sheet
        if (showMoreBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showMoreBottomSheet = false },
                containerColor = Color(0xFF0D0B14),
                scrimColor = Color.Black.copy(alpha = 0.65f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("Параметры воспроизведения", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                    // Seek to start option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                if (!useNativeFallback) {
                                    playerManager.seekTo(0)
                                } else {
                                    nativeVideoView?.seekTo(0)
                                    currentTimeMs = 0L
                                }
                                showMoreBottomSheet = false
                                infoFeedbackText = "В начало видео"
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Начать сначала", color = Color(0xFFE4E2F0), fontSize = 14.sp)
                        Icon(Icons.Default.Replay, contentDescription = null, tint = Color(0xFFFF9A1A))
                    }

                    // Lock screen option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                isControlsLocked = true
                                showMoreBottomSheet = false
                                infoFeedbackText = "Экран заблокирован от касаний"
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Блокировка касаний", color = Color(0xFFE4E2F0), fontSize = 14.sp)
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFFF9A1A))
                    }

                    // Speed selector
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Скорость: ${currentSpeed}x", color = Color(0xFFE4E2F0), fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { spd ->
                                FilterChip(
                                    selected = currentSpeed == spd,
                                    onClick = {
                                        if (useNativeFallback) {
                                            val nativePlayer = nativeMediaPlayer
                                            if (nativePlayer == null) {
                                                infoFeedbackText = "Плеер ещё загружается"
                                            } else {
                                                try {
                                                    nativePlayer.playbackParams = nativePlayer.playbackParams.setSpeed(spd)
                                                    currentSpeed = spd
                                                    infoFeedbackText = "Скорость: ${spd}x"
                                                } catch (e: Exception) {
                                                    Log.w("PlayerScreen", "Native playback speed failed", e)
                                                    infoFeedbackText = "Эта скорость не поддерживается"
                                                }
                                            }
                                        } else {
                                            if (playerManager.setRate(spd)) {
                                                currentSpeed = spd
                                                infoFeedbackText = "Скорость: ${spd}x"
                                            } else {
                                                infoFeedbackText = "Не удалось изменить скорость воспроизведения"
                                            }
                                        }
                                    },
                                    label = { Text("${spd}x", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFF9A1A),
                                        selectedLabelColor = Color(0xFF3A1A00),
                                        labelColor = Color(0xFFA9A7C4)
                                    )
                                )
                            }
                        }
                    }

                    // Equalizer option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                showMoreBottomSheet = false
                                showEqualizerDialog = true
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Эквалайзер", color = Color(0xFFE4E2F0), fontSize = 14.sp)
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFFFF9A1A))
                    }

                    // Picture-in-picture option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable {
                                showMoreBottomSheet = false
                                val act = context as? Activity
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act != null) {
                                    try {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(Rational(16, 9))
                                            .build()
                                        act.enterPictureInPictureMode(params)
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "PiP режим: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Режим PiP поддерживается на Android 8.0+", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Картинка в картинке", color = Color(0xFFE4E2F0), fontSize = 14.sp)
                        Icon(Icons.Default.PictureInPictureAlt, contentDescription = null, tint = Color(0xFFFF9A1A))
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Brightness and Volume Gauges (player_screen_v4.html)
        BrightnessGauge(
            uVal = uVal,
            brightness = currentBrightness,
            visible = showBrightnessGauge,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(
                    x = (121f * uVal).dp,
                    y = (maxHeight * 0.46f) - (228f * uVal).dp
                )
        )

        VolumeGauge(
            uVal = uVal,
            volumeFraction = (currentVolumeFloat / maxVolume.toFloat()).coerceIn(0f, 1f),
            visible = showVolumeGauge,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(
                    x = -((121f * uVal).dp),
                    y = (maxHeight * 0.46f) - (228f * uVal).dp
                )
        )
    }
}

/* =========================================================================
   NEXORA OBSIDIAN-VIOLET & NEON ORANGE VECTOR CANVASES
   ========================================================================= */

@Composable
private fun NexoraLogoCanvas(uVal: Float) {
    val sizeDp = (64f * uVal).dp.coerceIn(28.dp, 38.dp)
    Canvas(modifier = Modifier.size(sizeDp)) {
        val sX = size.width / 100f
        val sY = size.height / 100f
        val badgePath = androidx.compose.ui.graphics.Path().apply {
            moveTo(30f * sX, 8f * sY)
            lineTo(42f * sX, 8f * sY)
            lineTo(90f * sX, 44f * sY)
            quadraticTo(98f * sX, 50f * sY, 90f * sX, 56f * sY)
            lineTo(42f * sX, 92f * sY)
            lineTo(30f * sX, 92f * sY)
            quadraticTo(21f * sX, 92f * sY, 21f * sX, 83f * sY)
            lineTo(21f * sX, 17f * sY)
            quadraticTo(21f * sX, 8f * sY, 30f * sX, 8f * sY)
            close()
        }
        drawPath(
            badgePath,
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFC01A), Color(0xFFFF5200)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        val facetPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(21f * sX, 17f * sY)
            quadraticTo(21f * sX, 8f * sY, 30f * sX, 8f * sY)
            lineTo(36f * sX, 8f * sY)
            lineTo(21f * sX, 60f * sY)
            close()
        }
        drawPath(facetPath, color = Color(0x8CFF6A00))

        val cutoutPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(42f * sX, 30f * sY)
            lineTo(42f * sX, 70f * sY)
            lineTo(72f * sX, 50f * sY)
            close()
        }
        drawPath(cutoutPath, color = Color(0xEB2A0C00))
    }
}

@Composable
private fun CastIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(24.dp, 20.dp)) {
        val sX = size.width / 52f
        val sY = size.height / 42f
        val gOrange = Brush.linearGradient(
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFFFB040)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.8f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val tvPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sX, 3f * sY)
            lineTo(42f * sX, 3f * sY)
            cubicTo(45.86f * sX, 3f * sY, 49f * sX, 6.13f * sY, 49f * sX, 10f * sY)
            lineTo(49f * sX, 32f * sY)
            cubicTo(49f * sX, 35.86f * sY, 45.86f * sX, 39f * sY, 42f * sX, 39f * sY)
            lineTo(28f * sX, 39f * sY)
        }
        drawPath(tvPath, brush = gOrange, style = stroke)

        drawArc(
            brush = gOrange,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-18f * sX, 18f * sY),
            size = Size(42f * sX, 42f * sY),
            style = stroke
        )
        drawArc(
            brush = gOrange,
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-8f * sX, 28f * sY),
            size = Size(22f * sX, 22f * sY),
            style = stroke
        )
        drawCircle(brush = gOrange, radius = (2.4f * sX).coerceAtLeast(1.5f), center = Offset(4.5f * sX, 37.5f * sY))
    }
}

@Composable
private fun TopDotsCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(8.dp, 22.dp)) {
        val sX = size.width / 16f
        val sY = size.height / 52f
        val r = (4.4f * sX).coerceAtLeast(2.2f)
        val cx = 8f * sX
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 7f * sY))
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 26f * sY))
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 45f * sY))
    }
}

@Composable
private fun SubtitlesIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(24.dp, 21.dp)) {
        val sX = size.width / 52f
        val sY = size.height / 46f
        val gSub = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFE4E2F0)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.8f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            brush = gSub,
            topLeft = Offset(2f * sX, 3f * sY),
            size = Size(48f * sX, 40f * sY),
            cornerRadius = CornerRadius(6f * sX, 6f * sY),
            style = stroke
        )
        val sw = (3.6f * sX).coerceAtLeast(1.8f)
        drawLine(brush = gSub, start = Offset(10f * sX, 15f * sY), end = Offset(42f * sX, 15f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gSub, start = Offset(10f * sX, 24f * sY), end = Offset(42f * sX, 24f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gSub, start = Offset(10f * sX, 33f * sY), end = Offset(23f * sX, 33f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gSub, start = Offset(29f * sX, 33f * sY), end = Offset(42f * sX, 33f * sY), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
private fun RepeatIconCanvas(uVal: Float, isActive: Boolean) {
    Canvas(modifier = Modifier.size(23.dp, 21.dp)) {
        val sX = size.width / 50f
        val sY = size.height / 46f
        val gRepeat = if (isActive) {
            Brush.linearGradient(
                colors = listOf(Color(0xFFFFC01A), Color(0xFFFF6A00)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        } else {
            Brush.linearGradient(
                colors = listOf(Color.White, Color(0xFFC9C7E0)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        }
        val stroke = Stroke(
            width = (3.8f * sX).coerceAtLeast(1.8f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val p1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(7f * sX, 21f * sY)
            cubicTo(8.5f * sX, 13f * sY, 15.5f * sX, 7f * sY, 24.5f * sX, 7f * sY)
            cubicTo(30f * sX, 7f * sY, 34f * sX, 9f * sY, 37f * sX, 12.5f * sY)
        }
        drawPath(p1, brush = gRepeat, style = stroke)

        val a1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(38f * sX, 3f * sY)
            lineTo(38f * sX, 13.5f * sY)
            lineTo(27.5f * sX, 13.5f * sY)
        }
        drawPath(a1, brush = gRepeat, style = stroke)

        val p2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(43f * sX, 25f * sY)
            cubicTo(41.5f * sX, 33f * sY, 34.5f * sX, 39f * sY, 25.5f * sX, 39f * sY)
            cubicTo(20f * sX, 39f * sY, 16f * sX, 37f * sY, 13f * sX, 33.5f * sY)
        }
        drawPath(p2, brush = gRepeat, style = stroke)

        val a2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(12f * sX, 43f * sY)
            lineTo(12f * sX, 32.5f * sY)
            lineTo(22.5f * sX, 32.5f * sY)
        }
        drawPath(a2, brush = gRepeat, style = stroke)
    }
}

@Composable
private fun PipIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(24.dp, 20.dp)) {
        val sX = size.width / 56f
        val sY = size.height / 46f
        val gFit = Brush.linearGradient(
            colors = listOf(Color.White, Color(0xFFE4E2F0)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        )
        val stroke = Stroke(
            width = (3.6f * sX).coerceAtLeast(1.8f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawRoundRect(
            brush = gFit,
            topLeft = Offset(2f * sX, 2f * sY),
            size = Size(52f * sX, 33f * sY),
            cornerRadius = CornerRadius(4f * sX, 4f * sY),
            style = stroke
        )
        val sw = (3.6f * sX).coerceAtLeast(1.8f)
        drawLine(brush = gFit, start = Offset(20f * sX, 43f * sY), end = Offset(36f * sX, 43f * sY), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(brush = gFit, start = Offset(28f * sX, 35f * sY), end = Offset(28f * sX, 43f * sY), strokeWidth = sw, cap = StrokeCap.Round)

        val pTL = androidx.compose.ui.graphics.Path().apply {
            moveTo(10f * sX, 14f * sY)
            lineTo(10f * sX, 10f * sY)
            lineTo(14f * sX, 10f * sY)
        }
        drawPath(pTL, brush = gFit, style = stroke)

        val pTR = androidx.compose.ui.graphics.Path().apply {
            moveTo(46f * sX, 14f * sY)
            lineTo(46f * sX, 10f * sY)
            lineTo(42f * sX, 10f * sY)
        }
        drawPath(pTR, brush = gFit, style = stroke)

        val pBL = androidx.compose.ui.graphics.Path().apply {
            moveTo(10f * sX, 23f * sY)
            lineTo(10f * sX, 27f * sY)
            lineTo(14f * sX, 27f * sY)
        }
        drawPath(pBL, brush = gFit, style = stroke)

        val pBR = androidx.compose.ui.graphics.Path().apply {
            moveTo(46f * sX, 23f * sY)
            lineTo(46f * sX, 27f * sY)
            lineTo(42f * sX, 27f * sY)
        }
        drawPath(pBR, brush = gFit, style = stroke)
    }
}

@Composable
private fun BottomMoreDotsCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(8.dp, 22.dp)) {
        val sX = size.width / 16f
        val sY = size.height / 48f
        val r = (4.4f * sX).coerceAtLeast(2.2f)
        val cx = 8f * sX
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 6f * sY))
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 24f * sY))
        drawCircle(color = Color.White, radius = r, center = Offset(cx, 42f * sY))
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

/* =========================================================================
   GLASS OBSIDIAN BRIGHTNESS & VOLUME GAUGES (nexora_2 mockup)
   ========================================================================= */

@Composable
private fun BrightnessGauge(
    uVal: Float,
    brightness: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color(0x9E0A0814))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(23.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            SunIconCanvas(uVal)

            Spacer(modifier = Modifier.height(10.dp))

            VerticalBarCanvas(fraction = brightness)

            Spacer(modifier = Modifier.height(8.dp))

            val pct = (brightness.coerceIn(0f, 1f) * 100f).roundToInt()
            Text(
                text = "$pct%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VolumeGauge(
    uVal: Float,
    volumeFraction: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(46.dp)
                .clip(RoundedCornerShape(23.dp))
                .background(Color(0x9E0A0814))
                .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(23.dp))
                .padding(vertical = 12.dp, horizontal = 6.dp)
        ) {
            SpeakerIconCanvas(uVal)

            Spacer(modifier = Modifier.height(10.dp))

            VerticalBarCanvas(fraction = volumeFraction)

            Spacer(modifier = Modifier.height(8.dp))

            val pct = (volumeFraction.coerceIn(0f, 1f) * 100f).roundToInt()
            Text(
                text = "$pct%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun VerticalBarCanvas(fraction: Float) {
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(modifier = Modifier.size(14.dp, 112.dp)) {
        val trackW = 6.dp.toPx()
        val trackLeft = (size.width - trackW) / 2f
        val r = trackW / 2f
        val barPath = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = trackLeft,
                    top = 0f,
                    right = trackLeft + trackW,
                    bottom = size.height,
                    cornerRadius = CornerRadius(r, r)
                )
            )
        }
        drawPath(barPath, color = Color(0x24FFFFFF))

        val fillH = size.height * clamped
        if (fillH > 0f) {
            clipPath(barPath) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.0f to Color(0xFFFF9A1A),
                        1.0f to Color(0xFFFF5200)
                    ),
                    topLeft = Offset(trackLeft, size.height - fillH),
                    size = Size(trackW, fillH)
                )
            }
        }

        // White dot thumb at level position
        val knobY = (size.height - fillH).coerceIn(5.dp.toPx(), size.height - 5.dp.toPx())
        drawCircle(
            color = Color(0x88FF6400),
            radius = 7.dp.toPx(),
            center = Offset(size.width / 2f, knobY)
        )
        drawCircle(
            color = Color.White,
            radius = 5.dp.toPx(),
            center = Offset(size.width / 2f, knobY)
        )
    }
}

@Composable
private fun SunIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(
            width = (1.8f * s).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        drawCircle(
            color = Color.White,
            radius = 4.2f * s,
            center = Offset(12f * s, 12f * s)
        )
        val sw = (1.8f * s).coerceAtLeast(1.5f)
        drawLine(color = Color.White, start = Offset(12f * s, 2.5f * s), end = Offset(12f * s, 5.2f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(12f * s, 18.8f * s), end = Offset(12f * s, 21.5f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(2.5f * s, 12f * s), end = Offset(5.2f * s, 12f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(18.8f * s, 12f * s), end = Offset(21.5f * s, 12f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(5.3f * s, 5.3f * s), end = Offset(7.2f * s, 7.2f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(16.8f * s, 16.8f * s), end = Offset(18.7f * s, 18.7f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(18.7f * s, 5.3f * s), end = Offset(16.8f * s, 7.2f * s), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(color = Color.White, start = Offset(7.2f * s, 16.8f * s), end = Offset(5.3f * s, 18.7f * s), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
private fun SpeakerIconCanvas(uVal: Float) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val s = size.width / 24f
        val stroke = Stroke(
            width = (1.8f * s).coerceAtLeast(1.5f),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val hornPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(3f * s, 9.5f * s)
            lineTo(7f * s, 9.5f * s)
            lineTo(12f * s, 5f * s)
            lineTo(12f * s, 19f * s)
            lineTo(7f * s, 14.5f * s)
            lineTo(3f * s, 14.5f * s)
            close()
        }
        drawPath(hornPath, color = Color.White)

        val wave1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(15.5f * s, 8.5f * s)
            cubicTo(17.5f * s, 10f * s, 17.5f * s, 14f * s, 15.5f * s, 15.5f * s)
        }
        drawPath(wave1, color = Color.White, style = stroke)

        val wave2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(18f * s, 6f * s)
            cubicTo(21.2f * s, 8.5f * s, 21.2f * s, 15.5f * s, 18f * s, 18f * s)
        }
        drawPath(wave2, color = Color.White, style = stroke)
    }
}
