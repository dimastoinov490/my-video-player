package com.example.videoplayer.model

data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val iconType: String = "playlist",
    val itemUris: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)
