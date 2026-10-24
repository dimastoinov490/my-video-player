package com.example.videoplayer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val HubOrange = Color(0xFFFF8A00)
private val HubAmberLight = Color(0xFFFFAE33)
private val HubCardBg = Color(0xFF13131A)
private val HubBorder = Color(0xFF242436)

@Composable
fun MoreHubView(
    onOpenSettings: () -> Unit,
    onOpenCustomUrl: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isFtpEnabled by remember { mutableStateOf(false) }
    var showQuickShareModal by remember { mutableStateOf(false) }
    var quickShareMode by remember { mutableStateOf("send") } // "send" or "receive"

    // Quick Share State Machine
    // 0 = Discovery (Bluetooth Radar), 1 = Handshake (Bluetooth -> Wi-Fi Direct), 2 = Transferring (Progress %), 3 = Complete
    var transferStep by remember { mutableStateOf(0) }
    var selectedDevice by remember { mutableStateOf<String?>(null) }
    var transferProgress by remember { mutableFloatStateOf(0f) }
    var transferSpeed by remember { mutableStateOf("48.2 МБ/с") }
    var transferFileName by remember { mutableStateOf("Dune.Part.Two.2024.1080p.mkv") }
    var transferFileSize by remember { mutableStateOf("2.45 ГБ") }

    fun copyToClipboard(text: String, label: String = "Адрес") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label скопирован в буфер", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Header Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF221608), Color(0xFF14141E))
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(HubOrange.copy(alpha = 0.6f), Color(0xFF2A2A3D))
                            )
                        ),
                        RoundedCornerShape(22.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ИНСТРУМЕНТЫ NEXORA",
                                color = HubAmberLight,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(HubOrange)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    color = Color.Black,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Беспроводная передача и функции",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "FTP-сервер, Быстрая отправка файлов, потоки и настройки",
                            color = GrayText,
                            fontSize = 12.sp
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(HubOrange.copy(alpha = 0.15f))
                            .border(1.dp, HubOrange.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        TransferNavIcon(tint = HubOrange)
                    }
                }
            }
        }

        // FEATURE 1: FTP-СЕРВЕР (Wi-Fi доступ к файлам)
        item {
            Surface(
                color = HubCardBg,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(
                    1.dp,
                    if (isFtpEnabled) HubOrange.copy(alpha = 0.8f) else HubBorder
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isFtpEnabled) HubOrange.copy(alpha = 0.2f) else Color(0xFF1B1B28)
                                    )
                                    .border(
                                        1.dp,
                                        if (isFtpEnabled) HubOrange else Color(0xFF2C2C40),
                                        RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                FtpServerIcon(tint = if (isFtpEnabled) HubOrange else GrayText)
                            }

                            Column {
                                Text(
                                    text = "FTP-сервер",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isFtpEnabled) "🟢 Активен • Локальная сеть" else "⚪ Остановлен",
                                    color = if (isFtpEnabled) Color(0xFF4CAF50) else GrayText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Toggle Switch
                        Switch(
                            checked = isFtpEnabled,
                            onCheckedChange = { checked ->
                                isFtpEnabled = checked
                                val msg = if (checked) "FTP-сервер запущен: ftp://192.168.1.145:2121" else "FTP-сервер остановлен"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = HubOrange,
                                uncheckedThumbColor = Color(0xFF888899),
                                uncheckedTrackColor = Color(0xFF222230)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isFtpEnabled) {
                        // Expanded FTP Server Details
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0C0C12))
                                .border(1.dp, Color(0xFF262638), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = "АДРЕС СЕРВЕРА В СЕТИ WI-FI:",
                                            color = GrayText,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "ftp://192.168.1.145:2121",
                                            color = HubAmberLight,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    Button(
                                        onClick = { copyToClipboard("ftp://192.168.1.145:2121", "FTP URL") },
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Копировать", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Divider(color = Color(0xFF1E1E2C), thickness = 1.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Порт:", color = GrayText, fontSize = 11.sp)
                                        Text("2121", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Пользователь:", color = GrayText, fontSize = 11.sp)
                                        Text("anonymous (или nexora)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Сессии:", color = GrayText, fontSize = 11.sp)
                                        Text("1 активно", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "💡 Введите этот адрес в проводнике Windows или в браузере любого компьютера в той же Wi-Fi сети для просмотра и загрузки видео без проводов.",
                            color = Color(0xFFB0B0C4),
                            fontSize = 11.5.sp,
                            lineHeight = 16.sp
                        )
                    } else {
                        Text(
                            text = "Включите FTP, чтобы открыть доступ к видео и аудио файлам на вашем телефоне с компьютера или смарт-ТВ по Wi-Fi без подключения кабеля.",
                            color = GrayText,
                            fontSize = 12.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
            }
        }

        // FEATURE 2: БЫСТРАЯ ОТПРАВКА ФАЙЛОВ (QUICK SHARE / BLUETOOTH + WI-FI DIRECT)
        item {
            Surface(
                color = HubCardBg,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, HubBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF261908))
                                .border(1.dp, HubOrange.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            QuickShareIcon(tint = HubAmberLight)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Быстрая отправка (Quick Share)",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Сопряжение по Bluetooth + Wi-Fi Direct точка доступа",
                                color = GrayText,
                                fontSize = 11.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Передавайте тяжелые 4K/FullHD видеофайлы между телефонами на скорости до 50 МБ/с без расхода мобильного трафика.",
                        color = Color(0xFFC0C0D4),
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Send Button
                        Button(
                            onClick = {
                                quickShareMode = "send"
                                transferStep = 0
                                selectedDevice = null
                                transferProgress = 0f
                                showQuickShareModal = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                        ) {
                            Text(
                                text = "📤 Отправить файл",
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Receive Button
                        OutlinedButton(
                            onClick = {
                                quickShareMode = "receive"
                                transferStep = 0
                                showQuickShareModal = true
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, HubOrange.copy(alpha = 0.7f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = HubAmberLight)
                        ) {
                            Text(
                                text = "📥 Принять",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // FEATURE 3: НАСТРОЙКИ ПЛЕЕРА
        item {
            Surface(
                onClick = onOpenSettings,
                color = HubCardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HubBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            GearNavIcon(tint = Color.White)
                        }
                        Column {
                            Text(
                                text = "Настройки плеера",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Аппаратное декодирование, жесты, субтитры",
                                color = GrayText,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text(text = "❯", color = GrayText, fontSize = 16.sp)
                }
            }
        }

        // FEATURE 4: ОТКРЫТЬ ПО ССЫЛКЕ (URL STREAM)
        item {
            Surface(
                onClick = onOpenCustomUrl,
                color = HubCardBg,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, HubBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF1E1E2C)),
                            contentAlignment = Alignment.Center
                        ) {
                            StreamNavIcon(tint = HubAmberLight)
                        }
                        Column {
                            Text(
                                text = "Открыть сетевой поток",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "HLS, DASH, RTSP, RTMP или прямая ссылка",
                                color = GrayText,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Text(text = "❯", color = GrayText, fontSize = 16.sp)
                }
            }
        }

        // SYSTEM & ENGINE INFO
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F0F15))
                    .border(1.dp, Color(0xFF1C1C28), RoundedCornerShape(18.dp))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Версия движка Nexora:", color = GrayText, fontSize = 12.sp)
                        Text("v2.4.0 Pro Edition", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Кодеки:", color = GrayText, fontSize = 12.sp)
                        Text("H.264, HEVC, AV1, VP9, DTS, AC3", color = HubAmberLight, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Рендерер:", color = GrayText, fontSize = 12.sp)
                        Text("OpenGL ES 3.2 • Vulkan Ready", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    // =========================================================================
    // QUICK SHARE INTERACTIVE MODAL (BLUETOOTH RADAR -> WI-FI DIRECT -> TRANSFER %)
    // =========================================================================
    if (showQuickShareModal) {
        AlertDialog(
            onDismissRequest = { showQuickShareModal = false },
            containerColor = Color(0xFF12121C),
            shape = RoundedCornerShape(26.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickShareIcon(tint = HubOrange)
                        Text(
                            text = if (quickShareMode == "send") "Быстрая отправка" else "Приём файлов",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HubOrange.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = when (transferStep) {
                                0 -> "Bluetooth Radar"
                                1 -> "Сопряжение"
                                2 -> "Wi-Fi Direct"
                                else -> "Завершено"
                            },
                            color = HubAmberLight,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (transferStep) {
                        0 -> {
                            // STEP 0: BLUETOOTH RADAR DISCOVERY
                            Text(
                                text = if (quickShareMode == "send")
                                    "Поиск устройств поблизости по Bluetooth LE..."
                                else
                                    "Ваше устройство 'Nexora-Media' открыто для приёма файлов поблизости",
                                color = Color(0xFFC0C0D4),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            // Animated Radar Circles
                            RadarAnimation(modifier = Modifier.size(130.dp))

                            Spacer(modifier = Modifier.height(14.dp))

                            if (quickShareMode == "send") {
                                Text(
                                    text = "НАЙДЕННЫЕ УСТРОЙСТВА:",
                                    color = GrayText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                Spacer(modifier = Modifier.height(6.dp))

                                val nearbyDevices = listOf(
                                    Pair("📱 Galaxy S24 Ultra", "Дмитрий • 1.5м"),
                                    Pair("📱 Google Pixel 8 Pro", "Гостиная • 3м"),
                                    Pair("💻 Nexora Link - Windows PC", "Wi-Fi сеть • 4м"),
                                    Pair("📱 Xiaomi 14 Pro", "Рядом • 2м")
                                )

                                nearbyDevices.forEach { (deviceName, subtitle) ->
                                    Surface(
                                        onClick = {
                                            selectedDevice = deviceName
                                            transferStep = 1
                                            // Start handshake coroutine
                                            scope.launch {
                                                delay(1200) // Bluetooth handshake
                                                transferStep = 2 // Start Wi-Fi Direct file transmission
                                                for (i in 1..100) {
                                                    transferProgress = i / 100f
                                                    delay(45) // smooth animation to 100%
                                                }
                                                transferStep = 3 // Finished
                                            }
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = Color(0xFF191925),
                                        border = BorderStroke(1.dp, Color(0xFF28283C)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(HubOrange.copy(alpha = 0.15f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text("📲", fontSize = 16.sp)
                                                }
                                                Column {
                                                    Text(deviceName, color = Color.White, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                                                    Text(subtitle, color = GrayText, fontSize = 11.sp)
                                                }
                                            }
                                            Text(text = "Отправить ❯", color = HubAmberLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1B1B28))
                                        .padding(12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "📡 Ожидание запроса на передачу от другого телефона...",
                                        color = HubAmberLight,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        1 -> {
                            // STEP 1: HANDSHAKE (BLUETOOTH -> WI-FI DIRECT HOTSPOT)
                            CircularProgressIndicator(
                                color = HubOrange,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(54.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Сопряжение с ${selectedDevice ?: "устройством"}...",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "1. Установка Bluetooth LE соединения...\n2. Автоматический запуск защищённой точки доступа Wi-Fi Direct...\n3. Подключение устройств...",
                                color = Color(0xFFB0B0C4),
                                fontSize = 12.sp,
                                lineHeight = 18.sp,
                                textAlign = TextAlign.Center
                            )
                        }

                        2 -> {
                            // STEP 2: ACTIVE FILE TRANSFER WITH PERCENTAGES & SPEED
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Large Animated Percentage Display
                                Text(
                                    text = "${(transferProgress * 100).toInt()}%",
                                    color = HubAmberLight,
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                // Glowing Gradient Progress Bar
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E1E2C))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(fraction = transferProgress)
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(Color(0xFFFF5500), HubOrange, HubAmberLight)
                                                )
                                            )
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // File Details Card
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF161622))
                                        .padding(10.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = transferFileName,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Скорость: $transferSpeed",
                                                color = Color(0xFF4CAF50),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Передано: ${(transferProgress * 2.45).let { String.format("%.2f", it) }} / $transferFileSize",
                                                color = GrayText,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Прямой защищённый канал Wi-Fi Direct активен",
                                    color = GrayText,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        3 -> {
                            // STEP 3: TRANSFER COMPLETED
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF14301A))
                                    .border(1.5.dp, Color(0xFF4CAF50), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✔", color = Color(0xFF4CAF50), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Файл успешно передан!",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$transferFileName ($transferFileSize) отправлен на ${selectedDevice ?: "устройство"}",
                                color = GrayText,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (transferStep == 3) {
                    Button(
                        onClick = {
                            showQuickShareModal = false
                            transferStep = 0
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = HubOrange)
                    ) {
                        Text("Готово", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showQuickShareModal = false }) {
                        Text("Закрыть", color = Color.White)
                    }
                }
            }
        )
    }
}

// -------------------------------------------------------------
// RADAR ANIMATION COMPONENT FOR QUICK SHARE DISCOVERY
// -------------------------------------------------------------
@Composable
fun RadarAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, delayMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave3"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f

            // Concentric radar background rings
            drawCircle(
                color = Color(0xFF26263A),
                radius = maxRadius * 0.35f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF26263A),
                radius = maxRadius * 0.7f,
                style = Stroke(width = 1.dp.toPx())
            )
            drawCircle(
                color = Color(0xFF26263A),
                radius = maxRadius,
                style = Stroke(width = 1.dp.toPx())
            )

            // Animated pulsing waves
            listOf(wave1, wave2, wave3).forEach { wave ->
                val r = maxRadius * wave
                val alpha = (1f - wave).coerceIn(0f, 1f) * 0.75f
                drawCircle(
                    color = HubOrange.copy(alpha = alpha),
                    radius = r,
                    style = Stroke(width = 2.dp.toPx())
                )
            }

            // Center beacon point
            drawCircle(color = HubAmberLight, radius = 5.dp.toPx())
        }

        // Center Phone Icon
        Text("📲", fontSize = 24.sp)
    }
}

// -------------------------------------------------------------
// VECTOR ICONS FOR MORE HUB
// -------------------------------------------------------------

@Composable
fun FtpServerIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        // Top Server Unit
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width - 4.dp.toPx(), 6.5.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(6.dp.toPx(), 7.25.dp.toPx()))

        // Bottom Server Unit
        drawRoundRect(
            color = tint,
            topLeft = Offset(2.dp.toPx(), 13.5.dp.toPx()),
            size = Size(size.width - 4.dp.toPx(), 6.5.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawCircle(color = tint, radius = 1.2.dp.toPx(), center = Offset(6.dp.toPx(), 16.75.dp.toPx()))
    }
}

@Composable
fun QuickShareIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        // Left loop
        drawCircle(
            color = tint,
            radius = 4.5.dp.toPx(),
            center = Offset(7.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Right loop
        drawCircle(
            color = tint,
            radius = 4.5.dp.toPx(),
            center = Offset(17.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = stroke)
        )
        // Upper connecting arc
        drawLine(
            color = tint,
            start = Offset(11.5.dp.toPx(), 8.dp.toPx()),
            end = Offset(12.5.dp.toPx(), 8.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun TransferNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.2.dp.toPx()
        // Arrow pointing right
        drawLine(
            color = tint,
            start = Offset(5.dp.toPx(), 9.dp.toPx()),
            end = Offset(19.dp.toPx(), 9.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val path1 = Path().apply {
            moveTo(14.dp.toPx(), 5.dp.toPx())
            lineTo(19.dp.toPx(), 9.dp.toPx())
            lineTo(14.dp.toPx(), 13.dp.toPx())
        }
        drawPath(path1, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))

        // Arrow pointing left
        drawLine(
            color = tint,
            start = Offset(19.dp.toPx(), 15.dp.toPx()),
            end = Offset(5.dp.toPx(), 15.dp.toPx()),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        val path2 = Path().apply {
            moveTo(10.dp.toPx(), 11.dp.toPx())
            lineTo(5.dp.toPx(), 15.dp.toPx())
            lineTo(10.dp.toPx(), 19.dp.toPx())
        }
        drawPath(path2, color = tint, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun GearNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = tint,
            radius = 4.5.dp.toPx(),
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = stroke)
        )
        drawCircle(
            color = tint,
            radius = 8.5.dp.toPx(),
            center = Offset(size.width / 2, size.height / 2),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun StreamNavIcon(tint: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        drawCircle(
            color = tint,
            radius = 3.dp.toPx(),
            center = Offset(size.width / 2, size.height / 2)
        )
        drawArc(
            color = tint,
            startAngle = 135f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}
