package com.example.videoplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.service.LocalFtpServer
import com.example.videoplayer.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val HubOrange = Color(0xFFFF8800)
val HubAmberLight = Color(0xFFFFB300)
val HubCardBg = Color(0xFF1E1E1E)
val HubBorder = Color(0x33FF8800)

@Composable
fun MoreHubView(
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isFtpEnabled by remember { mutableStateOf(LocalFtpServer.isRunning) }
    var ftpAddress by remember {
        mutableStateOf("ftp://${LocalFtpServer.getLocalIpAddress()}:${LocalFtpServer.port}")
    }

    // Quick share state
    var showQuickShareModal by remember { mutableStateOf(false) }
    var quickShareMode by remember { mutableStateOf("send") } // "send" or "receive"
    var transferStep by remember { mutableStateOf(1) } // 1: Bluetooth Scan, 2: Pairing, 3: Wi-Fi Direct Hotspot, 4: Transferring, 5: Done
    var selectedDevice by remember { mutableStateOf("Pixel 8 Pro") }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var transferSpeed by remember { mutableStateOf("24.5 МБ/с") }
    var transferFileName by remember { mutableStateOf("Video_2026_4K.mp4") }
    var transferFileSize by remember { mutableStateOf("1.4 ГБ") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header
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
            // FTP Server Card
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    color = HubCardBg,
                    border = BorderStroke(
                        1.dp,
                        if (isFtpEnabled) HubOrange.copy(alpha = 0.8f) else HubBorder
                    )
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
                                    .background(
                                        if (isFtpEnabled) HubOrange.copy(alpha = 0.2f)
                                        else Color.White.copy(alpha = 0.06f)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = if (isFtpEnabled) HubAmberLight else GrayText,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Локальный FTP-сервер",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    if (isFtpEnabled) "Сервер активен в локальной сети"
                                    else "Доступ к файлам устройства по Wi-Fi",
                                    color = if (isFtpEnabled) Color(0xFF4CAF50) else GrayText,
                                    fontSize = 12.sp
                                )
                            }

                            Switch(
                                checked = isFtpEnabled,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val started = LocalFtpServer.start(context, 2121)
                                        isFtpEnabled = started
                                        ftpAddress = "ftp://${LocalFtpServer.getLocalIpAddress()}:${LocalFtpServer.port}"
                                        Toast.makeText(
                                            context,
                                            if (started) "FTP-сервер запущен" else "Ошибка запуска FTP",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        LocalFtpServer.stop()
                                        isFtpEnabled = false
                                        Toast.makeText(context, "FTP-сервер остановлен", Toast.LENGTH_SHORT).show()
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

                        if (isFtpEnabled) {
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
                                        Text(
                                            "Адрес подключения:",
                                            color = GrayText,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            ftpAddress,
                                            color = HubAmberLight,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("FTP Address", ftpAddress)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "Адрес скопирован в буфер", Toast.LENGTH_SHORT).show()
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

            // Quick Share Card
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
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = HubAmberLight,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Быстрая отправка файлов",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Сопряжение по Bluetooth + Wi-Fi Hotspot",
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
                                    quickShareMode = "send"
                                    transferStep = 1
                                    transferProgress = 0f
                                    showQuickShareModal = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Отправить", color = Color.Black, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    quickShareMode = "receive"
                                    transferStep = 1
                                    transferProgress = 0f
                                    showQuickShareModal = true
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HubAmberLight),
                                border = BorderStroke(1.dp, HubOrange),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = null,
                                    tint = HubAmberLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Принять", color = HubAmberLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Other Hub actions
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

    // Quick Share Modal Dialog
    if (showQuickShareModal) {
        val scope = rememberCoroutineScope()

        AlertDialog(
            onDismissRequest = { showQuickShareModal = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = HubOrange,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        if (quickShareMode == "send") "Быстрая отправка" else "Приём файлов",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (transferStep) {
                        1 -> {
                            // Step 1: Bluetooth Discovery / Radar
                            RadarAnimation()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Поиск ближайших устройств по Bluetooth...",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Убедитесь, что на втором телефоне включен Bluetooth",
                                color = GrayText,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    transferStep = 2
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Найдено устройство: $selectedDevice", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }

                        2 -> {
                            // Step 2: Pairing & Hotspot Handshake
                            CircularProgressIndicator(color = HubOrange, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Сопряжение с $selectedDevice...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Включение безопасной точки доступа Wi-Fi...",
                                color = HubAmberLight,
                                fontSize = 12.sp
                            )
                            LaunchedEffect(Unit) {
                                delay(1200)
                                transferStep = 3
                            }
                        }

                        3 -> {
                            // Step 3: Direct Wi-Fi Connection Established
                            Icon(
                                imageVector = Icons.Default.WifiTethering,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Wi-Fi Direct подключен!",
                                color = Color(0xFF4CAF50),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Начало высокоскоростной передачи...",
                                color = GrayText,
                                fontSize = 12.sp
                            )
                            LaunchedEffect(Unit) {
                                delay(800)
                                transferStep = 4
                            }
                        }

                        4 -> {
                            // Step 4: Transfer in progress with %
                            LaunchedEffect(Unit) {
                                while (transferProgress < 1f) {
                                    delay(80)
                                    transferProgress += 0.03f
                                }
                                transferProgress = 1f
                                transferStep = 5
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(transferFileName, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                Text("${(transferProgress * 100).toInt()}%", color = HubAmberLight, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            LinearProgressIndicator(
                                progress = { transferProgress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp)),
                                color = HubOrange,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Скорость: $transferSpeed", color = GrayText, fontSize = 12.sp)
                                Text("Размер: $transferFileSize", color = GrayText, fontSize = 12.sp)
                            }
                        }

                        5 -> {
                            // Step 5: Transfer Complete
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "Передача успешно завершена!",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Файл сохранён в памяти устройства",
                                color = GrayText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (transferStep == 5) {
                    Button(
                        onClick = { showQuickShareModal = false },
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickShareModal = false }) {
                    Text("Закрыть", color = GrayText)
                }
            },
            containerColor = DarkBackground
        )
    }
}

@Composable
fun RadarAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, delayMillis = 600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "w2"
    )

    Box(
        modifier = modifier
            .size(110.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val center = this.center
            val maxRadius = size.minDimension / 2

            // outer wave 1
            drawCircle(
                color = HubOrange.copy(alpha = (1f - wave1) * 0.4f),
                radius = maxRadius * wave1
            )
            // outer wave 2
            drawCircle(
                color = HubAmberLight.copy(alpha = (1f - wave2) * 0.4f),
                radius = maxRadius * wave2
            )
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(HubOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.BluetoothSearching,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(24.dp)
            )
        }
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
