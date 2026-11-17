package com.example.videoplayer.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.videoplayer.player.EqualizerState
import com.example.videoplayer.player.PlaybackPreferences
import com.example.videoplayer.player.PlaybackTrack
import com.example.videoplayer.player.SubtitleStyleState
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private fun fmtAudioTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val sec = totalSec % 60L
    val min = (totalSec / 60L) % 60L
    val hour = totalSec / 3600L
    return if (hour > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hour, min, sec)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", min, sec)
    }
}

/* =========================================================================
   ARTBOARD 4 — DEDICATED FULL-SCREEN AUDIO PLAYER
   ========================================================================= */
@Composable
fun AudioPlayerOverlay(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    isFavorite: Boolean,
    isShuffle: Boolean,
    repeatMode: VideoRepeatMode,
    currentTimeMs: Long,
    totalDurationMs: Long,
    hasQueueNavigation: Boolean,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenMore: () -> Unit,
    onToggleShuffle: () -> Unit,
    onPreviousTrack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNextTrack: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_spec")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )
    val coverPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cover_pulse"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF0C0916),
                        Color(0xFF1A0B22),
                        Color(0xFF471808)
                    ),
                    start = Offset.Zero,
                    end = Offset(900f, 1900f)
                )
            )
    ) {
        // Ambient radial glows (Artboard 4)
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x456A20FF), Color.Transparent),
                    center = Offset(size.width * 0.16f, size.height * 0.82f),
                    radius = size.width * 0.65f
                ),
                center = Offset(size.width * 0.16f, size.height * 0.82f),
                radius = size.width * 0.65f
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x4DFF6A00), Color.Transparent),
                    center = Offset(size.width * 0.86f, size.height * 0.88f),
                    radius = size.width * 0.72f
                ),
                center = Offset(size.width * 0.86f, size.height * 0.88f),
                radius = size.width * 0.72f
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar: Back, Favorite, Equalizer, More
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("audio_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color.White
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("audio_fav_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "В избранное",
                            tint = if (isFavorite) Color(0xFFFF4D63) else Color.White
                        )
                    }
                    IconButton(
                        onClick = onOpenEqualizer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("audio_eq_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Эквалайзер",
                            tint = Color(0xFFFF9A1A)
                        )
                    }
                    IconButton(
                        onClick = onOpenMore,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("audio_more_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Ещё",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Glowing Album Art Card + Floating Music Note Badge
            Box(
                modifier = Modifier.size(214.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(196.dp)
                        .shadow(
                            elevation = if (isPlaying) (22f * coverPulse).dp else 10.dp,
                            shape = RoundedCornerShape(28.dp),
                            spotColor = Color(0xAAFF7A00),
                            ambientColor = Color(0xAA6A20FF)
                        )
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF281948),
                                    Color(0xFF150D27),
                                    Color(0xFF3E180A)
                                )
                            )
                        )
                        .border(1.5.dp, Color(0x66FF9A1A), RoundedCornerShape(28.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(134.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x33FF9A1A), Color(0xFF0A0812))
                            ),
                            radius = size.minDimension / 2f
                        )
                        drawCircle(
                            color = Color(0x55FFFFFF),
                            radius = size.minDimension * 0.42f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color(0x33FF9A1A),
                            radius = size.minDimension * 0.28f,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2.dp.toPx())
                        )
                        drawCircle(
                            brush = Brush.linearGradient(listOf(Color(0xFFFFC01A), Color(0xFFFF5200))),
                            radius = size.minDimension * 0.14f
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Floating #FF9A1A Circle Music Badge at bottom-right
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(48.dp)
                        .shadow(10.dp, CircleShape, spotColor = Color(0xFFFF8A00))
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(listOf(Color(0xFFFFB020), Color(0xFFFF6A00)))
                        )
                        .border(2.dp, Color(0xFF1A0B22), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = Color(0xFF280D00),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Track Title & Subtitle
            Text(
                text = title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = subtitle.ifBlank { "Аудиодорожка Nexora" },
                color = Color(0xFFB8B5CE),
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Seek Slider & Time Row
            val progressFraction = if (totalDurationMs > 0L) {
                (currentTimeMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .pointerInput(totalDurationMs) {
                        detectTapGestures { offset ->
                            if (totalDurationMs > 0L && size.width > 0) {
                                val frac = (offset.x / size.width).coerceIn(0f, 1f)
                                onSeekTo((frac * totalDurationMs).toLong())
                            }
                        }
                    }
                    .pointerInput(totalDurationMs) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            if (totalDurationMs > 0L && size.width > 0) {
                                val frac = (change.position.x / size.width).coerceIn(0f, 1f)
                                onSeekTo((frac * totalDurationMs).toLong())
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                    val lineH = 4.5.dp.toPx()
                    val y = (size.height - lineH) / 2f
                    val r = lineH / 2f
                    drawRoundRect(
                        color = Color(0x30FFFFFF),
                        topLeft = Offset(0f, y),
                        size = Size(size.width, lineH),
                        cornerRadius = CornerRadius(r, r)
                    )
                    val fillW = (size.width * progressFraction).coerceIn(0f, size.width)
                    if (fillW > 0f) {
                        drawRoundRect(
                            brush = Brush.horizontalGradient(listOf(Color(0xFFFF9A1A), Color(0xFFFF5200))),
                            topLeft = Offset(0f, y),
                            size = Size(fillW, lineH),
                            cornerRadius = CornerRadius(r, r)
                        )
                    }
                    val cx = fillW.coerceIn(7.dp.toPx(), (size.width - 7.dp.toPx()).coerceAtLeast(7.dp.toPx()))
                    drawCircle(color = Color(0x66FF8C00), radius = 11.dp.toPx(), center = Offset(cx, size.height / 2f))
                    drawCircle(color = Color.White, radius = 7.dp.toPx(), center = Offset(cx, size.height / 2f))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = fmtAudioTime(currentTimeMs),
                    color = Color(0xFFE4E2F0),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = fmtAudioTime(totalDurationMs),
                    color = Color(0xFFA9A7C4),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5-Button Transport Controls (Artboard 4)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("audio_shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Перемешать",
                        tint = if (isShuffle) Color(0xFFFF9A1A) else Color(0xFFA9A7C4)
                    )
                }

                IconButton(
                    onClick = onPreviousTrack,
                    enabled = hasQueueNavigation,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("audio_prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = "Предыдущий трек",
                        tint = if (hasQueueNavigation) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Glowing Ring Play/Pause
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .shadow(16.dp, CircleShape, spotColor = Color(0xAAFF7A00))
                        .clip(CircleShape)
                        .background(
                            Brush.verticalGradient(listOf(Color(0xFF4A2005), Color(0xFF1C0C02)))
                        )
                        .border(2.5.dp, Color(0xFFFF9A1A), CircleShape)
                        .clickable(onClick = onTogglePlayPause)
                        .testTag("audio_play_pause_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(
                    onClick = onNextTrack,
                    enabled = hasQueueNavigation,
                    modifier = Modifier
                        .size(50.dp)
                        .testTag("audio_next_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Следующий трек",
                        tint = if (hasQueueNavigation) Color.White else Color(0x66FFFFFF),
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = onToggleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("audio_repeat_button")
                ) {
                    Icon(
                        imageVector = if (repeatMode == VideoRepeatMode.REPEAT_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Повтор",
                        tint = if (repeatMode != VideoRepeatMode.OFF) Color(0xFFFF9A1A) else Color(0xFFA9A7C4)
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            // Animated Glowing Spectrum Waveform Visualizer (Artboard 4)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x4D0A0714))
                    .border(1.dp, Color(0x28FF9A1A), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val barCount = 34
                    val spacing = size.width / barCount
                    val barW = (spacing * 0.56f).coerceAtLeast(3.dp.toPx())
                    val centerY = size.height / 2f
                    for (i in 0 until barCount) {
                        val env = sin((i.toFloat() / (barCount - 1)) * PI).toFloat().coerceAtLeast(0.18f)
                        val wave = if (isPlaying) {
                            abs(sin(wavePhase + i * 0.42f)) * 0.65f +
                                abs(sin(wavePhase * 1.7f - i * 0.25f)) * 0.35f
                        } else {
                            0.18f
                        }
                        val barH = (size.height * env * wave).coerceIn(6.dp.toPx(), size.height)
                        val x = i * spacing + (spacing - barW) / 2f
                        val ratio = i.toFloat() / barCount
                        val barColor = Color(
                            red = 1.0f - ratio * 0.15f,
                            green = (0.68f - ratio * 0.36f).coerceIn(0.2f, 0.8f),
                            blue = (0.1f + ratio * 0.65f).coerceIn(0.1f, 0.85f),
                            alpha = if (isPlaying) 0.92f else 0.45f
                        )
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, centerY - barH / 2f),
                            size = Size(barW, barH),
                            cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/* =========================================================================
   ARTBOARD 9 — INTERACTIVE 5-BAND EQUALIZER DIALOG
   ========================================================================= */
@Composable
fun EqualizerDialog(
    state: EqualizerState,
    onStateChanged: (EqualizerState) -> Unit,
    onDismiss: () -> Unit
) {
    val bandsLabels = remember { listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, Color(0x809664AA)),
            shadowElevation = 24.dp,
            modifier = Modifier.widthIn(min = 290.dp, max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0F0C18), Color(0xFF050309)))
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = Color(0xFFFF9A1A),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Эквалайзер",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { onStateChanged(state.copy(enabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF8A00),
                            uncheckedThumbColor = Color(0xFFC9C7E0),
                            uncheckedTrackColor = Color(0xFF28253A)
                        )
                    )
                }

                // Preset Chips Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlaybackPreferences.EQ_PRESETS.forEach { (presetName, presetBands) ->
                        val active = state.presetName == presetName
                        Surface(
                            onClick = {
                                onStateChanged(
                                    state.copy(
                                        enabled = true,
                                        presetName = presetName,
                                        bands = presetBands
                                    )
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = if (active) Color(0xFFFF8A00) else Color(0xFF171426),
                            border = BorderStroke(
                                1.dp,
                                if (active) Color(0xFFFFC01A) else Color(0x28FFFFFF)
                            )
                        ) {
                            Text(
                                text = presetName,
                                color = if (active) Color(0xFF2A1000) else Color(0xFFE4E2F0),
                                fontSize = 12.sp,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }

                // 5 Vertical Glowing Sliders
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF141122))
                        .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(18.dp))
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    bandsLabels.forEachIndexed { idx, freq ->
                        val dbVal = state.bands.getOrElse(idx) { 0f }.coerceIn(-12f, 12f)
                        val fraction = ((dbVal + 12f) / 24f).coerceIn(0f, 1f)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(50.dp)
                        ) {
                            Text(
                                text = (if (dbVal > 0f) "+" else "") + "${dbVal.roundToInt()}dB",
                                color = Color(0xFFFF9A1A),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(144.dp)
                                    .pointerInput(state, idx) {
                                        detectTapGestures { offset ->
                                            if (size.height > 0) {
                                                val f = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                                                val newDb = ((f * 24f) - 12f).roundToInt().toFloat().coerceIn(-12f, 12f)
                                                val nextBands = state.bands.toMutableList()
                                                while (nextBands.size < 5) nextBands.add(0f)
                                                nextBands[idx] = newDb
                                                onStateChanged(
                                                    state.copy(
                                                        enabled = true,
                                                        presetName = "Свой",
                                                        bands = nextBands
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    .pointerInput(state, idx) {
                                        detectVerticalDragGestures { change, _ ->
                                            change.consume()
                                            if (size.height > 0) {
                                                val f = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                                val newDb = ((f * 24f) - 12f).roundToInt().toFloat().coerceIn(-12f, 12f)
                                                val nextBands = state.bands.toMutableList()
                                                while (nextBands.size < 5) nextBands.add(0f)
                                                nextBands[idx] = newDb
                                                onStateChanged(
                                                    state.copy(
                                                        enabled = true,
                                                        presetName = "Свой",
                                                        bands = nextBands
                                                    )
                                                )
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.width(20.dp).fillMaxHeight()) {
                                    val railW = 6.dp.toPx()
                                    val left = (size.width - railW) / 2f
                                    drawRoundRect(
                                        color = Color(0x28FFFFFF),
                                        topLeft = Offset(left, 0f),
                                        size = Size(railW, size.height),
                                        cornerRadius = CornerRadius(railW / 2f, railW / 2f)
                                    )
                                    val fillH = size.height * fraction
                                    if (fillH > 0f) {
                                        drawRoundRect(
                                            brush = Brush.verticalGradient(listOf(Color(0xFFFF9A1A), Color(0xFFFF5200))),
                                            topLeft = Offset(left, size.height - fillH),
                                            size = Size(railW, fillH),
                                            cornerRadius = CornerRadius(railW / 2f, railW / 2f)
                                        )
                                    }
                                    val knobY = (size.height - fillH).coerceIn(8.dp.toPx(), size.height - 8.dp.toPx())
                                    drawCircle(
                                        color = Color(0x70FF8A00),
                                        radius = 10.dp.toPx(),
                                        center = Offset(size.width / 2f, knobY)
                                    )
                                    drawCircle(
                                        color = Color.White,
                                        radius = 7.dp.toPx(),
                                        center = Offset(size.width / 2f, knobY)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = freq,
                                color = Color(0xFFA9A7C4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9A1A),
                        contentColor = Color(0xFF3A1A00)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Готово", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/* =========================================================================
   ARTBOARD 12 — SUBTITLES & AUDIO TRACK CUSTOMIZATION DIALOG
   ========================================================================= */
@Composable
fun SubtitlesCustomizationDialog(
    subStyle: SubtitleStyleState,
    useNativeFallback: Boolean,
    audioTracks: List<PlaybackTrack>,
    subtitleTracks: List<PlaybackTrack>,
    selectedAudioTrack: Int,
    selectedSubtitleTrack: Int,
    onStyleChanged: (SubtitleStyleState) -> Unit,
    onSelectAudioTrack: (Int) -> Unit,
    onSelectSubtitleTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val subColorSwatches = remember {
        listOf("#ECECF0", "#FFA500", "#2F6BFF", "#2EE26A", "#FF2D8F")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent,
            border = BorderStroke(1.5.dp, Color(0x809664AA)),
            shadowElevation = 24.dp,
            modifier = Modifier.widthIn(min = 290.dp, max = 380.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFF0F0C18), Color(0xFF050309)))
                    )
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header + Subtitles Toggle Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = Color(0xFFFF9A1A),
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = "Субтитры и Аудио",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Switch(
                        checked = subStyle.enabled && selectedSubtitleTrack != -1,
                        onCheckedChange = { on ->
                            onStyleChanged(subStyle.copy(enabled = on))
                            if (!on) {
                                onSelectSubtitleTrack(-1)
                            } else if (selectedSubtitleTrack == -1 && subtitleTracks.isNotEmpty()) {
                                val firstReal = subtitleTracks.firstOrNull { it.id >= 0 }
                                if (firstReal != null) onSelectSubtitleTrack(firstReal.id)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFFFF8A00),
                            uncheckedThumbColor = Color(0xFFC9C7E0),
                            uncheckedTrackColor = Color(0xFF28253A)
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Text Size Pill (Artboard 12)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF171424))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Размер текста", color = Color(0xFFE4E2F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                onClick = {
                                    onStyleChanged(subStyle.copy(fontSizeSp = (subStyle.fontSizeSp - 2).coerceIn(12, 32)))
                                },
                                shape = CircleShape,
                                color = Color(0xFF252136),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("−", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                            Text(
                                text = "${subStyle.fontSizeSp} sp",
                                color = Color(0xFFFF9A1A),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Surface(
                                onClick = {
                                    onStyleChanged(subStyle.copy(fontSizeSp = (subStyle.fontSizeSp + 2).coerceIn(12, 32)))
                                },
                                shape = CircleShape,
                                color = Color(0xFF252136),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("+", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    // Text Color Capsule w/ 5 Glowing Dots (Artboard 12)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF171424))
                            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Цвет текста", color = Color(0xFFE4E2F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            subColorSwatches.forEach { hex ->
                                val parsedColor = Color(android.graphics.Color.parseColor(hex))
                                val isSel = subStyle.colorHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .shadow(if (isSel) 10.dp else 0.dp, CircleShape, spotColor = parsedColor)
                                        .clip(CircleShape)
                                        .background(parsedColor)
                                        .border(
                                            width = if (isSel) 2.5.dp else 1.dp,
                                            color = if (isSel) Color.White else Color(0x44FFFFFF),
                                            shape = CircleShape
                                        )
                                        .clickable { onStyleChanged(subStyle.copy(colorHex = hex)) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x286E5FBE))

                    Text("Язык субтитров", color = Color(0xFFFF9A1A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (useNativeFallback) {
                        Text("Выбор дорожек недоступен в резервном Android-плеере.", color = Color(0xFFA9A7C4), fontSize = 12.sp)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onSelectSubtitleTrack(-1) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSubtitleTrack == -1,
                                onClick = { onSelectSubtitleTrack(-1) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Color(0xFFFF9A1A),
                                    unselectedColor = Color(0xFF8D8AA8)
                                )
                            )
                            Text("Отключены", color = Color(0xFFE4E2F0), fontSize = 13.sp)
                        }
                        if (subtitleTracks.isEmpty()) {
                            Text("Встроенные субтитры не обнаружены.", color = Color(0xFFA9A7C4), fontSize = 12.sp)
                        } else {
                            subtitleTracks.forEach { track ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { onSelectSubtitleTrack(track.id) }
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedSubtitleTrack == track.id,
                                        onClick = { onSelectSubtitleTrack(track.id) },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = Color(0xFFFF9A1A),
                                            unselectedColor = Color(0xFF8D8AA8)
                                        )
                                    )
                                    Text(track.name.ifBlank { "Субтитры ${track.id}" }, color = Color(0xFFE4E2F0), fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0x286E5FBE))

                    Text("Звуковая дорожка", color = Color(0xFFFF9A1A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    if (useNativeFallback) {
                        Text("Выбор дорожек недоступен в резервном Android-плеере.", color = Color(0xFFA9A7C4), fontSize = 12.sp)
                    } else if (audioTracks.isEmpty()) {
                        Text("Звуковые дорожки пока не обнаружены.", color = Color(0xFFA9A7C4), fontSize = 12.sp)
                    } else {
                        audioTracks.forEach { track ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onSelectAudioTrack(track.id) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedAudioTrack == track.id,
                                    onClick = { onSelectAudioTrack(track.id) },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFFFF9A1A),
                                        unselectedColor = Color(0xFF8D8AA8)
                                    )
                                )
                                Text(track.name.ifBlank { "Дорожка ${track.id}" }, color = Color(0xFFE4E2F0), fontSize = 13.sp)
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9A1A),
                        contentColor = Color(0xFF3A1A00)
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Text("Закрыть", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
