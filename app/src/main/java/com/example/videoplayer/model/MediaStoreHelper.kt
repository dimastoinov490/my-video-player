package com.example.videoplayer.model

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import com.example.videoplayer.R
import java.io.File

object MediaStoreHelper {
    private const val TAG = "MediaStoreHelper"

    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts", "m4v")

    fun getVideos(
        context: Context,
        onScanningPath: ((String) -> Unit)? = null
    ): List<VideoItem> {
        val videoList = mutableListOf<VideoItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. MediaStore query
        onScanningPath?.invoke("Индексация MediaStore...")
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATA
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
                val idCol = it.getColumnIndex(MediaStore.Video.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Video.Media.TITLE)
                val nameCol = it.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                val widthCol = it.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = it.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dateCol = it.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = it.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dataCol = it.getColumnIndex(MediaStore.Video.Media.DATA)

                while (it.moveToNext()) {
                    val id = if (idCol >= 0) it.getLong(idCol) else -1L
                    val title = if (titleCol >= 0) it.getString(titleCol) ?: "Video $id" else "Video"
                    val displayName = if (nameCol >= 0) it.getString(nameCol) ?: title else title
                    val duration = if (durCol >= 0) it.getLong(durCol) else 0L
                    val width = if (widthCol >= 0) it.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) it.getInt(heightCol) else 0
                    val dateAdded = if (dateCol >= 0) it.getLong(dateCol) else 0L
                    val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val dataPath = if (dataCol >= 0) it.getString(dataCol) else null

                    val nameLower = displayName.lowercase().trim()
                    val titleLower = title.lowercase().trim()
                    val pathLower = dataPath?.lowercase()?.trim()

                    if (!dataPath.isNullOrBlank()) {
                        seenPaths.add(dataPath.lowercase())
                    }

                    val contentUri = if (id >= 0) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else if (!dataPath.isNullOrBlank()) {
                        Uri.fromFile(File(dataPath))
                    } else {
                        continue
                    }

                    val resolution = if (width > 0 && height > 0) "${width}x${height}" else "HD"
                    videoList.add(
                        VideoItem(
                            id = if (id >= 0) id else videoList.size.toLong() + 1000,
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
        } catch (e: Throwable) {
            Log.e(TAG, "MediaStore query failed: ${e.message}")
        }

        // 2. Direct filesystem scanner fallback for common video folders
        val scanDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            File(Environment.getExternalStorageDirectory(), "Movies"),
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "DCIM/Camera"),
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES),
            context.getExternalFilesDir(null)
        )

        for (dir in scanDirs) {
            if (dir.exists() && dir.isDirectory) {
                onScanningPath?.invoke("Сканирование: ${dir.name}...")
                scanDirectoryRecursive(dir, seenPaths, videoList, maxDepth = 2)
            }
        }

        return videoList
    }

    private fun scanDirectoryRecursive(
        dir: File,
        seenPaths: MutableSet<String>,
        outList: MutableList<VideoItem>,
        maxDepth: Int
    ) {
        if (maxDepth < 0) return
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory && !file.name.startsWith(".") && !file.name.contains("secure_vault")) {
                    scanDirectoryRecursive(file, seenPaths, outList, maxDepth - 1)
                } else if (file.isFile && file.length() > 0 && !file.name.endsWith(".enc")) {
                    val ext = file.extension.lowercase()
                    if (ext in VIDEO_EXTENSIONS) {
                        val pathLower = file.absolutePath.lowercase()
                        if (seenPaths.add(pathLower)) {
                            outList.add(
                                VideoItem(
                                    id = outList.size.toLong() + 5000L,
                                    title = file.nameWithoutExtension,
                                    displayName = file.name,
                                    uri = Uri.fromFile(file),
                                    duration = 0L,
                                    resolution = "MP4",
                                    width = 1920,
                                    height = 1080,
                                    dateAdded = file.lastModified() / 1000L,
                                    size = file.length()
                                )
                            )
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
    }

    fun loadThumbnail(context: Context, id: Long): Bitmap? {
        if (id <= 0L) return null
        return try {
            val contentUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
        } catch (_: Throwable) {
            null
        }
    }

    fun getAudios(context: Context): List<AudioItem> {
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
                val idCol = it.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val dateCol = it.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)

                while (it.moveToNext()) {
                    val id = if (idCol >= 0) it.getLong(idCol) else -1L
                    val title = if (titleCol >= 0) it.getString(titleCol) ?: "Audio $id" else "Audio"
                    val artist = if (artistCol >= 0) it.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol >= 0) it.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durCol >= 0) it.getLong(durCol) else 0L
                    val size = if (sizeCol >= 0) it.getLong(sizeCol) else 0L
                    val dateAdded = if (dateCol >= 0) it.getLong(dateCol) else 0L

                    if (id >= 0) {
                        audioList.add(
                            AudioItem(
                                id = id,
                                title = title,
                                artist = artist,
                                album = album,
                                duration = duration,
                                uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                                size = size,
                                dateAdded = dateAdded
                            )
                        )
                    }
                }
            }
        } catch (_: Throwable) {}

        return audioList
    }

    fun requestMediaScan(context: Context) {
        try {
            val scanDirs = listOfNotNull(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                File(Environment.getExternalStorageDirectory(), "Movies"),
                File(Environment.getExternalStorageDirectory(), "Download"),
                File(Environment.getExternalStorageDirectory(), "DCIM")
            )
            val filePaths = mutableListOf<String>()

            fun collectFiles(dir: File, depth: Int) {
                if (depth < 0 || !dir.exists() || !dir.isDirectory) return
                val files = dir.listFiles() ?: return
                for (f in files) {
                    if (f.name.startsWith(".")) continue
                    if (f.isDirectory) {
                        collectFiles(f, depth - 1)
                    } else if (f.isFile && f.length() > 0) {
                        val ext = f.extension.lowercase()
                        if (ext in VIDEO_EXTENSIONS) {
                            filePaths.add(f.absolutePath)
                        }
                    }
                }
            }

            for (d in scanDirs) {
                collectFiles(d, 3)
            }

            if (filePaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context,
                    filePaths.toTypedArray(),
                    null,
                    null
                )
            }
        } catch (e: Throwable) {
            Log.e(TAG, "requestMediaScan error: ${e.message}")
        }
    }
}
