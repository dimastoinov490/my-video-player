package com.example.videoplayer.model

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.example.videoplayer.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreHelper {

    fun getSampleVideos(): List<VideoItem> {
        return listOf(
            VideoItem(
                id = -1L,
                title = "2026 09 19 172839.mp4",
                displayName = "2026 09 19 172839.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
                duration = 14000L,
                resolution = "720x480",
                width = 720,
                height = 480,
                dateAdded = 1789776000L,
                size = 12897485L,
                thumbnailResId = R.drawable.thumb_western
            ),
            VideoItem(
                id = -2L,
                title = "Django_osvobojdenn.mp4",
                displayName = "Django_osvobojdennyi_HDRip_Kinosimka RU1",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                duration = 9922000L,
                resolution = "1280x720",
                width = 1280,
                height = 720,
                dateAdded = 1789689600L,
                size = 1503238553L,
                thumbnailResId = R.drawable.thumb_western
            ),
            VideoItem(
                id = -3L,
                title = "Izgoi_Odin_Zvyozdnyy.mp4",
                displayName = "Izgoi_Odin_Zvyozdnyy.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
                duration = 8037000L,
                resolution = "1280x720",
                width = 1280,
                height = 720,
                dateAdded = 1789516800L,
                size = 1023410176L,
                thumbnailResId = R.drawable.thumb_scifi
            ),
            VideoItem(
                id = -4L,
                title = "Solo A Star Wars Story.mp4",
                displayName = "Solo A Star Wars Story.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
                duration = 8086000L,
                resolution = "1280x720",
                width = 1280,
                height = 720,
                dateAdded = 1789344000L,
                size = 1288490188L,
                thumbnailResId = R.drawable.thumb_scifi
            ),
            VideoItem(
                id = -5L,
                title = "Природа_4K.mp4",
                displayName = "Природа_4K.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
                duration = 5530000L,
                resolution = "3840x2160",
                width = 3840,
                height = 2160,
                dateAdded = 1789171200L,
                size = 3006477107L,
                thumbnailResId = R.drawable.thumb_nature
            ),
            VideoItem(
                id = -6L,
                title = "Киберпанк.mp4",
                displayName = "Киберпанк.mp4",
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"),
                duration = 2912000L,
                resolution = "1280x720",
                width = 1280,
                height = 720,
                dateAdded = 1788998400L,
                size = 778043392L,
                thumbnailResId = R.drawable.thumb_cyberpunk
            )
        )
    }

    suspend fun getVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val videoList = mutableListOf<VideoItem>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val widthCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Video $id"
                    val displayName = it.getString(nameCol) ?: title
                    val duration = it.getLong(durCol)
                    val width = it.getInt(widthCol)
                    val height = it.getInt(heightCol)
                    val dateAdded = it.getLong(dateCol)
                    val size = it.getLong(sizeCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "HD"

                    videoList.add(
                        VideoItem(
                            id = id,
                            title = title,
                            displayName = displayName,
                            uri = contentUri,
                            duration = duration,
                            resolution = resolution,
                            width = width,
                            height = height,
                            dateAdded = dateAdded,
                            size = size
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (videoList.isEmpty()) {
            getSampleVideos()
        } else {
            videoList
        }
    }

    suspend fun loadThumbnail(context: Context, id: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (id < 0) return@withContext null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                context.contentResolver.loadThumbnail(contentUri, Size(320, 180), null)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getSampleAudios(): List<AudioItem> {
        return listOf(
            AudioItem(
                id = -101L,
                title = "Night City Cyberpunk Theme",
                artist = "Marcin Przybyłowicz",
                album = "Cyberpunk 2077 OST",
                duration = 236000L,
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"),
                size = 5643000L,
                dateAdded = 1789776000L
            ),
            AudioItem(
                id = -102L,
                title = "Time (Inception Suite)",
                artist = "Hans Zimmer",
                album = "Inception OST",
                duration = 275000L,
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
                size = 6540000L,
                dateAdded = 1789689600L
            ),
            AudioItem(
                id = -103L,
                title = "Midnight Synthwave Ride",
                artist = "Kavinsky & The Midnight",
                album = "Outrun Days",
                duration = 212000L,
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
                size = 5088000L,
                dateAdded = 1789516800L
            ),
            AudioItem(
                id = -104L,
                title = "Django Unchained Title Theme",
                artist = "Ennio Morricone",
                album = "Django Soundtrack",
                duration = 248000L,
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
                size = 5950000L,
                dateAdded = 1789344000L
            ),
            AudioItem(
                id = -105L,
                title = "Relaxing Lo-Fi Study Beats",
                artist = "Chillhop Music",
                album = "Cafe Nights",
                duration = 184000L,
                uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
                size = 4416000L,
                dateAdded = 1789171200L
            )
        )
    }

    suspend fun getAudios(context: Context): List<AudioItem> = withContext(Dispatchers.IO) {
        val audioList = mutableListOf<AudioItem>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        try {
            val cursor = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )

            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = it.getLong(idCol)
                    val title = it.getString(titleCol) ?: "Audio $id"
                    val artist = it.getString(artistCol) ?: "Unknown Artist"
                    val album = it.getString(albumCol) ?: "Unknown Album"
                    val duration = it.getLong(durCol)
                    val size = it.getLong(sizeCol)
                    val dateAdded = it.getLong(dateCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    audioList.add(
                        AudioItem(
                            id = id,
                            title = title,
                            artist = artist,
                            album = album,
                            duration = duration,
                            uri = contentUri,
                            size = size,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (audioList.isEmpty()) {
            getSampleAudios()
        } else {
            audioList
        }
    }
}
