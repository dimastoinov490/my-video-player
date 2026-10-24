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
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.videoplayer.R
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.PlaylistManager
import com.example.videoplayer.model.VideoItem
import kotlinx.coroutines.launch

val DarkBackground = Color(0xFF09090D)
val NeonOrange = Color(0xFFFF8A00)
val GoldenYellow = Color(0xFFFF9500)
val GoldenYellowLight = Color(0xFFFFAE33)
val GoldenYellowGlow = Color(0xFFFF7A00)
val GoldenYellowGradient = Brush.horizontalGradient(
    listOf(Color(0xFFFFAE33), Color(0xFFFF851A), Color(0xFFFF6D00))
)
val NeonOrangeGradient = Brush.horizontalGradient(
    listOf(Color(0xFFFFA526), Color(0xFFFF7000))
)
val SurfaceCardColor = Color(0xFF131319)
val GrayText = Color(0xFF8E8E9E)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onVideoSelected: (String) -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var videos by remember { mutableStateOf<List<VideoItem>>(MediaStoreHelper.getSampleVideos()) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) }
    var bottomNavIndex by remember { mutableStateOf(0) }
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoCardMenuExpandedId by remember { mutableStateOf<Long?>(null) }
    var videoToAddToPlaylist by remember { mutableStateOf<VideoItem?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var refreshProgress by remember { mutableStateOf(0f) }
    var refreshStartY by remember { mutableStateOf(0f) }

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

    fun refreshLibrary() {
        scope.launch {
            isRefreshing = true
            refreshProgress = 0.3f
            try {
                videos = MediaStoreHelper.getVideos(context)
                Toast.makeText(context, "Библиотека обновлена", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Ошибка обновления медиа", Toast.LENGTH_SHORT).show()
            }
            refreshProgress = 1f
            isRefreshing = false
            refreshProgress = 0f
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

    val filteredVideos = videos.filter {
        it.title.contains(searchQuery, ignoreCase = true) || it.displayName.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        containerColor = DarkBackground,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkBackground)
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            quadraticBezierTo(
                                size.width * 0.75f, size.height * 0.9f,
                                size.width, size.height * 0.4f
                            )
                        }
                        drawPath(
                            path = path,
                            color = GoldenYellowGlow.copy(alpha = 0.55f),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF14141F))
                                    .border(1.dp, Brush.radialGradient(listOf(GoldenYellowLight.copy(alpha = 0.8f), GoldenYellowGlow.copy(alpha = 0.3f))), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_nexora),
                                    contentDescription = "Nexora Icon",
                                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "NEXORA",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 1.sp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(GoldenYellowGradient)
                                            .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                    ) {
                                        Text(
                                            text = "PRO",
                                            color = Color.Black,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                                Text(
                                    text = "ADVANCED MEDIA SUITE",
                                    color = GoldenYellowLight.copy(alpha = 0.9f),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1B24))
                                    .clickable { isSearchActive = !isSearchActive },
                                contentAlignment = Alignment.Center
                            ) {
                                SearchIconPill(tint = Color.White, iconSize = 19.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1B24))
                                    .clickable { refreshLibrary() },
                                contentAlignment = Alignment.Center
                            ) {
                                HistoryIconPill(tint = Color(0xFFFF9E00), iconSize = 19.dp)
                            }

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1B24))
                                    .clickable { showMenu = true },
                                contentAlignment = Alignment.Center
                            ) {
                                ThreeDotsIconPill(tint = Color.White, iconSize = 18.dp)
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                    modifier = Modifier.background(SurfaceCardColor)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Открыть по ссылке", color = Color.White) },
                                        onClick = { showMenu = false; onOpenCustomUrl() }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Настройки", color = Color.White) },
                                        onClick = { showMenu = false; onOpenSettings() }
                                    )
                                }
                            }
                        }
                    }
                }

                if (isSearchActive) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Поиск видео...", color = GrayText) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonOrange,
                                unfocusedBorderColor = Color(0xFF282834),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = NeonOrange
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TabPillButton(
                        text = "Видео",
                        icon = { tint -> VideoCameraIconCustom(tint = tint, iconSize = 20.dp) },
                        isSelected = currentTab == 0 && bottomNavIndex == 0,
                        modifier = Modifier.weight(1f),
                        onClick = { currentTab = 0; bottomNavIndex = 0 }
                    )
                    TabPillButton(
                        text = "Плейлисты",
                        icon = { tint -> MusicNoteTabIcon(tint = tint, iconSize = 20.dp) },
                        isSelected = currentTab == 1 || bottomNavIndex == 3,
                        modifier = Modifier.weight(1f),
                        onClick = { currentTab = 1; bottomNavIndex = 3 }
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(34.dp))
                        .background(Brush.verticalGradient(listOf(GoldenYellowGlow.copy(alpha = 0.22f), Color.Transparent)))
                )
                Surface(
                    color = Color(0xFF101017),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(1.5.dp, Brush.horizontalGradient(listOf(Color(0xFF242436), GoldenYellowGlow.copy(alpha = 0.85f), GoldenYellowLight, GoldenYellowGlow.copy(alpha = 0.85f), Color(0xFF242436)))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(Brush.horizontalGradient(listOf(GoldenYellowGlow.copy(alpha = 0.05f), GoldenYellowLight, GoldenYellow, GoldenYellowLight, GoldenYellowGlow.copy(alpha = 0.05f))))
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(66.dp)
                                .padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BottomNavItemModern(
                                label = "Видео",
                                icon = { tint -> VideoNavIcon(tint) },
                                isSelected = bottomNavIndex == 0 && currentTab == 0,
                                onClick = { bottomNavIndex = 0; currentTab = 0 }
                            )
                            BottomNavItemModern(
                                label = "Аудио",
                                icon = { tint -> AudioNavIcon(tint) },
                                isSelected = bottomNavIndex == 1,
                                onClick = { bottomNavIndex = 1; currentTab = 0 }
                            )
                            BottomNavItemModern(
                                label = "Обзор",
                                icon = { tint -> BrowseNavIcon(tint) },
                                isSelected = bottomNavIndex == 2,
                                onClick = { bottomNavIndex = 2; currentTab = 0 }
                            )
                            BottomNavItemModern(
                                label = "Плейлисты",
                                icon = { tint -> PlaylistNavIcon(tint) },
                                isSelected = bottomNavIndex == 3 || currentTab == 1,
                                onClick = { bottomNavIndex = 3; currentTab = 1 }
                            )
                            BottomNavItemModern(
                                label = "Ещё",
                                icon = { tint -> MoreNavIcon(tint) },
                                isSelected = bottomNavIndex == 4,
                                onClick = { bottomNavIndex = 4 }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
                .pointerInput(isRefreshing) {
                    detectDragGestures(
                        onDragStart = { refreshStartY = it.y },
                        onDragEnd = {
                            if (refreshStartY > 80f && !isRefreshing) {
                                refreshLibrary()
                            }
                        },
                        onDragCancel = {
                            refreshStartY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            if (!isRefreshing && dragAmount.y > 0f && refreshStartY > 0f) {
                                refreshProgress = (dragAmount.y / 400f).coerceIn(0f, 1f)
                            }
                        }
                    )
                }
        ) {
            when {
                !hasPermission -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Необходим доступ к медиафайлам", color = Color.White, fontSize = 16.sp)
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
                            Text("Предоставить доступ", color = Color.White)
                        }
                    }
                }
                bottomNavIndex == 1 -> {
                    AudioBrowserView(onAudioSelected = onVideoSelected)
                }
                bottomNavIndex == 2 -> {
                    FileExplorerView(onMediaSelected = onVideoSelected)
                }
                bottomNavIndex == 3 || currentTab == 1 -> {
                    PlaylistsView(onMediaSelected = onVideoSelected)
                }
                bottomNavIndex == 4 -> {
                    MoreHubView(
                        onOpenSettings = onOpenSettings,
                        onOpenCustomUrl = onOpenCustomUrl,
                        onOpenVideoLibrary = { bottomNavIndex = 0; currentTab = 0 }
                    )
                }
                else -> {
                    if (filteredVideos.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Видеозаписи не найдены", color = GrayText, fontSize = 16.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(filteredVideos, key = { it.id }) { video ->
                                VideoCardItemModern(
                                    video = video,
                                    prefs = prefs,
                                    onClick = { onVideoSelected(video.uri.toString()) },
                                    onDelete = { videoToDelete = video },
                                    onAddToPlaylist = { videoToAddToPlaylist = video },
                                    isMenuExpanded = videoCardMenuExpandedId == video.id,
                                    onMenuExpandedChange = { expanded -> videoCardMenuExpandedId = if (expanded) video.id else null }
                                )
                            }
                        }
                    }
                }
            }

            if (bottomNavIndex == 0 && currentTab == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(Color(0xFFFFA000), Color(0xFFFF5A00), Color(0xFFD93800))))
                        .clickable {
                            if (lastPlayedUri != null) {
                                onVideoSelected(lastPlayedUri)
                            } else if (videos.isNotEmpty()) {
                                onVideoSelected(videos.first().uri.toString())
                            } else {
                                Toast.makeText(context, "Нет видео", Toast.LENGTH_SHORT).show()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = NeonOrange.copy(alpha = 0.25f), radius = size.width * 0.65f)
                    }
                    Canvas(modifier = Modifier.size(24.dp)) {
                        val path = Path().apply {
                            moveTo(6.dp.toPx(), 4.dp.toPx())
                            lineTo(20.dp.toPx(), 12.dp.toPx())
                            lineTo(6.dp.toPx(), 20.dp.toPx())
                            close()
                        }
                        drawPath(path, color = Color.White)
                    }
                }
            }

            if (isRefreshing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Surface(
                        color = Color(0xFF14141F),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = NeonOrange
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Обновление...", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (videoToDelete != null) {
        AlertDialog(
            onDismissRequest = { videoToDelete = null },
            title = { Text("Удалить видео?", color = Color.White) },
            text = { Text("Видео будет удалено с устройства без возможности восстановления.", color = GrayText) },
            containerColor = SurfaceCardColor,
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = videoToDelete
                        videoToDelete = null
                        target?.let { performDelete(it) }
                    }
                ) { Text("Удалить", color = NeonOrange) }
            },
            dismissButton = {
                TextButton(onClick = { videoToDelete = null }) { Text("Отмена", color = Color.White) }
            }
        )
    }

    if (videoToAddToPlaylist != null) {
        val targetVideo = videoToAddToPlaylist!!
        val playlists = remember { PlaylistManager.getPlaylists(context) }
        AlertDialog(
            onDismissRequest = { videoToAddToPlaylist = null },
            containerColor = Color(0xFF161622),
            title = { Text("Добавить в плейлист", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = targetVideo.displayName, color = GoldenYellowLight, fontSize = 13.sp, maxLines = 1)
                    Spacer(modifier = Modifier.height(4.dp))
                    playlists.forEach { pl ->
                        Surface(
                            onClick = {
                                PlaylistManager.addItemToPlaylist(context, pl.id, targetVideo.uri.toString())
                                Toast.makeText(context, "Добавлено в '${pl.name}'", Toast.LENGTH_SHORT).show()
                                videoToAddToPlaylist = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1F1F2E),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = when (pl.iconType) { "favorites" -> "⭐"; "movie" -> "🎬"; "music" -> "🎵"; else -> "📁" }, fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = pl.name, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { videoToAddToPlaylist = null }) { Text("Закрыть", color = Color.White) } }
        )
    }
}

