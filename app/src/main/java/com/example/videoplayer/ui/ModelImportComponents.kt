package com.example.videoplayer.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.system.Os
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class AIModelInfo(
    val name: String,
    val size: String,
    val description: String,
    val downloadUrl: String,
    val fileName: String
)

val availableLightModels = listOf(
    AIModelInfo(
        name = "Qwen 2.5 Coder 1.5B Instruct",
        size = "950 МБ",
        description = "Идеально для правки кода, HTML/CSS и скриптов",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-1.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-1.5b-instruct-q4_k_m.gguf",
        fileName = "qwen1.5b.gguf"
    ),
    AIModelInfo(
        name = "Qwen 2.5 Coder 0.5B Instruct",
        size = "380 МБ",
        description = "Ультра-легкая модель для слабых устройств",
        downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF/resolve/main/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf",
        fileName = "qwen0.5b.gguf"
    )
)

@Composable
fun ModelImportButton(
    context: Context,
    onImportComplete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var importStatus by remember { mutableStateOf("Импорт .gguf") }
    var isImporting by remember { mutableStateOf(false) }

    // Создаем лаунчер для выбора любого файла (или конкретно .gguf)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            if (isImporting) return@let
            isImporting = true
            importStatus = "Копирование модели..."
            coroutineScope.launch(Dispatchers.IO) {
                val destPath = copyUriToLinuxModelsDirWithPath(context, selectedUri) { progress ->
                    coroutineScope.launch(Dispatchers.Main) {
                        importStatus = progress
                    }
                }
                withContext(Dispatchers.Main) {
                    isImporting = false
                    if (destPath != null) {
                        importStatus = "Модель подключена"
                        onImportComplete(destPath)
                    } else {
                        importStatus = "Ошибка импорта!"
                    }
                }
            }
        }
    }

    Button(
        onClick = {
            if (!isImporting) {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        },
        enabled = !isImporting,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary
        ),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("model_import_button")
    ) {
        Text(text = importStatus, color = Color.White, maxLines = 1)
    }
}

@Composable
fun LightAiModelsCatalogButton(
    context: Context,
    onModelReady: (String) -> Unit,
    onAiToggleChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDialog by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = { showDialog = true },
        border = BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF66)),
        modifier = modifier
            .heightIn(min = 48.dp)
            .testTag("ai_models_catalog_button")
    ) {
        Icon(
            imageVector = Icons.Default.SmartToy,
            contentDescription = "Каталог ИИ-моделей",
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "ИИ-модели", maxLines = 1)
    }

    if (showDialog) {
        LightAiModelsDialog(
            context = context,
            onDismiss = { showDialog = false },
            onModelReady = onModelReady,
            onAiToggleChanged = onAiToggleChanged
        )
    }
}

