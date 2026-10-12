package com.example.videoplayer.model

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object MediaStoreHelper {

    suspend fun getVideos(context: Context): List<VideoItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<VideoItem>()
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
            MediaStore.Video.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            titleCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.TITLE)
            displayCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            durationCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            resolutionCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RESOLUTION)
            widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = cursor.getString(displayCol) ?: "Video"
                val title = cursor.getString(titleCol) ?: displayName
                val duration = cursor.getLong(durationCol)
                val resolution = cursor.getString(resolutionCol) ?: ""
                val width = cursor.getInt(widthCol)
                val height = cursor.getInt(heightCol)
                val dateAdded = cursor.getLong(dateCol)

                val uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)

                list.add(
                    VideoItem(
                        id = id,
                        title = title,
                        uri = uri,
                        duration = duration,
                        resolution = resolution,
                        width = width,
                        height = height,
                        dateAdded = dateAdded
                    )
                )
            }
        }
        list
    }

    private var idCol = 0
    private var titleCol = 0
    private var displayCol = 0
    private var durationCol = 0
    private var resolutionCol = 0
    private var widthCol = 0
    private var heightCol = 0
    private var dateCol = 0

    suspend fun loadThumbnail(context: Context, id: Long): Bitmap? = withContext(Dispatchers.IO) {
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
}
