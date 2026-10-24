package com.example.videoplayer.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val title: String,
    val displayName: String,
    val uri: Uri,
    val duration: Long,
    val resolution: String,
    val width: Int,
    val height: Int,
    val dateAdded: Long,
    val size: Long,
    val thumbnailResId: Int? = null
)
