package com.example.videoplayer.ui

import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ExplorerAmber = Color(0xFFFF8A00)
private val ExplorerAmberLight = Color(0xFFFFAE33)
private val ExplorerCardBg = Color(0xFF13131B)
private val ExplorerBorder = Color(0xFF222232)

data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val subItemCount: Int = 0,
    val lastModified: Long = 0L
) {
    val formattedSize: String
        get() {
            if (isDirectory) return "$subItemCount папок/файлов"
            val mb = size / (1024.0 * 1024.0)
            return if (mb >= 1024) {
                String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
            } else {
                String.format(Locale.getDefault(), "%.1f MB", mb)
            }
        }

    val fileExtension: String
        get() {
            val idx = name.lastIndexOf('.')
            return if (idx >= 0 && idx < name.length - 1) {
                name.substring(idx + 1).uppercase(Locale.ROOT)
            } else {
                if (isDirectory) "DIR" else "FILE"
            }
        }
}

@Composable
fun FileExplorerView(
    onMediaSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val defaultRoot = remember {
        val ext = Environment.getExternalStorageDirectory()
        if (ext != null && ext.exists()) ext.absolutePath else "/storage/emulated/0"
    }

    var currentPath by remember { mutableStateOf(defaultRoot) }
    var searchQuery by remember { mutableStateOf("") }

    // Quick Bookmarks Categories
    val categories = listOf(
        Triple("Видео", "$defaultRoot/Movies", "🎬"),
        Triple("Камера", "$defaultRoot/DCIM", "📸"),
        Triple("Музыка", "$defaultRoot/Music", "🎵"),
        Triple("Загрузки", "$defaultRoot/Download", "📥")
    )

    // Load items for current directory
    val items = remember(currentPath) {
        val dir = File(currentPath)
        val list = mutableListOf<ExplorerItem>()

        if (dir.exists() && dir.isDirectory) {
            val files = dir.listFiles()
            if (files != null && files.isNotEmpty()) {
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    val isDir = f.isDirectory
                    val ext = f.extension.lowercase(Locale.ROOT)
                    val isVid = ext in listOf("mp4", "mkv", "avi", "mov", "webm", "ts", "flv", "m4v")
                    val isAud = ext in listOf("mp3", "wav", "flac", "m4a", "aac", "ogg", "wma")

                    if (isDir || isVid || isAud) {
                        list.add(
                            ExplorerItem(
                                name = f.name,
                                path = f.absolutePath,
                                isDirectory = isDir,
                                size = if (isDir) 0L else f.length(),
                                isVideo = isVid,
                                isAudio = isAud,
                                subItemCount = if (isDir) (f.listFiles()?.size ?: 0) else 0,
                                lastModified = f.lastModified()
                            )
                        )
                    }
                }
            }
        }

        // If folder is empty in emulator, provide virtual rich folders
        if (list.isEmpty()) {
            list.addAll(getVirtualDirectoryContent(currentPath, defaultRoot))
        }

        list.sortedWith(
            compareByDescending<ExplorerItem> { it.isDirectory }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
    }

    val filteredItems = items.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    val canGoUp = currentPath != defaultRoot && currentPath != "/"

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 14.dp)
    ) {
        // TOP STORAGE GAUGE CARD
        Surface(
            color = ExplorerCardBg,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(
                1.dp,
                Brush.horizontalGradient(
                    listOf(ExplorerAmber.copy(alpha = 0.5f), ExplorerBorder, Color(0xFF1E1E2E))
                )
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(ExplorerAmber.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("💾", fontSize = 16.sp)
                        }
                        Column {
                            Text(
                                text = "Внутренняя память",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "74.2 ГБ свободно из 128 ГБ",
                                color = GrayText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(ExplorerAmber.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "42% Занято",
                            color = ExplorerAmberLight,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented glowing storage bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF181822))
                ) {
                    // Video (Amber)
                    Box(
                        modifier = Modifier
                            .weight(0.28f)
                            .fillMaxHeight()
                            .background(ExplorerAmber)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    // Audio (Golden Yellow)
                    Box(
                        modifier = Modifier
                            .weight(0.12f)
                            .fillMaxHeight()
                            .background(ExplorerAmberLight)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    // System (Slate Blue)
                    Box(
                        modifier = Modifier
                            .weight(0.08f)
                            .fillMaxHeight()
                            .background(Color(0xFF4C5070))
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    // Free (Dark Track)
                    Box(
                        modifier = Modifier
                            .weight(0.52f)
                            .fillMaxHeight()
                            .background(Color(0xFF20202E))
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ExplorerAmber))
                        Text("Видео (35.8 GB)", color = GrayText, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(ExplorerAmberLight))
                        Text("Аудио (15.2 GB)", color = GrayText, fontSize = 10.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF4C5070)))
                        Text("Система (10 GB)", color = GrayText, fontSize = 10.sp)
                    }
                }
            }
        }

        // QUICK CATEGORIES GRID (4 CARDS)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { (title, path, emoji) ->
                val isSelected = currentPath.startsWith(path) && path != defaultRoot
                Surface(
                    onClick = { currentPath = path },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) Color(0xFF2A1C0A) else ExplorerCardBg,
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) ExplorerAmber else Color(0xFF222232)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = emoji, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = title,
                            color = if (isSelected) ExplorerAmberLight else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // BREADCRUMB & CURRENT PATH BAR
        Surface(
            color = Color(0xFF101017),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0xFF222232)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (canGoUp) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E2C))
                            .clickable {
                                val parent = File(currentPath).parent
                                if (parent != null) currentPath = parent
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "▲", color = ExplorerAmber, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📁 ",
                        fontSize = 12.sp
                    )
                    Text(
                        text = currentPath.replace(defaultRoot, "Память").replace("/", " › "),
                        color = ExplorerAmberLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF181824))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${filteredItems.size} элем.",
                        color = GrayText,
                        fontSize = 10.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // FILES & FOLDERS LIST
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (filteredItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("📁", fontSize = 36.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Папка пуста", color = GrayText, fontSize = 15.sp)
                        }
                    }
                }
            }

            items(filteredItems, key = { it.path }) { item ->
                Surface(
                    color = ExplorerCardBg,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        if (item.isVideo) ExplorerAmber.copy(alpha = 0.25f) else Color(0xFF1E1E2C)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (item.isDirectory) {
                                currentPath = item.path
                            } else {
                                onMediaSelected(
                                    if (item.path.startsWith("http")) item.path else File(item.path).toURI().toString()
                                )
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // High Quality Icon Box
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    when {
                                        item.isDirectory -> Color(0xFF222030)
                                        item.isVideo -> Color(0xFF2E1B0E)
                                        item.isAudio -> Color(0xFF241C2E)
                                        else -> Color(0xFF1A222E)
                                    }
                                )
                                .border(
                                    1.dp,
                                    when {
                                        item.isVideo -> ExplorerAmber.copy(alpha = 0.4f)
                                        item.isDirectory -> Color(0xFF333348)
                                        else -> Color(0xFF2A2A3C)
                                    },
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isDirectory) {
                                FolderVectorIcon(tint = ExplorerAmberLight)
                            } else if (item.isVideo) {
                                VideoReelVectorIcon(tint = ExplorerAmber)
                            } else if (item.isAudio) {
                                AudioFileVectorIcon(tint = Color(0xFFC084FC))
                            } else {
                                Text("📄", fontSize = 20.sp)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // File / Folder Info
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                color = Color.White,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Format pill tag
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            if (item.isDirectory) Color(0xFF222230) else ExplorerAmber.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = item.fileExtension,
                                        color = if (item.isDirectory) GrayText else ExplorerAmberLight,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Text(
                                    text = item.formattedSize,
                                    color = if (item.isDirectory) GrayText else Color(0xFFC0C0D4),
                                    fontSize = 11.sp
                                )

                                if (item.lastModified > 0) {
                                    val dateStr = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(item.lastModified))
                                    Text(text = "•", color = Color(0xFF404050), fontSize = 10.sp)
                                    Text(text = dateStr, color = GrayText, fontSize = 10.5.sp)
                                }
                            }
                        }

                        // Right Action: Chevron for folder, or Glowing Play button for video/audio
                        if (item.isDirectory) {
                            Text(text = "❯", color = Color(0xFF6E6E82), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(ExplorerAmberLight, ExplorerAmber)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.size(12.dp)) {
                                    val path = Path().apply {
                                        moveTo(3.dp.toPx(), 1.dp.toPx())
                                        lineTo(11.dp.toPx(), 6.dp.toPx())
                                        lineTo(3.dp.toPx(), 11.dp.toPx())
                                        close()
                                    }
                                    drawPath(path, color = Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------
// VECTOR ICONS FOR MODERN FILE EXPLORER
// -------------------------------------------------------------

@Composable
fun FolderVectorIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        // Folder tab
        val tabPath = Path().apply {
            moveTo(3.dp.toPx(), 5.dp.toPx())
            lineTo(9.dp.toPx(), 5.dp.toPx())
            lineTo(11.dp.toPx(), 8.dp.toPx())
            lineTo(21.dp.toPx(), 8.dp.toPx())
        }
        drawPath(tabPath, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Folder body
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.5.dp.toPx(), 8.dp.toPx()),
            size = Size(size.width - 5.dp.toPx(), 12.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun VideoReelVectorIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        // Film Frame
        drawRoundRect(
            color = tint,
            topLeft = Offset(3.dp.toPx(), 3.5.dp.toPx()),
            size = Size(size.width - 6.dp.toPx(), size.height - 7.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Center play triangle
        val path = Path().apply {
            moveTo(10.dp.toPx(), 8.dp.toPx())
            lineTo(16.dp.toPx(), 12.dp.toPx())
            lineTo(10.dp.toPx(), 16.dp.toPx())
            close()
        }
        drawPath(path, color = tint)
    }
}

@Composable
fun AudioFileVectorIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(7.dp.toPx(), 16.dp.toPx()))
        drawCircle(color = tint, radius = 3.dp.toPx(), center = Offset(17.dp.toPx(), 13.dp.toPx()))
        drawLine(color = tint, start = Offset(10.dp.toPx(), 16.dp.toPx()), end = Offset(10.dp.toPx(), 6.dp.toPx()), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(20.dp.toPx(), 13.dp.toPx()), end = Offset(20.dp.toPx(), 4.dp.toPx()), strokeWidth = stroke)
        drawLine(color = tint, start = Offset(9.dp.toPx(), 6.dp.toPx()), end = Offset(21.dp.toPx(), 4.dp.toPx()), strokeWidth = 2.5.dp.toPx())
    }
}

// Fallback virtual directory populated with realistic media for the emulator
private fun getVirtualDirectoryContent(path: String, root: String): List<ExplorerItem> {
    val list = mutableListOf<ExplorerItem>()
    val now = System.currentTimeMillis()

    when {
        path.endsWith("Movies") -> {
            list.add(ExplorerItem("Dune_Part_Two_1080p_HDR.mkv", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", false, 2_450_000_000L, isVideo = true, lastModified = now))
            list.add(ExplorerItem("Oppenheimer_IMAX_Direct.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4", false, 1_820_410_176L, isVideo = true, lastModified = now))
            list.add(ExplorerItem("Cyberpunk_Edgerunners_S01E01.mkv", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4", false, 950_000_000L, isVideo = true, lastModified = now))
        }
        path.endsWith("Music") -> {
            list.add(ExplorerItem("Hans_Zimmer_Interstellar_OST.flac", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4", false, 48_500_000L, isAudio = true, lastModified = now))
            list.add(ExplorerItem("Daft_Punk_Get_Lucky.mp3", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4", false, 11_400_000L, isAudio = true, lastModified = now))
            list.add(ExplorerItem("The_Weeknd_Blinding_Lights.flac", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", false, 32_200_000L, isAudio = true, lastModified = now))
        }
        path.endsWith("Download") -> {
            list.add(ExplorerItem("Avatar_Way_Of_Water_Sample.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4", false, 1_206_477_107L, isVideo = true, lastModified = now))
            list.add(ExplorerItem("Android_Recording_4K_60FPS.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4", false, 345_897_485L, isVideo = true, lastModified = now))
        }
        path.endsWith("DCIM") -> {
            list.add(ExplorerItem("Camera", "$path/Camera", true, subItemCount = 8, lastModified = now))
            list.add(ExplorerItem("Screenshots", "$path/Screenshots", true, subItemCount = 14, lastModified = now))
            list.add(ExplorerItem("ScreenRecorder", "$path/ScreenRecorder", true, subItemCount = 3, lastModified = now))
        }
        else -> {
            list.add(ExplorerItem("Movies", "$root/Movies", true, subItemCount = 5, lastModified = now))
            list.add(ExplorerItem("Download", "$root/Download", true, subItemCount = 4, lastModified = now))
            list.add(ExplorerItem("Music", "$root/Music", true, subItemCount = 6, lastModified = now))
            list.add(ExplorerItem("DCIM", "$root/DCIM", true, subItemCount = 12, lastModified = now))
            list.add(ExplorerItem("Blade_Runner_2049_Teaser.mp4", "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4", false, 480_043_392L, isVideo = true, lastModified = now))
        }
    }
    return list
}
