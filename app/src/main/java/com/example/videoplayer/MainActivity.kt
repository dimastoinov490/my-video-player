package com.example.videoplayer

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.ui.OrientationHelper
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.VideoLibraryScreen
import com.example.videoplayer.ui.theme.*

class MainActivity : FragmentActivity() {
    private var inPipState by mutableStateOf(false)
    private var orientationModeState by mutableStateOf(OrientationHelper.MODE_PORTRAIT)
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        ThemeConfigManager.init(this)
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
                    var showCustomUrlDialog by remember { mutableStateOf(false) }
                    var urlInput by remember { mutableStateOf("") }

                    val subEnterTransition = remember {
                        slideInHorizontally { it / 8 } + fadeIn(animationSpec = tween(420, easing = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)))
                    }
                    val subExitTransition = remember {
                        slideOutHorizontally { it / 8 } + fadeOut(animationSpec = tween(260))
                    }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            isInPipMode = inPipState,
                            onBack = { selectedVideoUri = null }
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            VideoLibraryScreen(
                                onVideoSelected = { uri -> selectedVideoUri = uri },
                                onOpenCustomUrl = {
                                    urlInput = ""
                                    showCustomUrlDialog = true
                                },
                                onOpenSettings = { /* В v4 настройки открываются прямо в HTML */ }
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
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { /* consume clicks inside dialog */ },
                                        shape = RoundedCornerShape(22.dp),
                                        color = theme.surface,
                                        border = BorderStroke(1.5.dp, theme.primary.copy(alpha = 0.35f)),
                                        shadowElevation = 24.dp
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(22.dp),
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Text(
                                                "Воспроизведение по ссылке",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp
                                            )

                                            Text(
                                                "Введите HTTP(S), HLS или RTSP URL потока:",
                                                color = GrayText,
                                                fontSize = 13.sp
                                            )

                                            OutlinedTextField(
                                                value = urlInput,
                                                onValueChange = { urlInput = it },
                                                placeholder = { Text("https://example.com/stream.m3u8", color = GrayText) },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = theme.primary,
                                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                    focusedTextColor = Color.White,
                                                    unfocusedTextColor = Color.White
                                                )
                                            )

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                TextButton(onClick = { showCustomUrlDialog = false }) {
                                                    Text("Отмена", color = GrayText)
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Button(
                                                    onClick = {
                                                        if (urlInput.isNotBlank()) {
                                                            showCustomUrlDialog = false
                                                            selectedVideoUri = urlInput.trim()
                                                        }
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                                                    shape = RoundedCornerShape(12.dp)
                                                ) {
                                                    Text("Играть", color = Color.Black, fontWeight = FontWeight.Bold)
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
