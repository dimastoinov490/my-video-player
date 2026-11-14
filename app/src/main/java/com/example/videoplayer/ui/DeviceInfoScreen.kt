package com.example.videoplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.player.DeviceInfoHelper
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.LocalAppTheme
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    var info by remember { mutableStateOf(DeviceInfoHelper.getDetails(context)) }

    LaunchedEffect(Unit) {
        while (true) {
            info = DeviceInfoHelper.getDetails(context)
            delay(3000L)
        }
    }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Информация о телефоне",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("device_info_back_button")
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
            // 1. Блок: Устройство и ОС
            InfoCategoryCard(
                title = "Устройство и Система",
                testTag = "device_section_system"
            ) {
                InfoRow(label = "Производитель", value = info.manufacturer)
                InfoRow(label = "Модель", value = info.model)
                InfoRow(label = "Версия Android", value = "${info.androidVersion} (API ${info.sdkLevel})")
                InfoRow(label = "Патч безопасности", value = info.securityPatch)
                InfoRow(label = "Сборка системы", value = info.buildId)
            }

            // 2. Блок: Экран
            InfoCategoryCard(
                title = "Дисплей",
                testTag = "device_section_display"
            ) {
                InfoRow(label = "Разрешение", value = info.screenResolution)
                InfoRow(label = "Плотность пикселей", value = info.screenDensity)
                InfoRow(label = "Частота обновления", value = "${info.refreshRate.toInt()} Гц")
            }

            // 3. Блок: Батарея
            InfoCategoryCard(
                title = "Батарея (Аккумулятор)",
                testTag = "device_section_battery"
            ) {
                InfoRow(label = "Заряд", value = "${info.batteryLevel}%")
                InfoRow(label = "Температура", value = "${info.batteryTemp} °C")
                InfoRow(label = "Напряжение", value = "${info.batteryVoltage} В")
                InfoRow(label = "Технология", value = info.batteryTech)
            }

            // 4. Блок: Накопитель (ПЗУ)
            InfoCategoryCard(
                title = "Внутренняя память (ПЗУ)",
                testTag = "device_section_storage"
            ) {
                InfoRow(label = "Всего памяти", value = String.format(Locale.US, "%.2f ГБ", info.storageTotalGb))
                InfoRow(label = "Свободно", value = String.format(Locale.US, "%.2f ГБ", info.storageFreeGb))
            }
        }
    }
}

@Composable
fun InfoCategoryCard(
    title: String,
    testTag: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalAppTheme.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (testTag.isNotEmpty()) Modifier.testTag(testTag) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            content()
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = GrayText
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}
