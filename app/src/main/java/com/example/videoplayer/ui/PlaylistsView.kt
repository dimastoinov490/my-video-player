package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.videoplayer.model.Playlist
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.ui.theme.*

@Composable
fun PlaylistsView(
    onMediaSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(PlaylistManager.getPlaylists(context)) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var selectedIcon by remember { mutableStateOf("playlist") }

    fun refresh() {
        playlists = PlaylistManager.getPlaylists(context)
    }

    if (selectedPlaylist != null) {
        PlaylistDetailView(
            playlist = selectedPlaylist!!,
            onBack = { selectedPlaylist = null },
            onPlayAll = {
                val first = selectedPlaylist!!.itemUris.firstOrNull()
                if (first != null) onMediaSelected(first)
            },
            onPlayItem = { uri -> onMediaSelected(uri) },
            onRemoveItem = { uri ->
                PlaylistManager.removeItemFromPlaylist(context, selectedPlaylist!!.id, uri)
                selectedPlaylist = PlaylistManager.getPlaylists(context).find { it.id == selectedPlaylist!!.id }
                refresh()
            },
            onDeletePlaylist = {
                PlaylistManager.deletePlaylist(context, selectedPlaylist!!.id)
                selectedPlaylist = null
                refresh()
            }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header with Create button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Мои плейлисты",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Всего: ${playlists.size}",
                    color = GrayText,
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonOrange
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Создать",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(playlists, key = { it.id }) { pl ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { selectedPlaylist = pl },
                    shape = RoundedCornerShape(16.dp),
                    color = SurfaceCardColor,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeonOrange.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (pl.iconType) {
                                    "favorites" -> Icons.Default.Favorite
                                    "recent" -> Icons.Default.History
                                    "movie" -> Icons.Default.Movie
                                    "music" -> Icons.Default.MusicNote
                                    else -> Icons.Default.PlaylistPlay
                                },
                                contentDescription = null,
                                tint = GoldenYellowLight,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (pl.description.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = pl.description,
                                    color = GrayText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${pl.itemUris.size} медиафайлов",
                                color = GoldenYellowLight.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = GrayText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                newName = ""
            },
            title = {
                Text("Новый плейлист", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Название плейлиста", color = GrayText) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Выберите значок:", color = GrayText, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(
                            "playlist" to Icons.Default.PlaylistPlay,
                            "favorites" to Icons.Default.Favorite,
                            "movie" to Icons.Default.Movie,
                            "music" to Icons.Default.MusicNote
                        ).forEach { (type, icon) ->
                            IconButton(
                                onClick = { selectedIcon = type },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (selectedIcon == type) NeonOrange.copy(alpha = 0.25f)
                                        else Color.Transparent
                                    )
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (selectedIcon == type) GoldenYellowLight else GrayText
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank()) {
                            PlaylistManager.createPlaylist(context, newName.trim(), selectedIcon)
                            showCreateDialog = false
                            newName = ""
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                ) {
                    Text("Создать", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateDialog = false
                    newName = ""
                }) {
                    Text("Отмена", color = GrayText)
                }
            },
            containerColor = DarkBackground
        )
    }
}

@Composable
fun PlaylistDetailView(
    playlist: Playlist,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayItem: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onDeletePlaylist: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Назад",
                    tint = NeonOrange
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${playlist.itemUris.size} треков / видео",
                    color = GrayText,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Удалить плейлист",
                    tint = Color(0xFFFF5252)
                )
            }
        }

        // Play All Banner
        if (playlist.itemUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonOrange
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Воспроизвести всё",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Items
        if (playlist.itemUris.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Плейлист пуст.\nДобавляйте медиафайлы из библиотеки или аудиоплеера.",
                    color = GrayText,
                    fontSize = 14.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlist.itemUris) { uri ->
                    val displayName = uri.substringAfterLast('/').substringBefore('?')
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPlayItem(uri) },
                        shape = RoundedCornerShape(12.dp),
                        color = SurfaceCardColor
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = GoldenYellowLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = displayName,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onRemoveItem(uri) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Удалить",
                                    tint = GrayText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить плейлист?", color = Color.White) },
            text = { Text("Плейлист \"${playlist.name}\" будет безвозвратно удалён.", color = GrayText) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDeletePlaylist()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = GrayText)
                }
            },
            containerColor = DarkBackground
        )
    }
}
