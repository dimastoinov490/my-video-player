package com.example.videoplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.videoplayer.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onThemeChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("video_prefs", Context.MODE_PRIVATE) }

    var hardwareAccel by remember { mutableStateOf(prefs.getBoolean("hw_accel", true)) }
    var backgroundPlay by remember { mutableStateOf(prefs.getBoolean("bg_play", false)) }
    var showBuffering by remember { mutableStateOf(prefs.getBoolean("show_buffering_status", false)) }
    var autoRotate by remember { mutableStateOf(prefs.getBoolean("auto_rotate", true)) }
    var currentTheme by remember {
        val saved = prefs.getBoolean("app_dark_theme", true)
        mutableStateOf(if (saved) "Ночной Неон" else "Светлый Сет")
    }

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
                            tint = NeonOrange
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
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
                    color = NeonOrange,
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
                        icon = Icons.Default.PlayCircle,
                        title = "Отображать буферизацию",
                        subtitle = "Индикатор процентов кэша во время проигрывания",
                        checked = showBuffering,
                        onCheckedChange = {
                            showBuffering = it
                            prefs.edit().putBoolean("show_buffering_status", it).apply()
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    SettingsSwitchItem(
                        icon = Icons.Default.ScreenRotation,
                        title = "Автоповорот в полноэкранном",
                        subtitle = "Автоматически поворачивать в альбомный режим",
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
                    "ИНТЕРФЕЙС И ОФОРМЛЕНИЕ",
                    color = NeonOrange,
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
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = NeonOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Цветовой стиль",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                currentTheme,
                                color = GrayText,
                                fontSize = 13.sp
                            )
                        }
                        TextButton(
                            onClick = {
                                val nextDark = prefs.getBoolean("app_dark_theme", true).not()
                                currentTheme = if (nextDark) "Ночной Неон" else "Светлый Сет"
                                prefs.edit().putBoolean("app_dark_theme", nextDark).apply()
                                onThemeChanged(nextDark)
                            }
                        ) {
                            Text("Изменить", color = GoldenYellowLight)
                        }
                    }
                }
            }

            item {
                Text(
                    "О ПРИЛОЖЕНИИ",
                    color = NeonOrange,
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
                            tint = NeonOrange,
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
                                "Версия 1.0.0 (LibVLC 3.6.0)",
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
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceCardColor
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NeonOrange,
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
                checkedTrackColor = NeonOrange,
                uncheckedThumbColor = GrayText,
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
            )
        )
    }
}
