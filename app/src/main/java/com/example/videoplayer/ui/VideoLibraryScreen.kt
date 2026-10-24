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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    var currentTab by remember { mutableStateOf(0) } // 0: Video, 1: Playlists
    var bottomNavIndex by remember { mutableStateOf(0) } // 0: Video, 1: Audio, 2: Browse, 3: Playlists, 4: More
    var videoToDelete by remember { mutableStateOf<VideoItem?>(null) }
    var videoCardMenuExpandedId by remember { mutableStateOf<Long?>(null) }
    var videoToAddToPlaylist by remember { mutableStateOf<VideoItem?>(null) }

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
                // Header Top Bar with Glowing Decorative Light Wave
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    // Subtle glowing orange light sweep in the background
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
                        // Nexora App Icon & Title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF14141F))
                                    .border(
                                        1.dp,
                                        Brush.radialGradient(
                                            listOf(GoldenYellowLight.copy(alpha = 0.8f), GoldenYellowGlow.copy(alpha = 0.3f))
                                        ),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_nexora),
                                    contentDescription = "Nexora Icon",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(10.dp)),
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

                        // Right 3 Circular Action Buttons (Search, History, Menu)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Search Button
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

                            // History Button
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1B1B24))
                                    .clickable { Toast.makeText(context, "История воспроизведения", Toast.LENGTH_SHORT).show() },
                                contentAlignment = Alignment.Center
                            ) {
                                HistoryIconPill(tint = Color(0xFFFF9E00), iconSize = 19.dp)
                            }

                            // Menu Button
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
                }

                // Search Bar Expandable
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

                // TOP TABS: "Видео" and "Плейлисты" (50% / 50% split with rounded capsule sides)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Tab 1: Видео (50% width)
                    TabPillButton(
                        text = "Видео",
                        icon = { tint -> VideoCameraIconCustom(tint = tint, iconSize = 20.dp) },
                        isSelected = currentTab == 0 && bottomNavIndex == 0,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentTab = 0
                            bottomNavIndex = 0
                        }
                    )

                    // Tab 2: Плейлисты (50% width)
                    TabPillButton(
                        text = "Плейлисты",
                        icon = { tint -> MusicNoteTabIcon(tint = tint, iconSize = 20.dp) },
                        isSelected = currentTab == 1 || bottomNavIndex == 3,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            currentTab = 1
                            bottomNavIndex = 3
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        },
        bottomBar = {
            // FLOATING ROUNDED BOTTOM NAVIGATION BAR WITH ROUNDED SIDES AND GLOWING AMBIENT CONTOUR
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Outer soft glowing aura following the rounded pill contour
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(34.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    GoldenYellowGlow.copy(alpha = 0.22f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Surface(
                    color = Color(0xFF101017),
                    shape = RoundedCornerShape(32.dp),
                    border = BorderStroke(
                        1.5.dp,
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF242436),
                                GoldenYellowGlow.copy(alpha = 0.85f),
                                GoldenYellowLight,
                                GoldenYellowGlow.copy(alpha = 0.85f),
                                Color(0xFF242436)
                            )
                        )
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // Glowing golden-yellow top line along the curve
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.5.dp)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            GoldenYellowGlow.copy(alpha = 0.05f),
                                            GoldenYellowLight,
                                            GoldenYellow,
                                            GoldenYellowLight,
                                            GoldenYellowGlow.copy(alpha = 0.05f)
                                        )
                                    )
                                )
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
                                onClick = {
                                    bottomNavIndex = 0
                                    currentTab = 0
                                }
                            )
                            BottomNavItemModern(
                                label = "Аудио",
                                icon = { tint -> AudioNavIcon(tint) },
                                isSelected = bottomNavIndex == 1,
                                onClick = {
                                    bottomNavIndex = 1
                                    currentTab = 0
                                }
                            )
                            BottomNavItemModern(
                                label = "Обзор",
                                icon = { tint -> BrowseNavIcon(tint) },
                                isSelected = bottomNavIndex == 2,
                                onClick = {
                                    bottomNavIndex = 2
                                    currentTab = 0
                                }
                            )
                            BottomNavItemModern(
                                label = "Плейлисты",
                                icon = { tint -> PlaylistNavIcon(tint) },
                                isSelected = bottomNavIndex == 3 || currentTab == 1,
                                onClick = {
                                    bottomNavIndex = 3
                                    currentTab = 1
                                }
                            )
                            BottomNavItemModern(
                                label = "Ещё",
                                icon = { tint -> MoreNavIcon(tint) },
                                isSelected = bottomNavIndex == 4,
                                onClick = {
                                    bottomNavIndex = 4
                                }
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
                    // Audio Browser Screen
                    AudioBrowserView(
                        onAudioSelected = onVideoSelected
                    )
                }
                bottomNavIndex == 2 -> {
                    // Custom In-App File Explorer Screen
                    FileExplorerView(
                        onMediaSelected = onVideoSelected
                    )
                }
                bottomNavIndex == 3 || currentTab == 1 -> {
                    // Playlists Screen
                    PlaylistsView(
                        onMediaSelected = onVideoSelected
                    )
                }
                bottomNavIndex == 4 -> {
                    // Additional Tools & Features Hub (FTP Server, Quick Share, Settings, Stream URL)
                    MoreHubView(
                        onOpenSettings = onOpenSettings,
                        onOpenCustomUrl = onOpenCustomUrl,
                        onOpenVideoLibrary = {
                            bottomNavIndex = 0
                            currentTab = 0
                        }
                    )
                }
                else -> {
                    if (filteredVideos.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Видеозаписи не найдены", color = GrayText, fontSize = 16.sp)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
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
                                    onMenuExpandedChange = { expanded ->
                                        videoCardMenuExpandedId = if (expanded) video.id else null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // FLOATING ACTION BUTTON (FAB) IN BOTTOM-RIGHT (Only in Videos tab)
            if (bottomNavIndex == 0 && currentTab == 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 18.dp, bottom = 18.dp)
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0xFFFFA000),
                                    Color(0xFFFF5A00),
                                    Color(0xFFD93800)
                                )
                            )
                        )
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
                    // Soft outer glow shadow
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = NeonOrange.copy(alpha = 0.25f),
                            radius = size.width * 0.65f
                        )
                    }
                    // Solid white Play triangle
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
        }
    }

    // Delete confirmation dialog
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

    // Add to Playlist Dialog
    if (videoToAddToPlaylist != null) {
        val targetVideo = videoToAddToPlaylist!!
        val playlists = remember { PlaylistManager.getPlaylists(context) }
        AlertDialog(
            onDismissRequest = { videoToAddToPlaylist = null },
            containerColor = Color(0xFF161622),
            title = {
                Text("Добавить в плейлист", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            },
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
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (pl.iconType) {
                                        "favorites" -> "⭐"
                                        "movie" -> "🎬"
                                        "music" -> "🎵"
                                        else -> "📁"
                                    },
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = pl.name, color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { videoToAddToPlaylist = null }) {
                    Text("Закрыть", color = Color.White)
                }
            }
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
                    Modifier.border(
                        border = BorderStroke(1.5.dp, GoldenYellow),
                        shape = RoundedCornerShape(26.dp)
                    )
                } else {
                    Modifier.border(
                        border = BorderStroke(1.dp, Color(0xFF262634)),
                        shape = RoundedCornerShape(26.dp)
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Glowing warm golden-yellow indicator when selected
        if (isSelected) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowBrush = Brush.radialGradient(
                    colors = listOf(
                        GoldenYellowGlow.copy(alpha = 0.35f),
                        GoldenYellow.copy(alpha = 0.15f),
                        Color.Transparent
                    ),
                    center = Offset(size.width / 2, size.height),
                    radius = size.width * 0.55f
                )
                drawCircle(brush = glowBrush)

                // Golden-yellow bottom accent line
                drawLine(
                    brush = Brush.horizontalGradient(
                        listOf(
                            GoldenYellowGlow.copy(alpha = 0.15f),
                            GoldenYellowLight,
                            GoldenYellow,
                            GoldenYellowGlow.copy(alpha = 0.15f)
                        )
                    ),
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
            // Thumbnail Area
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
                    // Dark placeholder with movie icon
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoCameraIconCustom(tint = Color(0xFF333344), iconSize = 32.dp)
                    }
                }

                // SD / HD badge in top-left
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Text(
                        text = if (isHD) "HD" else "SD",
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Top right 3-dots button on preview
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

                // Duration in bottom-right
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                ) {
                    Text(
                        text = formatDuration(video.duration),
                        color = Color.White,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Orange progress bar if watched
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

            // Info under preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
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
                        text = "${formatSize(video.size)}  •  ${formatDate(video.dateAdded)}",
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
                        ThreeDotsIconPill(tint = GrayText, iconSize = 16.dp)
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
                            text = { Text("В плейлист", color = GoldenYellowLight) },
                            onClick = {
                                onMenuExpandedChange(false)
                                onAddToPlaylist()
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

// -------------------------------------------------------------
// HIGH QUALITY CUSTOM VECTOR ICONS MATCHING SCREENSHOT
// -------------------------------------------------------------

@Composable
fun VideoCameraIconCustom(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        // Video camera body
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
            size = Size(w * 0.6f, h * 0.6f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Video camera lens
        val lensPath = Path().apply {
            moveTo(w * 0.62f, h * 0.35f)
            lineTo(w - 2.dp.toPx(), h * 0.22f)
            lineTo(w - 2.dp.toPx(), h * 0.78f)
            lineTo(w * 0.62f, h * 0.65f)
            close()
        }
        drawPath(lensPath, color = tint)
    }
}

@Composable
fun MusicNoteTabIcon(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val stroke = 2.dp.toPx()
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(size.width * 0.35f, size.height * 0.75f))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.44f, size.height * 0.75f),
            end = Offset(size.width * 0.44f, size.height * 0.25f),
            strokeWidth = stroke
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.44f, size.height * 0.25f),
            end = Offset(size.width * 0.8f, size.height * 0.25f),
            strokeWidth = stroke
        )
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(size.width * 0.71f, size.height * 0.65f))
        drawLine(
            color = tint,
            start = Offset(size.width * 0.8f, size.height * 0.65f),
            end = Offset(size.width * 0.8f, size.height * 0.25f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun SearchIconPill(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.width * 0.32f,
            center = Offset(size.width * 0.42f, size.height * 0.42f),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = tint,
            start = Offset(size.width * 0.65f, size.height * 0.65f),
            end = Offset(size.width * 0.9f, size.height * 0.9f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun HistoryIconPill(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = tint,
            radius = size.width * 0.42f,
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = stroke)
        )
        // Clock hands pointing to ~10:10
        drawLine(
            color = tint,
            start = Offset(size.width / 2, size.height / 2),
            end = Offset(size.width / 2, size.height * 0.25f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = Offset(size.width / 2, size.height / 2),
            end = Offset(size.width * 0.7f, size.height * 0.55f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun ThreeDotsIconPill(tint: Color, iconSize: Dp) {
    Canvas(modifier = Modifier.size(iconSize)) {
        val radius = 1.8.dp.toPx()
        val centerX = size.width / 2
        drawCircle(color = tint, radius = radius, center = Offset(centerX, size.height * 0.22f))
        drawCircle(color = tint, radius = radius, center = Offset(centerX, size.height * 0.5f))
        drawCircle(color = tint, radius = radius, center = Offset(centerX, size.height * 0.78f))
    }
}

@Composable
fun VideoNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        // Smooth rounded filmstrip
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width - 4.dp.toPx(), size.height - 8.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Center smooth play triangle
        val path = Path().apply {
            moveTo(size.width * 0.42f, size.height * 0.35f)
            lineTo(size.width * 0.65f, size.height * 0.5f)
            lineTo(size.width * 0.42f, size.height * 0.65f)
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun AudioNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        // Left note head
        drawCircle(color = tint, radius = 3.6.dp.toPx(), center = Offset(7.5.dp.toPx(), 18.dp.toPx()))
        // Right note head
        drawCircle(color = tint, radius = 3.6.dp.toPx(), center = Offset(18.5.dp.toPx(), 15.dp.toPx()))
        // Left vertical stem
        drawLine(
            color = tint,
            start = Offset(10.5.dp.toPx(), 18.dp.toPx()),
            end = Offset(10.5.dp.toPx(), 6.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Right vertical stem
        drawLine(
            color = tint,
            start = Offset(21.5.dp.toPx(), 15.dp.toPx()),
            end = Offset(21.5.dp.toPx(), 3.5.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // Connecting curved beam
        drawLine(
            color = tint,
            start = Offset(9.5.dp.toPx(), 6.dp.toPx()),
            end = Offset(22.5.dp.toPx(), 3.5.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun BrowseNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 2.dp.toPx()
        // Rounded folder body
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.5.dp.toPx(), 6.5.dp.toPx()),
            size = Size(size.width - 5.dp.toPx(), size.height - 10.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.5.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Rounded folder tab
        drawRoundRect(
            color = tint,
            topLeft = Offset(4.dp.toPx(), 3.5.dp.toPx()),
            size = Size(9.dp.toPx(), 5.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun PlaylistNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val stroke = 2.2.dp.toPx()
        // Smooth rounded pill lines
        drawLine(color = tint, start = Offset(8.dp.toPx(), 7.dp.toPx()), end = Offset(21.dp.toPx(), 7.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(8.dp.toPx(), 13.dp.toPx()), end = Offset(21.dp.toPx(), 13.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color = tint, start = Offset(8.dp.toPx(), 19.dp.toPx()), end = Offset(17.dp.toPx(), 19.dp.toPx()), strokeWidth = stroke, cap = StrokeCap.Round)
        // Rounded bullets
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(4.dp.toPx(), 7.dp.toPx()))
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(4.dp.toPx(), 13.dp.toPx()))
        drawCircle(color = tint, radius = 2.dp.toPx(), center = Offset(4.dp.toPx(), 19.dp.toPx()))
    }
}

@Composable
fun MoreNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(26.dp)) {
        val radius = 2.6.dp.toPx()
        val centerY = size.height / 2
        drawCircle(color = tint, radius = radius, center = Offset(5.dp.toPx(), centerY))
        drawCircle(color = tint, radius = radius, center = Offset(size.width / 2, centerY))
        drawCircle(color = tint, radius = radius, center = Offset(size.width - 5.dp.toPx(), centerY))
    }
}
