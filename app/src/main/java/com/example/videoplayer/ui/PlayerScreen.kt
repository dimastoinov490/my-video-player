package com.example.videoplayer.ui

import android.net.Uri
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videoplayer.player.VlcPlayerManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playerManager by remember { mutableStateOf<VlcPlayerManager?>(null) }
    var videoUrl by remember { mutableStateOf("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4") }
    var isPlaying by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistable URI permission if needed, or pass URI string directly to libVLC
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            videoUrl = it.toString()
            playerManager?.play(videoUrl)
            isPlaying = true
        }
    }

    DisposableEffect(context) {
        val manager = VlcPlayerManager(context)
        playerManager = manager
        onDispose {
            manager.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("libVLC Video Player") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Video Surface container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            playerManager?.mediaPlayer?.vlcVout?.setVideoView(this)
                            playerManager?.mediaPlayer?.vlcVout?.attachViews()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Controls & URL input
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        filePickerLauncher.launch(arrayOf("video/*", "audio/*", "application/octet-stream"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📁 Открыть видео (MP4, AVI, MKV...)")
                }

                OutlinedTextField(
                    value = videoUrl,
                    onValueChange = { videoUrl = it },
                    label = { Text("Video Stream URL or File URI") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        playerManager?.play(videoUrl)
                        isPlaying = true
                    })
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = {
                        playerManager?.play(videoUrl)
                        isPlaying = true
                    }) {
                        Text("Play")
                    }

                    Button(onClick = {
                        if (isPlaying) {
                            playerManager?.pause()
                            isPlaying = false
                        } else {
                            playerManager?.resume()
                            isPlaying = true
                        }
                    }) {
                        Text(if (isPlaying) "Pause" else "Resume")
                    }

                    Button(onClick = {
                        playerManager?.stop()
                        isPlaying = false
                    }) {
                        Text("Stop")
                    }
                }
            }
        }
    }
}
