package com.example.videoplayer.model

import android.net.Uri
import java.util.Locale

data class AudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val uri: Uri,
    val size: Long,
    val dateAdded: Long
) {
    fun getFormattedDuration(): String {
        val totalSec = duration / 1000
        val m = totalSec / 60
        val s = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }

    fun getFormattedSize(): String {
        val mb = size / (1024.0 * 1024.0)
        return if (mb >= 1024.0) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.1f MB", mb)
        }
    }
}
