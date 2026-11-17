package com.example.videoplayer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.archive.ArchiveFileUtils
import com.example.videoplayer.archive.ArchiveManager
import com.example.videoplayer.archive.ArchivePlaybackSpec
import com.example.videoplayer.archive.ArchiveSource
import com.example.videoplayer.archive.ArchiveStreamServer
import com.example.videoplayer.ui.ArchiveBrowserScreen
import com.example.videoplayer.ui.FullBrowserScreen
import com.example.videoplayer.ui.OrientationHelper
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.VideoLibraryScreen
import com.example.videoplayer.ui.theme.*
import java.io.File

class MainActivity : FragmentActivity() {
    private var inPipState by mutableStateOf(false)
    private var orientationModeState by mutableStateOf(OrientationHelper.MODE_PORTRAIT)
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var pendingArchiveState by mutableStateOf<Pair<ArchiveSource, File?>?>(null)

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPipState = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            OrientationHelper.apply(this, orientationModeState)
        }
    }

    override fun onDestroy() {
        prefListener?.let {
            getSharedPreferences(OrientationHelper.PREFS_NAME, Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(it)
        }
        prefListener = null
        ArchiveStreamServer.stopAll()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingArchiveIntent(intent)
    }

    private fun handleIncomingArchiveIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val name = ArchiveManager.resolveDisplayName(this, uri)
        val mime = intent.type.orEmpty().lowercase()
        val isArchiveMime = mime.contains("zip") || mime.contains("rar") || mime.contains("7z") ||
            mime.contains("tar") || mime.contains("gzip") || mime.contains("iso")
        if (isArchiveMime || ArchiveFileUtils.isArchiveFileName(name)) {
            val source = if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
                ArchiveSource.FilePath(uri.path!!, name)
            } else {
                ArchiveSource.ContentUri(uri, name)
            }
            pendingArchiveState = source to ArchiveManager.defaultExtractDir(name)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        ThemeConfigManager.init(this)
        ArchiveStreamServer.cleanupStaleCache(this)
        handleIncomingArchiveIntent(intent)
        orientationModeState = OrientationHelper.getMode(this)
        OrientationHelper.apply(this, orientationModeState)
        val prefs = getSharedPreferences(OrientationHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == OrientationHelper.KEY_MODE) {
                runOnUiThread {
                    orientationModeState = OrientationHelper.getMode(this@MainActivity)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N || !isInPictureInPictureMode) {
                        OrientationHelper.apply(this@MainActivity, orientationModeState)
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        prefListener = listener
        enableEdgeToEdge()

        setContent {
            VideoPlayerTheme {
                val theme = LocalAppTheme.current
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = theme.background
                ) {
                    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
                    var selectedArchivePlayback by remember { mutableStateOf<ArchivePlaybackSpec?>(null) }
                    var activeBrowserUrl by remember { mutableStateOf<String?>(null) }
                    var showThemeStudioScreen by remember { mutableStateOf(false) }
                    var archiveRefreshTick by remember { mutableIntStateOf(0) }
                    var mediaRefreshTick by remember { mutableIntStateOf(0) }
                    var showCustomUrlDialog by remember { mutableStateOf(false) }
                    var urlInput by remember { mutableStateOf("") }

                    val safArchivePicker = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Throwable) {}
                            val name = ArchiveManager.resolveDisplayName(this@MainActivity, uri)
                            pendingArchiveState = ArchiveSource.ContentUri(uri, name) to
                                ArchiveManager.defaultExtractDir(name)
                        }
                    }

                    val mediaPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocument()
                    ) { uri ->
                        if (uri != null) {
                            try {
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                            } catch (_: Throwable) {}
                            val name = ArchiveManager.resolveDisplayName(this@MainActivity, uri)
                            if (ArchiveFileUtils.isArchiveFileName(name)) {
                                pendingArchiveState = ArchiveSource.ContentUri(uri, name) to
                                    ArchiveManager.defaultExtractDir(name)
                            } else {
                                selectedArchivePlayback = null
                                selectedVideoUri = uri.toString()
                            }
                        }
                    }

                    val subEnterTransition = remember {
                        slideInHorizontally { it / 8 } + fadeIn(animationSpec = tween(420, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)))
                    }
                    val subExitTransition = remember {
                        slideOutHorizontally { it / 8 } + fadeOut(animationSpec = tween(260))
                    }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            archiveSpec = selectedArchivePlayback,
                            isInPipMode = inPipState,
                            onBack = {
                                selectedVideoUri = null
                                selectedArchivePlayback = null
                                mediaRefreshTick++
                            }
                        )
                    } else if (pendingArchiveState != null) {
                        val (archiveSource, destDir) = pendingArchiveState!!
                        ArchiveBrowserScreen(
                            source = archiveSource,
                            defaultDestinationDir = destDir,
                            onPlayVideoFromArchive = { spec ->
                                selectedArchivePlayback = spec
                                selectedVideoUri = "archive://${spec.entryName}"
                            },
                            onExtractionFinished = {
                                archiveRefreshTick++
                            },
                            onClose = {
                                pendingArchiveState = null
                            }
                        )
                    } else if (activeBrowserUrl != null) {
                        FullBrowserScreen(
                            initialUrl = activeBrowserUrl!!,
                            onClose = { activeBrowserUrl = null }
                        )
                    } else if (showThemeStudioScreen) {
                        SettingsScreen(
                            onBack = {
                                showThemeStudioScreen = false
                                mediaRefreshTick++
                            }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoLibraryScreen(
                                onVideoSelected = { uri ->
                                    selectedArchivePlayback = null
                                    selectedVideoUri = uri
                                },
                                onOpenCustomUrl = {
                                    urlInput = ""
                                    showCustomUrlDialog = true
                                },
                                onOpenSettings = { showThemeStudioScreen = true },
                                onOpenArchive = { source, destDir ->
                                    pendingArchiveState = source to destDir
                                },
                                onOpenArchivePicker = {
                                    safArchivePicker.launch(arrayOf("*/*"))
                                },
                                onOpenBrowser = { url ->
                                    activeBrowserUrl = url
                                },
                                onOpenSystemMediaPicker = {
                                    mediaPickerLauncher.launch(arrayOf("video/*", "audio/*", "*/*"))
                                },
                                onOpenNativeThemeStudio = {
                                    showThemeStudioScreen = true
                                },
                                browseRefreshTick = archiveRefreshTick,
                                mediaRefreshTick = mediaRefreshTick
                            )

                            // Воспроизведение по ссылке с плавной анимацией появления
                            AnimatedVisibility(
                                visible = showCustomUrlDialog,
                                enter = subEnterTransition,
                                exit = subExitTransition
                            ) {
                                BackHandler {
                                    showCustomUrlDialog = false
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.72f))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { showCustomUrlDialog = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth(0.92f)
                                            .widthIn(max = 520.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { /* consume clicks inside dialog */ },
                                        shape = RoundedCornerShape(22.dp),
                                        color = Color.Transparent,
                                        border = BorderStroke(1.5.dp, Color(0x59826EC8)),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .background(
                                                    Brush.verticalGradient(
                                                        listOf(Color(0xFF14121C), Color(0xFF0D0B14))
                                                    )
                                                )
                                                .padding(22.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(44.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            Brush.linearGradient(
                                                                listOf(Color(0xFF5A3010), Color(0xFF3A1C0C))
                                                            )
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Link,
                                                        contentDescription = null,
                                                        tint = Color(0xFFFFA31A),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Text(
                                                    "Воспроизведение по ссылке",
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp
                                                )
                                            }

                                            Text(
                                                "Введите HTTP(S), HLS или RTSP URL потока:",
                                                color = Color(0xFF8D8AA8),
                                                fontSize = 13.sp
                                            )

                                            OutlinedTextField(
                                                value = urlInput,
                                                onValueChange = { urlInput = it },
                                                placeholder = { Text("https://example.com/stream.m3u8", color = Color(0xFF8D8AA8)) },
                                                singleLine = true,
                                                shape = RoundedCornerShape(14.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .testTag("custom_url_input"),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = Color(0xFFFF9A1A),
                                                    unfocusedBorderColor = Color(0x616E5FBE),
                                                    focusedContainerColor = Color(0xFF0B0914),
                                                    unfocusedContainerColor = Color(0xFF0B0914),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { showCustomUrlDialog = false },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFF2C2A48),
                                                        contentColor = Color(0xFFC9C7E0)
                                                    ),
                                                    shape = RoundedCornerShape(18.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("custom_url_cancel")
                                                ) {
                                                    Text("Отмена", fontWeight = FontWeight.Medium)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Button(
                                                    onClick = {
                                                        if (urlInput.isNotBlank()) {
                                                            showCustomUrlDialog = false
                                                            selectedVideoUri = urlInput.trim()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = Color(0xFFFFA31A),
                                                        contentColor = Color(0xFF3A1A00)
                                                    ),
                                                    shape = RoundedCornerShape(18.dp),
                                                    modifier = Modifier
                                                        .heightIn(min = 48.dp)
                                                        .testTag("custom_url_play")
                                                ) {
                                                    Text("Играть", color = Color(0xFF3A1A00), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
