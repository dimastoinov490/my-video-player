package com.example.videoplayer.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import java.io.File

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

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAuthRequired(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTH_REQUIRED, false)
    }

    fun setAuthRequired(context: Context, required: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTH_REQUIRED, required).apply()
    }

    fun getUsername(context: Context): String {
        return getPrefs(context).getString(KEY_USERNAME, "admin") ?: "admin"
    }

    fun setUsername(context: Context, username: String) {
        getPrefs(context).edit().putString(KEY_USERNAME, username.trim()).apply()
    }

    fun getPassword(context: Context): String {
        return getPrefs(context).getString(KEY_PASSWORD, "1234") ?: "1234"
    }

    fun setPassword(context: Context, password: String) {
        getPrefs(context).edit().putString(KEY_PASSWORD, password.trim()).apply()
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

    fun getFolderDirectory(context: Context, folder: HttpBroadcastFolder): File? {
        return when (folder) {
            HttpBroadcastFolder.ALL -> null // uses full device scan (MediaStore + all public dirs)
            HttpBroadcastFolder.MOVIES -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            HttpBroadcastFolder.DOWNLOADS -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            HttpBroadcastFolder.DCIM -> Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
            HttpBroadcastFolder.SHARE_DIR -> File(context.getExternalFilesDir(null), "http_share").apply { mkdirs() }
        }
    }
}