@Composable
private fun LightAiModelsDialog(
    context: Context,
    onDismiss: () -> Unit,
    onModelReady: (String) -> Unit,
    onAiToggleChanged: (Boolean) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var aiEnabled by remember { mutableStateOf(isAiAssistantEnabled(context)) }
    var activePath by remember { mutableStateOf(getActiveModelPath(context)) }
    var downloadingFileName by remember { mutableStateOf<String?>(null) }
    var downloadProgressPercent by remember { mutableStateOf<Int?>(null) }
    var downloadStatusText by remember { mutableStateOf<String?>(null) }
    var downloadedVersionTick by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (downloadingFileName == null) onDismiss() },
        containerColor = Color(0xFF1A1D24),
        titleContentColor = Color.White,
        textContentColor = Color(0xFFD0D5DD),
        title = {
            Text(
                text = "Локальные ИИ-модели",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF232833),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "ИИ-ассистент вёрстки",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Сервер 127.0.0.1:8080 (/api/fix-ui)",
                                color = Color(0xFF98A2B3),
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = aiEnabled,
                            onCheckedChange = { checked ->
                                aiEnabled = checked
                                setAiAssistantEnabled(context, checked)
                                onAiToggleChanged(checked)
                            },
                            modifier = Modifier.testTag("ai_assistant_switch")
                        )
                    }
                }

                availableLightModels.forEach { model ->
                    val isInstalled = remember(downloadedVersionTick, model.fileName) {
                        isModelDownloaded(context, model.fileName)
                    }
                    val modelLinuxPath = "/app_data/models/${model.fileName}"
                    val isActive = activePath == modelLinuxPath && isInstalled
                    val isThisDownloading = downloadingFileName == model.fileName

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF232833),
                        border = if (isActive) {
                            BorderStroke(1.dp, Color(0xFF00FF66).copy(alpha = 0.7f))
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = model.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = model.size,
                                    color = Color(0xFF00FF66),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Text(
                                text = model.description,
                                color = Color(0xFF98A2B3),
                                fontSize = 12.sp
                            )

                            if (isThisDownloading) {
                                if (downloadProgressPercent != null) {
                                    LinearProgressIndicator(
                                        progress = { (downloadProgressPercent ?: 0) / 100f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF00FF66)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF00FF66)
                                    )
                                }
                                Text(
                                    text = downloadStatusText ?: "Загрузка...",
                                    color = Color(0xFFD0D5DD),
                                    fontSize = 11.sp
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    if (isActive) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Активная модель",
                                                tint = Color(0xFF00FF66),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Активна",
                                                color = Color(0xFF00FF66),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    } else if (isInstalled) {
                                        Button(
                                            onClick = {
                                                val path = activateDownloadedModel(context, model.fileName)
                                                activePath = path
                                                onModelReady(path)
                                            },
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("activate_model_${model.fileName}")
                                        ) {
                                            Text("Выбрать")
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                if (downloadingFileName != null) return@Button
                                                downloadingFileName = model.fileName
                                                downloadProgressPercent = 0
                                                downloadStatusText = "Подключение к Hugging Face..."
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val path = downloadAiModelToLinuxDir(context, model) { pct, msg ->
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            downloadProgressPercent = pct
                                                            downloadStatusText = msg
                                                        }
                                                    }
                                                    withContext(Dispatchers.Main) {
                                                        downloadingFileName = null
                                                        if (path != null) {
                                                            downloadedVersionTick++
                                                            activePath = path
                                                            aiEnabled = true
                                                            setAiAssistantEnabled(context, true)
                                                            onAiToggleChanged(true)
                                                            onModelReady(path)
                                                        } else {
                                                            downloadStatusText = "Ошибка загрузки модели"
                                                        }
                                                    }
                                                }
                                            },
                                            enabled = downloadingFileName == null,
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .testTag("download_model_${model.fileName}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Скачать")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                enabled = downloadingFileName == null,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Закрыть")
            }
        }
    )
}

private fun ensureModelsDir(context: Context): File {
    val modelsDir = File(context.filesDir, "models")
    if (!modelsDir.exists()) modelsDir.mkdirs()

    val rootfsAppData = File(context.filesDir, "rootfs/app_data")
    if (!rootfsAppData.exists()) rootfsAppData.mkdirs()
    val rootfsModelsAlias = File(rootfsAppData, "models")
    if (!rootfsModelsAlias.exists()) {
        try {
            Os.symlink(modelsDir.absolutePath, rootfsModelsAlias.absolutePath)
        } catch (_: Throwable) {
        }
    }
    return modelsDir
}

suspend fun downloadAiModelToLinuxDir(
    context: Context,
    model: AIModelInfo,
    onProgress: (Int?, String) -> Unit
): String? = withContext(Dispatchers.IO) {
    val modelsDir = ensureModelsDir(context)
    val destFile = File(modelsDir, model.fileName)
    val tempFile = File(modelsDir, "${model.fileName}.part")

    var connection: HttpURLConnection? = null
    try {
        var currentUrl = URL(model.downloadUrl)
        var redirects = 0
        while (redirects < 6) {
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "NexoraVideoPlayer/1.0")
            }
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Пустой redirect Location")
                currentUrl = URL(currentUrl, location)
                redirects++
            } else {
                connection = conn
                break
            }
        }

        val activeConn = connection ?: throw IOException("Слишком много перенаправлений")
        val code = activeConn.responseCode
        if (code !in 200..299) {
            throw IOException("HTTP $code")
        }

        val totalBytes = activeConn.contentLengthLong
        activeConn.inputStream.use { input ->
            FileOutputStream(tempFile).use { output ->
                val buffer = ByteArray(32 * 1024)
                var downloaded = 0L
                var lastReportedMb = -1L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    downloaded += read
                    val mb = downloaded / (1024L * 1024L)
                    if (mb != lastReportedMb) {
                        lastReportedMb = mb
                        if (totalBytes > 0) {
                            val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            val totalMb = totalBytes / (1024L * 1024L)
                            onProgress(pct, "Загрузка: $mb / $totalMb МБ ($pct%)")
                        } else {
                            onProgress(null, "Загрузка: $mb МБ")
                        }
                    }
                }
            }
        }

        if (destFile.exists()) destFile.delete()
        if (!tempFile.renameTo(destFile)) {
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()
        }

        val prootModelPath = "/app_data/models/${destFile.name}"
        updateModelConfig(modelsDir, prootModelPath)
        prootModelPath
    } catch (_: Exception) {
        tempFile.delete()
        null
    } finally {
        connection?.disconnect()
    }
}

