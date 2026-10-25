package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcPlayerManager(context: Context) {
    private val options = arrayListOf(
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--rtsp-tcp",
        "-vvv"
    )

    val libVLC: LibVLC = LibVLC(context, options)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)

    var onStatusChanged: ((String) -> Unit)? = null
    var onTimeChanged: ((Long, Long) -> Unit)? = null

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Buffering -> {
                onStatusChanged?.invoke("Buffering: ${event.buffering}%")
            }
            MediaPlayer.Event.Playing -> {
                onStatusChanged?.invoke("Playing")
            }
            MediaPlayer.Event.Paused -> {
                onStatusChanged?.invoke("Paused")
            }
            MediaPlayer.Event.Stopped -> {
                onStatusChanged?.invoke("Stopped")
            }
            MediaPlayer.Event.EndReached -> {
                onStatusChanged?.invoke("EndReached")
            }
            MediaPlayer.Event.EncounteredError -> {
                onStatusChanged?.invoke("Error")
            }
            MediaPlayer.Event.TimeChanged -> {
                val time = mediaPlayer.time
                val length = mediaPlayer.length
                onTimeChanged?.invoke(time, length)
            }
        }
    }

    init {
        mediaPlayer.setEventListener(eventListener)
    }

    fun attachSurface(surfaceView: SurfaceView) {
        val vout = mediaPlayer.vlcVout
        vout.setVideoView(surfaceView)
        vout.attachViews()
    }

    fun detachSurface() {
        val vout = mediaPlayer.vlcVout
        vout.detachViews()
    }

    fun updateWindowSize(width: Int, height: Int) {
        mediaPlayer.vlcVout.setWindowSize(width, height)
    }

    fun playUrl(urlOrUri: String) {
        try {
            val media = if (urlOrUri.startsWith("http://") || urlOrUri.startsWith("https://") || urlOrUri.startsWith("rtsp://") || urlOrUri.startsWith("ftp://")) {
                Media(libVLC, Uri.parse(urlOrUri))
            } else if (urlOrUri.startsWith("content://") || urlOrUri.startsWith("file://")) {
                Media(libVLC, Uri.parse(urlOrUri))
            } else {
                Media(libVLC, urlOrUri)
            }
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (e: Exception) {
            onStatusChanged?.invoke("Error: ${e.message}")
        }
    }

    fun seekTo(timeMs: Long) {
        if (mediaPlayer.isSeekable) {
            mediaPlayer.time = timeMs.coerceAtLeast(0)
        }
    }

    fun seekRelative(offsetMs: Long) {
        if (mediaPlayer.isSeekable) {
            val newTime = (mediaPlayer.time + offsetMs).coerceIn(0, mediaPlayer.length)
            mediaPlayer.time = newTime
        }
    }

    fun setRate(rate: Float) {
        mediaPlayer.rate = rate
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.play()
        }
    }

    fun stop() {
        mediaPlayer.stop()
    }

    fun release() {
        mediaPlayer.setEventListener(null)
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }
}
