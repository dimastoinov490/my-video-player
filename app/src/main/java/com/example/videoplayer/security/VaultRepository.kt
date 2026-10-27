package com.example.videoplayer.security

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream

private const val VAULT_DIR_NAME = "secure_vault"
private const val TAG = "SecureVaultRepo"

class SecureVaultRepository(private val context: Context) {
    private val rootDir = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }

    private val prefs: android.content.SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_vault_state",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "EncryptedSharedPreferences error, falling back to standard prefs", e)
            context.getSharedPreferences("secure_vault_fallback", Context.MODE_PRIVATE)
        }
    }

    data class SecureVaultItem(
        val id: String,
        val displayName: String,
        val file: File
    )

    fun isPasswordConfigured(): Boolean {
        val salt = prefs.getString("vault_password_salt", null)
        val hash = prefs.getString("vault_password_hash", null)
        return !salt.isNullOrBlank() && !hash.isNullOrBlank()
    }

    fun setupPassword(password: String) {
        val salt = VaultCrypto.generateSalt()
        val hash = VaultCrypto.hashPassword(password, salt)
        prefs.edit()
            .putString("vault_password_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("vault_password_hash", Base64.encodeToString(hash, Base64.NO_WRAP))
            .apply()
    }

    fun verifyPassword(password: String): Boolean {
        val saltText = prefs.getString("vault_password_salt", null) ?: return false
        val hashText = prefs.getString("vault_password_hash", null) ?: return false
        return try {
            val salt = Base64.decode(saltText, Base64.NO_WRAP)
            val hash = Base64.decode(hashText, Base64.NO_WRAP)
            VaultCrypto.passwordMatches(password, salt, hash)
        } catch (e: Exception) {
            Log.e(TAG, "Password verification failed", e)
            false
        }
    }

    fun addStream(input: java.io.InputStream, password: String, displayName: String): String {
        if (!verifyPassword(password)) {
            throw IllegalStateException("Неверный пароль хранилища")
        }

        val id = UUID.randomUUID().toString()
        val targetFile = File(rootDir, "$id.enc")
        val salt = VaultCrypto.generateSalt()
        val iv = VaultCrypto.generateIv()
        val cipher = VaultCrypto.getEncryptCipher(password, salt, iv)

        FileOutputStream(targetFile).use { fos ->
            fos.write(salt)
            fos.write(iv)
            CipherOutputStream(fos, cipher).use { cos ->
                input.use { ins ->
                    ins.copyTo(cos, bufferSize = 256 * 1024)
                }
            }
        }

        prefs.edit().putString("name_$id", displayName).apply()
        recordHiddenItem(displayName)
        return id
    }

    fun addFile(source: File, password: String, displayName: String): String {
        return addStream(source.inputStream(), password, displayName)
    }

    fun recordHiddenItem(identifier: String) {
        val current = getHiddenItems().toMutableSet()
        val clean = identifier.trim().lowercase()
        current.add(clean)
        val withoutExt = clean.substringBeforeLast('.')
        if (withoutExt.isNotBlank()) current.add(withoutExt)
        val lastSegment = clean.substringAfterLast('/')
        if (lastSegment.isNotBlank()) current.add(lastSegment)
        prefs.edit().putStringSet("vault_hidden_items", current).apply()
    }

    fun getHiddenItems(): Set<String> {
        return prefs.getStringSet("vault_hidden_items", emptySet()) ?: emptySet()
    }

    fun removeHiddenItem(identifier: String) {
        val current = getHiddenItems().toMutableSet()
        val clean = identifier.trim().lowercase()
        current.remove(clean)
        current.remove(clean.substringBeforeLast('.'))
        current.remove(clean.substringAfterLast('/'))
        prefs.edit().putStringSet("vault_hidden_items", current).apply()
    }

    fun isItemHidden(id: String? = null, uri: String? = null, path: String? = null, name: String? = null): Boolean {
        val hidden = getHiddenItems()
        val vaultNames = listItems().map { it.displayName.trim().lowercase() }.toSet()
        if (hidden.isEmpty() && vaultNames.isEmpty()) return false

        if (id != null && hidden.contains(id.trim().lowercase())) return true
        if (uri != null) {
            val uLower = uri.trim().lowercase()
            if (hidden.contains(uLower) || hidden.contains(uLower.substringAfterLast('/'))) return true
        }
        if (name != null) {
            val nameLower = name.trim().lowercase()
            if (hidden.contains(nameLower) || vaultNames.contains(nameLower)) return true
            val withoutExt = nameLower.substringBeforeLast('.')
            if (withoutExt.isNotBlank() && (hidden.contains(withoutExt) || vaultNames.contains(withoutExt))) return true
        }
        if (path != null) {
            val pathLower = path.trim().lowercase()
            if (hidden.contains(pathLower)) return true
            val fileName = pathLower.substringAfterLast('/')
            if (hidden.contains(fileName) || vaultNames.contains(fileName)) return true
            if (hidden.any { it.length > 3 && (pathLower.endsWith(it) || pathLower.contains(it)) }) return true
        }
        return false
    }

    fun listItems(): List<SecureVaultItem> {
        val files = rootDir.listFiles()?.filter { it.isFile && it.name.endsWith(".enc") } ?: return emptyList()
        return files.map { file ->
            val id = file.nameWithoutExtension
            val displayName = prefs.getString("name_$id", id) ?: id
            SecureVaultItem(id, displayName, file)
        }
    }

    fun openFile(id: String, password: String): File {
        if (!verifyPassword(password)) {
            throw IllegalStateException("Неверный пароль хранилища")
        }

        val source = File(rootDir, "$id.enc")
        if (!source.exists()) {
            throw IllegalStateException("Файл не найден в хранилище")
        }

        val outFile = File(context.cacheDir, "$id.decrypted")
        FileInputStream(source).use { fis ->
            val salt = ByteArray(16)
            var read = fis.read(salt)
            if (read < 16) throw IllegalStateException("Поврежденный файл хранилища")
            val iv = ByteArray(16)
            read = fis.read(iv)
            if (read < 16) throw IllegalStateException("Поврежденный файл хранилища")

            val cipher = VaultCrypto.getDecryptCipher(password, salt, iv)
            CipherInputStream(fis, cipher).use { cis ->
                FileOutputStream(outFile).use { fos ->
                    cis.copyTo(fos, bufferSize = 256 * 1024)
                }
            }
        }

        return outFile
    }

    fun deleteFile(id: String): Boolean {
        val file = File(rootDir, "$id.enc")
        val displayName = prefs.getString("name_$id", null)
        val deleted = file.delete()
        if (displayName != null) {
            removeHiddenItem(displayName)
        }
        prefs.edit().remove("name_$id").apply()
        return deleted
    }

    fun clearTempDecryptedFiles() {
        try {
            context.cacheDir.listFiles()?.filter { it.name.endsWith(".decrypted") }?.forEach { it.delete() }
        } catch (_: Exception) {}
    }
}
