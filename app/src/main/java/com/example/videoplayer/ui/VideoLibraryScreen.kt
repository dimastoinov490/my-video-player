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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

val DarkBackground = Color(0xFF08080C)
val NeonOrange = Color(0xFFFF7A00)
val NeonOrangeGradient = Brush.horizontalGradient(listOf(Color(0xFFFF9E00), Color(0xFFFF5500)))
val SurfaceCardColor = Color(0xFF14141A)
val GrayText = Color(0xFF9E9EAE)

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
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
            ) {
                // Header Top Bar (Height 52.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "App Logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                        )
                        Text(
                            text = "Nexora",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        IconButton(
                            onClick = { isSearchActive = !isSearchActive },
                            modifier = Modifier.size(22.dp)
                        ) {
                            SearchIconCustom(tint = NeonOrange)
                        }
                        IconButton(
                            onClick = { Toast.makeText(context, "История", Toast.LENGTH_SHORT).show() },
                            modifier = Modifier.size(22.dp)
                        ) {
                            HistoryIconCustom(tint = NeonOrange)
                        }
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(22.dp)
                            ) {
                                ThreeDotsIconCustom(tint = NeonOrange)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(SurfaceCardColor)
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

                // Neon orange divider line with soft glow under top bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.5.dp)
                        .background(NeonOrangeGradient)
                )

                if (isSearchActive) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск видео...", color = GrayText) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonOrange,
                                unfocusedBorderColor = Color.DarkGray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = NeonOrange
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Top Navigation Tabs ("Видео", "Плейлисты")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    NeonTabButton(
                        text = "Видео",
                        icon = { tint -> VideoCameraIconCustom(tint = tint, size = 18.dp) },
                        isSelected = currentTab == 0,
                        onClick = {
                            currentTab = 0
                            bottomNavIndex = 0
                        }
                    )
                    NeonTabButton(
                        text = "Плейлисты",
                        icon = { tint -> PlaylistTabIconCustom(tint = tint, size = 18.dp) },
                        isSelected = currentTab == 1,
                        onClick = {
                            currentTab = 1
                            bottomNavIndex = 3
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        },
        bottomBar = {
            // Bottom Navigation Bar (Height 72.dp, Custom Row/Box with neon glowing top & bottom lines)
            Surface(
                color = DarkBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(NeonOrangeGradient)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(70.dp)
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BottomNavItemCustom(
                            label = "Видео",
                            icon = { tint -> VideoNavIcon(tint) },
                            isSelected = bottomNavIndex == 0,
                            onClick = {
                                bottomNavIndex = 0
                                currentTab = 0
                            }
                        )
                        BottomNavItemCustom(
                            label = "Аудио",
                            icon = { tint -> AudioNavIcon(tint) },
                            isSelected = bottomNavIndex == 1,
                            onClick = { bottomNavIndex = 1 }
                        )
                        BottomNavItemCustom(
                            label = "Обзор",
                            icon = { tint -> BrowseNavIcon(tint) },
                            isSelected = bottomNavIndex == 2,
                            onClick = { bottomNavIndex = 2 }
                        )
                        BottomNavItemCustom(
                            label = "Плейлисты",
                            icon = { tint -> PlaylistNavIcon(tint) },
                            isSelected = bottomNavIndex == 3,
                            onClick = {
                                bottomNavIndex = 3
                                currentTab = 1
                            }
                        )
                        BottomNavItemCustom(
                            label = "Ещё",
                            icon = { tint -> MoreNavIcon(tint) },
                            isSelected = bottomNavIndex == 4,
                            onClick = {
                                bottomNavIndex = 4
                                onOpenSettings()
                            }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(NeonOrangeGradient)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
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
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                        ) {
                            Text("Разрешить доступ", color = Color.White)
                        }
                    }
                }
                bottomNavIndex == 1 || bottomNavIndex == 2 || (bottomNavIndex == 3 && currentTab == 1) -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Скоро",
                            color = NeonOrange,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                else -> {
                    if (filteredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Видео не найдены", color = GrayText, fontSize = 16.sp)
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

            // Floating Play Button (FAB) in bottom right above bottom bar
            if (lastPlayedUri != null && bottomNavIndex == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(NeonOrangeGradient)
                        .clickable { onVideoSelected(lastPlayedUri) },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(6f, 4f)
                            lineTo(20f, 12f)
                            lineTo(6f, 20f)
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Удалить файл", color = Color.White) },
            text = { Text("Удалить файл с телефона? Это нельзя отменить.", color = GrayText) },
            containerColor = SurfaceCardColor,
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = videoToDelete
                        videoToDelete = null
                        target?.let { performDelete(it) }
                    }
                ) {
                    Text("Удалить", color = NeonOrange)
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
fun NeonTabButton(
    text: String,
    icon: @Composable (Color) -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) SurfaceCardColor else Color.Transparent
    val contentColor = if (isSelected) NeonOrange else GrayText

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(
                border = BorderStroke(1.5.dp, if (isSelected) NeonOrange else Color.DarkGray.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon(contentColor)
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomNavItemCustom(
    label: String,
    icon: @Composable (Color) -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isSelected) NeonOrange else GrayText.copy(alpha = 0.7f)
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterVertically,
        verticalArrangement = Arrangement.Center
    ) {
        icon(tint)
        Spacer(modifier = Modifier.height(3.dp))
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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(BorderStroke(1.dp, NeonOrangeGradient), RoundedCornerShape(14.dp))
            .background(SurfaceCardColor)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(Color.Black)
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = video.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                // Light black overlay on preview
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.15f))
                )

                // SD / HD badge in top-left (dark oval block)
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                ) {
                    Text(
                        text = if (isHD) "HD" else "SD",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Duration in bottom-right
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Progress bar if watched
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Gray.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(NeonOrange)
                        )
                    }
                }
            }

            // Info under preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
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
                        color = GrayText,
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
                        ThreeDotsIconCustom(tint = GrayText)
                    }
                    DropdownMenu(
                        expanded = isMenuExpanded,
                        onDismissRequest = { onMenuExpandedChange(false) },
                        modifier = Modifier.background(SurfaceCardColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Воспроизвести", color = Color.White) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Удалить", color = NeonOrange) },
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

