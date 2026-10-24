package com.example.videoplayer.ui

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.videoplayer.model.VideoItem

val DarkBackground = Color(0xFF0B0F14)
val NeonOrange = Color(0xFFFF8A00)
val SurfaceCardColor = Color(0xFF1B212B)

@Composable
fun VideoLibraryScreen(
    onVideoSelected: (String) -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHub: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var isRefreshing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result.all { it.value }
        if (granted) {
            videos = queryLocalVideos(context)
            isRefreshing = false
        }
    }

    fun loadVideos() {
        val hasReadPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

        if (hasReadPermission) {
            isRefreshing = true
            videos = queryLocalVideos(context)
            isRefreshing = false
        } else {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            permissionLauncher.launch(permissions)
        }
    }

    LaunchedEffect(Unit) {
        loadVideos()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    if (dragAmount.y > 0f) {
                        loadVideos()
                    }
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Медиатека", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenCustomUrl,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                    ) {
                        Text("URL")
                    }
                    Button(
                        onClick = onOpenHub,
                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                    ) {
                        Text("Hub")
                    }
                    Button(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F2731))
                    ) {
                        Text("Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isRefreshing) {
                Text("Обновление медиатеки…", color = NeonOrange, fontSize = 14.sp)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos) { video ->
                    VideoCard(
                        item = video,
                        onClick = { onVideoSelected(video.uri.toString()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(item: VideoItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = SurfaceCardColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF2C3440)),
                contentAlignment = Alignment.Center
            ) {
                Text("▶", color = NeonOrange, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(item.resolution, color = Color(0xFFB4BBC8), fontSize = 12.sp)
                Text("${item.duration / 1000}s", color = Color(0xFF8A92A6), fontSize = 12.sp)
            }
        }
    }
}

private fun queryLocalVideos(context: Context): List<VideoItem> {
    val projection = arrayOf(
        MediaStore.Video.Media._ID,
        MediaStore.Video.Media.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION,
        MediaStore.Video.Media.SIZE,
        MediaStore.Video.Media.WIDTH,
        MediaStore.Video.Media.HEIGHT,
        MediaStore.Video.Media.DATE_ADDED,
        MediaStore.Video.Media.MIME_TYPE
    )

    val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val result = mutableListOf<VideoItem>()
    val cursor = context.contentResolver.query(collection, projection, null, null, sortOrder) ?: return emptyList()

    cursor.use {
        val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
        val widthColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
        val heightColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
        val addedColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

        while (it.moveToNext()) {
            val id = it.getLong(idColumn)
            val displayName = it.getString(nameColumn) ?: "video"
            val duration = it.getLong(durationColumn)
            val size = it.getLong(sizeColumn)
            val width = it.getInt(widthColumn)
            val height = it.getInt(heightColumn)
            val dateAdded = it.getLong(addedColumn)
            val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

            result.add(
                VideoItem(
                    id = id,
                    title = displayName,
                    displayName = displayName,
                    uri = uri,
                    duration = duration,
                    resolution = "$width x $height",
                    width = width,
                    height = height,
                    dateAdded = dateAdded,
                    size = size,
                    thumbnailResId = null
                )
            )
        }
    }

    return result
}
