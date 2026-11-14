package com.example.videoplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.videoplayer.R
import com.example.videoplayer.archive.ArchiveSource
import com.example.videoplayer.model.MediaStoreHelper
import java.io.File
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.ui.theme.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onVideoSelected: (String) -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchive: (ArchiveSource, File?) -> Unit = { _, _ -> },
    onOpenArchivePicker: () -> Unit = {},
    onOpenBrowser: (String) -> Unit = {},
    onOpenTerminal: () -> Unit = {},
    browseRefreshTick: Int = 0
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalAppTheme.current

    var currentTab by remember { mutableIntStateOf(0) }
    var bottomNavIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var scanStatusText by remember { mutableStateOf("") }
    var showScanBanner by remember { mutableStateOf(false) }
    var hasStoragePermission by remember {
        mutableStateOf(checkMediaPermissions(context))
    }
    var hasAudioPermission by remember {
        mutableStateOf(checkAudioPermissions(context))
    }
    var videoLoadJob by remember { mutableStateOf<Job?>(null) }

    fun startVideoLoad() {
        videoLoadJob?.cancel()
        videoLoadJob = loadVideosInternal(
            context,
            coroutineScope,
            onVideosLoaded = { videos = it },
            onStatus = { scanStatusText = it },
            onRefreshing = { isRefreshing = it },
            onBanner = { showScanBanner = it }
        )
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        // The video library only requires video access. Image/audio grants must not
        // be treated as permission to query MediaStore.Video.
        val granted = checkMediaPermissions(context)
        hasStoragePermission = granted
        if (granted) {
            startVideoLoad()
        } else {
            scanStatusText = "Для просмотра видео необходимо разрешение на доступ к видеофайлам"
            showScanBanner = false
            isRefreshing = false
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted || checkAudioPermissions(context)
    }

    fun requestAudioAccessIfNeeded() {
        if (checkAudioPermissions(context)) {
            hasAudioPermission = true
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            hasAudioPermission = checkAudioPermissions(context)
        }
    }

    fun loadVideos() {
        if (!checkMediaPermissions(context)) {
            hasStoragePermission = false
            requestMediaPermissions(permissionLauncher)
            return
        }
        hasStoragePermission = true
        startVideoLoad()
    }

    LaunchedEffect(Unit) {
        // Request once; calling loadVideos() here also handles the granted case.
        loadVideos()
    }

    val filteredVideos = remember(videos, searchQuery) {
        if (searchQuery.isBlank()) {
            videos
        } else {
            videos.filter { it.displayName.contains(searchQuery, ignoreCase = true) }
        }
    }

    if (bottomNavIndex != 0) {
        BackHandler {
            bottomNavIndex = 0
        }
    }

    Scaffold(
        containerColor = Color(0xFF020306),
        bottomBar = {}
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ExactHtmlVideoLibraryView(
                videos = videos,
                onVideoSelected = onVideoSelected,
                onNavTab = { tabName, idx ->
                    bottomNavIndex = idx
                    if (tabName.equals("audio", ignoreCase = true) || idx == 1) {
                        requestAudioAccessIfNeeded()
                    }
                },
                audioPermissionGranted = hasAudioPermission,
                onOpenSearch = {
                    isSearchActive = true
                },
                onOpenHistory = {
                    val target = videos.firstOrNull()?.uri?.toString() ?: "sample://western_full"
                    onVideoSelected(target)
                },
                onOpenMenu = onOpenSettings,
                onOpenSettings = onOpenSettings,
                onOpenCustomUrl = onOpenCustomUrl,
                onOpenArchive = onOpenArchive,
                onOpenArchivePicker = onOpenArchivePicker,
                onOpenBrowser = onOpenBrowser,
                onOpenTerminal = onOpenTerminal,
                browseRefreshTick = browseRefreshTick,
                onRefreshVideos = {
                    Toast.makeText(context, "Обновление списка видео…", Toast.LENGTH_SHORT).show()
                    videoLoadJob?.cancel()
                    coroutineScope.launch {
                        if (!checkMediaPermissions(context)) {
                            loadVideos()
                            return@launch
                        }
                        try {
                            withContext(Dispatchers.IO) {
                                MediaStoreHelper.requestMediaScan(context)
                            }
                            startVideoLoad()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e("VideoLibraryScreen", "Manual media refresh failed", e)
                            scanStatusText = "Не удалось обновить список видео"
                        }
                    }
                },
                currentNavIndex = bottomNavIndex,
                modifier = Modifier.fillMaxSize()
            )
            if (bottomNavIndex == 999) {
                // Fallback stub
            }
        }
    }
}

