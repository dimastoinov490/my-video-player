package com.example.videoplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.videoplayer.security.SecureVaultScreen
import com.example.videoplayer.ui.DarkBackground
import com.example.videoplayer.ui.MoreHubView
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
            val darkColors = darkColorScheme(
                primary = NeonOrange,
                background = DarkBackground,
                surface = SurfaceCardColor,
                onPrimary = Color.White,
                onBackground = Color.White,
                onSurface = Color.White
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(modifier = Modifier.fillMaxSize(), color = DarkBackground) {
                    var selectedVideoUri by remember { mutableStateOf<String?>(null) }
                    var showSettings by remember { mutableStateOf(false) }
                    var showHub by remember { mutableStateOf(false) }
                    var showSecureVault by remember { mutableStateOf(false) }
                    var customUrlDialog by remember { mutableStateOf(false) }

                    if (selectedVideoUri != null) {
                        PlayerScreen(
                            videoUri = selectedVideoUri!!,
                            onBack = { selectedVideoUri = null }
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            onBack = { showSettings = false }
                        )
                    } else if (showHub) {
                        MoreHubView(
                            onOpenSettings = { showSettings = true },
                            onOpenCustomUrl = { customUrlDialog = true },
                            onOpenVideoLibrary = { showHub = false },
                            onOpenSecureVault = { showSecureVault = true }
                        )
                    } else if (showSecureVault) {
                        SecureVaultScreen(
                            onBack = { showSecureVault = false },
                            onOpenVaultFile = { decryptedPath ->
                                showSecureVault = false
                                selectedVideoUri = decryptedPath
                            }
                        )
                    } else {
                        VideoLibraryScreen(
                            onVideoSelected = { selectedVideoUri = it },
                            onOpenCustomUrl = { customUrlDialog = true },
                            onOpenSettings = { showSettings = true },
                            onOpenHub = { showHub = true }
                        )
                    }

                    if (customUrlDialog) {
                        var urlInput by remember {
                            mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4")
                        }

                        AlertDialog(
                            onDismissRequest = { customUrlDialog = false },
                            title = { Text("Открыть по ссылке") },
                            containerColor = SurfaceCardColor,
                            text = {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("URL или URI") },
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
                                        selectedVideoUri = urlInput.trim()
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
