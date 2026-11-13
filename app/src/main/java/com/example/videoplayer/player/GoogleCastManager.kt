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
        val remoteClient = cast.sessionManager.currentCastSession?.remoteMediaClient
            ?: return CastStartResult.NOT_CONNECTED

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
                onResult(result.status.isSuccess)
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
        return when {
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".m3u8") -> "application/x-mpegURL"
            path.endsWith(".mpd") -> "application/dash+xml"
            path.endsWith(".mov") -> "video/quicktime"
            else -> "video/mp4"
        }
    }

    private companion object {
        const val TAG = "GoogleCastManager"
    }
}
