package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext

enum class CastStartResult {
    STARTED,
    NOT_CONNECTED,
    UNSUPPORTED_URI,
    UNAVAILABLE
}

/**
 * Small sender-side bridge. Discovery and route selection are handled by the
 * official Cast framework; this class only loads a supported media URL into the
 * active receiver session.
 */
class GoogleCastManager(context: Context) {
    private val castContext: CastContext? = try {
        CastContext.getSharedInstance(context.applicationContext)
    } catch (error: Exception) {
        Log.w(TAG, "Google Cast framework is unavailable", error)
        null
    }

    fun castVideo(
        source: String,
        title: String,
        onResult: (Boolean) -> Unit
    ): CastStartResult {
        val cast = castContext ?: return CastStartResult.UNAVAILABLE
        val mediaUrl = normalizeCastUrl(source) ?: return CastStartResult.UNSUPPORTED_URI
        val remoteClient = try {
            cast.sessionManager.currentCastSession?.remoteMediaClient
        } catch (error: Exception) {
            Log.e(TAG, "Unable to access the active Google Cast session", error)
            return CastStartResult.UNAVAILABLE
        } ?: return CastStartResult.NOT_CONNECTED

        return try {
            val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
                putString(MediaMetadata.KEY_TITLE, title.ifBlank { "Video" })
            }
            val mediaInfo = MediaInfo.Builder(mediaUrl)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(contentTypeFor(mediaUrl))
                .setMetadata(metadata)
                .build()
            val request = MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .build()

            remoteClient.load(request).setResultCallback { result ->
                val succeeded = try {
                    result.status.isSuccess
                } catch (error: Exception) {
                    Log.e(TAG, "Unable to read Google Cast load result", error)
                    false
                }
                if (!succeeded) {
                    Log.w(
                        TAG,
                        "Cast receiver rejected media: code=${result.status.statusCode}, message=${result.status.statusMessage}"
                    )
                }
                try {
                    onResult(succeeded)
                } catch (callbackError: Exception) {
                    Log.e(TAG, "Cast result callback failed", callbackError)
                }
            }
            CastStartResult.STARTED
        } catch (error: Exception) {
            Log.e(TAG, "Unable to send media to Google Cast receiver", error)
            CastStartResult.UNAVAILABLE
        }
    }

    private fun normalizeCastUrl(source: String): String? {
        val candidate = if (source.startsWith("sample://")) {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        } else {
            source
        }
        val uri = runCatching { Uri.parse(candidate) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.host.isNullOrBlank()) return null
        return candidate
    }

    private fun contentTypeFor(url: String): String {
        val path = Uri.parse(url).path.orEmpty().lowercase()
        val fileName = path.substringAfterLast('/')
        val extension = if ('.' in fileName) fileName.substringAfterLast('.') else ""
        return when (extension) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "m3u8" -> "application/x-mpegURL"
            "mpd" -> "application/dash+xml"
            "mov", "qt" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mpeg", "mpg", "mpe" -> "video/mpeg"
            "ts", "m2ts" -> "video/mp2t"
            "3gp", "3gpp" -> "video/3gpp"
            "ogv" -> "video/ogg"
            "flv" -> "video/x-flv"
            "wmv" -> "video/x-ms-wmv"
            // Extensionless stream URLs commonly expose MP4-compatible media.
            "" -> "video/mp4"
            // Do not mislabel a known, different file type as MP4.
            else -> "application/octet-stream"
        }
    }

    private companion object {
        const val TAG = "GoogleCastManager"
    }
}
