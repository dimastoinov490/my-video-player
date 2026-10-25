package com.example.videoplayer

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.VideoLibraryScreen
import com.example.videoplayer.ui.theme.DarkBackground
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.NeonOrange
import com.example.videoplayer.ui.theme.VideoPlayerTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            var darkTheme by remember { mutableStateOf(true) }
            var selectedVideoUri by remember { mutableStateOf<String?>(null) }
            var showCustomUrlDialog by remember { mutableStateOf(false) }
            var showSettingsScreen by remember { mutableStateOf(false) }

            VideoPlayerTheme(darkTheme = darkTheme) {
                Surface(
                    color = if (darkTheme) DarkBackground else Color(0xFFF7F3EE),
                    modifier = Modifier
                ) {
                    if (selectedVideoUri != null) {
                        com.example.videoplayer.ui.PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else if (showSettingsScreen) {
                        SettingsScreen(
                            onBack = { showSettingsScreen = false },
                            onThemeChanged = { darkTheme = it }
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
                                        color = if (darkTheme) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                },
                                text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            "Введите HTTP(S), HLS или RTSP URL:",
                                            color = if (darkTheme) GrayText else Color(0xFF4A4A4A),
                                            fontSize = 13.sp
                                        )
                                        OutlinedTextField(
                                            value = urlInput,
                                            onValueChange = { urlInput = it },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = NeonOrange,
                                                unfocusedBorderColor = if (darkTheme) Color.White.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.2f),
                                                focusedTextColor = if (darkTheme) Color.White else Color.Black,
                                                unfocusedTextColor = if (darkTheme) Color.White else Color.Black
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
                                containerColor = if (darkTheme) DarkBackground else Color(0xFFF8F4F0),
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