private fun loadVideosInternal(
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onVideosLoaded: (List<VideoItem>) -> Unit,
    onStatus: (String) -> Unit,
    onRefreshing: (Boolean) -> Unit,
    onBanner: (Boolean) -> Unit
): Job = coroutineScope.launch {
    val loadJob = currentCoroutineContext()[Job]
    val mainHandler = Handler(Looper.getMainLooper())
    onRefreshing(true)
    onBanner(true)
    onStatus("Инициализация поиска видеофайлов...")

    try {
        val list = withContext(Dispatchers.IO) {
            MediaStoreHelper.getVideos(context) { path ->
                // MediaStore scanning runs on IO; never mutate Compose state from that thread.
                if (loadJob?.isActive == true) {
                    mainHandler.post {
                        if (loadJob.isActive) onStatus(path)
                    }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        onVideosLoaded(list)
        onStatus("Найдено: ${list.size} видеофайлов")
        delay(1500)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("VideoLibraryScreen", "Failed to load videos", e)
        onVideosLoaded(emptyList())
        onStatus("Ошибка сканирования медиа")
    } finally {
        // A cancelled older request must not hide the progress of a newer request.
        if (currentCoroutineContext().isActive) {
            onRefreshing(false)
            onBanner(false)
        }
    }
}

private fun checkMediaPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_MEDIA_VIDEO
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

private fun checkAudioPermissions(context: Context): Boolean {
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun requestMediaPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Do not request unrelated audio/image access from the video library.
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    launcher.launch(permissions)
}

@Composable
fun TopTabPillButton(
    text: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalAppTheme.current
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(44.dp)
            .shadow(
                elevation = if (isSelected) 8.dp else 0.dp,
                shape = RoundedCornerShape(22.dp),
                spotColor = theme.glow,
                ambientColor = theme.glow
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (isSelected) theme.primary.copy(alpha = 0.22f) else theme.surface,
        border = BorderStroke(
            if (isSelected) 1.5.dp else 1.dp,
            if (isSelected) theme.primary else Color.White.copy(alpha = 0.08f)
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
                tint = if (isSelected) theme.primary else GrayText,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = if (isSelected) theme.primary else Color.White,
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
    val theme = LocalAppTheme.current
    Surface(
        onClick = onClick,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) theme.primary.copy(alpha = 0.18f) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) theme.primary else GrayText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = if (isSelected) theme.primary else GrayText,
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
    val theme = LocalAppTheme.current
    var bitmapThumbnail by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(video.id) {
        if (video.thumbnailResId == null && video.id > 0) {
            bitmapThumbnail = withContext(Dispatchers.IO) {
                MediaStoreHelper.loadThumbnail(context, video.id)
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(theme.cornerRadiusDp.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(theme.cornerRadiusDp.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, theme.cardBorder)
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
                            tint = theme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Quality badge top-left (SD / HD) со скриншота
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Text(
                        text = video.resolution,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // 3-dots pill top-right со скриншота
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                // Duration badge bottom-right
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.8f)
                ) {
                    Text(
                        text = if (video.duration > 0) formatDuration(video.duration) else "0:00",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${if (video.size > 0) formatSize(video.size) else "12.3 MB"} • 2026",
                        color = Color(0xFFAAAAAA),
                        fontSize = 11.sp
                    )
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
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
