package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.ai.AiDesignStudioDialog
import com.example.videoplayer.ui.ai.UiTemplateFromScreenshotDialog
import com.example.videoplayer.ui.terminal.TerminalConsoleOverlay
import com.example.videoplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val theme = LocalAppTheme.current
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }

    var hardwareAccel by remember { mutableStateOf(prefs.getBoolean("hw_accel", true)) }
    var backgroundPlay by remember { mutableStateOf(prefs.getBoolean("bg_play", false)) }
    var showBuffering by remember { mutableStateOf(prefs.getBoolean("show_buffering_status", false)) }
    var autoRotate by remember { mutableStateOf(prefs.getBoolean("auto_rotate", true)) }
    var showAiStudio by remember { mutableStateOf(false) }
    var showScreenshotDialog by remember { mutableStateOf(false) }
    var showTerminalConsole by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Настройки",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                Text(
                    "ВОСПРОИЗВЕДЕНИЕ",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            item {
                SettingsCard {
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Аппаратное декодирование",
                        subtitle = "Использовать аппаратный декодер LibVLC",
                        checked = hardwareAccel,
                        onCheckedChange = {
                            hardwareAccel = it
                            prefs.edit().putBoolean("hw_accel", it).apply()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchItem(
                        icon = Icons.Default.Settings,
                        title = "Фоновое воспроизведение",
                        subtitle = "Продолжать звук при блокировке экрана",
                        checked = backgroundPlay,
                        onCheckedChange = {
                            backgroundPlay = it
                            prefs.edit().putBoolean("bg_play", it).apply()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchItem(
                        icon = Icons.Default.ScreenRotation,
                        title = "Отображать буферизацию",
                        subtitle = "Показывать состояние буферизации во время воспроизведения",
                        checked = showBuffering,
                        onCheckedChange = {
                            showBuffering = it
                            prefs.edit().putBoolean("show_buffering_status", it).apply()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchItem(
                        icon = Icons.Default.PlayCircle,
                        title = "Автоповорот",
                        subtitle = "Переворачивать экран при смене ориентации",
                        checked = autoRotate,
                        onCheckedChange = {
                            autoRotate = it
                            prefs.edit().putBoolean("auto_rotate", it).apply()
                        }
                    )
                }
            }

            item {
                Text(
                    "ОФОРМЛЕНИЕ И ИИ СТИЛИ",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = theme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Текущий стиль: ${theme.name}",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    theme.description,
                                    color = GrayText,
                                    fontSize = 12.sp,
                                    maxLines = 2
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "ВЫБОР ДИЗАЙНА ПО УМОЛЧАНИЮ:",
                            color = theme.secondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Список ключевых дизайнов со скриншотов для выбора по умолчанию
                        val defaultDesignPresets = listOf(
                            ThemePresets.NexoraOriginalOrange,
                            ThemePresets.NexoraCyberScreenshot,
                            ThemePresets.Cyberpunk,
                            ThemePresets.DeepOled
                        )

                        defaultDesignPresets.forEach { preset ->
                            val isSelected = theme.id == preset.id ||
                                (preset.id == "nexora_orange_original" && theme.id == "nexora_neon") ||
                                (preset.id == "nexora_neon_screenshot" && theme.id == "nexora_screenshot")

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        ThemeManager.applyDefaultPreset(context, preset)
                                        android.widget.Toast.makeText(
                                            context,
                                            "Дизайн \"${preset.name}\" применен ко всему плееру!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) preset.primary.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.04f),
                                border = BorderStroke(
                                    if (isSelected) 1.5.dp else 1.dp,
                                    if (isSelected) preset.primary else Color.White.copy(alpha = 0.08f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(preset.primary)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = preset.name,
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                if (preset.id == "nexora_neon_screenshot" || preset.id == "nexora_screenshot") {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF00E5FF).copy(alpha = 0.25f)
                                                    ) {
                                                        Text(
                                                            "СКРИНШОТ НЕОН",
                                                            color = Color(0xFF00E5FF),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                } else if (preset.id == "nexora_orange_original" || preset.id == "nexora_neon") {
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFFFF7A00).copy(alpha = 0.25f)
                                                    ) {
                                                        Text(
                                                            "ОРИГИНАЛ VLC",
                                                            color = Color(0xFFFF9500),
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Black,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = preset.description,
                                                color = GrayText,
                                                fontSize = 11.sp,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            ThemeManager.applyDefaultPreset(context, preset)
                                        },
                                        colors = RadioButtonDefaults.colors(
                                            selectedColor = preset.primary,
                                            unselectedColor = Color.Gray
                                        )
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Кнопка возврата к оригинальному оформлению по умолчанию со скриншотов
                        OutlinedButton(
                            onClick = {
                                ThemeManager.resetToOriginalNeon(context)
                                android.widget.Toast.makeText(context, "Оформление со скриншотов установлено по умолчанию!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.5.dp, Color(0xFFFF7A00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = Color(0xFFFF7A00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вернуть оформление Неон (По скриншотам)", color = Color(0xFFFF7A00), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Прямая кнопка вызова терминала UI прямо из настроек
                        Button(
                            onClick = { showTerminalConsole = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.surface),
                            border = BorderStroke(1.dp, theme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Terminal, contentDescription = null, tint = theme.secondary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(">_ Открыть консоль терминала UI", color = theme.secondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = { showScreenshotDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.primary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("UI дизайн по скриншоту (макету)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = { showAiStudio = true },
                            border = BorderStroke(1.dp, theme.primary.copy(alpha = 0.6f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ИИ Студия: Сгенерировать текстом", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }
            }

            item {
                Text(
                    "О ПРИЛОЖЕНИИ",
                    color = theme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
            }

            item {
                SettingsCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = theme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                "Nexora Video Player",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Версия 1.1.0 (LibVLC 3.6.0 + AI Theme Engine)",
                                color = GrayText,
                                fontSize = 13.sp
                            )
                            Text(
                                "Современный мультимедийный проигрыватель",
                                color = GrayText,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (showScreenshotDialog) {
        UiTemplateFromScreenshotDialog(
            onDismiss = { showScreenshotDialog = false }
        )
    }

    if (showAiStudio) {
        AiDesignStudioDialog(
            onDismiss = { showAiStudio = false }
        )
    }

    TerminalConsoleOverlay(
        isVisible = showTerminalConsole,
        onClose = { showTerminalConsole = false }
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val theme = LocalAppTheme.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = theme.surface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val theme = LocalAppTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = theme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                color = GrayText,
                fontSize = 12.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = theme.primary,
                uncheckedThumbColor = GrayText,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
