package com.example.videoplayer.ui

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
import com.example.videoplayer.model.Playlist
import com.example.videoplayer.model.PlaylistManager

@Composable
fun PlaylistsView(
    onMediaSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var playlists by remember { mutableStateOf(PlaylistManager.getPlaylists(context)) }
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    fun refresh() {
        playlists = PlaylistManager.getPlaylists(context)
        selectedPlaylist?.let { curr ->
            selectedPlaylist = playlists.find { it.id == curr.id }
        }
    }

    if (selectedPlaylist != null) {
        // Detailed Playlist Screen
        PlaylistDetailView(
            playlist = selectedPlaylist!!,
            onBack = { selectedPlaylist = null },
            onPlayAll = {
                val firstUri = selectedPlaylist!!.itemUris.firstOrNull()
                if (firstUri != null) {
                    onMediaSelected(firstUri)
                }
            },
            onPlayItem = { uri -> onMediaSelected(uri) },
            onRemoveItem = { uri ->
                PlaylistManager.removeItemFromPlaylist(context, selectedPlaylist!!.id, uri)
                refresh()
            },
            onDeletePlaylist = {
                PlaylistManager.deletePlaylist(context, selectedPlaylist!!.id)
                selectedPlaylist = null
                refresh()
            }
        )
    } else {
        // Playlists List
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(horizontal = 14.dp)
        ) {
            // Header with "+ Создать" button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Мои Плейлисты",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${playlists.size} списков воспроизведения",
                        color = GrayText,
                        fontSize = 12.sp
                    )
                }

                Surface(
                    onClick = { showCreateDialog = true },
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, GoldenYellowLight.copy(alpha = 0.8f))
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF9E00), Color(0xFFFF6D00))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("+", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Black)
                            Text("Создать", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Playlists Cards Grid / List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Surface(
                        color = Color(0xFF13131D),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Color(0xFF222234)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedPlaylist = playlist }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon Box with Warm Orange Tint
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF222030), Color(0xFF181824))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (playlist.iconType) {
                                        "favorites" -> "⭐"
                                        "recent" -> "🕒"
                                        "movie" -> "🎬"
                                        "music" -> "🎵"
                                        else -> "📁"
                                    },
                                    fontSize = 24.sp
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (playlist.description.isNotEmpty()) playlist.description else "${playlist.itemUris.size} медиафайлов",
                                    color = GrayText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${playlist.itemUris.size} файлов",
                                    color = SoftOrangeLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Quick Play button
                            if (playlist.itemUris.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(SoftOrangeAccent)
                                        .clickable {
                                            onMediaSelected(playlist.itemUris.first())
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("▶", color = Color.White, fontSize = 14.sp)
                                }
                            } else {
                                Text(
                                    text = "›",
                                    color = Color(0xFF656580),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        var newName by remember { mutableStateOf("") }
        var selectedIcon by remember { mutableStateOf("playlist") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = Color(0xFF161622),
            title = { Text("Новый плейлист", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Название плейлиста...", color = GrayText) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftOrangeAccent,
                            unfocusedBorderColor = Color(0xFF282838),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = SoftOrangeAccent
                        )
                    )

                    Text("Выберите значок:", color = GrayText, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        listOf(
                            Pair("📁", "playlist"),
                            Pair("⭐", "favorites"),
                            Pair("🎬", "movie"),
                            Pair("🎵", "music")
                        ).forEach { (icon, type) ->
                            val isSel = selectedIcon == type
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSel) SoftOrangeAccent else Color(0xFF20202F))
                                    .clickable { selectedIcon = type },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(icon, fontSize = 20.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            PlaylistManager.createPlaylist(context, newName.trim(), selectedIcon)
                            refresh()
                            showCreateDialog = false
                        }
                    }
                ) {
                    Text("Создать", color = SoftOrangeAccent, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Отмена", color = Color.White)
                }
            }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 14.dp)
    ) {
        // Top Back Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = Color(0xFF1A1A26),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Column {
                    Text(
                        text = playlist.name,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${playlist.itemUris.size} медиафайлов",
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
            }

            // Delete button for custom playlists
            if (!playlist.id.startsWith("pl_fav") && !playlist.id.startsWith("pl_rec")) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Text("Удалить", color = Color(0xFFFF5252), fontSize = 12.sp)
                }
            }
        }

        // Action Buttons Row: Play All
        if (playlist.itemUris.isNotEmpty()) {
            Surface(
                onClick = onPlayAll,
                shape = RoundedCornerShape(20.dp),
                color = SoftOrangeAccent,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("▶", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Воспроизвести всё",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Плейлист пуст", color = GrayText, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Добавляйте видео и аудио через меню ⋮ на карточках",
                        color = Color(0xFF656578),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Items List
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(playlist.itemUris) { uri ->
                val fileName = uri.substringAfterLast("/").substringBefore("?")
                Surface(
                    color = Color(0xFF13131D),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0xFF1F1F2E)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayItem(uri) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222030)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("▶", color = SoftOrangeAccent, fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Text(
                            text = fileName.ifEmpty { uri },
                            color = Color.White,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onRemoveItem(uri) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("✕", color = Color(0xFF88889C), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF161622),
            title = { Text("Удалить плейлист?", color = Color.White) },
            text = { Text("Плейлист будет безвозвратно удален.", color = GrayText) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDeletePlaylist()
                }) {
                    Text("Удалить", color = Color(0xFFFF5252))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = Color.White)
                }
            }
        )
    }
}
