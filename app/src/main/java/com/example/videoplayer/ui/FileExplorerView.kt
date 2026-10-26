package com.example.videoplayer.ui

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.theme.*
import java.io.File
import java.util.Locale

val ExplorerAmber = Color(0xFFFF8800)
val ExplorerAmberLight = Color(0xFFFFB300)
val ExplorerCardBg = Color(0xFF1E1E1E)
val ExplorerBorder = Color(0x33FF8800)

@Composable
fun FileExplorerView(
    onMediaSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val rootPath = remember {
        try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            "/storage/emulated/0"
        }
    }

    var currentPath by remember { mutableStateOf(rootPath) }
    var searchQuery by remember { mutableStateOf("") }

    val items = remember(currentPath, searchQuery) {
        getDirectoryItems(currentPath, rootPath, searchQuery)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                placeholder = {
                    Text("Поиск файлов и папок...", color = GrayText, fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Поиск",
                        tint = ExplorerAmber,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Очистить",
                                tint = GrayText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(26.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ExplorerAmber,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.12f),
                    focusedContainerColor = SurfaceCardColor,
                    unfocusedContainerColor = SurfaceCardColor,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
        }

        // Breadcrumbs & Navigation bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPath != rootPath) {
                IconButton(
                    onClick = {
                        val parent = File(currentPath).parent
                        if (parent != null && parent.startsWith(rootPath)) {
                            currentPath = parent
                        } else {
                            currentPath = rootPath
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Вверх",
                        tint = ExplorerAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            Text(
                text = formatBreadcrumbs(currentPath, rootPath),
                color = GoldenYellowLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "${items.size} объектов",
                color = GrayText,
                fontSize = 11.sp
            )
        }

        // List
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.path }) { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (item.isDirectory) {
                                currentPath = item.path
                                searchQuery = ""
                            } else {
                                onMediaSelected(item.path)
                            }
                        },
                    shape = RoundedCornerShape(14.dp),
                    color = ExplorerCardBg,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (item.isDirectory) Color(0xFF2E2216)
                                    else if (item.isVideo) Color(0xFF382315)
                                    else Color(0xFF1E2836)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (item.isDirectory) Icons.Default.Folder
                                else if (item.isVideo) Icons.Default.Movie
                                else if (item.isAudio) Icons.Default.MusicNote
                                else Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = if (item.isDirectory) ExplorerAmberLight
                                else if (item.isVideo) NeonOrange
                                else Color(0xFF64B5F6),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.getFormattedSize(),
                                color = GrayText,
                                fontSize = 11.sp
                            )
                        }

                        Icon(
                            imageVector = if (item.isDirectory) Icons.Default.ChevronRight else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = if (item.isDirectory) GrayText else ExplorerAmber,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatBreadcrumbs(path: String, root: String): String {
    if (path == root) return "Хранилище /"
    val relative = path.removePrefix(root).trimStart('/')
    return "Хранилище / " + relative.replace('/', ' ')
}

private fun getDirectoryItems(path: String, root: String, query: String): List<ExplorerItem> {
    val results = mutableListOf<ExplorerItem>()
    try {
        val dir = File(path)
        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null) {
                for (f in files) {
                    if (f.name.startsWith(".") || f.name.endsWith(".enc") || f.name.contains("secure_vault")) continue
                    val isDir = f.isDirectory
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val isVideo = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "wmv", "3gp")
                    val isAudio = ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg", "opus")
                    val subCount = if (isDir) (f.listFiles()?.size ?: 0) else 0

                    if (query.isNotEmpty() && !f.name.contains(query, ignoreCase = true)) {
                        continue
                    }

                    results.add(
                        ExplorerItem(
                            name = f.name,
                            path = f.absolutePath,
                            isDirectory = isDir,
                            size = if (isDir) 0L else f.length(),
                            isVideo = isVideo,
                            isAudio = isAudio,
                            subItemCount = subCount,
                            lastModified = f.lastModified()
                        )
                    )
                }
            }
        }
    } catch (_: Exception) {}

    if (results.isEmpty() && path == root) {
        return getVirtualFallbackItems()
    }

    return results.sortedWith(
        compareByDescending<ExplorerItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) }
    )
}

private fun getVirtualFallbackItems(): List<ExplorerItem> {
    val commonDirs = listOf("DCIM", "Download", "Movies", "Music", "Pictures", "Documents")
    val list = mutableListOf<ExplorerItem>()
    for (name in commonDirs) {
        val dir = File("/storage/emulated/0/$name")
        if (dir.exists() && dir.isDirectory) {
            val count = dir.listFiles()?.size ?: 0
            list.add(ExplorerItem(name = name, path = dir.absolutePath, isDirectory = true, subItemCount = count))
        } else {
            list.add(ExplorerItem(name = name, path = "/storage/emulated/0/$name", isDirectory = true, subItemCount = 0))
        }
    }
    return list
}
