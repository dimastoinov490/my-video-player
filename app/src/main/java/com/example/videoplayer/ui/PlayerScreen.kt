package com.example.videoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.player.VlcPlayerManager

@Composable
fun PlayerScreen(videoUri: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val player = remember { VlcPlayerManager(context.applicationContext) }

    var playing by remember { mutableStateOf(false) }
    var aspectMode by remember { mutableStateOf("Original") }
    var subtitleText by remember { mutableStateOf("Подсказка: для субтитров используйте .srt/.vtt с тем же именем файла.") }
    var isLocked by remember { mutableStateOf(false) }
    var isLandscape by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(videoUri) {
        player.playUrl(context, videoUri)
        playing = true
        val currentRatios = listOf("", "16:9", "1.1")
        val idx = currentRatios.indexOfFirst { it == "" }
        if (idx >= 0) player.setAspectRatio(currentRatios[idx])
    }

    DisposableEffect(player) {
        onDispose {
            player.detachSurface()
            player.mediaPlayer.stop()
            player.mediaPlayer.release()
            player.libVLC.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    player.attachSurface(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        if (!isLocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Text("←")
                    }
                    Text("${videoUri}", color = Color.White, fontSize = 12.sp, maxLines = 1)
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0x66000000)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ControlButton(label = if (playing) "Pause" else "Play") {
                                if (playing) {
                                    player.mediaPlayer.pause()
                                    playing = false
                                } else {
                                    player.mediaPlayer.play()
                                    playing = true
                                }
                            }

                            ControlButton(label = "16:9") {
                                player.setAspectRatio("16:9")
                                aspectMode = "16:9"
                            }
                            ControlButton(label = "1.1") {
                                player.setAspectRatio("1.1")
                                aspectMode = "1.1"
                            }
                            ControlButton(label = "Original") {
                                player.setAspectRatio("")
                                aspectMode = "Original"
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ControlButton(label = "Rotate") {
                                isLandscape = !isLandscape
                                val orientation = if (isLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                activity?.requestedOrientation = orientation
                            }

                            ControlButton(label = "Subtitles") {
                                Toast.makeText(context, "Subtitle file should be loaded from .srt or .vtt", Toast.LENGTH_SHORT).show()
                            }

                            ControlButton(label = "Cast") {
                                val intent = android.content.Intent(Intent.ACTION_SEND).apply {
                                    type = "video/*"
                                    putExtra(android.content.Intent.EXTRA_TEXT, videoUri)
                                    putExtra(android.content.Intent.EXTRA_TITLE, "Play on TV")
                                }
                                context.startActivity(Intent.createChooser(intent, "Cast video"))
                            }

                            ControlButton(label = if (isLocked) "Unlock" else "Lock") {
                                isLocked = !isLocked
                            }
                        }
                    }
                }
            }
        }

        if (isLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xAA000000)),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { isLocked = false },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                ) {
                    Text("Unlock screen")
                }
            }
        }

        Text(
            text = subtitleText,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 160.dp),
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ControlButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2A35)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, color = Color.White)
    }
}
