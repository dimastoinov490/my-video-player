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
import com.example.videoplayer.service.WifiDirectInfo
import com.example.videoplayer.service.WifiDirectManager
import com.example.videoplayer.ui.ai.AiDesignStudioDialog
import com.example.videoplayer.ui.ai.UiTemplateFromScreenshotDialog
import com.example.videoplayer.ui.theme.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun generateQrCodeBitmap(text: String, size: Int = 512): Bitmap {
    return try {
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
        bitmap
    } catch (_: Exception) {
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    }
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
    val coroutineScope = rememberCoroutineScope()
    val theme = LocalAppTheme.current

    var httpEnabled by remember { mutableStateOf(LocalHttpShareServer.isRunning(context)) }
    var httpAddress by remember { mutableStateOf(LocalHttpShareServer.getServerUrl(context)) }
    var serverError by remember { mutableStateOf<String?>(LocalHttpShareServer.lastErrorMessage) }
    var isStartingServer by remember { mutableStateOf(false) }

    var showDirectModal by remember { mutableStateOf(false) }
    var showAiStudioDialog by remember { mutableStateOf(false) }
    var showScreenshotCloneDialog by remember { mutableStateOf(false) }

    var wifiInfo by remember {
        mutableStateOf(
            WifiDirectInfo(
                ssid = WifiDirectManager.currentSsid,
                password = WifiDirectManager.currentPass,
                qrPayload = WifiDirectManager.getWifiQrPayload(),
                isNativeP2p = false
            )
        )
    }
    var wifiStatusText by remember { mutableStateOf("") }
    var isWifiStarting by remember { mutableStateOf(false) }

    val defaultShareFile = remember {
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "share_video.mp4")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
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
                    "Nexora Hub • ${theme.name}",
                    color = theme.secondary,
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
            // Local HTTP Server Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, if (httpEnabled) theme.primary.copy(alpha = 0.8f) else theme.cardBorder)
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
                                    .background(if (httpEnabled) theme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = if (httpEnabled) theme.secondary else GrayText,
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
                                    if (httpEnabled) "Сервер активен на порту ${LocalHttpShareServer.currentPort}" else "Обмен файлами по Wi‑Fi через HTTP",
                                    color = if (httpEnabled) Color(0xFF4CAF50) else GrayText,
                                    fontSize = 12.sp
                                )
                            }

                            if (isStartingServer) {
                                CircularProgressIndicator(color = theme.primary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Switch(
                                    checked = httpEnabled,
                                    onCheckedChange = { checked ->
                                        isStartingServer = true
                                        serverError = null
                                        coroutineScope.launch {
                                            if (checked) {
                                                val started = withContext(Dispatchers.IO) {
                                                    LocalHttpShareServer.start(context, 8765)
                                                }
                                                httpEnabled = started
                                                httpAddress = LocalHttpShareServer.getServerUrl(context)
                                                if (!started) {
                                                    serverError = LocalHttpShareServer.lastErrorMessage ?: "Не удалось занять порт"
                                                    Toast.makeText(context, "Ошибка: $serverError", Toast.LENGTH_LONG).show()
                                                } else {
                                                    Toast.makeText(context, "Сервер запущен: $httpAddress", Toast.LENGTH_SHORT).show()
                                                }
                                            } else {
                                                withContext(Dispatchers.IO) {
                                                    LocalHttpShareServer.stop()
                                                }
                                                httpEnabled = false
                                                httpAddress = LocalHttpShareServer.getServerUrl(context)
                                                Toast.makeText(context, "Сервер остановлен", Toast.LENGTH_SHORT).show()
                                            }
                                            isStartingServer = false
                                        }
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = theme.primary,
                                        uncheckedThumbColor = GrayText,
                                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }

                        if (serverError != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "Ошибка: $serverError",
                                color = Color(0xFFFF5252),
                                fontSize = 12.sp
                            )
                        }

                        if (httpEnabled) {
                            Spacer(modifier = Modifier.height(14.dp))
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Black.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Адрес для браузера:", color = GrayText, fontSize = 11.sp)
                                        Text(
                                            httpAddress,
                                            color = theme.secondary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("HTTP address", httpAddress)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Адрес скопирован в буфер", Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Копировать",
                                            tint = theme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Wi-Fi Direct & QR Sharing Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.cardBorder)
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
                                    .background(theme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = theme.secondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Передача по QR и Wi‑Fi Direct",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Прямое подключение по QR с паролем от Wi‑Fi Direct",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                isWifiStarting = true
                                WifiDirectManager.startHotspotOrDirect(
                                    context = context,
                                    onStatus = { wifiStatusText = it },
                                    onComplete = { info ->
                                        wifiInfo = info
                                        isWifiStarting = false
                                        showDirectModal = true
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            if (isWifiStarting) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Включение Wi‑Fi Direct...", color = Color.Black, fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.WifiTethering, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Открыть QR подключения с паролем сети", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "НАВИГАЦИЯ И ИНСТРУМЕНТЫ",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                )
            }

            // Quick Restore Neon Theme Button Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            ThemeManager.resetToOriginalNeon(context)
                            Toast.makeText(context, "Оригинальное оформление Неон возвращено!", Toast.LENGTH_SHORT).show()
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color(0xFFFF8A00).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFF8A00).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFF8A00), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Вернуть оформление Неон (Оригинал)", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("Восстановить фирменный оранжево-золотой стиль Nexora", color = GrayText, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFFF8A00), modifier = Modifier.size(20.dp))
                    }
                }
            }

            // UI by Screenshot / Layout Cloner Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showScreenshotCloneDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(theme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "UI дизайн по скриншоту",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = theme.primary
                                ) {
                                    Text(
                                        "МАКЕТ",
                                        color = Color.Black,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                "Загрузите скриншот главного экрана и плеера — цвета и кнопки подстроятся точь-в-точь по макету",
                                color = GrayText,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                        Icon(
                            Icons.Default.ArrowForwardIos,
                            contentDescription = null,
                            tint = theme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // AI Design Studio Card
            item {
                HubActionCard(
                    icon = Icons.Default.AutoAwesome,
                    title = "ИИ Дизайн Студия",
                    subtitle = "Генерация уникального оформления и цветовых тем плеера",
                    onClick = { showAiStudioDialog = true }
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

    if (showScreenshotCloneDialog) {
        UiTemplateFromScreenshotDialog(
            onDismiss = { showScreenshotCloneDialog = false }
        )
    }

    if (showAiStudioDialog) {
        AiDesignStudioDialog(
            onDismiss = { showAiStudioDialog = false }
        )
    }

    // Modal with Wi-Fi Direct and File Download QR
    if (showDirectModal) {
        var tabIndex by remember { mutableIntStateOf(0) }
        val wifiQrBitmap = remember(wifiInfo.qrPayload) { generateQrCodeBitmap(wifiInfo.qrPayload) }
        val downloadUrl = remember(httpAddress) {
            LocalHttpShareServer.getDownloadUrl(context, defaultShareFile)
        }
        val fileQrBitmap = remember(downloadUrl) { generateQrCodeBitmap(downloadUrl) }

        AlertDialog(
            onDismissRequest = { showDirectModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiTethering, contentDescription = null, tint = theme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Wi‑Fi Direct & QR передача", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Tabs: Wi-Fi connect QR / File download QR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { tabIndex = 0 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tabIndex == 0) theme.primary else Color(0xFF252525)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "1. Пароль Wi‑Fi",
                                color = if (tabIndex == 0) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Button(
                            onClick = { tabIndex = 1 },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tabIndex == 1) theme.primary else Color(0xFF252525)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                "2. Скачать видео",
                                color = if (tabIndex == 1) Color.Black else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    if (tabIndex == 0) {
                        // Wi-Fi Connection QR
                        Image(
                            bitmap = wifiQrBitmap.asImageBitmap(),
                            contentDescription = "Wi-Fi QR Code",
                            modifier = Modifier.size(200.dp)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF252525),
                            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.3f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Наведите камеру любого телефона для автоподключения:", color = GrayText, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Имя сети (SSID):", color = Color.White, fontSize = 12.sp)
                                    Text(wifiInfo.ssid, color = theme.secondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Пароль Wi‑Fi:", color = Color.White, fontSize = 12.sp)
                                    Text(wifiInfo.password, color = theme.primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        // File Download QR
                        Image(
                            bitmap = fileQrBitmap.asImageBitmap(),
                            contentDescription = "File Download QR",
                            modifier = Modifier.size(200.dp)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF252525)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("После подключения к Wi‑Fi откройте ссылку или QR:", color = GrayText, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(downloadUrl, color = theme.secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showDirectModal = false },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectModal = false }) {
                    Text("Закрыть", color = GrayText)
                }
            },
            containerColor = theme.surface,
            shape = RoundedCornerShape(20.dp)
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
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
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
                    tint = theme.secondary,
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