fun isModelDownloaded(context: Context, fileName: String): Boolean {
    val file = File(File(context.filesDir, "models"), fileName)
    return file.isFile && file.length() > 0L
}

fun activateDownloadedModel(context: Context, fileName: String): String {
    val modelsDir = ensureModelsDir(context)
    val prootPath = "/app_data/models/$fileName"
    updateModelConfig(modelsDir, prootPath)
    return prootPath
}

fun getActiveModelPath(context: Context): String? {
    val configFile = File(context.filesDir, "config.json")
    if (!configFile.exists()) return null
    return try {
        JSONObject(configFile.readText()).optString("active_model_path").takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

// Функция физического переноса файла в папку PRoot (/app_data/models внутри контейнера)
suspend fun copyUriToLinuxModelsDir(
    context: Context,
    uri: Uri,
    onProgress: (String) -> Unit
): Boolean = copyUriToLinuxModelsDirWithPath(context, uri, onProgress) != null

private suspend fun copyUriToLinuxModelsDirWithPath(
    context: Context,
    uri: Uri,
    onProgress: (String) -> Unit
): String? = withContext(Dispatchers.IO) {
    try {
        // Так как TermuxEngine монтирует context.filesDir в /app_data, реальная папка для PRoot — filesDir/models
        val modelsDir = ensureModelsDir(context)

        // Получаем безопасное оригинальное имя файла через ContentResolver
        val rawName = getOriginalFileName(context, uri)
        val safeName = rawName?.let { File(it).name }?.takeIf { it.isNotBlank() && it != "." && it != ".." }
            ?: "imported_model.gguf"
        val destFile = File(modelsDir, safeName)

        val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
        inputStream.use { input ->
            destFile.outputStream().use { output ->
                val buffer = ByteArray(16384)
                var read: Int
                var totalBytes = 0L
                var lastReportedMb = -1L

                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalBytes += read
                    val mb = totalBytes / (1024L * 1024L)
                    if (mb != lastReportedMb) {
                        lastReportedMb = mb
                        onProgress("Импорт: $mb МБ скопировано")
                    }
                }
            }
        }

        // Автоматически создаем конфиг-файл связи для Python
        val prootModelPath = "/app_data/models/${destFile.name}"
        updateModelConfig(modelsDir, prootModelPath)
        prootModelPath
    } catch (_: Exception) {
        null
    }
}

fun updateModelConfig(modelsDir: File, modelPath: String) {
    val parentDir = modelsDir.parentFile ?: return
    val configFile = File(parentDir, "config.json")

    // Записываем путь в JSON, который читает Python-скрипт ИИ, сохраняя флаг ai_assistant_enabled
    val json = if (configFile.exists()) {
        try {
            JSONObject(configFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    } else {
        JSONObject()
    }
    json.put("active_model_path", modelPath)
    if (!json.has("context_size")) json.put("context_size", 2048)
    if (!json.has("n_threads")) json.put("n_threads", 4)
    if (!json.has("ai_assistant_enabled")) json.put("ai_assistant_enabled", false)

    configFile.writeText(json.toString(4))
}

fun isAiAssistantEnabled(context: Context): Boolean {
    val configFile = File(context.filesDir, "config.json")
    if (!configFile.exists()) return false
    return try {
        JSONObject(configFile.readText()).optBoolean("ai_assistant_enabled", false)
    } catch (_: Exception) {
        false
    }
}

fun setAiAssistantEnabled(context: Context, enabled: Boolean) {
    val configFile = File(context.filesDir, "config.json")
    val json = if (configFile.exists()) {
        try {
            JSONObject(configFile.readText())
        } catch (_: Exception) {
            JSONObject()
        }
    } else {
        JSONObject()
    }
    json.put("ai_assistant_enabled", enabled)
    if (!json.has("active_model_path")) json.put("active_model_path", "/app_data/models/qwen1.5b.gguf")
    if (!json.has("context_size")) json.put("context_size", 2048)
    if (!json.has("n_threads")) json.put("n_threads", 4)
    configFile.writeText(json.toString(4))
}

// Вспомогательная функция для получения имени файла из URI
fun getOriginalFileName(context: Context, uri: Uri): String? {
    var name: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                name = cursor.getString(nameIndex)
            }
        }
    }
    return name ?: uri.lastPathSegment
}
