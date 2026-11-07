package com.example.videoplayer.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

enum class HttpBroadcastFolder(val id: String, val title: String) {
    ALL("all", "Вся библиотека (Видео и Музыка)"),
    MOVIES("movies", "Папка Movies (Фильмы)"),
    DOWNLOADS("downloads", "Папка Downloads (Загрузки)"),
    DCIM("dcim", "Папка DCIM (Камера и галерея)"),
    SHARE_DIR("share_dir", "Выделенная папка обмена Nexora")
}

object HttpServerConfig {
    private const val PREFS_NAME = "http_server_config"
    private const val KEY_AUTH_REQUIRED = "auth_required"
    private const val KEY_USERNAME = "auth_username"
    private const val KEY_PASSWORD = "auth_password"
    private const val KEY_BROADCAST_FOLDER = "broadcast_folder"
    private const val KEY_PORT = "http_port"

    const val DEFAULT_ADMIN_LOGIN = "admin"
    const val DEFAULT_ADMIN_PASSWORD = "admin"
    const val DEFAULT_USER_LOGIN = "admin"
    const val DEFAULT_USER_PASSWORD = "1234"

    private const val KEY_ADMIN_USERNAME = "admin_username"
    private const val KEY_ADMIN_SALT = "admin_password_salt"
    private const val KEY_ADMIN_HASH = "admin_password_hash"
    private const val KEY_ADMIN_PASS_IS_DEFAULT = "admin_password_is_default"

    private var appContext: Context? = null
    @Volatile private var oldAccountsCleaned = false

    fun init(context: Context) {
        appContext = context.applicationContext
        if (!oldAccountsCleaned) {
            oldAccountsCleaned = true
            try {
                val oldPrefs = context.getSharedPreferences("http_accounts", Context.MODE_PRIVATE)
                if (oldPrefs.all.isNotEmpty()) {
                    oldPrefs.edit().clear().apply()
                }
            } catch (_: Exception) {}
        }
    }

    private fun getPrefs(context: Context): SharedPreferences {
        init(context)
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hashPassword(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, 60000, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }

    fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    fun bytesToHex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    fun getAdminUsername(context: Context): String {
        return getPrefs(context).getString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_LOGIN) ?: DEFAULT_ADMIN_LOGIN
    }

