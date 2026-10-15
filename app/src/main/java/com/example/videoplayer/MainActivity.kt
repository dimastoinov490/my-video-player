package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.VideoLibraryScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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
                            var urlInput by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
                            AlertDialog(
                                onDismissRequest = { customUrlDialog = false },
                                title = { Text("Открыть по ссылке") },
                                text = {
                                    OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        label = { Text("URL или URI") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            customUrlDialog = false
                                            selectedVideoUri = urlInput
                                        }
                                    ) {
                                        Text("Воспроизвести")
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { customUrlDialog = false }) {
                                        Text("Отмена")
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
