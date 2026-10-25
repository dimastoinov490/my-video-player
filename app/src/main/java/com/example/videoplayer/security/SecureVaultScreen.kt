package com.example.videoplayer.security

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.videoplayer.ui.theme.DarkBackground
import com.example.videoplayer.ui.theme.NeonOrange
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun SecureVaultScreen(
    onBack: () -> Unit,
    onOpenVaultFile: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val vaultRepository = remember { SecureVaultRepository(context) }
    val activity = context as? FragmentActivity

    var password by remember { mutableStateOf("") }
    var isSetupMode by remember { mutableStateOf(!vaultRepository.isPasswordConfigured()) }
    var isUnlocked by remember { mutableStateOf(false) }
    var files by remember { mutableStateOf(vaultRepository.listItems()) }
    var statusText by remember { mutableStateOf("") }
    var biometricAvailable by remember { mutableStateOf(false) }
    var lockDeadline by remember { mutableStateOf(System.currentTimeMillis() + 60_000L) }

    LaunchedEffect(Unit) {
        biometricAvailable = BiometricManager.from(context)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(isUnlocked) {
        if (!isUnlocked) return@LaunchedEffect
        while (isUnlocked) {
            val remaining = lockDeadline - System.currentTimeMillis()
            if (remaining <= 0L) {
                vaultRepository.clearTempDecryptedFiles()
                isUnlocked = false
                password = ""
                statusText = "Vault closed automatically after inactivity"
                break
            }
            delay(1000L)
        }
    }

    fun secureLock() {
        vaultRepository.clearTempDecryptedFiles()
        isUnlocked = false
        password = ""
        statusText = "Vault locked"
    }

    fun promptBiometricUnlock() {
        val fragmentActivity = activity ?: run {
            statusText = "Biometric unlock is unavailable on this device"
            return
        }

        if (BiometricManager.from(context)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_SUCCESS
        ) {
            statusText = "Biometric authentication is not supported"
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
                    lockDeadline = System.currentTimeMillis() + 60_000L
                    files = vaultRepository.listItems()
                    statusText = "Vault unlocked with biometric protection"
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    statusText = "Biometric unlock failed: $errString"
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    statusText = "Biometric authentication failed. Try again."
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Secure Vault")
            .setSubtitle("Use biometric authentication")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        prompt.authenticate(promptInfo)
    }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri: Uri? ->
            if (uri == null) {
                statusText = "File not selected"
                return@rememberLauncherForActivityResult
            }
            if (password.isBlank()) {
                statusText = "Set or enter vault password first"
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
                statusText = "File saved to encrypted vault"
                lockDeadline = System.currentTimeMillis() + 60_000L
            } catch (_: Exception) {
                statusText = "Unable to save file to vault"
            } finally {
                tempFile.delete()
            }
        }
    )

    fun unlockVault() {
        if (isSetupMode) {
            if (password.length < 6) {
                statusText = "Password must be at least 6 characters"
                return
            }
            vaultRepository.setupPassword(password)
            isSetupMode = false
            isUnlocked = true
            files = vaultRepository.listItems()
            statusText = "Vault created. Files are stored in encrypted app storage."
            lockDeadline = System.currentTimeMillis() + 60_000L
            return
        }

        if (vaultRepository.verifyPassword(password)) {
            isUnlocked = true
            files = vaultRepository.listItems()
            statusText = "Vault unlocked"
            lockDeadline = System.currentTimeMillis() + 60_000L
        } else {
            statusText = "Incorrect vault password"
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
                        text = if (isSetupMode) "Create Secure Vault" else "Unlock Secure Vault",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isSetupMode) "Strong vault mode: password + AES-GCM + app-private storage + biometric unlock option." else "Enter your password or unlock with biometrics.",
                        color = Color(0xFFB6BDCB),
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Back")
                        }
                        Button(
                            onClick = { unlockVault() },
                            colors = ButtonDefaults.buttonColors(containerColor = NeonOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Unlock")
                        }
                    }
                    if (biometricAvailable) {
                        Button(
                            onClick = { promptBiometricUnlock() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3A3B4A)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Use biometrics")
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
                    onClick = { secureLock() },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                ) {
                    Text("Lock")
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
                    Text("Add file")
                }
                Button(
                    onClick = {
                        secureLock()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2B35)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Close")
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
                        text = "No files in vault yet.",
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
                                                lockDeadline = System.currentTimeMillis() + 60_000L
                                                onOpenVaultFile(decrypted.absolutePath)
                                                statusText = "File opened in app memory only. It is not exposed outside the app."
                                            } catch (_: Exception) {
                                                statusText = "Unable to open file. Check password."
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonOrange)
                                    ) {
                                        Text("Open")
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            vaultRepository.deleteFile(item.id)
                                            files = vaultRepository.listItems()
                                            statusText = "File removed from vault"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B3C4B))
                                    ) {
                                        Text("Delete")
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