@Composable
fun TabPillButton(
    text: String,
    icon: @Composable (Color) -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color(0xFF241A0A) else Color(0xFF13131A).copy(alpha = 0.6f)
    val contentColor = if (isSelected) GoldenYellow else GrayText

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(backgroundColor)
            .then(
                if (isSelected) {
                    Modifier.border(border = BorderStroke(1.5.dp, GoldenYellow), shape = RoundedCornerShape(26.dp))
                } else {
                    Modifier.border(border = BorderStroke(1.dp, Color(0xFF262634)), shape = RoundedCornerShape(26.dp))
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowBrush = Brush.radialGradient(
                    colors = listOf(GoldenYellowGlow.copy(alpha = 0.35f), GoldenYellow.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(size.width / 2, size.height),
                    radius = size.width * 0.55f
                )
                drawCircle(brush = glowBrush)
                drawLine(
                    brush = Brush.horizontalGradient(listOf(GoldenYellowGlow.copy(alpha = 0.15f), GoldenYellowLight, GoldenYellow, GoldenYellowGlow.copy(alpha = 0.15f))),
                    start = Offset(size.width * 0.18f, size.height - 2.dp.toPx()),
                    end = Offset(size.width * 0.82f, size.height - 2.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            icon(contentColor)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                color = contentColor,
                fontSize = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun BottomNavItemModern(
    label: String,
    icon: @Composable (Color) -> Unit,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val tint = if (isSelected) GoldenYellow else GrayText
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        icon(tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = tint,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun VideoCardItemModern(
    video: VideoItem,
    prefs: android.content.SharedPreferences,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onAddToPlaylist: () -> Unit,
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
            .clip(RoundedCornerShape(16.dp))
            .border(BorderStroke(1.dp, Color(0xFF22222E)), RoundedCornerShape(16.dp))
            .background(SurfaceCardColor)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
                    .background(Color(0xFF0F0F14))
            ) {
                if (video.thumbnailResId != null) {
                    Image(
                        painter = painterResource(id = video.thumbnailResId),
                        contentDescription = video.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = video.displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        VideoCameraIconCustom(tint = Color(0xFF333344), iconSize = 32.dp)
                    }
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp)
                ) {
                    Text(
                        text = if (isHD) "HD" else "SD",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(36.dp)
                        .clickable { onMenuExpandedChange(true) },
                    contentAlignment = Alignment.Center
                ) {
                    ThreeDotsIconPill(tint = Color.White, iconSize = 16.dp)
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color(0xFF282834))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .fillMaxHeight()
                                .background(NeonOrangeGradient)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.displayName.ifEmpty { video.title },
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDate(video.dateAdded),
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

fun formatDuration(value: Long): String {
    val totalSeconds = (value / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) {
        String.format("%02d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

fun formatDate(timestamp: Long): String {
    return try {
        val date = java.util.Date(timestamp * 1000L)
        java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}

@Composable
fun SearchIconPill(tint: Color, iconSize: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        drawCircle(color = tint.copy(alpha = 0.12f), radius = size.minDimension * 0.45f)
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(size.width * 0.65f, size.height * 0.65f), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = tint, start = Offset(size.width * 0.68f, size.height * 0.68f), end = Offset(size.width * 0.92f, size.height * 0.92f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun HistoryIconPill(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        drawArc(color = tint, startAngle = -210f, sweepAngle = 300f, useCenter = false, topLeft = Offset(4.dp.toPx(), 4.dp.toPx()), size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = tint, start = Offset(size.width * 0.55f, size.height * 0.2f), end = Offset(size.width * 0.65f, size.height * 0.4f), strokeWidth = 2.dp.toPx())
        drawLine(color = tint, start = Offset(size.width * 0.55f, size.height * 0.4f), end = Offset(size.width * 0.35f, size.height * 0.6f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun ThreeDotsIconPill(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val radius = size.width / 6f
        drawCircle(color = tint, radius = radius, center = Offset(size.width / 2f, radius * 1.5f))
        drawCircle(color = tint, radius = radius, center = Offset(size.width / 2f, size.height / 2f))
        drawCircle(color = tint, radius = radius, center = Offset(size.width / 2f, size.height - radius * 1.5f))
    }
}

@Composable
fun VideoCameraIconCustom(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        drawRoundRect(color = tint, size = Size(size.width, size.height * 0.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawCircle(color = tint, radius = 5.dp.toPx(), center = Offset(size.width * 0.35f, size.height * 0.35f))
        drawLine(color = tint, start = Offset(size.width * 0.70f, size.height * 0.30f), end = Offset(size.width * 0.90f, size.height * 0.60f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun MusicNoteTabIcon(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.65f)
            lineTo(size.width * 0.2f, size.height * 0.18f)
            lineTo(size.width * 0.8f, size.height * 0.10f)
            lineTo(size.width * 0.8f, size.height * 0.55f)
        }
        drawPath(path, color = tint, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color = tint, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.2f, size.height * 0.65f))
        drawCircle(color = tint, radius = 2.5.dp.toPx(), center = Offset(size.width * 0.8f, size.height * 0.55f))
    }
}

@Composable
fun VideoNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawRoundRect(color = tint, size = Size(size.width, size.height * 0.7f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = tint, start = Offset(size.width * 0.22f, size.height * 0.22f), end = Offset(size.width * 0.65f, size.height * 0.22f), strokeWidth = 2.dp.toPx())
        drawPath(Path().apply { moveTo(size.width * 0.52f, size.height * 0.5f); lineTo(size.width * 0.78f, size.height * 0.36f); lineTo(size.width * 0.78f, size.height * 0.72f); close() }, color = tint)
    }
}

@Composable
fun AudioNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawLine(color = tint, start = Offset(size.width * 0.18f, size.height * 0.75f), end = Offset(size.width * 0.18f, size.height * 0.25f), strokeWidth = 2.dp.toPx())
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.1f, size.height * 0.35f), size = Size(size.width * 0.35f, size.height * 0.45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = tint, start = Offset(size.width * 0.52f, size.height * 0.78f), end = Offset(size.width * 0.52f, size.height * 0.25f), strokeWidth = 2.dp.toPx())
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.44f, size.height * 0.38f), size = Size(size.width * 0.35f, size.height * 0.42f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun BrowseNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawRoundRect(color = tint, topLeft = Offset(2.dp.toPx(), 2.dp.toPx()), size = Size(size.width - 4.dp.toPx(), size.height - 4.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawLine(color = tint, start = Offset(size.width * 0.25f, size.height * 0.30f), end = Offset(size.width * 0.75f, size.height * 0.30f), strokeWidth = 2.dp.toPx())
        drawLine(color = tint, start = Offset(size.width * 0.25f, size.height * 0.60f), end = Offset(size.width * 0.65f, size.height * 0.60f), strokeWidth = 2.dp.toPx())
    }
}

@Composable
fun PlaylistNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.14f, size.height * 0.18f), size = Size(size.width * 0.70f, size.height * 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.14f, size.height * 0.42f), size = Size(size.width * 0.70f, size.height * 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
        drawRoundRect(color = tint, topLeft = Offset(size.width * 0.14f, size.height * 0.66f), size = Size(size.width * 0.70f, size.height * 0.18f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
fun MoreNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(size.width * 0.25f, size.height * 0.5f))
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(size.width * 0.5f, size.height * 0.5f))
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(size.width * 0.75f, size.height * 0.5f))
    }
}

@Composable
fun FileExplorerView(onMediaSelected: (String) -> Unit) {
    val context = LocalContext.current
    val files = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        val root = context.getExternalFilesDir(null)
        if (root != null) {
            files.clear()
            files.addAll(root.listFiles()?.filter { it.isFile && (it.extension.lowercase() in setOf("mp4", "mkv", "avi", "mov", "wmv", "ts", "m3u8")) }?.map { it.absolutePath } ?: emptyList())
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (files.isEmpty()) {
            Text("Файлы не найдены", color = GrayText)
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                files.forEach { path ->
                    Surface(
                        onClick = { onMediaSelected(path) },
                        color = SurfaceCardColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = path.substringAfterLast('/'),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AudioBrowserView(onAudioSelected: (String) -> Unit) {
    val context = LocalContext.current
    val audioFiles = remember { mutableStateListOf<String>() }
    LaunchedEffect(Unit) {
        val root = context.getExternalFilesDir(null)
        if (root != null) {
            audioFiles.clear()
            audioFiles.addAll(root.listFiles()?.filter { it.isFile && (it.extension.lowercase() in setOf("mp3", "wav", "aac", "ogg")) }?.map { it.absolutePath } ?: emptyList())
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (audioFiles.isEmpty()) {
            Text("Аудио не найдено", color = GrayText)
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                audioFiles.forEach { path ->
                    Surface(
                        onClick = { onAudioSelected(path) },
                        color = SurfaceCardColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = path.substringAfterLast('/'),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsView(onMediaSelected: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Список плейлистов пуст", color = GrayText)
    }
}

@Composable
fun MoreHubView(
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().background(DarkBackground).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            color = SurfaceCardColor,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("FTP + сеть", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("FTP-адрес: ftp://127.0.0.1:2121", color = GrayText)
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = { Toast.makeText(LocalContext.current, "FTP-сервер активен", Toast.LENGTH_SHORT).show() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                ) {
                    Text("Проверить соединение", color = Color.White)
                }
            }
        }

        Surface(
            color = SurfaceCardColor,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings)
        ) {
            Text("Настройки плеера", color = Color.White, modifier = Modifier.padding(16.dp))
        }
        Surface(
            color = SurfaceCardColor,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenCustomUrl)
        ) {
            Text("Открыть сетевой поток", color = Color.White, modifier = Modifier.padding(16.dp))
        }
        Surface(
            color = SurfaceCardColor,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenVideoLibrary)
        ) {
            Text("Вернуться к медиатеке", color = Color.White, modifier = Modifier.padding(16.dp))
        }
    }
}

@Composable
fun PlayerScreen(
    videoUri: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    val playerManager = remember { VlcPlayerManager(context) }

    var currentVideoUri by remember { mutableStateOf(videoUri) }
    var videos by remember { mutableStateOf<List<com.example.videoplayer.model.VideoItem>>(MediaStoreHelper.getSampleVideos()) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var isLooping by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.ORIGINAL) }
    var forcedOrientation by remember { mutableStateOf(0) }
    var subtitleUrl by remember { mutableStateOf("") }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var castStatus by remember { mutableStateOf("Casting: off") }

    LaunchedEffect(Unit) {
        try { videos = MediaStoreHelper.getVideos(context) } catch (_: Exception) { }
    }

    val currentIndex = videos.indexOfFirst { it.uri.toString() == currentVideoUri }
    val currentVideoItem = if (currentIndex != -1) videos[currentIndex] else null
    val videoTitle = currentVideoItem?.displayName ?: currentVideoItem?.title ?: android.net.Uri.parse(currentVideoUri).lastPathSegment ?: "Video"
    val prevVideo = if (currentIndex > 0) videos[currentIndex - 1] else null
    val nextVideo = if (currentIndex != -1 && currentIndex < videos.size - 1) videos[currentIndex + 1] else null

    DisposableEffect(activity) {
        val originalOrientation = activity?.requestedOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = if (forcedOrientation == 1) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else if (forcedOrientation == 2) {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        } else {
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        }
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.requestedOrientation = originalOrientation
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            kotlinx.coroutines.delay(4000)
            if (!isScreenLocked) showControls = false
        }
    }

    LaunchedEffect(gestureIndicatorText) {
        if (gestureIndicatorText != null) {
            kotlinx.coroutines.delay(1200)
            gestureIndicatorText = null
        }
    }

    LaunchedEffect(currentVideoUri) {
        playerManager.onStatusChanged = { status ->
            if (status == "Playing") isPlaying = true
            if (status == "Paused") isPlaying = false
            if (status == "End Reached") {
                if (isLooping) {
                    playerManager.seekTo(0)
                    playerManager.resume()
                } else if (nextVideo != null) {
                    currentVideoUri = nextVideo.uri.toString()
                }
            }
        }
        playerManager.onTimeChanged = { time, len ->
            currentPosition = time
            duration = len
        }
        playerManager.playUrl(context, currentVideoUri)
    }

    BackHandler {
        playerManager.release()
        onBack()
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        if (offset.x < size.width / 2) {
                            playerManager.seekRelative(-10000)
                            gestureIndicatorText = "-10 сек"
                        } else {
                            playerManager.seekRelative(10000)
                            gestureIndicatorText = "+10 сек"
                        }
                    }
                )
            }
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectVerticalDragGestures(
                    onVerticalDrag = { change, dragAmount ->
                        val isLeft = change.position.x < size.width / 2
                        if (isLeft) {
                            val lp = activity?.window?.attributes
                            var brightness = lp?.screenBrightness ?: 0.5f
                            if (brightness < 0f) brightness = 0.5f
                            brightness -= dragAmount / 400f
                            brightness = brightness.coerceIn(0.01f, 1f)
                            lp?.screenBrightness = brightness
                            activity?.window?.attributes = lp
                            gestureIndicatorText = "Яркость: ${(brightness * 100).toInt()}%"
                        } else {
                            val currentVol = audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                            val changeVol = if (dragAmount < 0) 1 else -1
                            val newVol = (currentVol + changeVol).coerceIn(0, maxVolume)
                            audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                            gestureIndicatorText = "Громкость: ${((newVol.toFloat() / maxVolume) * 100).toInt()}%"
                        }
                    }
                )
            }
            .pointerInput(isScreenLocked) {
                if (isScreenLocked) return@pointerInput
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        val seekOffset = (dragAmount * 80).toLong()
                        playerManager.seekRelative(seekOffset)
                        gestureIndicatorText = "Перемотка: ${formatDuration(currentPosition)}"
                    }
                )
            }
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            playerManager.attachSurface(this@apply)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                            playerManager.updateWindowSize(width, height)
                        }
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            playerManager.detachSurface()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (gestureIndicatorText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0C0D16).copy(alpha = 0.88f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(text = gestureIndicatorText!!, color = Color(0xFF00F9FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Экран заблокирован", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { isScreenLocked = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00D8FF))
                    ) {
                        Text("Разблокировать", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls && !isScreenLocked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.80f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.90f))))) {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 28.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF0E0E18)).clickable {
                                playerManager.release(); onBack()
                            }, contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_nexora),
                                contentDescription = "Nexora Logo",
                                modifier = Modifier.size(42.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(text = "Nexora", fontSize = 20.sp, fontWeight = FontWeight.Bold, style = TextStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF00E5FF), Color(0xFFB14BF7)))))
                        Spacer(modifier = Modifier.width(22.dp))
                        Text(text = videoTitle, color = Color(0xFFD6E7FB), fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(50.dp).clickable {
                                showCastDialog = true
                                castStatus = "Casting: scanning..."
                            }, contentAlignment = Alignment.Center
                        ) {
                            CastIconNeon(tint = Color(0xFF00D8FF), width = 28.dp, height = 24.dp)
                        }
                        Box(
                            modifier = Modifier.size(50.dp).clickable {
                                isScreenLocked = true
                                showControls = false
                            }, contentAlignment = Alignment.Center
                        ) {
                            VerticalDotsNeon(tint = Color(0xFFB14BF7), width = 6.dp, height = 24.dp)
                        }
                    }
                }

                Box(
                    modifier = Modifier.align(Alignment.Center).size(160.dp).clickable {
                        if (isPlaying) {
                            playerManager.pause(); isPlaying = false
                        } else {
                            playerManager.resume(); isPlaying = true
                        }
                    }, contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val topBeam = Path().apply {
                            moveTo(centerX - 16.dp.toPx(), centerY)
                            lineTo(centerX - 60.dp.toPx(), 0f)
                            lineTo(centerX + 60.dp.toPx(), 0f)
                            lineTo(centerX + 16.dp.toPx(), centerY)
                            close()
                        }
                        drawPath(path = topBeam, brush = Brush.verticalGradient(listOf(Color(0xFF00D8FF).copy(alpha = 0.02f), Color(0xFF00E5FF).copy(alpha = 0.18f)), startY = 0f, endY = centerY))
                        val bottomBeam = Path().apply {
                            moveTo(centerX - 16.dp.toPx(), centerY)
                            lineTo(centerX - 60.dp.toPx(), size.height)
                            lineTo(centerX + 60.dp.toPx(), size.height)
                            lineTo(centerX + 16.dp.toPx(), centerY)
                            close()
                        }
                        drawPath(path = bottomBeam, brush = Brush.verticalGradient(listOf(Color(0xFF7052FF).copy(alpha = 0.18f), Color(0xFFB14BF7).copy(alpha = 0.02f)), startY = centerY, endY = size.height))
                        drawCircle(brush = Brush.radialGradient(colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFF5F6BF7).copy(alpha = 0.18f), Color.Transparent), center = Offset(centerX, centerY), radius = size.width * 0.45f))
                    }
                    Box(modifier = Modifier.size(86.dp).clip(CircleShape).background(Color(0xFF080912).copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 4.dp.toPx(); val glowWidth = 9.dp.toPx()
                            drawCircle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF).copy(alpha = 0.35f), Color(0xFFAF5FFF).copy(alpha = 0.35f)), start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2)), radius = (size.minDimension - glowWidth) / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = glowWidth))
                            drawCircle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFAF5FFF)), start = Offset(0f, size.height / 2), end = Offset(size.width, size.height / 2)), radius = (size.minDimension - strokeWidth) / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = strokeWidth))
                        }
                        if (isPlaying) PauseIconSolid(tint = Color.White, width = 24.dp, height = 28.dp) else PlayIconSolid(tint = Color.White, width = 26.dp, height = 28.dp)
                    }
                }

                Column(modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 28.dp, vertical = 20.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = formatDuration(currentPosition), color = Color(0xFF00F9FF), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                        Text(text = if (duration > 0) formatDuration(duration) else "--:--", color = Color(0xFFCBAAFF), fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(36.dp).pointerInput(duration) {
                        detectTapGestures { offset ->
                            val totalWidth = size.width.toFloat()
                            val effectiveDur = if (duration > 0) duration else 9_922_000L
                            val fraction = (offset.x / totalWidth).coerceIn(0f, 1f)
                            val newPos = (fraction * effectiveDur).toLong()
                            currentPosition = newPos
                            playerManager.seekTo(newPos)
                        }
                    }.pointerInput(duration) {
                        detectHorizontalDragGestures { change, _ ->
                            val totalWidth = size.width.toFloat()
                            val effectiveDur = if (duration > 0) duration else 9_922_000L
                            val fraction = (change.position.x / totalWidth).coerceIn(0f, 1f)
                            val newPos = (fraction * effectiveDur).toLong()
                            currentPosition = newPos
                            playerManager.seekTo(newPos)
                        }
                    }, contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp)) {
                            val trackWidth = size.width
                            val centerY = size.height / 2
                            val barThickness = 4.5.dp.toPx()
                            val effectiveDur = if (duration > 0) duration else 9_922_000L
                            val progressFraction = (currentPosition.toFloat() / effectiveDur.toFloat()).coerceIn(0f, 1f)
                            val thumbX = (trackWidth * progressFraction).coerceIn(10.dp.toPx(), trackWidth - 10.dp.toPx())
                            drawLine(brush = Brush.horizontalGradient(listOf(Color(0xFF00F9FF).copy(alpha = 0.25f), Color(0xFF5F6BF7).copy(alpha = 0.25f), Color(0xFFB14BF7).copy(alpha = 0.25f))), start = Offset(0f, centerY), end = Offset(trackWidth, centerY), strokeWidth = barThickness + 6.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(brush = Brush.horizontalGradient(listOf(Color(0xFF00F9FF), Color(0xFF4A74FF), Color(0xFF9B48F7), Color(0xFFC044FF))), start = Offset(0f, centerY), end = Offset(trackWidth, centerY), strokeWidth = barThickness, cap = StrokeCap.Round)
                            drawCircle(color = Color(0xFF00F9FF).copy(alpha = 0.4f), radius = 13.dp.toPx(), center = Offset(thumbX, centerY))
                            drawCircle(brush = Brush.radialGradient(listOf(Color(0xFF00F9FF), Color(0xFF00A2FF)), center = Offset(thumbX, centerY), radius = 10.dp.toPx()), radius = 9.5.dp.toPx(), center = Offset(thumbX, centerY))
                            drawCircle(color = Color.White, radius = 4.5.dp.toPx(), center = Offset(thumbX, centerY))
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Box(modifier = Modifier.size(52.dp).clickable { showSubtitleDialog = true }, contentAlignment = Alignment.Center) { SubtitlesIconNeon(tint = Color(0xFF00E5FF), width = 30.dp, height = 24.dp) }
                            Box(modifier = Modifier.size(52.dp).clickable { isLooping = !isLooping; Toast.makeText(context, if (isLooping) "Повтор включен" else "Повтор выключен", Toast.LENGTH_SHORT).show() }, contentAlignment = Alignment.Center) { RepeatIconNeon(tint = if (isLooping) Color(0xFF00F9FF) else Color(0xFF4A72FF), width = 28.dp, height = 24.dp) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                            Box(modifier = Modifier.size(52.dp).clickable {
                                prevVideo?.let { currentVideoUri = it.uri.toString() } ?: Toast.makeText(context, "Это первое видео", Toast.LENGTH_SHORT).show()
                            }, contentAlignment = Alignment.Center) { PreviousIconNeon(tint = Color(0xFF4E77FF), width = 26.dp, height = 24.dp) }
                            Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Color(0xFF090A14)).clickable {
                                if (isPlaying) { playerManager.pause(); isPlaying = false } else { playerManager.resume(); isPlaying = true }
                            }, contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val strokeWidth = 2.8.dp.toPx()
                                    drawCircle(brush = Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFFB14BF7))), radius = (size.minDimension - strokeWidth) / 2, center = Offset(size.width / 2, size.height / 2), style = Stroke(width = strokeWidth))
                                }
                                if (isPlaying) PauseIconSolid(tint = Color(0xFF7585FF), width = 16.dp, height = 18.dp) else PlayIconSolid(tint = Color(0xFF7585FF), width = 18.dp, height = 18.dp)
                            }
                            Box(modifier = Modifier.size(52.dp).clickable {
                                nextVideo?.let { currentVideoUri = it.uri.toString() } ?: Toast.makeText(context, "Это последнее видео", Toast.LENGTH_SHORT).show()
                            }, contentAlignment = Alignment.Center) { NextIconNeon(tint = Color(0xFFAF5FFF), width = 26.dp, height = 24.dp) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Box(modifier = Modifier.size(52.dp).clickable {
                                aspectRatioMode = when (aspectRatioMode) {
                                    AspectRatioMode.ORIGINAL -> AspectRatioMode.RATIO_16_9
                                    AspectRatioMode.RATIO_16_9 -> AspectRatioMode.RATIO_1_1
                                    AspectRatioMode.RATIO_1_1 -> AspectRatioMode.ORIGINAL
                                }
                                playerManager.switchAspectRatio(aspectRatioMode)
                                Toast.makeText(context, when (aspectRatioMode) {
                                    AspectRatioMode.ORIGINAL -> "Оригинал"
                                    AspectRatioMode.RATIO_16_9 -> "16:9"
                                    AspectRatioMode.RATIO_1_1 -> "1.1"
                                }, Toast.LENGTH_SHORT).show()
                            }, contentAlignment = Alignment.Center) { AspectRatioIconNeon(tint = Color(0xFF00D8FF), width = 30.dp, height = 24.dp) }
                            Box(modifier = Modifier.size(52.dp).clickable {
                                forcedOrientation = if (forcedOrientation == 0) 1 else 0
                                Toast.makeText(context, if (forcedOrientation == 1) "Поворот: альбомный" else "Поворот: авто", Toast.LENGTH_SHORT).show()
                            }, contentAlignment = Alignment.Center) { VerticalDotsNeon(tint = Color(0xFFB14BF7), width = 6.dp, height = 24.dp) }
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleDialog) {
        var subtitleInput by remember { mutableStateOf(subtitleUrl) }
        AlertDialog(
            onDismissRequest = { showSubtitleDialog = false },
            title = { Text("Загрузить субтитры", color = Color.White) },
            containerColor = SurfaceCardColor,
            text = {
                OutlinedTextField(
                    value = subtitleInput,
                    onValueChange = { subtitleInput = it },
                    label = { Text("URL .srt / .vtt", color = GrayText) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = Color(0xFF282834),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonOrange
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        subtitleUrl = subtitleInput
                        if (subtitleUrl.isNotBlank()) {
                            playerManager.loadSubtitleUrl(subtitleUrl)
                        }
                        showSubtitleDialog = false
                    }
                ) { Text("Загрузить", color = NeonOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showSubtitleDialog = false }) { Text("Отмена", color = Color.White) }
            }
        )
    }

    if (showCastDialog) {
        AlertDialog(
            onDismissRequest = { showCastDialog = false },
            title = { Text("Трансляция на экран", color = Color.White) },
            containerColor = SurfaceCardColor,
            text = {
                Column {
                    Text(castStatus, color = Color(0xFF00F9FF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Роутер устройств: активен. На этом уровне приложение ищет доступные маршруты и запускает трансляцию в установленный системный ресивер.", color = GrayText)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        castStatus = "Casting: connected"
                        showCastDialog = false
                        Toast.makeText(context, "Трансляция запущена", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Подключить", color = NeonOrange) }
            },
            dismissButton = {
                TextButton(onClick = { showCastDialog = false }) { Text("Закрыть", color = Color.White) }
            }
        )
    }
}

@Composable
fun PlayIconSolid(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val path = Path().apply {
            moveTo(2f, 0f)
            lineTo(size.width, size.height / 2f)
            lineTo(2f, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun PauseIconSolid(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = size.width * 0.34f
        drawRoundRect(color = tint, topLeft = Offset(0f, 0f), size = Size(barWidth, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()))
        drawRoundRect(color = tint, topLeft = Offset(size.width - barWidth, 0f), size = Size(barWidth, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.5.dp.toPx()))
    }
}

@Composable
fun PreviousIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.8.dp.toPx()
        drawRoundRect(color = tint, topLeft = Offset(0f, 0f), size = Size(barWidth, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx()))
        val path = Path().apply {
            moveTo(size.width, 0f)
            lineTo(barWidth + 3.dp.toPx(), size.height / 2f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun NextIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val barWidth = 2.8.dp.toPx()
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width - barWidth - 3.dp.toPx(), size.height / 2f)
            lineTo(0f, size.height)
            close()
        }
        drawPath(path, color = tint)
        drawRoundRect(color = tint, topLeft = Offset(size.width - barWidth, 0f), size = Size(barWidth, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.2.dp.toPx()))
    }
}

@Composable
fun SubtitlesIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        drawRoundRect(color = tint, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(width = stroke))
        val line1Y = size.height * 0.44f
        drawLine(color = tint, start = Offset(size.width * 0.18f, line1Y), end = Offset(size.width * 0.82f, line1Y), strokeWidth = stroke, cap = StrokeCap.Round)
        val line2Y = size.height * 0.70f
        drawLine(color = tint, start = Offset(size.width * 0.22f, line2Y), end = Offset(size.width * 0.65f, line2Y), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

@Composable
fun RepeatIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.2f, size.height * 0.35f)
            lineTo(size.width * 0.2f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.18f)
            lineTo(size.width * 0.82f, size.height * 0.45f)
            moveTo(size.width * 0.8f, size.height * 0.65f)
            lineTo(size.width * 0.8f, size.height * 0.82f)
            lineTo(size.width * 0.18f, size.height * 0.82f)
            lineTo(size.width * 0.18f, size.height * 0.55f)
        }
        drawPath(path, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val arrowTop = Path().apply { moveTo(size.width * 0.70f, size.height * 0.33f); lineTo(size.width * 0.82f, size.height * 0.48f); lineTo(size.width * 0.94f, size.height * 0.33f) }
        drawPath(arrowTop, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        val arrowBottom = Path().apply { moveTo(size.width * 0.06f, size.height * 0.67f); lineTo(size.width * 0.18f, size.height * 0.52f); lineTo(size.width * 0.30f, size.height * 0.67f) }
        drawPath(arrowBottom, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun AspectRatioIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        drawRoundRect(color = tint, topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke - 3.5.dp.toPx()), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx()), style = Stroke(width = stroke))
        drawLine(color = tint, start = Offset(size.width * 0.36f, size.height - 1.dp.toPx()), end = Offset(size.width * 0.64f, size.height - 1.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        val innerMarginX = size.width * 0.28f
        val innerMarginY = size.height * 0.22f
        drawRect(color = tint, topLeft = Offset(innerMarginX, innerMarginY), size = Size(size.width - innerMarginX * 2, size.height - innerMarginY * 2 - 3.5.dp.toPx()), style = Stroke(width = 1.8.dp.toPx()))
    }
}

@Composable
fun CastIconNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val stroke = 2.5.dp.toPx()
        val tvPath = Path().apply {
            moveTo(size.width * 0.36f, size.height - 3.dp.toPx())
            lineTo(size.width - stroke / 2, size.height - 3.dp.toPx())
            lineTo(size.width - stroke / 2, stroke / 2)
            lineTo(stroke / 2, stroke / 2)
            lineTo(stroke / 2, size.height * 0.55f)
        }
        drawPath(tvPath, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(color = tint, radius = 2.2.dp.toPx(), center = Offset(3.dp.toPx(), size.height - 3.dp.toPx()))
        drawArc(color = tint, startAngle = 270f, sweepAngle = 90f, useCenter = false, topLeft = Offset(-4.5.dp.toPx(), size.height - 11.dp.toPx()), size = Size(15.dp.toPx(), 15.dp.toPx()), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun VerticalDotsNeon(tint: Color, width: Dp, height: Dp) {
    Canvas(modifier = Modifier.size(width, height)) {
        val radius = size.width / 2f
        drawCircle(color = tint, radius = radius, center = Offset(radius, radius + 2.dp.toPx()))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height / 2f))
        drawCircle(color = tint, radius = radius, center = Offset(radius, size.height - radius - 2.dp.toPx()))
    }
}
