package com.example.videoplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.player.LinuxInstaller
import com.example.videoplayer.player.TermuxEngine
import com.example.videoplayer.ui.theme.GrayText
import com.example.videoplayer.ui.theme.LocalAppTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinuxTerminalScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val outputLines = remember { mutableStateListOf<String>() }
    fun appendOutputLine(line: String) {
        // Keep terminal history bounded so long-running sessions cannot grow UI memory indefinitely.
        if (outputLines.size >= 1000) outputLines.removeAt(0)
        outputLines.add(line)
    }
    var inputText by remember { mutableStateOf("") }
    var processWriter by remember { mutableStateOf<PrintWriter?>(null) }
    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var statusText by remember { mutableStateOf("Инициализация окружения...") }

    BackHandler { onBack() }

    DisposableEffect(Unit) {
        onDispose {
            try {
                processWriter?.close()
            } catch (_: Exception) {
            }
            try {
                activeProcess?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    // Не подменяем Linux-окружение системной оболочкой Android: это вводило бы пользователя в заблуждение.
    LaunchedEffect(Unit) {
        val prootReady = TermuxEngine.isProotAvailable(context)
        if (!prootReady) {
            appendOutputLine("Linux PRoot runtime is not installed in this APK.")
            appendOutputLine("Нужны PRoot, loader, libtalloc и libandroid-shmem для ABI устройства.")
            appendOutputLine("Системная оболочка Android вместо Alpine не запускается.")
            statusText = "Не найден комплект Linux runtime"
            isLoading = false
            return@LaunchedEffect
        }

        if (!LinuxInstaller.isInstalled(context)) {
            try {
                LinuxInstaller.installAlpine(context) { progress ->
                    coroutineScope.launch(Dispatchers.Main) {
                        statusText = progress
                    }
                }
            } catch (e: Exception) {
                appendOutputLine("Ошибка установки Alpine rootfs: ${e.localizedMessage ?: "нет сети"}")
            }
        }

        statusText = "Запуск сессии..."
        if (LinuxInstaller.isInstalled(context)) {
            appendOutputLine("Welcome to Alpine Linux (Termux-style PRoot)")
            appendOutputLine("PRoot virtual root is enabled (-0); Android system root is not granted.")
        } else {
            appendOutputLine("Alpine rootfs не установлен. Установка не завершилась.")
            isLoading = false
            return@LaunchedEffect
        }
        appendOutputLine("--------------------------------------------------")

        coroutineScope.launch(Dispatchers.IO) {
            var sessionProcess: Process? = null
            try {
                LinuxInstaller.ensureAiEngineScript(context)
                LinuxInstaller.ensureBootstrapScript(context)
                val process = TermuxEngine.createTermuxSession(context).start()
                sessionProcess = process
                val writer = PrintWriter(process.outputStream, true)
                withContext(Dispatchers.Main) {
                    activeProcess = process
                    processWriter = writer
                    isLoading = false
                }
                if (!LinuxInstaller.isBootstrapDone(context)) {
                    coroutineScope.launch(Dispatchers.IO) {
                        // Даем сессии время на старт и запускаем bootstrap.sh (внутри проверка .bootstrap_done)
                        kotlinx.coroutines.delay(500)
                        processWriter?.println("sh /root/bootstrap.sh")
                    }
                }

                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = reader.readLine()
                while (line != null) {
                    val currentLine = line
                    withContext(Dispatchers.Main) {
                        appendOutputLine(currentLine)
                        if (outputLines.isNotEmpty()) {
                            listState.animateScrollToItem(outputLines.size - 1)
                        }
                    }
                    line = reader.readLine()
                }
                withContext(Dispatchers.Main) {
                    if (activeProcess === process) {
                        processWriter = null
                        activeProcess = null
                        statusText = "Сессия Linux завершена"
                    }
                    appendOutputLine("[*] Сессия Linux завершена.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendOutputLine("Ошибка терминала: ${e.localizedMessage}")
                    isLoading = false
                    processWriter = null
                    activeProcess = null
                }
            } finally {
                // Also covers cancellation between process.start() and publishing it to Compose state.
                sessionProcess?.let { process ->
                    try {
                        if (process.isAlive) process.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Terminal (PRoot)", color = Color.White) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("terminal_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF141414))
            )
        },
        containerColor = Color(0xFF1E1E1E)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(Color(0xFF1E1E1E))
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelImportButton(
                    context = context,
                    onImportComplete = { linuxModelPath ->
                        appendOutputLine("[*] Модель импортирована в PRoot: $linuxModelPath")
                        appendOutputLine("[*] Конфиг обновлен: /app_data/config.json")
                        appendOutputLine("[*] Проверка: python3 /app_data/ai_engine.py")
                    },
                    modifier = Modifier.weight(1f)
                )
                LightAiModelsCatalogButton(
                    context = context,
                    onModelReady = { linuxModelPath ->
                        appendOutputLine("[*] Подключена лёгкая модель: $linuxModelPath")
                        appendOutputLine("[*] Конфиг обновлен: /app_data/config.json")
                        appendOutputLine("[*] Запуск ИИ-сервера: python3 /app_data/ai_server.py")
                    },
                    onAiToggleChanged = { enabled ->
                        appendOutputLine(
                            if (enabled) "[*] ИИ-ассистент вёрстки включён в config.json"
                            else "[*] ИИ-ассистент вёрстки выключен в config.json"
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.Green)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(statusText, color = Color.White, fontSize = 14.sp)
                    }
                }
            } else {
                // Оконный вывод консоли
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("terminal_output_list")
                ) {
                    items(outputLines) { text ->
                        Text(
                            text = text,
                            color = Color(0xFF00FF66),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Панель ввода команд
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("terminal_command_input"),
                    placeholder = { Text("команда (например: apk update, python3 --version)", color = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.Green,
                        unfocusedBorderColor = Color.DarkGray
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        val cmd = inputText
                        val writer = processWriter
                        if (cmd.isNotBlank() && writer != null) {
                            appendOutputLine("# $cmd")
                            inputText = ""
                            coroutineScope.launch(Dispatchers.IO) {
                                writer.println(cmd)
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("terminal_enter_button"),
                    enabled = !isLoading && processWriter != null
                ) {
                    Text("Enter")
                }
            }
        }
    }
}

@Composable
fun LinuxTerminalEntryCard(
    onOpenTerminal: () -> Unit
) {
    val theme = LocalAppTheme.current
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("more_terminal_card")
            .clickable(onClick = onOpenTerminal),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.outlinedCardColors(containerColor = theme.surface),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Terminal,
                contentDescription = null,
                tint = Color(0xFF00FF66),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Linux-окружение (Termux)",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Alpine Linux, пакеты apk и Python",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}
