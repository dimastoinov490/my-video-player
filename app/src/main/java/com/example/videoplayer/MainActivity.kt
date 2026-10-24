package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.videoplayer.ui.DarkBackground
import com.example.videoplayer.ui.NeonOrange
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.SurfaceCardColor
import com.example.videoplayer.ui.VideoLibraryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val darkColorScheme = darkColorScheme(
                primary = NeonOrange,
                background = DarkBackground,
                surface = SurfaceCardColor,
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )

            MaterialTheme(colorScheme = darkColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
                    var customUrlDialog by remember { mutableStateOf(false) }
                    var settingsScreen by remember { mutableStateOf(false) }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else if (settingsScreen) {
                        SettingsScreen(
                            onBack = { settingsScreen = false }
                        )
                    } else {
                        VideoLibraryScreen(
                            onVideoSelected = { uri -> selectedVideoUri = uri },
                            onOpenCustomUrl = { customUrlDialog = true },
                            onOpenSettings = { settingsScreen = true }
                        )

                        if (customUrlDialog) {
                            var urlInput by remember {
                                mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                            }
                            AlertDialog(
                                onDismissRequest = { customUrlDialog = false },
                                title = { Text("Открыть по ссылке", color = Color.White) },
                                containerColor = SurfaceCardColor,
                                text = {
                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        label = { Text("URL или URI", color = Color(0xFF8E8E9E)) },
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
                                            customUrlDialog = false
                                            selectedVideoUri = urlInput
                                        }
                                    ) {
                                        Text("Воспроизвести", color = NeonOrange)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { customUrlDialog = false }) {
                                        Text("Отмена", color = Color.White)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
