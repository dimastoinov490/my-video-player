package com.example.videoplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.service.LocalHttpShareServer
import com.example.videoplayer.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.io.File

val HubOrange = Color(0xFFFF8800)
val HubAmberLight = Color(0xFFFFB300)
val HubCardBg = Color(0xFF1E1E1E)
val HubBorder = Color(0x33FF8800)

private fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap {
    val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
    val pixels = IntArray(size * size)
    for (y in 0 until size) {
        val offset = y * size
        for (x in 0 until size) {
            pixels[offset + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
    }
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
    return bitmap
}

@Composable
fun MoreHubView(
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    onOpenProtectedVault: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var httpEnabled by remember { mutableStateOf(LocalHttpShareServer.isRunning(context)) }
    var httpAddress by remember { mutableStateOf(LocalHttpShareServer.getServerUrl(context)) }
    var showQrDialog by remember { mutableStateOf(false) }
    var qrContent by remember { mutableStateOf(httpAddress) }
    var qrTitle by remember { mutableStateOf("Подключение к сети") }

    val defaultShareFile = remember {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "nexora_share_example.mp4")
    }
    val shareUrl = remember(httpEnabled, httpAddress) {
        if (httpEnabled) LocalHttpShareServer.getDownloadUrl(context, defaultShareFile) else ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Центр возможностей",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Nexora Hub • Сервисы и обмен",
                    color = HubAmberLight,
                    fontSize = 12.sp
                )
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = HubCardBg,
                    border = BorderStroke(1.dp, if (httpEnabled) HubOrange.copy(alpha = 0.8f) else HubBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (httpEnabled) HubOrange.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (httpEnabled) HubAmberLight else GrayText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Локальный HTTP-сервер",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (httpEnabled) "Сервер активен в локальной сети" else "Обмен файлами по Wi‑Fi через HTTP",
                                    color = if (httpEnabled) Color(0xFF4CAF50) else GrayText,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = httpEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val started = LocalHttpShareServer.start(context, 8765)
                                        httpEnabled = started
                                        httpAddress = LocalHttpShareServer.getServerUrl(context)
                                        Toast.makeText(context, if (started) "HTTP-сервер запущен" else "Ошибка запуска HTTP-сервера", Toast.LENGTH_SHORT).show()
                                    } else {
                                        LocalHttpShareServer.stop()
                                        httpEnabled = false
                                        httpAddress = LocalHttpShareServer.getServerUrl(context)
                                        Toast.makeText(context, "HTTP-сервер остановлен", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = HubOrange,
                                    uncheckedThumbColor = GrayText,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }

                        if (httpEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, HubOrange.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Адрес подключения:", color = GrayText, fontSize = 11.sp)
                                        Text(
                                            httpAddress,
                                            color = HubAmberLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("HTTP address", httpAddress)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Адрес скопирован", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Копировать",
                                            tint = HubOrange,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = HubCardBg,
                    border = BorderStroke(1.dp, HubBorder)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(HubOrange.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = HubAmberLight,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Передача по QR",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Подключение и передача файла через локальную сеть",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    qrTitle = "Подключение к сети"
                                    qrContent = httpAddress.ifBlank { "http://127.0.0.1:8765" }
                                    showQrDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ConnectWithoutContact,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QR сети", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    qrTitle = "Прямая передача файла"
                                    qrContent = shareUrl.ifBlank { "http://127.0.0.1:8765/download/nexora_share_example.mp4" }
                                    showQrDialog = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HubAmberLight),
                                border = BorderStroke(1.dp, HubOrange),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = null,
                                    tint = HubAmberLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("QR файла", color = HubAmberLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "НАВИГАЦИЯ И ИНСТРУМЕНТЫ",
                    color = HubOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            item {
                HubActionCard(
                    icon = Icons.Default.Lock,
                    title = "Защищенная папка",
                    subtitle = "Скрытое хранилище с шифрованием и безопасным доступом",
                    onClick = onOpenProtectedVault
                )
            }

            item {
                HubActionCard(
                    icon = Icons.Default.Link,
                    title = "Воспроизвести поток (URL)",
                    subtitle = "Открыть RTSP, HLS, HTTP(S) или прямой видеолинк",
                    onClick = onOpenCustomUrl
                )
            }

            item {
                HubActionCard(
                    icon = Icons.Default.Settings,
                    title = "Системные настройки",
                    subtitle = "Декодеры, темы оформления, параметры кэширования",
                    onClick = onOpenSettings
                )
            }

            item {
                HubActionCard(
                    icon = Icons.Default.VideoLibrary,
                    title = "Библиотека видео",
                    subtitle = "Вернуться к списку видеофильмов и роликов",
                    onClick = onOpenVideoLibrary
                )
            }
        }
    }

    if (showQrDialog) {
        val qrBitmap = remember(qrContent) { generateQrCodeBitmap(qrContent) }
        AlertDialog(
            onDismissRequest = { showQrDialog = false },
            title = {
                Text(qrTitle, color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = qrTitle,
                        modifier = Modifier.size(220.dp)
                    )
                    Text(
                        qrContent,
                        color = GrayText,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showQrDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                ) {
                    Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQrDialog = false }) {
                    Text("Закрыть", color = GrayText)
                }
            },
            containerColor = DarkBackground
        )
    }
}

@Composable
private fun HubActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = HubCardBg,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = HubAmberLight,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = GrayText,
                    fontSize = 12.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = GrayText,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
