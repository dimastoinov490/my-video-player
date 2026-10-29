package com.example.videoplayer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.ui.terminal.TerminalConsoleOverlay
import com.example.videoplayer.ui.theme.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
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
    onOpenProtectedVault: () -> Unit,
    refreshKey: Any? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalAppTheme.current

    var currentTab by remember { mutableIntStateOf(0) }
    var bottomNavIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showTerminalConsole by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var scanStatusText by remember { mutableStateOf("") }
    var showScanBanner by remember { mutableStateOf(false) }
    var hasStoragePermission by remember {
        mutableStateOf(checkMediaPermissions(context))
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.values.any { it }
        hasStoragePermission = granted
        if (granted) {
            loadVideosInternal(context, coroutineScope, onVideosLoaded = { videos = it }, onStatus = { scanStatusText = it }, onRefreshing = { isRefreshing = it }, onBanner = { showScanBanner = it })
        }
    }

    fun loadVideos() {
        if (!hasStoragePermission) {
            requestMediaPermissions(permissionLauncher)
        }
        loadVideosInternal(context, coroutineScope, onVideosLoaded = { videos = it }, onStatus = { scanStatusText = it }, onRefreshing = { isRefreshing = it }, onBanner = { showScanBanner = it })
    }

    LaunchedEffect(refreshKey) {
        if (!hasStoragePermission) {
            requestMediaPermissions(permissionLauncher)
        }
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
        bottomBar = {
            if (bottomNavIndex != 0) {
                ApprovedExactBottomBar(
                    selectedIndex = bottomNavIndex,
                    onTabSelected = { newIndex ->
                        bottomNavIndex = newIndex
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (bottomNavIndex != 0) Modifier.padding(innerPadding) else Modifier)
        ) {
            when (bottomNavIndex) {
                0 -> {
                    ExactHtmlVideoLibraryView(
                        videos = videos,
                        onVideoSelected = onVideoSelected,
                        onNavTab = { tabName, _ ->
                            when (tabName) {
                                "video" -> bottomNavIndex = 0
                                "audio" -> bottomNavIndex = 1
                                "explorer" -> bottomNavIndex = 2
                                "playlists" -> bottomNavIndex = 3
                                "more" -> bottomNavIndex = 4
                            }
                        },
                        onOpenSearch = {
                            isSearchActive = true
                        },
                        onOpenHistory = {
                            val target = videos.firstOrNull()?.uri?.toString() ?: "sample://western_full"
                            onVideoSelected(target)
                        },
                        onOpenMenu = onOpenSettings,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                999 -> {
                    if (currentTab == 1) {
                        PlaylistsView(onMediaSelected = onVideoSelected)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top bar (По скриншоту: динамический логотип Nexora, поиск, история, меню)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 18.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Динамический значок Nexora (без конуса VLC)
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.Black.copy(alpha = 0.7f))
                                            .border(
                                                1.8.dp,
                                                Brush.linearGradient(listOf(theme.primary, theme.secondary)),
                                                RoundedCornerShape(12.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Nexora Logo",
                                            tint = theme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "NEXORA",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 2.sp
                                        )
                                        Text(
                                            "MEDIA PLAYER",
                                            color = Color.LightGray,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 2.sp
                                        )
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Кнопка поиска в круглом элементе со скриншота
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable { isSearchActive = !isSearchActive },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Поиск",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Кнопка истории в круглом элементе со скриншота
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                if (videos.isNotEmpty()) {
                                                    onVideoSelected(videos.first().uri.toString())
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = "История",
                                            tint = Color.White,
                                            modifier = Modifier.size(19.dp)
                                        )
                                    }

                                    // Меню из 3 точек с прямым доступом к Терминалу UI
                                    Box {
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(CircleShape)
                                                .background(Color.White.copy(alpha = 0.08f))
                                                .clickable { showTopMenu = true },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MoreVert,
                                                contentDescription = "Опции",
                                                tint = Color.White,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showTopMenu,
                                            onDismissRequest = { showTopMenu = false },
                                            modifier = Modifier.background(theme.surface)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text(">_ Открыть консоль терминала UI", color = theme.secondary, fontWeight = FontWeight.Bold) },
                                                leadingIcon = { Icon(Icons.Default.Terminal, contentDescription = null, tint = theme.secondary) },
                                                onClick = {
                                                    showTopMenu = false
                                                    showTerminalConsole = true
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Настройки оформления", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, tint = theme.primary) },
                                                onClick = {
                                                    showTopMenu = false
                                                    onOpenSettings()
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("Воспроизвести поток (URL)", color = Color.White) },
                                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = Color.LightGray) },
                                                onClick = {
                                                    showTopMenu = false
                                                    onOpenCustomUrl()
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Неоновый плавный контур-свуш со скриншота под шапкой
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                            ) {
                                val w = size.width
                                val h = size.height
                                val swoosh = Path().apply {
                                    moveTo(w * 0.52f, 0f)
                                    cubicTo(w * 0.60f, h * 0.8f, w * 0.75f, h, w, h)
                                }
                                drawPath(
                                    path = swoosh,
                                    color = theme.primary.copy(alpha = 0.85f),
                                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                                )
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            theme.primary.copy(alpha = 0.25f),
                                            theme.primary.copy(alpha = 0.7f),
                                            Color.Transparent
                                        )
                                    ),
                                    start = Offset(0f, h - 1.dp.toPx()),
                                    end = Offset(w, h - 1.dp.toPx()),
                                    strokeWidth = 1.dp.toPx()
                                )
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
                                        focusedBorderColor = theme.primary,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )
                            }

                            // Top tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                TopTabPillButton(
                                    text = "Видео (${filteredVideos.size})",
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

                            // Permission warning banner if permission is missing
                            if (!hasStoragePermission) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFF2A1C0A),
                                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Доступ к памяти ограничен", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            Text("Разрешите чтение медиафайлов для поиска видео на устройстве.", color = Color.LightGray, fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = { requestMediaPermissions(permissionLauncher) },
                                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Разрешить", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Video Grid with SwipeRefresh (positioned down so it doesn't overlap header tabs)
                            SwipeRefresh(
                                state = rememberSwipeRefreshState(isRefreshing),
                                onRefresh = { loadVideos() },
                                indicatorPadding = PaddingValues(top = 90.dp),
                                indicator = { state, trigger ->
                                    SwipeRefreshIndicator(
                                        state = state,
                                        refreshTriggerDistance = trigger,
                                        contentColor = theme.primary,
                                        backgroundColor = theme.surface,
                                        elevation = 8.dp
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                if (filteredVideos.isEmpty() && !isRefreshing) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(rememberScrollState()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.padding(32.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .background(theme.primary.copy(alpha = 0.12f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Movie,
                                                    contentDescription = null,
                                                    tint = theme.primary,
                                                    modifier = Modifier.size(36.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(16.dp))
                                            Text(
                                                text = if (searchQuery.isNotEmpty()) "Ничего не найдено" else "Видео не найдены",
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = if (searchQuery.isNotEmpty())
                                                    "По запросу \"$searchQuery\" ничего не найдено."
                                                else
                                                    "В памяти устройства пока нет видеофайлов. Загрузите видео или потяните вниз для обновления.",
                                                color = GrayText,
                                                fontSize = 13.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Button(
                                                onClick = { loadVideos() },
                                                colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Search,
                                                    contentDescription = null,
                                                    tint = Color.Black,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Сканировать память",
                                                    color = Color.Black,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    LazyVerticalGrid(
                                        columns = GridCells.Fixed(2),
                                        modifier = Modifier.fillMaxSize(),
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

                            // VLC-Style Animated Bottom Memory Scanning Panel
                            AnimatedVisibility(
                                visible = showScanBanner,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                            ) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF181B22),
                                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f)),
                                    shadowElevation = 8.dp
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            CircularProgressIndicator(
                                                color = theme.primary,
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Сканирование медиафайлов...",
                                                    color = Color.White,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    scanStatusText,
                                                    color = theme.secondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Text(
                                                "VLC Scan",
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        LinearProgressIndicator(
                                            color = theme.primary,
                                            trackColor = Color.White.copy(alpha = 0.1f),
                                            modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
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

            // FAB встроен напрямую в ExactHtmlVideoLibraryView
        }
    }

    // Внутренняя консоль терминала
    TerminalConsoleOverlay(
        isVisible = showTerminalConsole,
        onClose = { showTerminalConsole = false }
    )
}

private fun loadVideosInternal(
    context: Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    onVideosLoaded: (List<VideoItem>) -> Unit,
    onStatus: (String) -> Unit,
    onRefreshing: (Boolean) -> Unit,
    onBanner: (Boolean) -> Unit
) {
    onRefreshing(true)
    onBanner(true)
    onStatus("Инициализация поиска видеофайлов...")

    coroutineScope.launch {
        try {
            val list = withContext(Dispatchers.IO) {
                MediaStoreHelper.getVideos(context) { path ->
                    onStatus(path)
                }
            }
            onVideosLoaded(list)
            onStatus("Найдено: ${list.size} видеофайлов")
            delay(1500)
        } catch (e: Exception) {
            Log.e("VideoLibraryScreen", "Failed to load videos", e)
            onVideosLoaded(emptyList())
            onStatus("Ошибка сканирования медиа")
        } finally {
            onRefreshing(false)
            onBanner(false)
        }
    }
}

private fun checkMediaPermissions(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }
}

private fun requestMediaPermissions(launcher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO
            )
        )
    } else {
        launcher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE
            )
        )
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
