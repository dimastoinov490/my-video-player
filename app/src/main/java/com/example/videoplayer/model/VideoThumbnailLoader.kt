package com.example.videoplayer.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Base64
import android.util.Log
import android.util.Size
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

object VideoThumbnailLoader {
    private const val TAG = "VideoThumbnailLoader"

    fun getDataUri(context: Context, video: VideoItem): String? {
        return try {
            val cacheDir = File(context.cacheDir, "video_thumbs")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val key = "${video.uri}|${video.size}|${video.dateAdded}"
            val fileName = md5(key) + ".jpg"
            val cacheFile = File(cacheDir, fileName)

            if (cacheFile.exists()) {
                if (cacheFile.length() > 0 && isValidImage(cacheFile)) {
                    try {
                        val bytes = cacheFile.readBytes()
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                        return "data:image/jpeg;base64,$b64"
                    } catch (_: Throwable) {
                        // Fall through and regenerate an unreadable cache entry.
                    }
                }
                // A truncated or invalid cache entry must not be returned as a thumbnail.
                cacheFile.delete()
            }

            var bitmap: Bitmap? = null
            val uri = video.uri

            // 1) On Android 10+ (API 29) for content:// URI
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                try {
                    bitmap = context.contentResolver.loadThumbnail(uri, Size(320, 180), null)
                } catch (_: Throwable) {
                    bitmap = null
                }
            }

            // 2) Fallback to MediaMetadataRetriever
            if (bitmap == null) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    if (uri.scheme == "file") {
                        val path = uri.path
                        if (!path.isNullOrBlank()) {
                            retriever.setDataSource(path)
                        } else {
                            retriever.setDataSource(context, uri)
                        }
                    } else {
                        retriever.setDataSource(context, uri)
                    }

                    val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    val durationMs = durationStr?.toLongOrNull() ?: 0L
                    val timeUs = if (durationMs > 1000L) {
                        (durationMs * 100L).coerceAtLeast(1_000_000L)
                    } else {
                        0L
                    }

                    var rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    if (rawFrame == null) {
                        rawFrame = retriever.frameAtTime
                    }

                    if (rawFrame != null) {
                        val targetW = 320
                        val targetH = (rawFrame.height * (320f / rawFrame.width)).toInt().coerceAtLeast(1)
                        bitmap = Bitmap.createScaledBitmap(rawFrame, targetW, targetH, true)
                        if (rawFrame != bitmap) {
                            rawFrame.recycle()
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaMetadataRetriever failed for ${video.uri}: ${t.message}")
                } finally {
                    try {
                        retriever?.release()
                    } catch (_: Throwable) {}
                }
            }

            if (bitmap == null) return null

            val thumbnail = bitmap ?: return null
            val bytes = try {
                val baos = ByteArrayOutputStream()
                if (!thumbnail.compress(Bitmap.CompressFormat.JPEG, 75, baos)) {
                    return null
                }
                baos.toByteArray()
            } finally {
                if (!thumbnail.isRecycled) thumbnail.recycle()
            }
            if (bytes.isEmpty()) return null

            // Use a unique temporary file so simultaneous requests cannot corrupt
            // each other's cache writes for the same media item.
            val tmpFile = File.createTempFile("$fileName.", ".tmp", cacheDir)
            try {
                tmpFile.writeBytes(bytes)
                if (!tmpFile.renameTo(cacheFile) && !cacheFile.exists()) {
                    Log.w(TAG, "Unable to move thumbnail cache into place for ${video.uri}")
                }
            } finally {
                if (tmpFile.exists()) tmpFile.delete()
            }

            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            "data:image/jpeg;base64,$b64"
        } catch (t: Throwable) {
            Log.e(TAG, "getDataUri error: ${t.message}")
            null
        }
    }

    private fun isValidImage(file: File): Boolean {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            options.outWidth > 0 && options.outHeight > 0
        } catch (_: Throwable) {
            false
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
