package com.example.videoplayer.ui

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.R
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.ui.theme.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onVideoSelected: (String) -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProtectedVault: () -> Unit,
) {
    val context = LocalContext.current

    var currentTab by remember { mutableIntStateOf(0) }
    var bottomNavIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    fun loadVideos() {
        isRefreshing = true
        try {
            videos = MediaStoreHelper.getVideos(context)
        } catch (e: Exception) {
            Log.e("VideoLibraryScreen", "Failed to load videos", e)
            videos = emptyList()
        } finally {
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        loadVideos()
    }

    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) {
            videos
        } else {
            videos.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(28.dp),
                        spotColor = GoldenYellowGlow,
                        ambientColor = GoldenYellowGlow
                    ),
                shape = RoundedCornerShape(28.dp),
                color = SurfaceCardColor,
                border = BorderStroke(1.dp, GoldenYellowLight.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavRoundedItem(
                        icon = Icons.Default.PlayCircle,
                        label = "Видео",
                        isSelected = bottomNavIndex == 0,
                        onClick = {
                            bottomNavIndex = 0
                            currentTab = 0
                        }
                    )
                    BottomNavRoundedItem(
                        icon = Icons.Default.MusicNote,
                        label = "Аудио",
                        isSelected = bottomNavIndex == 1,
                        onClick = { bottomNavIndex = 1 }
                    )
                    BottomNavRoundedItem(
                        icon = Icons.Default.Folder,
                        label = "Обзор",
                        isSelected = bottomNavIndex == 2,
                        onClick = { bottomNavIndex = 2 }
                    )
                    BottomNavRoundedItem(
                        icon = Icons.Default.PlaylistPlay,
                        label = "Плейлист",
                        isSelected = bottomNavIndex == 3,
                        onClick = { bottomNavIndex = 3 }
                    )
                    BottomNavRoundedItem(
                        icon = Icons.Default.MoreHoriz,
                        label = "Ещё",
                        isSelected = bottomNavIndex == 4,
                        onClick = { bottomNavIndex = 4 }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (bottomNavIndex) {
                0 -> {
                    if (currentTab == 1) {
                        PlaylistsView(onMediaSelected = onVideoSelected)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Image(
                                        painter = painterResource(id = R.drawable.ic_nexora),
                                        contentDescription = "Nexora Logo",
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "NEXORA",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.5.sp
                                        )
                                        Text(
                                            "Медиаплеер",
                                            color = GoldenYellowLight,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { isSearchActive = !isSearchActive }) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Поиск",
                                            tint = GoldenYellowLight
                                        )
                                    }
                                    IconButton(onClick = onOpenCustomUrl) {
                                        Icon(
                                            imageVector = Icons.Default.Link,
                                            contentDescription = "Поток",
                                            tint = NeonOrange
                                        )
                                    }
                                }
                            }

                            if (isSearchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    placeholder = { Text("Поиск видео...", color = GrayText) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = NeonOrange,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TopTabPillButton(
                                    text = "Видео",
                                    icon = Icons.Default.Movie,
                                    isSelected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    modifier = Modifier.weight(1f)
                                )
                                TopTabPillButton(
                                    text = "Плейлисты",
                                    icon = Icons.Default.PlaylistPlay,
                                    isSelected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            SwipeRefresh(
                                state = rememberSwipeRefreshState(isRefreshing),
                                onRefresh = { loadVideos() }
                            ) {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentPadding = PaddingValues(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(filteredVideos, key = { it.id }) { video ->
                                        VideoCardItem(
                                            video = video,
                                            onClick = { onVideoSelected(video.uri.toString()) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    AudioBrowserView(onAudioSelected = onVideoSelected)
                }
                2 -> {
                    FileExplorerView(onMediaSelected = onVideoSelected)
                }
                3 -> {
                    PlaylistsView(onMediaSelected = onVideoSelected)
                }
                4 -> {
                    MoreHubView(
                        onOpenSettings = onOpenSettings,
                        onOpenCustomUrl = onOpenCustomUrl,
                        onOpenVideoLibrary = { bottomNavIndex = 0 },
                        onOpenProtectedVault = onOpenProtectedVault
                    )
                }
            }
        }
    }
}

@Composable
fun TopTabPillButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(24.dp),
                spotColor = GoldenYellowGlow,
                ambientColor = GoldenYellowGlow
            ),
        shape = RoundedCornerShape(24.dp),
        color = if (isSelected) NeonOrange else SurfaceCardColor,
        border = BorderStroke(
            1.dp,
            if (isSelected) GoldenYellowLight else Color.White.copy(alpha = 0.08f)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) Color.Black else GrayText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomNavRoundedItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) NeonOrange.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) GoldenYellowLight else GrayText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) GoldenYellowLight else GrayText,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun VideoCardItem(
    video: VideoItem,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    var bitmapThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.id) {
        if (video.thumbnailResId == null && video.id > 0) {
            bitmapThumbnail = MediaStoreHelper.loadThumbnail(context, video.id)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCardColor,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color(0xFF151515))
            ) {
                if (video.thumbnailResId != null) {
                    Image(
                        painter = painterResource(id = video.thumbnailResId),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (bitmapThumbnail != null) {
                    Image(
                        bitmap = bitmapThumbnail!!.asImageBitmap(),
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = NeonOrange.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = NeonOrange.copy(alpha = 0.85f)
                ) {
                    Text(
                        text = video.resolution,
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = video.displayName,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatSize(video.size),
                        color = GrayText,
                        fontSize = 11.sp
                    )
                    Text(
                        text = "MP4",
                        color = GoldenYellowLight,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
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

fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
    } else {
        String.format(Locale.getDefault(), "%.1f MB", mb)
    }
}
