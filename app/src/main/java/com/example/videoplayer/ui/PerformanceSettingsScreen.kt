package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.player.AppPreferences
import com.example.videoplayer.player.MemoryMonitor
import com.example.videoplayer.player.VlcEngineConfigurator
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceSettingsScreen(
    onBack: () -> Unit,
    onNavigateToConsumption: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }

    var currentEngine by remember { mutableStateOf(AppPreferences.getRenderEngine(context)) }
    var hardwareAccel by remember { mutableStateOf(prefs.getBoolean("hw_accel", true)) }
    val hardwareProfile = remember(currentEngine) {
        VlcEngineConfigurator.getHardwareProfile(context, currentEngine)
    }
    var ramInfo by remember { mutableStateOf(MemoryMonitor.getRamInfo(context)) }
    var showConsumptionScreen by remember { mutableStateOf(false) }

    if (showConsumptionScreen) {
        ConsumptionScreen(onBack = { showConsumptionScreen = false })
        return
    }

    LaunchedEffect(Unit) {
        while (true) {
            ramInfo = MemoryMonitor.getRamInfo(context)
            delay(2000L)
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Производительность",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("performance_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = theme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = theme.background)
            )
        },
        containerColor = theme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Профиль оборудования",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Параметры применяются при следующем запуске видео",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        PerformanceInfoRow(
                            label = "Многопоточность CPU",
                            value = "${hardwareProfile.cpuCores} ядер / потоков"
                        )
                        PerformanceInfoRow(
                            label = "Аппаратный Vulkan",
                            value = if (hardwareProfile.isVulkanSupported) "Поддерживается" else "Не обнаружен"
                        )
                        PerformanceInfoRow(
                            label = "Активный конвейер",
                            value = when (currentEngine) {
                                AppPreferences.RenderEngine.AUTO ->
                                    if (hardwareProfile.isVulkanUsed) "Vulkan (Авто)" else "OpenGL ES 2.0 (Авто)"
                                AppPreferences.RenderEngine.VULKAN -> "Vulkan"
                                AppPreferences.RenderEngine.GLES2 -> "OpenGL ES 2.0"
                                AppPreferences.RenderEngine.SOFTWARE -> "Software CPU"
                            }
                        )
                    }
                }
            }

            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("performance_ram_card"),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Memory,
                                    contentDescription = null,
                                    tint = theme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Оперативная память (RAM)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(
                                text = "${ramInfo.usagePercent}%",
                                color = theme.primary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        LinearProgressIndicator(
                            progress = { (ramInfo.usagePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = theme.primary,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        PerformanceInfoRow(
                            label = "Занято / Всего",
                            value = "${ramInfo.usedMb} МБ / ${ramInfo.totalMb} МБ"
                        )
                        PerformanceInfoRow(
                            label = "Доступно",
                            value = "${ramInfo.availableMb} МБ"
                        )
                    }
                }
            }

            item {
                Text(
                    text = "ГРАФИЧЕСКИЙ ДВИЖОК (GPU / CPU)",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        AppPreferences.RenderEngine.entries.forEachIndexed { index, engine ->
                            val isSelected = currentEngine == engine
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .selectable(
                                        selected = isSelected,
                                        role = Role.RadioButton,
                                        onClick = {
                                            currentEngine = engine
                                            AppPreferences.setRenderEngine(context, engine)
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 12.dp)
                                    .testTag("performance_engine_${engine.value}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null,
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = theme.primary,
                                        unselectedColor = GrayText
                                    )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = engine.title,
                                        color = if (isSelected) Color.White else Color.LightGray,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = engine.description,
                                        color = GrayText,
                                        fontSize = 12.sp
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = theme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            if (index < AppPreferences.RenderEngine.entries.lastIndex) {
                                HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    text = "ДЕКОДИРОВАНИЕ И КЭШ",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = theme.surface,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = !hardwareAccel
                                    hardwareAccel = next
                                    prefs.edit().putBoolean("hw_accel", next).apply()
                                }
                                .padding(16.dp)
                                .testTag("performance_hw_accel_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Аппаратное декодирование",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Использовать аппаратный декодер LibVLC",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                            Switch(
                                checked = hardwareAccel,
                                onCheckedChange = {
                                    hardwareAccel = it
                                    prefs.edit().putBoolean("hw_accel", it).apply()
                                },
                                modifier = Modifier.testTag("performance_hw_accel_switch"),
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = theme.primary,
                                    uncheckedThumbColor = GrayText,
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Memory,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Потоки декодера и масштабирования",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "avcodec=${hardwareProfile.cpuCores}, swscale=${hardwareProfile.cpuCores}",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Буферизация LibVLC",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Локальные файлы: 300 мс • Сеть / поток: 1000 мс",
                                    color = GrayText,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                OutlinedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("performance_consumption_card")
                        .clickable {
                            if (onNavigateToConsumption != null) {
                                onNavigateToConsumption()
                            } else {
                                showConsumptionScreen = true
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = theme.surface),
                    border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Analytics,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Потребление ресурсов (Монитор)",
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Нагрузка по всем ${hardwareProfile.cpuCores} ядрам CPU и график RAM",
                                style = MaterialTheme.typography.bodySmall,
                                color = GrayText
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Открыть",
                            tint = GrayText
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PerformanceInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = GrayText,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
