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

    fun getSampleVideos(): List<VideoItem> = listOf(
        VideoItem(
            id = -1L,
            title = "2026 09 19 172839.mp4",
            displayName = "2026 09 19 172839.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            duration = 14_000L,
            resolution = "720x480",
            width = 720,
            height = 480,
            dateAdded = 1789776000L,
            size = 12_897_485L,
            thumbnailResId = R.drawable.thumb_western
        ),
        VideoItem(
            id = -2L,
            title = "Django_osvobojdenn.mp4",
            displayName = "Django_osvobojdennyi_HDRip_Kinosimka RU1",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            duration = 9_922_000L,
            resolution = "1280x720",
            width = 1280,
            height = 720,
            dateAdded = 1789689600L,
            size = 1_503_238_553L,
            thumbnailResId = R.drawable.thumb_western
        ),
        VideoItem(
            id = -3L,
            title = "Izgoi_Odin_Zvyozdnyy.mp4",
            displayName = "Izgoi_Odin_Zvyozdnyy.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
            duration = 8_037_000L,
            resolution = "1280x720",
            width = 1280,
            height = 720,
            dateAdded = 1789516800L,
            size = 1_023_410_176L,
            thumbnailResId = R.drawable.thumb_scifi
        ),
        VideoItem(
            id = -4L,
            title = "Solo A Star Wars Story.mp4",
            displayName = "Solo A Star Wars Story.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
            duration = 8_086_000L,
            resolution = "1280x720",
            width = 1280,
            height = 720,
            dateAdded = 1789344000L,
            size = 1_288_490_188L,
            thumbnailResId = R.drawable.thumb_scifi
        ),
        VideoItem(
            id = -5L,
            title = "Природа_4K.mp4",
            displayName = "Природа_4K.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4"),
            duration = 5_530_000L,
            resolution = "3840x2160",
            width = 3840,
            height = 2160,
            dateAdded = 1789171200L,
            size = 3_006_477_107L,
            thumbnailResId = R.drawable.thumb_nature
        ),
        VideoItem(
            id = -6L,
            title = "Киберпанк.mp4",
            displayName = "Киберпанк.mp4",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"),
            duration = 2_912_000L,
            resolution = "1280x720",
            width = 1280,
            height = 720,
            dateAdded = 1788998400L,
            size = 778_043_392L,
            thumbnailResId = R.drawable.thumb_cyberpunk
        )
    )

    suspend fun getVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.RESOLUTION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.SIZE
            )

            val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val displayCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val resolutionCol = cursor.getColumnIndex(MediaStore.Video.Media.RESOLUTION)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val displayName = cursor.getString(displayCol) ?: "video.mp4"
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: displayName else displayName
                    val duration = cursor.getLong(durationCol)
                    val resolution = if (resolutionCol != -1) cursor.getString(resolutionCol) ?: "" else ""
                    val width = if (widthCol != -1) cursor.getInt(widthCol) else 0
                    val height = if (heightCol != -1) cursor.getInt(heightCol) else 0
                    val dateAdded = cursor.getLong(dateCol)
                    val size = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L

                    val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        VideoItem(
                            id = id,
                            title = title,
                            displayName = displayName,
                            uri = uri,
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
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            getSampleVideos()
        } else {
            list
        }
    }

    suspend fun loadThumbnail(context: Context, id: Long): Bitmap? = withContext(Dispatchers.IO) {
        if (id < 0) return@withContext null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                context.contentResolver.loadThumbnail(uri, Size(320, 240), null)
            } else {
                MediaStore.Video.Thumbnails.getThumbnail(
                    context.contentResolver,
                    id,
                    MediaStore.Video.Thumbnails.MINI_KIND,
                    null
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getSampleAudios(): List<AudioItem> = listOf(
        AudioItem(
            id = -101L,
            title = "Night City Cyberpunk Theme",
            artist = "Marcin Przybyłowicz",
            album = "Cyberpunk 2077 OST",
            duration = 236_000L,
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/SubaruOutbackSeeTheWorld.mp4"),
            size = 5_643_000L,
            dateAdded = 1789776000L
        ),
        AudioItem(
            id = -102L,
            title = "Time (Inception Suite)",
            artist = "Hans Zimmer",
            album = "Inception OST",
            duration = 275_000L,
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"),
            size = 6_540_000L,
            dateAdded = 1789689600L
        ),
        AudioItem(
            id = -103L,
            title = "Midnight Synthwave Ride",
            artist = "Kavinsky & The Midnight",
            album = "Outrun Days",
            duration = 212_000L,
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"),
            size = 5_088_000L,
            dateAdded = 1789516800L
        ),
        AudioItem(
            id = -104L,
            title = "Django Unchained Title Theme",
            artist = "Ennio Morricone",
            album = "Django Soundtrack",
            duration = 248_000L,
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"),
            size = 5_950_000L,
            dateAdded = 1789344000L
        ),
        AudioItem(
            id = -105L,
            title = "Relaxing Lo-Fi Study Beats",
            artist = "Chillhop Music",
            album = "Cafe Nights",
            duration = 184_000L,
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"),
            size = 4_416_000L,
            dateAdded = 1789171200L
        )
    )

    suspend fun getAudios(context: Context): List<AudioItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<AudioItem>()
        try {
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED
            )

            val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

            context.contentResolver.query(
                collection,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol != -1) cursor.getString(titleCol) ?: "Аудиозапись" else "Аудиозапись"
                    val artist = if (artistCol != -1) cursor.getString(artistCol) ?: "Неизвестный исполнитель" else "Неизвестный исполнитель"
                    val album = if (albumCol != -1) cursor.getString(albumCol) ?: "" else ""
                    val duration = cursor.getLong(durationCol)
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol)

                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    list.add(
                        AudioItem(
                            id = id,
                            title = title,
                            artist = if (artist == "<unknown>") "Неизвестный исполнитель" else artist,
                            album = album,
                            duration = duration,
                            uri = uri,
                            size = size,
                            dateAdded = dateAdded
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            getSampleAudios()
        } else {
            list
        }
    }
}
