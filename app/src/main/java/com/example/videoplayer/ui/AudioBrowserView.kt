package com.example.videoplayer.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.model.AudioItem
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.PlaylistManager
import kotlinx.coroutines.launch

val SoftOrangeAccent = Color(0xFFFF8A00)
val SoftOrangeLight = Color(0xFFFFA733)
val SoftOrangeGlowColor = Color(0xFFFF7A00)

@Composable
fun AudioBrowserView(
    onAudioSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var audios by remember { mutableStateOf<List<AudioItem>>(MediaStoreHelper.getSampleAudios()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(0) } // 0: Все, 1: Избранное, 2: Недавние
    var currentlyPlayingAudio by remember { mutableStateOf<AudioItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<AudioItem?>(null) }

    LaunchedEffect(Unit) {
        scope.launch {
            val list = MediaStoreHelper.getAudios(context)
            if (list.isNotEmpty()) {
                audios = list
            }
        }
    }

    val filteredAudios = audios.filter {
        it.title.contains(searchQuery, ignoreCase = true) ||
        it.artist.contains(searchQuery, ignoreCase = true) ||
        it.album.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search & Filter header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск треков, альбомов...", color = GrayText, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoftOrangeAccent,
                    unfocusedBorderColor = Color(0xFF222230),
                    focusedContainerColor = Color(0xFF13131B),
                    unfocusedContainerColor = Color(0xFF13131B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = SoftOrangeAccent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("Все треки (${filteredAudios.size})", "Популярные", "Саундтреки").forEachIndexed { index, title ->
                    val isSel = selectedFilter == index
                    Surface(
                        onClick = { selectedFilter = index },
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSel) SoftOrangeAccent else Color(0xFF171722),
                        border = if (isSel) null else BorderStroke(1.dp, Color(0xFF262638))
                    ) {
                        Text(
                            text = title,
                            color = if (isSel) Color.White else Color(0xFFB0B0C4),
                            fontSize = 12.sp,
                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Audio Track List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = if (currentlyPlayingAudio != null) 90.dp else 16.dp)
        ) {
            items(filteredAudios, key = { it.id }) { audio ->
                val isCurrent = currentlyPlayingAudio?.id == audio.id

                Surface(
                    color = if (isCurrent) Color(0xFF1D1B28) else Color(0xFF13131C),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isCurrent) SoftOrangeAccent.copy(alpha = 0.8f) else Color(0xFF20202E)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            currentlyPlayingAudio = audio
                            isPlaying = true
                            onAudioSelected(audio.uri.toString())
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Disc / Music Icon Pill
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isCurrent) {
                                        Brush.linearGradient(listOf(SoftOrangeLight, SoftOrangeAccent))
                                    } else {
                                        Brush.linearGradient(listOf(Color(0xFF242436), Color(0xFF181824)))
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "♪",
                                color = if (isCurrent) Color.White else SoftOrangeAccent,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Title, Artist, Duration
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = audio.title,
                                color = if (isCurrent) SoftOrangeLight else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = audio.artist,
                                    color = GrayText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(text = "•", color = Color(0xFF45455A), fontSize = 10.sp)
                                Text(
                                    text = audio.formattedDuration,
                                    color = Color(0xFFA5A5BA),
                                    fontSize = 11.sp
                                )
                                Text(text = "•", color = Color(0xFF45455A), fontSize = 10.sp)
                                Text(
                                    text = audio.formattedSize,
                                    color = Color(0xFF75758A),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Add to Playlist / Menu button
                        IconButton(
                            onClick = { showAddToPlaylistDialog = audio },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text(text = "⋮", color = Color(0xFFB0B0C4), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Floating Mini-Player bar when a track is chosen
        currentlyPlayingAudio?.let { audio ->
            Surface(
                color = Color(0xFF161522),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(SoftOrangeAccent, SoftOrangeLight))),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .clickable { onAudioSelected(audio.uri.toString()) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(SoftOrangeAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = if (isPlaying) "❚❚" else "▶", color = Color.White, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audio.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${audio.artist} • ${audio.formattedDuration}",
                            color = SoftOrangeLight,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // Open full player button
                    TextButton(
                        onClick = { onAudioSelected(audio.uri.toString()) }
                    ) {
                        Text("Плеер", color = SoftOrangeAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Add to Playlist Dialog
    showAddToPlaylistDialog?.let { audio ->
        val playlists = remember { PlaylistManager.getPlaylists(context) }
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            containerColor = Color(0xFF161622),
            title = {
                Text("Добавить в плейлист", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = audio.title, color = SoftOrangeLight, fontSize = 13.sp, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    playlists.forEach { pl ->
                        Surface(
                            onClick = {
                                PlaylistManager.addItemToPlaylist(context, pl.id, audio.uri.toString())
                                showAddToPlaylistDialog = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1F1F2E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (pl.iconType) {
                                        "favorites" -> "⭐"
                                        "movie" -> "🎬"
                                        "music" -> "🎵"
                                        else -> "📁"
                                    },
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = pl.name, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = null }) {
                    Text("Закрыть", color = Color.White)
                }
            }
        )
    }
}
