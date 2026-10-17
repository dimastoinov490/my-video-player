package com.example.videoplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.videoplayer.R
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onVideoSelected: (String) -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // 0: Video, 1: Playlists
    var bottomNavIndex by remember { mutableStateOf(0) } // 0: Video, 1: Audio, 2: Browse, 3: Playlists, 4: More
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoCardMenuExpandedId by remember { mutableStateOf<Long?>(null) }

    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }
    val lastPlayedUri = prefs.getString("last_played_uri", null)

    var hasPermission by remember {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        mutableStateOf(ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
    }

    val pendingDeleteVideo = remember { mutableStateOf<VideoItem?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingDeleteVideo.value?.let { video ->
                scope.launch {
                    try {
                        context.contentResolver.delete(video.uri, null, null)
                        videos = MediaStoreHelper.getVideos(context)
                        Toast.makeText(context, "Файл удален", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    pendingDeleteVideo.value = null
                }
            }
        } else {
            pendingDeleteVideo.value = null
        }
    }

    fun performDelete(video: VideoItem) {
        pendingDeleteVideo.value = video
        try {
            val rows = context.contentResolver.delete(video.uri, null, null)
            if (rows > 0) {
                scope.launch {
                    videos = MediaStoreHelper.getVideos(context)
                }
                Toast.makeText(context, "Файл удален", Toast.LENGTH_SHORT).show()
                pendingDeleteVideo.value = null
            }
        } catch (e: android.app.RecoverableSecurityException) {
            try {
                val intentSender = e.userAction.actionIntent.intentSender
                deleteLauncher.launch(
                    androidx.activity.result.IntentSenderRequest.Builder(intentSender).build()
                )
            } catch (ex: Exception) {
                ex.printStackTrace()
                pendingDeleteVideo.value = null
            }
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                try {
                    val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, listOf(video.uri))
                    deleteLauncher.launch(
                        androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                    )
                } catch (ex: Exception) {
                    ex.printStackTrace()
                    pendingDeleteVideo.value = null
                }
            } else {
                e.printStackTrace()
                pendingDeleteVideo.value = null
            }
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            videos = MediaStoreHelper.getVideos(context)
        } else {
            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_VIDEO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            permissionLauncher.launch(perm)
        }
    }

    val filteredVideos = videos.filter { it.title.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        containerColor = Color(0xFF0B0B0F),
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF0B0B0F))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Text(
                            text = "Nexora",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { isSearchActive = !isSearchActive }) {
                            SearchIconCustom(tint = Color.White)
                        }
                        IconButton(onClick = {
                            Toast.makeText(context, "Недавние видео", Toast.LENGTH_SHORT).show()
                        }) {
                            RecentsIconCustom(tint = Color.White)
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                ThreeDotsIconCustom(tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF1E1E1E))
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Открыть по ссылке", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        onOpenCustomUrl()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Настройки", color = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        onOpenSettings()
                                    }
                                )
                            }
                        }
                    }
                }

                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск видео...", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF8A00),
                            unfocusedBorderColor = Color.DarkGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Tabs: "Видео" and "Плейлисты"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabButton(
                        text = "Видео",
                        isSelected = currentTab == 0,
                        onClick = {
                            currentTab = 0
                            bottomNavIndex = 0
                        }
                    )
                    TabButton(
                        text = "Плейлисты",
                        isSelected = currentTab == 1,
                        onClick = {
                            currentTab = 1
                            bottomNavIndex = 3
                        }
                    )
                }
            }
        },
        bottomBar = {
            // Bottom navigation with 5 items
            Surface(
                color = Color(0xFF0B0B0F),
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BottomNavItem(
                        label = "Видео",
                        icon = { tint -> VideoNavIcon(tint) },
                        isSelected = bottomNavIndex == 0,
                        onClick = {
                            bottomNavIndex = 0
                            currentTab = 0
                        }
                    )
                    BottomNavItem(
                        label = "Аудио",
                        icon = { tint -> AudioNavIcon(tint) },
                        isSelected = bottomNavIndex == 1,
                        onClick = { bottomNavIndex = 1 }
                    )
                    BottomNavItem(
                        label = "Обзор",
                        icon = { tint -> BrowseNavIcon(tint) },
                        isSelected = bottomNavIndex == 2,
                        onClick = { bottomNavIndex = 2 }
                    )
                    BottomNavItem(
                        label = "Плейлисты",
                        icon = { tint -> PlaylistNavIcon(tint) },
                        isSelected = bottomNavIndex == 3,
                        onClick = {
                            bottomNavIndex = 3
                            currentTab = 1
                        }
                    )
                    BottomNavItem(
                        label = "Ещё",
                        icon = { tint -> MoreNavIcon(tint) },
                        isSelected = bottomNavIndex == 4,
                        onClick = {
                            bottomNavIndex = 4
                            onOpenSettings()
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0B0B0F))
        ) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Необходим доступ к хранилищу", color = Color.White, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    Manifest.permission.READ_MEDIA_VIDEO
                                } else {
                                    Manifest.permission.READ_EXTERNAL_STORAGE
                                }
                                permissionLauncher.launch(perm)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A00))
                        ) {
                            Text("Разрешить доступ", color = Color.White)
                        }
                    }
                }
                bottomNavIndex == 1 || bottomNavIndex == 2 || (bottomNavIndex == 3 && currentTab == 1) -> {
                    // Stub screen "Скоро"
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Скоро",
                            color = Color(0xFFFF8A00),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    // Video Library Grid
                    if (filteredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Видео не найдены", color = Color.Gray, fontSize = 16.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(filteredVideos, key = { it.id }) { video ->
                                VideoCardItem(
                                    video = video,
                                    prefs = prefs,
                                    onClick = { onVideoSelected(video.uri.toString()) },
                                    onDelete = { videoToDelete = video },
                                    isMenuExpanded = videoCardMenuExpandedId == video.id,
                                    onMenuExpandedChange = { expanded ->
                                        videoCardMenuExpandedId = if (expanded) video.id else null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Floating Play button for last played video above bottom panel
            if (lastPlayedUri != null && bottomNavIndex == 0) {
                FloatingActionButton(
                    onClick = { onVideoSelected(lastPlayedUri) },
                    containerColor = Color(0xFFFF8A00),
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Resume Last Video")
                }
            }
        }
    }

    // Delete confirmation dialog
    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Удалить файл", color = Color.White) },
            text = { Text("Удалить файл с телефона? Это нельзя отменить.", color = Color.LightGray) },
            containerColor = Color(0xFF1E1E1E),
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = videoToDelete
                        videoToDelete = null
                        target?.let { performDelete(it) }
                    }
                ) {
                    Text("Удалить", color = Color(0xFFFF8A00))
                }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) {
                    Text("Отмена", color = Color.White)
                }
            }
        )
    }
}