    fun isAdminPasswordDefault(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ADMIN_PASS_IS_DEFAULT, true)
    }

    fun isUserPasswordDefault(context: Context): Boolean {
        return getPassword(context) == DEFAULT_USER_PASSWORD
    }

    @Synchronized
    fun verifyAdminPassword(context: Context, password: String): Boolean {
        val prefs = getPrefs(context)
        var saltHex = prefs.getString(KEY_ADMIN_SALT, null)
        var hashHex = prefs.getString(KEY_ADMIN_HASH, null)

        if (saltHex.isNullOrEmpty() || hashHex.isNullOrEmpty()) {
            val salt = generateSalt()
            val hash = hashPassword(DEFAULT_ADMIN_PASSWORD.toCharArray(), salt)
            saltHex = bytesToHex(salt)
            hashHex = bytesToHex(hash)
            prefs.edit()
                .putString(KEY_ADMIN_SALT, saltHex)
                .putString(KEY_ADMIN_HASH, hashHex)
                .putBoolean(KEY_ADMIN_PASS_IS_DEFAULT, true)
                .apply()
        }

        return try {
            val salt = hexToBytes(saltHex)
            val expectedHash = hexToBytes(hashHex)
            val computedHash = hashPassword(password.toCharArray(), salt)
            MessageDigest.isEqual(computedHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    @Synchronized
    fun setAdminCredentials(context: Context, newLogin: String?, newPassword: String?) {
        val editor = getPrefs(context).edit()
        if (!newLogin.isNullOrBlank()) {
            editor.putString(KEY_ADMIN_USERNAME, newLogin.trim())
        }
        if (!newPassword.isNullOrEmpty()) {
            val salt = generateSalt()
            val hash = hashPassword(newPassword.toCharArray(), salt)
            editor.putString(KEY_ADMIN_SALT, bytesToHex(salt))
            editor.putString(KEY_ADMIN_HASH, bytesToHex(hash))
            editor.putBoolean(KEY_ADMIN_PASS_IS_DEFAULT, false)
        }
        editor.apply()
    }

    @Synchronized
    fun setUserCredentials(context: Context, newLogin: String?, newPassword: String?) {
        val editor = getPrefs(context).edit()
        if (!newLogin.isNullOrBlank()) {
            editor.putString(KEY_USERNAME, newLogin.trim())
        }
        if (!newPassword.isNullOrEmpty()) {
            editor.putString(KEY_PASSWORD, newPassword)
        }
        editor.apply()
    }

    @Synchronized
    fun resetAdmin(context: Context) {
        val salt = generateSalt()
        val hash = hashPassword(DEFAULT_ADMIN_PASSWORD.toCharArray(), salt)
        getPrefs(context).edit()
            .putString(KEY_ADMIN_USERNAME, DEFAULT_ADMIN_LOGIN)
            .putString(KEY_ADMIN_SALT, bytesToHex(salt))
            .putString(KEY_ADMIN_HASH, bytesToHex(hash))
            .putBoolean(KEY_ADMIN_PASS_IS_DEFAULT, true)
            .apply()
        LocalHttpShareServer.closeAdminSessions()
    }

    fun isAuthRequired(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTH_REQUIRED, false)
    }

    fun setAuthRequired(context: Context, required: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTH_REQUIRED, required).apply()
        LocalHttpShareServer.clearSessions()
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, DEFAULT_USER_LOGIN) ?: DEFAULT_USER_LOGIN
    }

    fun setUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username.trim()).apply()
        LocalHttpShareServer.clearSessions()
    }

    fun getPassword(context: Context): String {
        return getPrefs(context).getString(KEY_PASSWORD, DEFAULT_USER_PASSWORD) ?: DEFAULT_USER_PASSWORD
    }

    fun setPassword(context: Context, password: String) {
        getPrefs(context).edit().putString(KEY_PASSWORD, password.trim()).apply()
        LocalHttpShareServer.clearSessions()
    }

    fun getBroadcastFolder(context: Context): HttpBroadcastFolder {
        val id = getPrefs(context).getString(KEY_BROADCAST_FOLDER, HttpBroadcastFolder.ALL.id)
        return HttpBroadcastFolder.values().find { it.id == id } ?: HttpBroadcastFolder.ALL
    }

    fun setBroadcastFolder(context: Context, folder: HttpBroadcastFolder) {
        getPrefs(context).edit().putString(KEY_BROADCAST_FOLDER, folder.id).apply()
    }

    fun getPort(context: Context): Int {
        return getPrefs(context).getInt(KEY_PORT, 8765)
    }

    fun setPort(context: Context, port: Int) {
        getPrefs(context).edit().putInt(KEY_PORT, port).apply()
    }

    private const val KEY_PROTOCOL = "http_protocol"
    private const val KEY_IP_MODE = "http_ip_mode"
    private const val KEY_MANUAL_IP = "http_manual_ip"
    private const val KEY_PORT_MODE = "http_port_mode"
    private const val KEY_STRICT = "http_strict"

    fun getProtocol(context: Context): String {
        return getPrefs(context).getString(KEY_PROTOCOL, "http") ?: "http"
    }

    fun setProtocol(context: Context, protocol: String) {
        getPrefs(context).edit().putString(KEY_PROTOCOL, if (protocol.equals("https", ignoreCase = true)) "https" else "http").apply()
    }

    fun getIpMode(context: Context): String {
        return getPrefs(context).getString(KEY_IP_MODE, "auto") ?: "auto"
    }

    fun setIpMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_IP_MODE, if (mode == "manual") "manual" else "auto").apply()
    }

    fun getManualIp(context: Context): String {
        return getPrefs(context).getString(KEY_MANUAL_IP, "") ?: ""
    }

    fun setManualIp(context: Context, ip: String) {
        getPrefs(context).edit().putString(KEY_MANUAL_IP, ip.trim()).apply()
    }

    fun getPortMode(context: Context): String {
        return getPrefs(context).getString(KEY_PORT_MODE, "auto") ?: "auto"
    }

    fun setPortMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_PORT_MODE, if (mode == "manual") "manual" else "auto").apply()
    }

    fun isStrict(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_STRICT, false)
    }

    fun setStrict(context: Context, strict: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_STRICT, strict).apply()
    }

    fun getFolderDirectory(context: Context, folder: HttpBroadcastFolder): File? {
        return when (folder) {
            HttpBroadcastFolder.ALL -> null
            HttpBroadcastFolder.MOVIES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            HttpBroadcastFolder.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            HttpBroadcastFolder.DCIM -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            HttpBroadcastFolder.SHARE_DIR -> File(context.getExternalFilesDir(null), "http_share").apply { mkdirs() }
        }
    }
}
