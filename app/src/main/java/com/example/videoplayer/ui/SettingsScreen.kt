package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.ai.AiDesignStudioDialog
import com.example.videoplayer.ui.ai.UiTemplateFromScreenshotDialog
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

                        Spacer(modifier = Modifier.height(14.dp))

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

                        Spacer(modifier = Modifier.height(10.dp))

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

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedButton(
                            onClick = {
                                ThemeManager.resetToOriginalNeon(context)
                                android.widget.Toast.makeText(context, "Оригинальное оформление Неон возвращено!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            border = BorderStroke(1.dp, Color(0xFFFF8A00)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = Color(0xFFFF8A00), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вернуть оформление Неон (Оригинал)", color = Color(0xFFFF8A00), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
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
