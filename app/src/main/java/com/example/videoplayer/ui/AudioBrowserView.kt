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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.model.AudioItem
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.Playlist
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.ui.theme.*
import kotlinx.coroutines.launch

val SoftOrangeAccent = Color(0xFFFF8800)
val SoftOrangeLight = Color(0xFFFFB300)
val SoftOrangeGlowColor = Color(0x66FF8800)

@Composable
fun AudioBrowserView(
    onAudioSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var audios by remember { mutableStateOf<List<AudioItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var currentlyPlayingAudio by remember { mutableStateOf<AudioItem?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf<AudioItem?>(null) }

    LaunchedEffect(Unit) {
        audios = MediaStoreHelper.getAudios(context)
    }

    val filteredAudios = remember(audios, searchQuery) {
        if (searchQuery.isBlank()) {
            audios
        } else {
            audios.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search & Filter header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = {
                    Text("Поиск аудио треков...", color = GrayText, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = SoftOrangeAccent,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = GrayText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SoftOrangeAccent,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = SurfaceCardColor,
                    unfocusedContainerColor = SurfaceCardColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Всего треков: ${filteredAudios.size}",
                color = GrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                "АУДИОТЕКА",
                color = GoldenYellowLight,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredAudios, key = { it.id }) { audio ->
                val isSelected = currentlyPlayingAudio?.id == audio.id

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            currentlyPlayingAudio = audio
                            isPlaying = true
                            onAudioSelected(audio.uri.toString())
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) SurfaceCardColor.copy(alpha = 0.9f) else SurfaceCardColor,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) SoftOrangeAccent else Color.White.copy(alpha = 0.05f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) SoftOrangeAccent else Color.White.copy(alpha = 0.08f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected && isPlaying) Icons.Default.GraphicEq else Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = if (isSelected) Color.Black else SoftOrangeLight,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = audio.title,
                                color = if (isSelected) SoftOrangeLight else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${audio.artist} • ${audio.album}",
                                color = GrayText,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${audio.getFormattedDuration()} • ${audio.getFormattedSize()}",
                                color = GrayText.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }

                        IconButton(
                            onClick = {
                                showAddToPlaylistDialog = audio
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "В плейлист",
                                tint = GrayText,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                currentlyPlayingAudio = audio
                                isPlaying = !isPlaying
                                onAudioSelected(audio.uri.toString())
                            }
                        ) {
                            Icon(
                                imageVector = if (isSelected && isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Играть",
                                tint = SoftOrangeAccent,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Add to playlist dialog
    showAddToPlaylistDialog?.let { audio ->
        val playlists = remember { PlaylistManager.getPlaylists(context) }
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = null },
            title = {
                Text("Добавить в плейлист", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Выберите плейлист для:\n${audio.title}",
                        color = GrayText,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    playlists.forEach { pl ->
                        Button(
                            onClick = {
                                PlaylistManager.addItemToPlaylist(context, pl.id, audio.uri.toString())
                                showAddToPlaylistDialog = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SurfaceCardColor
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = null,
                                    tint = SoftOrangeAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(pl.name, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAddToPlaylistDialog = null }) {
                    Text("Отмена", color = GrayText)
                }
            },
            containerColor = DarkBackground
        )
    }
}
