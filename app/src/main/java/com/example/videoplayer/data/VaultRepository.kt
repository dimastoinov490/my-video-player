package com.example.videoplayer.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class VaultRepository(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "vault_secured_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveVaultPin(pin: String) {
        prefs.edit().putString("vault_pin", pin).apply()
    }

    fun getVaultPin(): String? {
        return prefs.getString("vault_pin", null)
    }

    fun isVaultLocked(): Boolean {
        return prefs.getBoolean("vault_locked", true)
    }

    fun setVaultLocked(locked: Boolean) {
        prefs.edit().putBoolean("vault_locked", locked).apply()
    }

    fun addHiddenPath(path: String) {
        val paths = getHiddenPaths().toMutableSet()
        paths.add(path)
        prefs.edit().putStringSet("hidden_paths", paths).apply()
    }

    fun removeHiddenPath(path: String) {
        val paths = getHiddenPaths().toMutableSet()
        paths.remove(path)
        prefs.edit().putStringSet("hidden_paths", paths).apply()
    }

    fun getHiddenPaths(): Set<String> {
        return prefs.getStringSet("hidden_paths", emptySet()) ?: emptySet()
    }
}