@Composable
fun TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) Color(0xFF1E1E1E) else Color.Transparent)
            .border(
                border = BorderStroke(1.dp, if (isSelected) Color(0xFFFF8A00) else Color.DarkGray),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = if (isSelected) Color(0xFFFF8A00) else Color.LightGray,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun BottomNavItem(label: String, icon: @Composable (Color) -> Unit, isSelected: Boolean, onClick: () -> Unit) {
    val tint = if (isSelected) Color(0xFFFF8A00) else Color.Gray
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        icon(tint)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun VideoCardItem(
    video: VideoItem,
    prefs: android.content.SharedPreferences,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isMenuExpanded: Boolean,
    onMenuExpandedChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.id) {
        bitmap = MediaStoreHelper.loadThumbnail(context, video.id)
    }

    val savedPosition = prefs.getLong("pos_${video.id}", 0L)
    val progressFraction = if (video.duration > 0) (savedPosition.toFloat() / video.duration.toFloat()).coerceIn(0f, 1f) else 0f

    val isHD = video.height >= 720

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, Color(0xFFFF8A00)), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .background(Color.DarkGray)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = video.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // SD / HD badge
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = if (isHD) "HD" else "SD",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Duration at bottom right of thumbnail
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                // Progress bar if watched
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Gray)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(Color(0xFFFF8A00))
                        )
                    }
                }
            }

            // Info under thumbnail
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = video.displayName.ifEmpty { video.title },
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatSize(video.size)} • ${formatDate(video.dateAdded)}",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { onMenuExpandedChange(true) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        ThreeDotsIconCustom(tint = Color.LightGray)
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        modifier = Modifier.background(Color(0xFF1E1E1E))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Воспроизвести", color = Color.White) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = Color(0xFFFF8A00)) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val secs = totalSecs % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

fun formatSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "0 MB"
    val mb = sizeBytes / (1024.0 * 1024.0)
    return if (mb >= 1024) {
        String.format("%.1f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}

fun formatDate(dateAddedSeconds: Long): String {
    if (dateAddedSeconds <= 0) return ""
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    return sdf.format(Date(dateAddedSeconds * 1000L))
}

// Custom Icon Composables using Canvas (No external extensions required)
@Composable
fun VideoNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(5f, 4f)
            lineTo(19f, 12f)
            lineTo(5f, 20f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun AudioNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 4f, center = androidx.compose.ui.geometry.Offset(8f, 18f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(10f, 6f), size = androidx.compose.ui.geometry.Size(2f, 12f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(10f, 6f), size = androidx.compose.ui.geometry.Size(8f, 3f))
    }
}

@Composable
fun BrowseNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(2f, 5f), size = androidx.compose.ui.geometry.Size(20f, 14f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(2f, 3f), size = androidx.compose.ui.geometry.Size(8f, 3f))
    }
}

@Composable
fun PlaylistNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(6f, 6f), end = androidx.compose.ui.geometry.Offset(20f, 6f), strokeWidth = 2f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(6f, 12f), end = androidx.compose.ui.geometry.Offset(20f, 12f), strokeWidth = 2f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(6f, 18f), end = androidx.compose.ui.geometry.Offset(20f, 18f), strokeWidth = 2f)
        drawCircle(color = tint, radius = 1.5f, center = androidx.compose.ui.geometry.Offset(3f, 6f))
        drawCircle(color = tint, radius = 1.5f, center = androidx.compose.ui.geometry.Offset(3f, 12f))
        drawCircle(color = tint, radius = 1.5f, center = androidx.compose.ui.geometry.Offset(3f, 18f))
    }
}

@Composable
fun MoreNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 5f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 12f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 19f))
    }
}

@Composable
fun SearchIconCustom(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 6f, center = androidx.compose.ui.geometry.Offset(10f, 10f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(14f, 14f), end = androidx.compose.ui.geometry.Offset(20f, 20f), strokeWidth = 2f)
    }
}

@Composable
fun RecentsIconCustom(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 9f, center = androidx.compose.ui.geometry.Offset(12f, 12f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(12f, 6f), end = androidx.compose.ui.geometry.Offset(12f, 12f), strokeWidth = 2f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(12f, 12f), end = androidx.compose.ui.geometry.Offset(16f, 14f), strokeWidth = 2f)
    }
}

@Composable
fun ThreeDotsIconCustom(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 5f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 12f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(12f, 19f))
    }
}
