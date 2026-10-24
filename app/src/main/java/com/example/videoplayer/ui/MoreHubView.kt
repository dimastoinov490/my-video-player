package com.example.videoplayer.security

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.videoplayer.ui.DarkBackground
import com.example.videoplayer.ui.NeonOrange
import java.io.File

@Composable
fun SecureVaultScreen(
    onBack: () -> Unit,
    onOpenVaultFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vaultRepository = remember { SecureVaultRepository(context) }

    var password by remember { mutableStateOf("") }
    var isSetupMode by remember { mutableStateOf(!vaultRepository.isPasswordConfigured()) }
    var isUnlocked by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(vaultRepository.listItems()) }
    var statusText by remember { mutableStateOf("") }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null || password.isBlank()) {
                statusText = if (password.isBlank()) "Сначала задайте пароль vault" else "Файл не выбран"
                return@rememberLauncherForActivityResult
            }

            val tempFile = File.createTempFile("vault_import_", ".tmp", context.cacheDir)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val originalName = uri.lastPathSegment ?: "vault_file"
                vaultRepository.addFile(tempFile, password, originalName)
                files = vaultRepository.listItems()
                statusText = "Файл добавлен в защищённое хранилище"
            } catch (_: Exception) {
                statusText = "Не удалось сохранить файл в vault"
            } finally {
                tempFile.delete()
            }
        }
    )

    fun unlockVault() {
        if (isSetupMode) {
            if (password.length < 6) {
                statusText = "Пароль должен быть не меньше 6 символов"
                return
            }
            vaultRepository.setupPassword(password)
            isSetupMode = false
            isUnlocked = true
            files = vaultRepository.listItems()
            statusText = "Vault настроен. Добавляйте файлы и открывайте их только по паролю."
            return
        }

        if (vaultRepository.verifyPassword(password)) {
            isUnlocked = true
            files = vaultRepository.listItems()
            statusText = "Vault разблокирован"
        } else {
            statusText = "Неверный пароль vault"
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
                colors = CardDefaults.cardColors(containerColor = Color(0xFF171821)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isSetupMode) "Создать защищённый vault" else "Разблокировать vault",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSetupMode) "Задайте пароль для секретной папки. Файлы будут шифроваться и не будут видны в обычном проводнике." else "Введите пароль для доступа к зашифрованным файлам.",
                        color = Color(0xFFB6BDCB),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Пароль") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonOrange,
                            unfocusedBorderColor = Color(0xFF2A2B35),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonOrange
                        )
                    )
                    if (statusText.isNotBlank()) {
                        Text(statusText, color = Color(0xFFFFD166), fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35))
                        ) {
                            Text("Назад")
                        }
                        Button(
                            onClick = { unlockVault() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                        ) {
                            Text("OK")
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
            .padding(20.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Secure Vault", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                ) {
                    Text("Назад")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { filePicker.launch(arrayOf("*/*")) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Добавить файл")
                }
                Button(
                    onClick = { isUnlocked = false; statusText = "Vault закрыт" },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (statusText.isNotBlank()) {
                Text(statusText, color = Color(0xFFFFD166), fontSize = 12.sp)
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (files.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF171821)),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Пока нет файлов в vault.",
                        color = Color(0xFFB6BDCB),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(18.dp)
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(files) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF171821)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = item.displayName,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Row {
                                    Button(
                                        onClick = {
                                            try {
                                                val decrypted = vaultRepository.openFile(item.id, password)
                                                onOpenVaultFile(decrypted.absolutePath)
                                            } catch (_: Exception) {
                                                statusText = "Не удалось открыть файл. Проверьте пароль."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                                    ) {
                                        Text("Открыть")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            vaultRepository.deleteFile(item.id)
                                            files = vaultRepository.listItems()
                                            statusText = "Файл удалён из vault"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B3C4B))
                                    ) {
                                        Text("Удалить")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Context.readBytesFromUri(uri: Uri): ByteArray? {
    return try {
        contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }
}

private fun Context.writeBytesToFile(path: String, bytes: ByteArray) {
    File(path).writeBytes(bytes)
}

private fun Context.safeFileName(name: String): String = name.ifBlank { "vault_file" }
