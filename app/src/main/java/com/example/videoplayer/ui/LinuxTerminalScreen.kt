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

    // Проверка и установка при первом открытии, затем запуск PRoot или fallback-сессии
    LaunchedEffect(Unit) {
        val prootReady = TermuxEngine.isProotAvailable(context)
        if (prootReady && !LinuxInstaller.isInstalled(context)) {
            try {
                LinuxInstaller.installAlpine(context) { progress ->
                    coroutineScope.launch(Dispatchers.Main) {
                        statusText = progress
                    }
                }
            } catch (e: Exception) {
                outputLines.add("Ошибка установки Alpine rootfs: ${e.localizedMessage ?: "нет сети"}")
            }
        }

        statusText = "Запуск сессии..."
        if (prootReady && LinuxInstaller.isInstalled(context)) {
            outputLines.add("Welcome to Alpine Linux (Termux-style PRoot)")
            outputLines.add("Type 'su' to gain root privileges if host is rooted.")
        } else {
            outputLines.add("Android System Shell (/system/bin/sh)")
            if (!prootReady) {
                outputLines.add("Примечание: libproot.so не найден в nativeLibraryDir; запущена встроенная оболочка Android.")
            }
        }
        outputLines.add("--------------------------------------------------")
        isLoading = false

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val useProot = prootReady && LinuxInstaller.isInstalled(context)
                if (useProot) {
                    LinuxInstaller.ensureBootstrapScript(context)
                }
                val process = if (useProot) {
                    TermuxEngine.createTermuxSession(context).start()
                } else {
                    ProcessBuilder("/system/bin/sh").apply {
                        directory(context.filesDir)
                        environment()["HOME"] = context.filesDir.absolutePath
                        environment()["TERM"] = "xterm-256color"
                        redirectErrorStream(true)
                    }.start()
                }
                val writer = PrintWriter(process.outputStream, true)
                withContext(Dispatchers.Main) {
                    activeProcess = process
                    processWriter = writer
                }
                if (useProot && !LinuxInstaller.isBootstrapDone(context)) {
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
                        outputLines.add(currentLine)
                        if (outputLines.isNotEmpty()) {
                            listState.animateScrollToItem(outputLines.size - 1)
                        }
                    }
                    line = reader.readLine()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    outputLines.add("Ошибка терминала: ${e.localizedMessage}")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Terminal (PRoot + su)", color = Color.White) },
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
                    placeholder = { Text("команда (например: apk update, su)", color = Color.Gray) },
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
                            outputLines.add("# $cmd")
                            inputText = ""
                            coroutineScope.launch(Dispatchers.IO) {
                                writer.println(cmd)
                            }
                        }
                    },
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("terminal_enter_button"),
                    enabled = !isLoading
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
                    text = "Консоль, пакеты APK и поддержка su",
                    style = MaterialTheme.typography.bodySmall,
                    color = GrayText
                )
            }
        }
    }
}
