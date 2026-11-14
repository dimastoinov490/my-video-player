package com.example.videoplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.player.CpuMonitor
import com.example.videoplayer.player.MemoryMonitor
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsumptionScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    var ramInfo by remember { mutableStateOf(MemoryMonitor.getRamInfo(context)) }
    val coreUsages by CpuMonitor.coreUsageFlow(1000L).collectAsState(initial = emptyList())

    // Периодическое обновление информации об ОЗУ каждую секунду
    LaunchedEffect(Unit) {
        while (true) {
            ramInfo = MemoryMonitor.getRamInfo(context)
            delay(1000L)
        }
    }

    // Общая загрузка CPU как среднее арифметическое всех ядер
    val avgCpuUsage = if (coreUsages.isNotEmpty()) {
        coreUsages.sumOf { it.usagePercent } / coreUsages.size
    } else {
        0
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Потребление ресурсов (Монитор)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("consumption_back_button")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. БЛОК ОПЕРАТИВНОЙ ПАМЯТИ (RAM)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("consumption_ram_card"),
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Оперативная память (RAM)",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${ramInfo.usagePercent}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (ramInfo.usagePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = theme.primary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Занято: ${ramInfo.usedMb} МБ",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                        Text(
                            text = "Доступно: ${ramInfo.availableMb} МБ",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                        Text(
                            text = "Всего: ${ramInfo.totalMb} МБ",
                            style = MaterialTheme.typography.bodySmall,
                            color = GrayText
                        )
                    }
                }
            }

            // 2. БЛОК ОБЩЕЙ ЗАГРУЗКИ ПРОЦЕССОРА (CPU)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("consumption_cpu_total_card"),
                shape = RoundedCornerShape(16.dp),
                color = theme.surface,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = theme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Общая загрузка CPU",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "$avgCpuUsage%",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    LinearProgressIndicator(
                        progress = { (avgCpuUsage / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = theme.secondary,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                }
            }

            // 3. БЛОК ДЕТАЛИЗАЦИИ ПО ВСЕМ ЯДРАМ
            val coreLabelCount = if (coreUsages.isNotEmpty()) coreUsages.size else Runtime.getRuntime().availableProcessors()
            Text(
                text = "Нагрузка по всем $coreLabelCount ядрам CPU",
                style = MaterialTheme.typography.titleMedium,
                color = theme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                coreUsages.forEach { core ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("consumption_core_${core.coreId}"),
                        shape = RoundedCornerShape(12.dp),
                        color = theme.surface,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Column(modifier = Modifier.width(84.dp)) {
                                Text(
                                    text = "Ядро #${core.coreId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                                if (core.freqMhz != null) {
                                    Text(
                                        text = "${core.freqMhz} МГц",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = GrayText
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (core.usagePercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = theme.primary,
                                trackColor = Color.White.copy(alpha = 0.1f)
                            )

                            Text(
                                text = "${core.usagePercent}%",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(44.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
