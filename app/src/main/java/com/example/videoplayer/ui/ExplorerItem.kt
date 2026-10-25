package com.example.videoplayer.ui

import java.util.Locale

data class ExplorerItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val isVideo: Boolean = false,
    val isAudio: Boolean = false,
    val subItemCount: Int = 0,
    val lastModified: Long = 0L
) {
    fun getFormattedSize(): String {
        if (isDirectory) {
            return "$subItemCount папок/файлов"
        }
        val mb = size / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }

    fun getFileExtension(): String {
        val idx = name.lastIndexOf('.')
        if (idx < 0 || idx >= name.length - 1) {
            return if (isDirectory) "DIR" else "FILE"
        }
        return name.substring(idx + 1).uppercase(Locale.ROOT)
    }
}
