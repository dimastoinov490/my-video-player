package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.MoreHubView
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.VideoLibraryScreen
import com.example.videoplayer.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VideoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
                    var showCustomUrlDialog by remember { mutableStateOf(false) }
                    var showSettingsScreen by remember { mutableStateOf(false) }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else if (showSettingsScreen) {
                        SettingsScreen(
                            onBack = { showSettingsScreen = false }
                        )
                    } else {
                        VideoLibraryScreen(
                            onVideoSelected = { uri -> selectedVideoUri = uri },
                            onOpenCustomUrl = { showCustomUrlDialog = true },
                            onOpenSettings = { showSettingsScreen = true }
                        )

                        if (showCustomUrlDialog) {
                            var urlInput by remember {
                                mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                            }

                            AlertDialog(
                                onDismissRequest = { showCustomUrlDialog = false },
                                title = {
                                    Text(
                                        "Воспроизведение по ссылке",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Введите HTTP(S), HLS или RTSP URL:",
                                            color = GrayText,
                                            fontSize = 13.sp
                                        )
                                        OutlinedTextField(
                                            value = urlInput,
                                            onValueChange = { urlInput = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonOrange,
                                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White
                                            )
                                        )
                                    }
                                },
                                confirmButton = {
                                    Button(
                                        onClick = {
                                            if (urlInput.isNotBlank()) {
                                                showCustomUrlDialog = false
                                                selectedVideoUri = urlInput.trim()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                                    ) {
                                        Text("Играть", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCustomUrlDialog = false }) {
                                        Text("Отмена", color = GrayText)
                                    }
                                },
                                containerColor = DarkBackground,
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