// Custom Custom Canvas Icons
@Composable
fun VideoCameraIconCustom(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        // Draw video camera shape
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(2f, 5f), size = androidx.compose.ui.geometry.Size(w * 0.6f, h * 0.6f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.62f, h * 0.3f)
            lineTo(w - 2f, h * 0.15f)
            lineTo(w - 2f, h * 0.85f)
            lineTo(w * 0.62f, h * 0.7f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun PlaylistTabIconCustom(tint: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, h * 0.25f), end = androidx.compose.ui.geometry.Offset(w - 2f, h * 0.25f), strokeWidth = 1.5f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, h * 0.5f), end = androidx.compose.ui.geometry.Offset(w - 2f, h * 0.5f), strokeWidth = 1.5f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(2f, h * 0.75f), end = androidx.compose.ui.geometry.Offset(w - 2f, h * 0.75f), strokeWidth = 1.5f)
    }
}

@Composable
fun VideoNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(6f, 4f)
            lineTo(20f, 12f)
            lineTo(6f, 20f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun AudioNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        drawCircle(color = tint, radius = 3.5f, center = androidx.compose.ui.geometry.Offset(8f, 18f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(10.5f, 6f), size = androidx.compose.ui.geometry.Size(2f, 12f))
        drawRect(color = tint, topLeft = androidx.compose.ui.geometry.Offset(10.5f, 6f), size = androidx.compose.ui.geometry.Size(7.5f, 3f))
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
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCircle(color = tint, radius = 6f, center = androidx.compose.ui.geometry.Offset(9f, 9f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(13.5f, 13.5f), end = androidx.compose.ui.geometry.Offset(19.5f, 19.5f), strokeWidth = 2f)
    }
}

@Composable
fun HistoryIconCustom(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCircle(color = tint, radius = 8f, center = androidx.compose.ui.geometry.Offset(11f, 11f), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(11f, 6f), end = androidx.compose.ui.geometry.Offset(11f, 11f), strokeWidth = 2f)
        drawLine(color = tint, start = androidx.compose.ui.geometry.Offset(11f, 11f), end = androidx.compose.ui.geometry.Offset(15f, 13f), strokeWidth = 2f)
    }
}

@Composable
fun ThreeDotsIconCustom(tint: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(11f, 5f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(11f, 11f))
        drawCircle(color = tint, radius = 2f, center = androidx.compose.ui.geometry.Offset(11f, 17f))
    }
}
