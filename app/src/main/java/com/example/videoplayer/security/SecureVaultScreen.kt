package com.example.videoplayer.security

import android.content.ContentUris
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.IntentSenderRequest
import com.example.videoplayer.model.MediaStoreHelper
import com.example.videoplayer.model.VideoItem
import com.example.videoplayer.ui.theme.DarkBackground
import com.example.videoplayer.ui.theme.NeonOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SecureVaultScreen(
    onBack: () -> Unit,
    onOpenVaultFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val vaultRepository = remember { SecureVaultRepository(context) }
    val activity = context as? FragmentActivity

    var password by remember { mutableStateOf("") }
    var isSetupMode by remember { mutableStateOf(!vaultRepository.isPasswordConfigured()) }
    var isUnlocked by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(vaultRepository.listItems()) }
    var statusText by remember { mutableStateOf("") }
    var isBusy by remember { mutableStateOf(false) }
    var busyMessage by remember { mutableStateOf("") }
    var biometricAvailable by remember { mutableStateOf(false) }
    var lockDeadline by remember { mutableStateOf(System.currentTimeMillis() + 120_000L) }
    var showVideoPickerModal by remember { mutableStateOf(false) }
    var deviceVideos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        biometricAvailable = try {
            BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) {
            false
        }
    }

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) return@LaunchedEffect
        while (isUnlocked) {
            val remaining = lockDeadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                withContext(Dispatchers.IO) {
                    vaultRepository.clearTempDecryptedFiles()
                }
                isUnlocked = false
                password = ""
                statusText = "Хранилище закрыто после периода неактивности"
                break
            }
            delay(1000L)
        }
    }

    fun secureLock() {
        coroutineScope.launch(Dispatchers.IO) {
            vaultRepository.clearTempDecryptedFiles()
        }
        isUnlocked = false
        password = ""
        statusText = "Хранилище заблокировано"
    }

    fun promptBiometricUnlock() {
        val fragmentActivity = activity ?: run {
            statusText = "Биометрия недоступна на этом устройстве"
            return
        }

        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(
            fragmentActivity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    isUnlocked = true
                    lockDeadline = System.currentTimeMillis() + 120_000L
                    files = vaultRepository.listItems()
                    statusText = "Доступ открыт по биометрии"
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    statusText = "Ошибка биометрии: $errString"
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    statusText = "Биометрическая аутентификация не удалась"
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Защищенная папка")
            .setSubtitle("Разблокировка отпечатком или сканером лица")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Отмена")
            .build()

        try {
            prompt.authenticate(promptInfo)
        } catch (e: Exception) {
            statusText = "Не удалось запустить биометрию: ${e.message}"
        }
    }

    val deleteOriginalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            statusText = "Оригинальный файл успешно удален из памяти устройства!"
            files = vaultRepository.listItems()
        } else {
            statusText = "Файл зашифрован в сейф. Для очистки проводника подтвердите удаление в системном диалоге"
        }
    }

    fun queryActualFileName(uri: Uri): String {
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}
        return uri.lastPathSegment?.substringAfterLast('/') ?: "video_${System.currentTimeMillis()}.mp4"
    }

    fun resolveFilePath(uri: Uri, displayName: String): String? {
        if (uri.scheme == "file") return uri.path
        try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (idx >= 0) {
                        val path = cursor.getString(idx)
                        if (!path.isNullOrBlank()) return path
                    }
                }
            }
        } catch (_: Exception) {}

        val checkDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Camera"),
            Environment.getExternalStorageDirectory()
        )
        for (dir in checkDirs) {
            val candidate = File(dir, displayName)
            if (candidate.exists() && candidate.isFile) {
                return candidate.absolutePath
            }
        }
        return null
    }

    fun resolveMediaStoreUri(uri: Uri, displayName: String): Uri? {
        if (uri.authority == "media") return uri
        try {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                "${MediaStore.Video.Media.DISPLAY_NAME} = ?",
                arrayOf(displayName),
                null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val id = c.getLong(0)
                    return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    fun importUriToVault(uri: Uri, displayName: String) {
        coroutineScope.launch {
            isBusy = true
            busyMessage = "Шифрование и удаление оригинала: $displayName..."
            try {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Не удалось прочитать выбранный файл")
                    vaultRepository.addStream(input, password, displayName)

                    val resolvedPath = resolveFilePath(uri, displayName)
                    val mediaStoreUri = resolveMediaStoreUri(uri, displayName)

                    // 1. Immediately register in hidden vault registry to hide from player & file explorer
                    vaultRepository.recordHiddenItem(displayName)
                    vaultRepository.recordHiddenItem(displayName.lowercase())
                    vaultRepository.recordHiddenItem(displayName.substringBeforeLast('.'))
                    vaultRepository.recordHiddenItem(uri.toString())
                    if (!resolvedPath.isNullOrBlank()) {
                        vaultRepository.recordHiddenItem(resolvedPath)
                        vaultRepository.recordHiddenItem(resolvedPath.lowercase())
                    }
                    if (mediaStoreUri != null) {
                        vaultRepository.recordHiddenItem(mediaStoreUri.toString())
                    }

                    // 2. Physical disk deletion & truncation of the original file
                    var originalDeleted = false

                    if (!resolvedPath.isNullOrBlank()) {
                        try {
                            val f = File(resolvedPath)
                            if (f.exists()) {
                                // Truncate first to zero-out bytes in case OS blocks full unlinking
                                try {
                                    java.io.FileOutputStream(f).use { /* 0 bytes */ }
                                } catch (_: Exception) {}
                                originalDeleted = f.delete()
                                MediaScannerConnection.scanFile(context, arrayOf(f.absolutePath), null, null)
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. Truncate via ContentResolver stream so other apps see empty file
                    try {
                        context.contentResolver.openOutputStream(uri, "wt")?.use { /* write 0 bytes */ }
                    } catch (_: Exception) {}

                    // 4. Try DocumentsContract deletion
                    if (!originalDeleted) {
                        try {
                            originalDeleted = DocumentsContract.deleteDocument(context.contentResolver, uri)
                        } catch (_: Exception) {}
                    }

                    // 5. Try MediaStore / ContentResolver deletion
                    val targetDeleteUri = mediaStoreUri ?: uri
                    if (!originalDeleted) {
                        try {
                            val rows = context.contentResolver.delete(targetDeleteUri, null, null)
                            originalDeleted = rows > 0
                        } catch (sec: android.app.RecoverableSecurityException) {
                            withContext(Dispatchers.Main) {
                                deleteOriginalLauncher.launch(
                                    IntentSenderRequest.Builder(sec.userAction.actionIntent.intentSender).build()
                                )
                            }
                        } catch (_: Exception) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && mediaStoreUri != null) {
                                try {
                                    val deleteReq = MediaStore.createDeleteRequest(context.contentResolver, listOf(mediaStoreUri))
                                    withContext(Dispatchers.Main) {
                                        deleteOriginalLauncher.launch(
                                            IntentSenderRequest.Builder(deleteReq.intentSender).build()
                                        )
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }

                    if (!resolvedPath.isNullOrBlank()) {
                        MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), null, null)
                    }
                }
                files = vaultRepository.listItems()
                statusText = "Файл «$displayName» зашифрован в защищенную папку и скрыт из проводника!"
                lockDeadline = System.currentTimeMillis() + 120_000L
            } catch (e: Throwable) {
                Log.e("SecureVault", "Error adding file", e)
                statusText = "Ошибка добавления файла: ${e.localizedMessage ?: "Сбой шифрования"}"
            } finally {
                isBusy = false
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                statusText = "Файл не выбран"
                return@rememberLauncherForActivityResult
            }
            if (password.isBlank()) {
                statusText = "Введите пароль хранилища"
                return@rememberLauncherForActivityResult
            }
            val originalName = queryActualFileName(uri)
            importUriToVault(uri, originalName)
        }
    )

    fun unlockVault() {
        if (isSetupMode) {
            if (password.length < 4) {
                statusText = "Пароль должен содержать минимум 4 символа"
                return
            }
            isBusy = true
            busyMessage = "Инициализация защищенного хранилища..."
            coroutineScope.launch {
                withContext(Dispatchers.IO) {
                    vaultRepository.setupPassword(password)
                }
                isSetupMode = false
                isUnlocked = true
                files = vaultRepository.listItems()
                statusText = "Хранилище создано и зашифровано AES-GCM"
                lockDeadline = System.currentTimeMillis() + 120_000L
                isBusy = false
            }
            return
        }

        isBusy = true
        busyMessage = "Проверка ключа..."
        coroutineScope.launch {
            val valid = withContext(Dispatchers.IO) {
                vaultRepository.verifyPassword(password)
            }
            isBusy = false
            if (valid) {
                isUnlocked = true
                files = vaultRepository.listItems()
                statusText = "Хранилище успешно разблокировано"
                lockDeadline = System.currentTimeMillis() + 120_000L
            } else {
                statusText = "Неверный пароль хранилища"
            }
        }
    }

    if (!isUnlocked) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, NeonOrange.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(NeonOrange.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = NeonOrange)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isSetupMode) "Создание Защищенной папки" else "Вход в Защищенную папку",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Шифрование AES-GCM 256-bit",
                                color = Color(0xFFFFAE33),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = if (isSetupMode)
                            "Задайте мастер-пароль для шифрования личных видео и медиафайлов."
                        else
                            "Введите мастер-пароль или используйте биометрию для доступа.",
                        color = Color(0xFFB0B0B0),
                        fontSize = 13.sp
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Мастер-пароль") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonOrange,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    if (statusText.isNotBlank()) {
                        Text(statusText, color = Color(0xFFFFD166), fontSize = 12.sp)
                    }

                    if (isBusy) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(color = NeonOrange, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(busyMessage, color = Color.White, fontSize = 12.sp)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Назад", color = Color.White)
                        }
                        Button(
                            onClick = { unlockVault() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                if (isSetupMode) "Создать" else "Войти",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (biometricAvailable && !isSetupMode) {
                        FilledTonalButton(
                            onClick = { promptBiometricUnlock() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Биометрический вход")
                        }
                    }
                }
            }
        }
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { secureLock(); onBack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text("Защищенная папка", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Шифрование активно", color = Color(0xFF4CAF50), fontSize = 11.sp)
                    }
                }
                Button(
                    onClick = { secureLock() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Запереть", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            filePicker.launch(arrayOf("video/*", "*/*"))
                        } catch (e: Exception) {
                            statusText = "Проводник недоступен, выберите видео из медиатеки"
                            showVideoPickerModal = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Из проводника", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = {
                        deviceVideos = MediaStoreHelper.getVideos(context)
                        showVideoPickerModal = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35)),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.VideoLibrary, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Из медиатеки", color = Color.White, fontSize = 13.sp)
                }
            }

            if (isBusy) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252525), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = NeonOrange, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(busyMessage, color = Color.White, fontSize = 12.sp)
                }
            }

            if (statusText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(statusText, color = Color(0xFFFFD166), fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (files.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Shield, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("В защищенной папке пока нет файлов", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Нажмите «Из медиатеки» или «Из проводника», чтобы зашифровать видео.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(files, key = { it.id }) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Lock, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = item.displayName,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "Зашифровано • ${(item.file.length() / (1024 * 1024.0)).coerceAtLeast(0.1).let { "%.1f MB".format(it) }}",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isBusy = true
                                                busyMessage = "Расшифровка для просмотра..."
                                                try {
                                                    val decrypted = withContext(Dispatchers.IO) {
                                                        vaultRepository.openFile(item.id, password)
                                                    }
                                                    lockDeadline = System.currentTimeMillis() + 120_000L
                                                    onOpenVaultFile(decrypted.absolutePath)
                                                } catch (e: Exception) {
                                                    statusText = "Ошибка расшифровки: ${e.message}"
                                                } finally {
                                                    isBusy = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Играть", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            vaultRepository.deleteFile(item.id)
                                            files = vaultRepository.listItems()
                                            statusText = "Файл удален из защищенного хранилища"
                                        }
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showVideoPickerModal) {
        AlertDialog(
            onDismissRequest = { showVideoPickerModal = false },
            title = {
                Text("Выберите видео для шифрования", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                if (deviceVideos.isEmpty()) {
                    Text("Видеофайлы не найдены в памяти устройства.", color = Color.Gray)
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 350.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deviceVideos, key = { it.id }) { video ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showVideoPickerModal = false
                                        importUriToVault(video.uri, video.displayName)
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF252525)
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Movie, contentDescription = null, tint = NeonOrange, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(video.displayName, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(video.resolution, color = Color.Gray, fontSize = 11.sp)
                                    }
                                    Icon(Icons.Default.Add, contentDescription = null, tint = NeonOrange)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showVideoPickerModal = false }) {
                    Text("Отмена", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E1E1E)
        )
    }
}
