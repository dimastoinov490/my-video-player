package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
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
            .background(Color(0xFF030208))
    ) {
        // Header with Create button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Плейлисты",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Всего: ${playlists.size}",
                    color = Color(0xFF8D8AA8),
                    fontSize = 12.sp
                )
            }

            Button(
                onClick = { showCreateDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFFF9A1A),
                    contentColor = Color(0xFF3A1A00)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("playlists_create_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = Color(0xFF3A1A00),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "Создать",
                    color = Color(0xFF3A1A00),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Artboard 6: 2x2 Colored Gradient Cards Grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(playlists, key = { it.id }) { pl ->
                val nameLower = pl.name.lowercase()
                val typeLower = pl.iconType.lowercase()
                val (gradColors, iconVec, iconTint) = when {
                    typeLower == "favorites" || nameLower.contains("избран") -> Triple(
                        listOf(Color(0xFF3D2A2E), Color(0xFF1C1218)),
                        Icons.Default.Star,
                        Color(0xFFFFA81F)
                    )
                    typeLower == "recent" || nameLower.contains("недавн") -> Triple(
                        listOf(Color(0xFF152A66), Color(0xFF0B1233)),
                        Icons.Default.History,
                        Color(0xFF2F8CF5)
                    )
                    typeLower == "movie" || nameLower.contains("фильм") || nameLower.contains("настроен") -> Triple(
                        listOf(Color(0xFF3A1FB0), Color(0xFF120A3C)),
                        Icons.Default.GraphicEq,
                        Color(0xFFB86BFF)
                    )
                    typeLower == "music" || nameLower.contains("клип") || nameLower.contains("роман") -> Triple(
                        listOf(Color(0xFF5A2248), Color(0xFF2A1020)),
                        Icons.Default.Favorite,
                        Color(0xFFFF4D63)
                    )
                    else -> Triple(
                        listOf(Color(0xFF1A162D), Color(0xFF0D0B1A)),
                        Icons.Default.PlaylistPlay,
                        Color(0xFFFF9A1A)
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(168.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable { selectedPlaylist = pl }
                        .testTag("playlist_card_${pl.id}"),
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, Color(0x28FFFFFF))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(gradColors))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVec,
                                contentDescription = null,
                                tint = iconTint,
                                modifier = Modifier.size(48.dp)
                            )
                        }

                        Column {
                            Text(
                                text = pl.name,
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${pl.itemUris.size} медиафайлов",
                                color = Color(0xCCB8B5CE),
                                fontSize = 12.sp
                            )
                        }
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
