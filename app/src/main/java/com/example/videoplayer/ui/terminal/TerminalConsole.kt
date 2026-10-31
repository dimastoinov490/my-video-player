@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.videoplayer.ui.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.theme.ButtonConfig
import com.example.videoplayer.ui.theme.DynamicPlayerConfig
import com.example.videoplayer.ui.theme.ThemeConfigManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ConsoleLogEntry(
    val timestamp: String,
    val text: String,
    val type: LogType
)

enum class LogType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
    COMMAND
}

@Composable
fun TerminalConsoleOverlay(
    isVisible: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return

    BackHandler { onClose() }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var commandInput by remember { mutableStateOf("") }
    var jsonEditorContent by remember { mutableStateOf(ThemeConfigManager.currentConfig.toJson(indent = 2)) }
    var activeTab by remember { mutableIntStateOf(0) } // 0: Terminal Log & CLI, 1: Live JSON Editor

    val logs = remember {
        mutableStateListOf(
            ConsoleLogEntry(
                timestamp = currentTimeFormatted(),
                text = "NEXORA TERMINAL PIPELINE v2.4 (BUILD 2026)",
                type = LogType.SUCCESS
            ),
            ConsoleLogEntry(
                timestamp = currentTimeFormatted(),
                text = "Инициализирован движок динамического UI без пересборки APK.",
                type = LogType.INFO
            ),
            ConsoleLogEntry(
                timestamp = currentTimeFormatted(),
                text = "Введите 'help' для списка команд или используйте кнопки быстрого запуска.",
                type = LogType.INFO
            )
        )
    }

    fun addLog(text: String, type: LogType = LogType.INFO) {
        logs.add(ConsoleLogEntry(currentTimeFormatted(), text, type))
        coroutineScope.launch {
            if (logs.isNotEmpty()) {
                listState.animateScrollToItem(logs.size - 1)
            }
        }
    }

    fun executeCommand(rawCommand: String) {
        val trimmed = rawCommand.trim()
        if (trimmed.isBlank()) return

        addLog("> $trimmed", LogType.COMMAND)
        commandInput = ""

        val parts = trimmed.split("\\s+".toRegex())
        val cmd = parts[0].lowercase()

        when (cmd) {
            "help" -> {
                addLog("Доступные команды терминала:", LogType.INFO)
                addLog("  help                 - справка по командам", LogType.INFO)
                addLog("  preset neon          - неоновый стиль (#9D00FF, #00E5FF, темный фон #05050D)", LogType.INFO)
                addLog("  preset amoled        - ультрачерный OLED с бирюзовым акцентом", LogType.INFO)
                addLog("  preset minimal       - скрыть второстепенные кнопки", LogType.INFO)
                addLog("  yolo | import_yolo   - автоматическая загрузка JSON из офлайн-детектора", LogType.INFO)
                addLog("  toggle <btn>         - переключить видимость кнопки (pip, cast, repeat, etc.)", LogType.INFO)
                addLog("  set <key> <value>    - изменить цвет или размер (например: set primary #00E5FF)", LogType.INFO)
                addLog("  export               - показать и скопировать текущий JSON в буфер", LogType.INFO)
                addLog("  apply <json>         - применить JSON напрямую", LogType.INFO)
                addLog("  clear                - очистить экран терминала", LogType.INFO)
            }

            "clear" -> {
                logs.clear()
                addLog("Терминал очищен.", LogType.INFO)
            }

            "preset" -> {
                val presetName = parts.getOrNull(1)?.lowercase() ?: "neon"
                when (presetName) {
                    "screenshot", "default" -> {
                        val screenshotCfg = DynamicPlayerConfig.screenshotPreset()
                        ThemeConfigManager.applyConfig(screenshotCfg, context)
                        jsonEditorContent = screenshotCfg.toJson(2)
                        addLog("Применен оригинальный пресет со скриншота (Неон + луч + градиент).", LogType.SUCCESS)
                    }
                    "orange", "amber" -> {
                        val orangeCfg = DynamicPlayerConfig.neonOrangePreset()
                        ThemeConfigManager.applyConfig(orangeCfg, context)
                        jsonEditorContent = orangeCfg.toJson(2)
                        addLog("Применен янтарный пресет Nexora Orange.", LogType.SUCCESS)
                    }
                    "neon" -> {
                        ThemeConfigManager.resetToNeon(context)
                        jsonEditorContent = ThemeConfigManager.currentConfig.toJson(2)
                        addLog("Успешно применен неоновый пресет: фон #05050D, сиреневый #9D00FF, голубой #00E5FF.", LogType.SUCCESS)
                    }
                    "amoled" -> {
                        val amoled = DynamicPlayerConfig.oledPreset()
                        ThemeConfigManager.applyConfig(amoled, context)
                        jsonEditorContent = amoled.toJson(2)
                        addLog("Применен пресет AMOLED Pitch Black.", LogType.SUCCESS)
                    }
                    "minimal" -> {
                        val minCfg = ThemeConfigManager.currentConfig.copy(
                            castButton = ButtonConfig(visible = false),
                            subtitlesListButton = ButtonConfig(visible = false),
                            pipButton = ButtonConfig(visible = false),
                            moreMenuButton = ButtonConfig(visible = false)
                        )
                        ThemeConfigManager.applyConfig(minCfg, context)
                        jsonEditorContent = minCfg.toJson(2)
                        addLog("Включен минималистичный режим: второстепенные кнопки скрыты.", LogType.SUCCESS)
                    }
                    else -> addLog("Неизвестный пресет: $presetName. Доступно: screenshot, neon, orange, amoled, minimal", LogType.WARNING)
                }
            }

            "yolo", "import_yolo" -> {
                // Симуляция прямого пайплайна передачи детекции от офлайн YOLO-детектора
                addLog("Чтение выходного потока офлайн-детектора YOLO...", LogType.INFO)
                val mockYoloJson = """
                    {
                      "model": "YOLO-UI-Mobile-v3",
                      "confidence": 0.94,
                      "detected_classes": [
                        "logo", "cast", "big_play", "subtitles", "repeat", "prev", "play_pause", "next", "pip", "more"
                      ],
                      "palette": {
                        "primary_color": "#9D00FF",
                        "secondary_color": "#00E5FF",
                        "background_color": "#05050D"
                      }
                    }
                """.trimIndent()
                val result = ThemeConfigManager.applyYoloJson(mockYoloJson, context)
                result.onSuccess { cfg ->
                    jsonEditorContent = cfg.toJson(2)
                    addLog("JSON от детектора YOLO успешно обработан пайплайном и применен к UI плеера!", LogType.SUCCESS)
                }.onFailure { err ->
                    addLog("Ошибка парсинга потока YOLO: ${err.message}", LogType.ERROR)
                }
            }

            "toggle" -> {
                val target = parts.getOrNull(1)?.lowercase()
                if (target == null) {
                    addLog("Использование: toggle <logo|cast|pip|subtitles|repeat|prev|next|more|center_play>", LogType.WARNING)
                } else {
                    val cur = ThemeConfigManager.currentConfig
                    val updated = when (target) {
                        "logo" -> cur.copy(logo = cur.logo.copy(visible = !cur.logo.visible))
                        "cast" -> cur.copy(castButton = cur.castButton.copy(visible = !cur.castButton.visible))
                        "pip" -> cur.copy(pipButton = cur.pipButton.copy(visible = !cur.pipButton.visible))
                        "subtitles", "subs" -> cur.copy(subtitlesListButton = cur.subtitlesListButton.copy(visible = !cur.subtitlesListButton.visible))
                        "repeat", "loop" -> cur.copy(repeatButton = cur.repeatButton.copy(visible = !cur.repeatButton.visible))
                        "prev" -> cur.copy(prevButton = cur.prevButton.copy(visible = !cur.prevButton.visible))
                        "next" -> cur.copy(nextButton = cur.nextButton.copy(visible = !cur.nextButton.visible))
                        "more", "menu" -> cur.copy(moreMenuButton = cur.moreMenuButton.copy(visible = !cur.moreMenuButton.visible))
                        "center_play", "big_play" -> cur.copy(bigPlayButton = cur.bigPlayButton.copy(visible = !cur.bigPlayButton.visible))
                        else -> null
                    }
                    if (updated != null) {
                        ThemeConfigManager.applyConfig(updated, context)
                        jsonEditorContent = updated.toJson(2)
                        addLog("Состояние кнопки '$target' изменено.", LogType.SUCCESS)
                    } else {
                        addLog("Неизвестная кнопка '$target'.", LogType.WARNING)
                    }
                }
            }

            "export" -> {
                val json = ThemeConfigManager.currentConfig.toJson(2)
                addLog("Текущий JSON конфигурации плеера:", LogType.INFO)
                addLog(json, LogType.SUCCESS)
                copyToClipboard(context, json)
                Toast.makeText(context, "JSON скопирован в буфер обмена!", Toast.LENGTH_SHORT).show()
            }

            "apply" -> {
                val jsonPayload = trimmed.removePrefix("apply").trim()
                if (jsonPayload.isBlank()) {
                    addLog("Использование: apply <валидный JSON-текст>", LogType.WARNING)
                } else {
                    val res = ThemeConfigManager.applyJson(jsonPayload, context)
                    res.onSuccess {
                        jsonEditorContent = it.toJson(2)
                        addLog("JSON успешно распарсен и мгновенно применен к UI плеера!", LogType.SUCCESS)
                    }.onFailure { err ->
                        addLog("Ошибка JSON парсера: ${err.message}", LogType.ERROR)
                    }
                }
            }

            else -> {
                addLog("Команда не распознана: '$cmd'. Наберите 'help' для списка.", LogType.WARNING)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.88f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .align(Alignment.Center),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF090A12),
            border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
            shadowElevation = 24.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Terminal Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF111422))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF0055), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFFFFD700), CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.size(10.dp).background(Color(0xFF00FF66), CircleShape))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "NEXORA TERMINAL PIPELINE [DYNAMIC UI]",
                            color = Color(0xFF00E5FF),
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть консоль", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                // Sub-navigation tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0E18))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = activeTab == 0,
                        onClick = { activeTab = 0 },
                        label = { Text("Терминал и CLI", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF9D00FF).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFF00E5FF)
                        )
                    )
                    FilterChip(
                        selected = activeTab == 1,
                        onClick = {
                            jsonEditorContent = ThemeConfigManager.currentConfig.toJson(2)
                            activeTab = 1
                        },
                        label = { Text("JSON Редактор", fontFamily = FontFamily.Monospace, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF9D00FF).copy(alpha = 0.25f),
                            selectedLabelColor = Color(0xFF00E5FF)
                        )
                    )
                }

                // Quick Action Toolbar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip("🎨 Пресет Скриншот", Color(0xFF00E5FF)) {
                        executeCommand("preset screenshot")
                    }
                    ActionChip("⚡ Неон (#9D00FF)", Color(0xFF9D00FF)) {
                        executeCommand("preset neon")
                    }
                    ActionChip("🤖 YOLO Auto-Feed", Color(0xFF00E5FF)) {
                        executeCommand("yolo")
                    }
                    ActionChip("AMOLED Black", Color.White) {
                        executeCommand("preset amoled")
                    }
                    ActionChip("📋 Вставить JSON", Color(0xFFFF9500)) {
                        val clip = getFromClipboard(context)
                        if (!clip.isNullOrBlank()) {
                            val res = ThemeConfigManager.applyJson(clip, context)
                            res.onSuccess {
                                jsonEditorContent = it.toJson(2)
                                addLog("JSON из буфера успешно применен!", LogType.SUCCESS)
                            }.onFailure {
                                addLog("Ошибка парсинга JSON из буфера: ${it.message}", LogType.ERROR)
                            }
                        } else {
                            addLog("Буфер обмена пуст.", LogType.WARNING)
                        }
                    }
                    ActionChip("📤 Экспорт JSON", Color(0xFF00FF66)) {
                        executeCommand("export")
                    }
                }

                Divider(color = Color.White.copy(alpha = 0.1f))

                if (activeTab == 0) {
                    // TAB 0: Console Logs & Interactive CLI Input
                    Column(modifier = Modifier.weight(1f).padding(10.dp)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(Color(0xFF06070C), RoundedCornerShape(8.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(logs) { log ->
                                val color = when (log.type) {
                                    LogType.SUCCESS -> Color(0xFF00FF66)
                                    LogType.INFO -> Color(0xFF00E5FF)
                                    LogType.WARNING -> Color(0xFFFFD700)
                                    LogType.ERROR -> Color(0xFFFF0055)
                                    LogType.COMMAND -> Color(0xFFE2E8F0)
                                }
                                Text(
                                    text = "[${log.timestamp}] ${log.text}",
                                    color = color,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // CLI Prompt Input
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "nexora$ > ",
                                color = Color(0xFF00E5FF),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            OutlinedTextField(
                                value = commandInput,
                                onValueChange = { commandInput = it },
                                placeholder = {
                                    Text("help, preset neon, yolo, toggle pip, export...", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = { executeCommand(commandInput) }),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                    focusedContainerColor = Color(0xFF0B0D18),
                                    unfocusedContainerColor = Color(0xFF0B0D18)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = { executeCommand(commandInput) },
                                modifier = Modifier.size(42.dp).background(Color(0xFF9D00FF), RoundedCornerShape(8.dp))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "Выполнить", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                } else {
                    // TAB 1: Live JSON Editor & Hot-Reload Pipeline
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(10.dp)
                    ) {
                        Text(
                            "Прямой редактор JSON-конфигурации (Hot-Reload без пересборки):",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = jsonEditorContent,
                            onValueChange = { jsonEditorContent = it },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = Color(0xFF00FF66)),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF9D00FF),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedContainerColor = Color(0xFF06070C),
                                unfocusedContainerColor = Color(0xFF06070C)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val res = ThemeConfigManager.applyJson(jsonEditorContent, context)
                                    res.onSuccess {
                                        addLog("JSON успешно применен к плееру!", LogType.SUCCESS)
                                        Toast.makeText(context, "UI обновлен без перезагрузки!", Toast.LENGTH_SHORT).show()
                                        activeTab = 0
                                    }.onFailure { err ->
                                        addLog("Ошибка валидации JSON: ${err.message}", LogType.ERROR)
                                        Toast.makeText(context, "Ошибка JSON: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9D00FF))
                            ) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ПРИМЕНИТЬ К UI", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }

                            OutlinedButton(
                                onClick = {
                                    jsonEditorContent = ThemeConfigManager.currentConfig.toJson(2)
                                },
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f))
                            ) {
                                Text("Сбросить", color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, accentColor: Color, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF141829),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

private fun currentTimeFormatted(): String {
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("Nexora Config", text)
    clipboard?.setPrimaryClip(clip)
}

private fun getFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val item = clipboard?.primaryClip?.getItemAt(0)
    return item?.text?.toString()
}
