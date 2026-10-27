package com.example.videoplayer

import android.os.Bundle
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
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.security.SecureVaultScreen
import com.example.videoplayer.ui.PlayerScreen
import com.example.videoplayer.ui.SettingsScreen
import com.example.videoplayer.ui.VideoLibraryScreen
import com.example.videoplayer.ui.theme.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager.init(this)
        ThemeConfigManager.init(this)
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
                    var showSettingsScreen by remember { mutableStateOf(false) }
                    var showProtectedVault by remember { mutableStateOf(false) }
                    var vaultClosedKey by remember { mutableIntStateOf(0) }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else if (showSettingsScreen) {
                        SettingsScreen(
                            onBack = { showSettingsScreen = false }
                        )
                    } else if (showProtectedVault) {
                        SecureVaultScreen(
                            onBack = {
                                showProtectedVault = false
                                vaultClosedKey++
                            },
                            onOpenVaultFile = { path -> selectedVideoUri = path }
                        )
                    } else {
                        VideoLibraryScreen(
                            onVideoSelected = { uri -> selectedVideoUri = uri },
                            onOpenCustomUrl = { showCustomUrlDialog = true },
                            onOpenSettings = { showSettingsScreen = true },
                            onOpenProtectedVault = { showProtectedVault = true },
                            refreshKey = vaultClosedKey
                        )

                        if (showCustomUrlDialog) {
                            var urlInput by remember {
                                mutableStateOf("")
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
                                            placeholder = { Text("https://example.com/video.mp4", color = GrayText) },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = theme.primary,
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
                                        colors = ButtonDefaults.buttonColors(containerColor = theme.primary)
                                    ) {
                                        Text("Играть", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showCustomUrlDialog = false }) {
                                        Text("Отмена", color = GrayText)
                                    }
                                },
                                containerColor = theme.surface,
                                shape = RoundedCornerShape(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
