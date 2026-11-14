package com.example.videoplayer.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.archive.ArchiveEntryItem
import com.example.videoplayer.archive.ArchiveFileUtils
import com.example.videoplayer.archive.ArchiveInspectResult
import com.example.videoplayer.archive.ArchiveManager
import com.example.videoplayer.archive.ArchivePasswordRequiredException
import com.example.videoplayer.archive.ArchivePlaybackSpec
import com.example.videoplayer.archive.ArchiveSource
import com.example.videoplayer.archive.ExtractionProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

private val BgDark = Color(0xFF020306)
private val CardDark = Color(0xFF0D0F14)
private val CardBorder = Color(0x22FFFFFF)
private val NeonOrange = Color(0xFFFF7A00)
private val TextMuted = Color(0xFF8A90A0)

@Composable
fun ArchiveBrowserScreen(
    source: ArchiveSource,
    defaultDestinationDir: File? = null,
    onPlayVideoFromArchive: (ArchivePlaybackSpec) -> Unit,
    onExtractionFinished: () -> Unit = {},
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentPassword by remember(source) { mutableStateOf<String?>(null) }
    var inspectResult by remember(source) { mutableStateOf<ArchiveInspectResult?>(null) }
    var isLoading by remember(source) { mutableStateOf(true) }
    var loadError by remember(source) { mutableStateOf<String?>(null) }
    var showPasswordDialog by remember(source) { mutableStateOf(false) }
    var passwordError by remember(source) { mutableStateOf<String?>(null) }
    var pendingExtractAfterPassword by remember(source) { mutableStateOf<(() -> Unit)?>(null) }

    var currentSubPath by remember(source) { mutableStateOf("") }
    var searchQuery by remember(source) { mutableStateOf("") }
    var infoEntry by remember { mutableStateOf<ArchiveEntryItem?>(null) }

    var extractionJob by remember { mutableStateOf<Job?>(null) }
    var extractionState by remember { mutableStateOf<ExtractionProgress?>(null) }
    var hasAllFiles by remember { mutableStateOf(ArchiveManager.hasAllFilesAccess()) }

    fun loadArchive(passwordAttempt: String?) {
        isLoading = true
        loadError = null
        scope.launch {
            try {
                val res = ArchiveManager.inspectArchive(context, source, passwordAttempt)
                inspectResult = res
                currentPassword = passwordAttempt
                isLoading = false
                passwordError = null
            } catch (_: ArchivePasswordRequiredException) {
                isLoading = false
                passwordError = if (passwordAttempt != null) "Неверный пароль" else null
                showPasswordDialog = true
            } catch (t: Throwable) {
                isLoading = false
                loadError = t.message ?: "Не удалось открыть архив"
            }
        }
    }

    fun resolveExtractDir(): File {
        val preferred = defaultDestinationDir ?: ArchiveManager.defaultExtractDir(source.displayName)
        return if (ArchiveManager.hasAllFilesAccess()) {
            preferred
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "Extracted/${source.displayName.substringBeforeLast('.')}")
        }
    }

    fun startExtraction(indices: IntArray?, label: String, stripPrefix: String? = null) {
        val destDir = resolveExtractDir()
        extractionJob?.cancel()
        extractionJob = scope.launch {
            ArchiveManager.extract(
                context = context,
                source = source,
                destinationDir = destDir,
                entryIndices = indices,
                password = currentPassword,
                stripRootPrefix = stripPrefix
            ).collect { progress ->
                extractionState = progress
                when (progress) {
                    is ExtractionProgress.Completed -> {
                        Toast.makeText(context, "Распаковано ($label) в ${progress.destinationPath}", Toast.LENGTH_LONG).show()
                        onExtractionFinished()
                    }
                    is ExtractionProgress.Error -> {
                        if (progress.requiresPassword) {
                            extractionState = null
                            passwordError = progress.message
                            pendingExtractAfterPassword = { startExtraction(indices, label, stripPrefix) }
                            showPasswordDialog = true
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    LaunchedEffect(source) {
        loadArchive(null)
    }

    BackHandler {
        when {
            extractionState is ExtractionProgress.Progress || extractionState is ExtractionProgress.Preparing -> {
                extractionJob?.cancel()
                extractionState = null
            }
            showPasswordDialog -> {
                showPasswordDialog = false
                if (inspectResult == null) onClose()
            }
            infoEntry != null -> infoEntry = null
            currentSubPath.isNotEmpty() -> {
                currentSubPath = if (currentSubPath.contains('/')) currentSubPath.substringBeforeLast('/') else ""
            }
            else -> onClose()
        }
    }

    val visibleEntries = remember(inspectResult, currentSubPath, searchQuery) {
        val entries = inspectResult?.entries ?: return@remember emptyList()
        if (searchQuery.isNotBlank()) {
            entries.filter { !it.isDirectory && it.name.contains(searchQuery, ignoreCase = true) }
        } else {
            ArchiveManager.listFolderEntries(entries, currentSubPath)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().background(BgDark).systemBarsPadding(),
        color = BgDark
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (currentSubPath.isNotEmpty()) {
                            currentSubPath = if (currentSubPath.contains('/')) currentSubPath.substringBeforeLast('/') else ""
                        } else {
                            onClose()
                        }
                    },
                    modifier = Modifier.size(48.dp).testTag("archive_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Color.White)
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                    Text(
                        text = source.displayName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = buildString {
                        inspectResult?.let {
                            append(it.formatName)
                            if (it.isEncrypted) append(" • Пароль")
                            if (it.isMultiVolume) append(" • Многотомный")
                            append(" • /")
                            append(currentSubPath)
                        } ?: append("Анализ архива...")
                    }
                    Text(
                        text = subtitle,
                        color = NeonOrange,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(48.dp).testTag("archive_close_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = TextMuted)
                }
            }

            if (!hasAllFiles) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF231307),
                    border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Нет доступа ко всем файлам (Android 11+). Распаковка пойдёт во внутреннюю папку.",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            context.startActivity(ArchiveManager.buildAllFilesAccessIntent(context))
                            hasAllFiles = ArchiveManager.hasAllFilesAccess()
                        }) {
                            Text("Разрешить", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Action & Search row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск в архиве...", fontSize = 13.sp, color = TextMuted) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = NeonOrange) },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("archive_search_input"),
                    shape = RoundedCornerShape(22.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Button(
                    onClick = { startExtraction(null, source.displayName) },
                    enabled = inspectResult != null,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.height(48.dp).testTag("archive_extract_all_button")
                ) {
                    Icon(Icons.Default.Unarchive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Всё", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }

            // Content area
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = NeonOrange)
                            Spacer(Modifier.height(12.dp))
                            Text("Чтение структуры архива...", color = TextMuted, fontSize = 14.sp)
                        }
                    }
                }
                loadError != null -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFFF808B), modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(10.dp))
                            Text(loadError!!, color = Color.White, fontSize = 14.sp)
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(onClick = { showPasswordDialog = true }) {
                                    Text("Ввести пароль", color = NeonOrange)
                                }
                                Button(
                                    onClick = { loadArchive(currentPassword) },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = Color.Black)
                                ) {
                                    Text("Повторить")
                                }
                            }
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currentSubPath.isNotEmpty() && searchQuery.isBlank()) {
                            item(key = "..") {
                                ArchiveRowCard(
                                    title = ".. [На уровень выше]",
                                    subtitle = "/${currentSubPath.substringBeforeLast('/', "")}",
                                    isDirectory = true,
                                    isVideo = false,
                                    isEncrypted = false,
                                    onClick = {
                                        currentSubPath = if (currentSubPath.contains('/')) {
                                            currentSubPath.substringBeforeLast('/')
                                        } else ""
                                    },
                                    onExtractClick = null
                                )
                            }
                        }

                        if (visibleEntries.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("В этой папке архива нет файлов", color = TextMuted, fontSize = 14.sp)
                                }
                            }
                        } else {
                            items(visibleEntries, key = { "${it.index}_${it.path}" }) { entry ->
                                val subtitle = when {
                                    entry.isDirectory -> "Папка"
                                    entry.isVideo -> "Видео • ${ArchiveFileUtils.formatSize(entry.size)}"
                                    else -> ArchiveFileUtils.formatSize(entry.size)
                                }
                                ArchiveRowCard(
                                    title = entry.name,
                                    subtitle = subtitle,
                                    isDirectory = entry.isDirectory,
                                    isVideo = entry.isVideo,
                                    isEncrypted = entry.isEncrypted,
                                    onClick = {
                                        if (entry.isDirectory) {
                                            currentSubPath = entry.path
                                        } else if (entry.isVideo) {
                                            val result = inspectResult ?: return@ArchiveRowCard
                                            val spec = ArchivePlaybackSpec(
                                                source = source,
                                                entryPath = entry.path,
                                                entryIndex = entry.index,
                                                entrySize = entry.size,
                                                entryName = entry.name,
                                                isEncrypted = entry.isEncrypted || result.isEncrypted,
                                                isMultiVolume = result.isMultiVolume,
                                                formatName = result.formatName,
                                                password = currentPassword
                                            )
                                            onPlayVideoFromArchive(spec)
                                        } else {
                                            infoEntry = entry
                                        }
                                    },
                                    onExtractClick = {
                                        if (entry.isDirectory) {
                                            val all = inspectResult?.entries.orEmpty()
                                            val indices = ArchiveManager.collectEntryIndicesForFolder(all, entry.path)
                                            startExtraction(indices, entry.name)
                                        } else {
                                            startExtraction(intArrayOf(entry.index), entry.name)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordPromptDialog(
            archiveName = source.displayName,
            errorText = passwordError,
            onDismiss = {
                showPasswordDialog = false
                pendingExtractAfterPassword = null
                if (inspectResult == null) onClose()
            },
            onConfirm = { entered ->
                showPasswordDialog = false
                currentPassword = entered
                val pending = pendingExtractAfterPassword
                pendingExtractAfterPassword = null
                if (pending != null) {
                    pending()
                } else {
                    loadArchive(entered)
                }
            }
        )
    }

    infoEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { infoEntry = null },
            containerColor = CardDark,
            title = { Text(entry.name, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Путь: ${entry.path}", color = TextMuted, fontSize = 13.sp)
                    Text("Размер: ${ArchiveFileUtils.formatSize(entry.size)}", color = Color.White, fontSize = 13.sp)
                    Text("Сжатый: ${ArchiveFileUtils.formatSize(entry.packedSize)}", color = TextMuted, fontSize = 13.sp)
                    if (entry.crc.isNotEmpty()) Text("CRC32: ${entry.crc}", color = TextMuted, fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = entry
                        infoEntry = null
                        startExtraction(intArrayOf(target.index), target.name)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = Color.Black)
                ) {
                    Text("Распаковать")
                }
            },
            dismissButton = {
                TextButton(onClick = { infoEntry = null }) {
                    Text("Закрыть", color = TextMuted)
                }
            }
        )
    }

    val activeProg = extractionState
    if (activeProg != null && activeProg !is ExtractionProgress.Completed && activeProg !is ExtractionProgress.Cancelled) {
        ExtractionProgressDialog(
            state = activeProg,
            onCancel = {
                extractionJob?.cancel()
                extractionState = null
            },
            onDismissError = {
                extractionState = null
            }
        )
    }
}

@Composable
private fun ArchiveRowCard(
    title: String,
    subtitle: String,
    isDirectory: Boolean,
    isVideo: Boolean,
    isEncrypted: Boolean,
    onClick: () -> Unit,
    onExtractClick: (() -> Unit)?
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardDark,
        border = BorderStroke(1.dp, if (isVideo) NeonOrange.copy(alpha = 0.35f) else CardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isVideo) NeonOrange.copy(alpha = 0.16f) else Color(0xFF1A120A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        isDirectory -> Icons.Default.Folder
                        isVideo -> Icons.Default.PlayArrow
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = NeonOrange
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isEncrypted) {
                        Icon(Icons.Default.Lock, contentDescription = "Зашифрован", tint = NeonOrange, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = subtitle,
                        color = if (isVideo) NeonOrange else TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onExtractClick != null) {
                IconButton(onClick = onExtractClick, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Распаковать", tint = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun PasswordPromptDialog(
    archiveName: String,
    errorText: String?,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pwd by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardDark,
        title = { Text("Введите пароль", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Архив «$archiveName» защищён паролем.", color = TextMuted, fontSize = 13.sp)
                if (!errorText.isNullOrBlank()) {
                    Text(errorText, color = Color(0xFFFF808B), fontSize = 12.5.sp)
                }
                OutlinedTextField(
                    value = pwd,
                    onValueChange = { pwd = it },
                    label = { Text("Пароль") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать пароль",
                                tint = TextMuted
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().testTag("archive_password_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonOrange,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pwd) },
                enabled = pwd.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = Color.Black),
                modifier = Modifier.testTag("archive_password_confirm")
            ) {
                Text("Открыть", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = TextMuted)
            }
        }
    )
}

@Composable
private fun ExtractionProgressDialog(
    state: ExtractionProgress,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (state is ExtractionProgress.Error) onDismissError() },
        containerColor = CardDark,
        title = {
            Text(
                if (state is ExtractionProgress.Error) "Ошибка распаковки" else "Распаковка архива",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (state) {
                is ExtractionProgress.Preparing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = NeonOrange)
                        Text("Подготовка...", color = TextMuted, fontSize = 13.sp)
                    }
                }
                is ExtractionProgress.Progress -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { (state.percent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                            color = NeonOrange,
                            trackColor = Color(0xFF22252E)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${state.percent}%", color = NeonOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "${ArchiveFileUtils.formatSize(state.bytesProcessed)} / ${ArchiveFileUtils.formatSize(state.totalBytes)}",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                        }
                        if (state.currentFile.isNotBlank()) {
                            Text(state.currentFile, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                is ExtractionProgress.Error -> {
                    Text(state.message, color = Color(0xFFFF808B), fontSize = 13.5.sp)
                }
                else -> Unit
            }
        },
        confirmButton = {
            if (state is ExtractionProgress.Error) {
                Button(onClick = onDismissError, colors = ButtonDefaults.buttonColors(containerColor = NeonOrange, contentColor = Color.Black)) {
                    Text("ОК")
                }
            } else {
                TextButton(onClick = onCancel, modifier = Modifier.testTag("archive_extract_cancel")) {
                    Text("Отменить", color = Color(0xFFFF808B), fontWeight = FontWeight.Bold)
                }
            }
        }
    )
}
