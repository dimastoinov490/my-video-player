package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.videoplayer.ui.PlayerScreen
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

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else {
                        VideoLibraryScreen(
                            onVideoSelected = { uri -> selectedVideoUri = uri },
                            onOpenCustomUrl = { customUrlDialog = true }
                        )

                        if (customUrlDialog) {
                            var urlInput by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { customUrlDialog = false },
                                title = { androidx.compose.material3.Text("Открыть по ссылке") },
                                text = {
                                    androidx.compose.material3.OutlinedTextField(
                                        value = urlInput,
                                        onValueChange = { urlInput = it },
                                        label = { androidx.compose.material3.Text("URL или URI") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(
                                        onClick = {
                                            customUrlDialog = false
                                            selectedVideoUri = urlInput
                                        }
                                    ) {
                                        androidx.compose.material3.Text("Воспроизвести")
                                    }
                                },
                                dismissButton = {
                                    androidx.compose.material3.TextButton(onClick = { customUrlDialog = false }) {
                                        androidx.compose.material3.Text("Отмена")
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
