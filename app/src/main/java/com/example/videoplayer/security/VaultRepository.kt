package com.example.videoplayer.security

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

private const val VAULT_DIR_NAME = "secure_vault"

class SecureVaultRepository(private val context: Context) {
    private val rootDir = File(context.filesDir, VAULT_DIR_NAME).apply { mkdirs() }

    private val prefs: android.content.SharedPreferences by lazy {
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
        val salt = Base64.decode(saltText, Base64.NO_WRAP)
        val hash = Base64.decode(hashText, Base64.NO_WRAP)
        return VaultCrypto.passwordMatches(password, salt, hash)
    }

    fun addFile(source: File, password: String, displayName: String): String {
        if (!verifyPassword(password)) {
            throw IllegalStateException("Invalid vault password")
        }

        val payload = VaultCrypto.encrypt(source.readBytes(), password)
        val combined = ByteArray(payload.salt.size + payload.iv.size + payload.cipherText.size)
        var offset = 0

        payload.salt.copyInto(combined, offset)
        offset += payload.salt.size
        payload.iv.copyInto(combined, offset)
        offset += payload.iv.size
        payload.cipherText.copyInto(combined, offset)

        val id = UUID.randomUUID().toString()
        val targetFile = File(rootDir, "$id.enc")
        FileOutputStream(targetFile).use { it.write(combined) }

        prefs.edit().putString("name_$id", displayName).apply()
        return id
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
            throw IllegalStateException("Invalid vault password")
        }

        val source = File(rootDir, "$id.enc")
        val raw = source.readBytes()
        val saltSize = 16
        val ivSize = 12
        val salt = raw.copyOfRange(0, saltSize)
        val iv = raw.copyOfRange(saltSize, saltSize + ivSize)
        val cipherText = raw.copyOfRange(saltSize + ivSize, raw.size)

        val decrypted = VaultCrypto.decrypt(
            EncryptedVaultPayload(salt = salt, iv = iv, cipherText = cipherText),
            password
        )

        val outFile = File(context.cacheDir, "$id.decrypted")
        outFile.writeBytes(decrypted)
        return outFile
    }

    fun deleteFile(id: String): Boolean {
        val file = File(rootDir, "$id.enc")
        val deleted = file.delete()
        prefs.edit().remove("name_$id").apply()
        return deleted
    }

    fun clearTempDecryptedFiles() {
        context.cacheDir.listFiles()?.filter { it.name.endsWith(".decrypted") }?.forEach { it.delete() }
    }
}
