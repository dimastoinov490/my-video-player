package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

class VlcPlayerManager(context: Context) {
    private val options = ArrayList<String>().apply {
        add("--no-drop-late-frames")
        add("--no-skip-frames")
        add("--rtsp-tcp")
        add("-v")
    }

    val libVLC = LibVLC(context.applicationContext, options)
    val mediaPlayer = MediaPlayer(libVLC)

    var onStatusChanged: ((String) -> Unit)? = null
    var onTimeChanged: ((Long, Long) -> Unit)? = null

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
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
                onStatusChanged?.invoke("End Reached")
            }
            MediaPlayer.Event.EncounteredError -> {
                onStatusChanged?.invoke("Error: Encountered Error")
            }
            MediaPlayer.Event.Buffering -> {
                onStatusChanged?.invoke("Buffering: ${event.buffering}%")
            }
            MediaPlayer.Event.TimeChanged -> {
                onTimeChanged?.invoke(mediaPlayer.time, mediaPlayer.length)
            }
            MediaPlayer.Event.PositionChanged -> {
                onTimeChanged?.invoke(mediaPlayer.time, mediaPlayer.length)
            }
            else -> {}
        }
    }

    init {
        mediaPlayer.setEventListener(eventListener)
    }

    fun attachSurface(surfaceView: SurfaceView) {
        val vout = mediaPlayer.vlcVout
        if (!vout.areViewsAttached()) {
            vout.setVideoView(surfaceView)
            vout.attachViews()
        }
    }

    fun detachSurface() {
        val vout = mediaPlayer.vlcVout
        if (vout.areViewsAttached()) {
            vout.detachViews()
        }
    }

    fun updateWindowSize(width: Int, height: Int) {
        mediaPlayer.updateVideoSurfaces()
        mediaPlayer.vlcVout.setWindowSize(width, height)
    }

    fun playUrl(context: Context, urlOrUri: String) {
        val normalized = urlOrUri.trim()
        if (normalized.isEmpty()) {
            onStatusChanged?.invoke("Error: empty URL")
            return
        }

        try {
            mediaPlayer.stop()
            val media = if (normalized.startsWith("content://")) {
                val uri = Uri.parse(normalized)
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    Media(libVLC, pfd.fileDescriptor)
                } else {
                    Media(libVLC, uri)
                }
            } else {
                Media(libVLC, Uri.parse(normalized))
            }

            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            onStatusChanged?.invoke("Playing")
        } catch (e: Exception) {
            e.printStackTrace()
            onStatusChanged?.invoke("Error: ${e.localizedMessage}")
        }
    }

    fun togglePlayback() {
        if (mediaPlayer.isPlaying) {
            pause()
        } else {
            resume()
        }
    }

    fun pause() {
        if (mediaPlayer.isPlaying) {
            mediaPlayer.pause()
            onStatusChanged?.invoke("Paused")
        }
    }

    fun resume() {
        if (!mediaPlayer.isPlaying) {
            mediaPlayer.play()
            onStatusChanged?.invoke("Playing")
        }
    }

    fun seekTo(timeMs: Long) {
        mediaPlayer.time = timeMs
    }

    fun seekRelative(offsetMs: Long) {
        val newTime = (mediaPlayer.time + offsetMs).coerceIn(0, mediaPlayer.length)
        mediaPlayer.time = newTime
    }

    fun setRate(rate: Float) {
        try {
            mediaPlayer.rate = rate
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        mediaPlayer.stop()
        onStatusChanged?.invoke("Stopped")
    }

    fun release() {
        try {
            mediaPlayer.stop()
            detachSurface()
            mediaPlayer.release()
            libVLC.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
