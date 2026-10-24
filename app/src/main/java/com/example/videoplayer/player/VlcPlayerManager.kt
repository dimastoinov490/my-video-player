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
        "-v"
    )

    val libVLC: LibVLC = LibVLC(context.applicationContext, options)
    val mediaPlayer: MediaPlayer = MediaPlayer(libVLC)

    var onStatusChanged: ((String) -> Unit)? = null
    var onTimeChanged: ((Long, Long) -> Unit)? = null

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Playing -> onStatusChanged?.invoke("Playing")
            MediaPlayer.Event.Paused -> onStatusChanged?.invoke("Paused")
            MediaPlayer.Event.Stopped -> onStatusChanged?.invoke("Stopped")
            MediaPlayer.Event.EndReached -> onStatusChanged?.invoke("End Reached")
            MediaPlayer.Event.EncounteredError -> onStatusChanged?.invoke("Error: Encountered Error")
            MediaPlayer.Event.Buffering -> onStatusChanged?.invoke("Buffering: ${event.buffering}%")
            MediaPlayer.Event.TimeChanged -> onTimeChanged?.invoke(mediaPlayer.time, mediaPlayer.length)
            MediaPlayer.Event.PositionChanged -> onTimeChanged?.invoke(mediaPlayer.time, mediaPlayer.length)
            else -> Unit
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

    fun playUrl(context: Context, urlOrUri: String) {
        val normalized = urlOrUri.trim()
        if (normalized.isEmpty()) {
            onStatusChanged?.invoke("Error: empty URL")
            return
        }

        try {
            mediaPlayer.stop()
            val media = Media(libVLC, Uri.parse(normalized))
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            onStatusChanged?.invoke("Starting playback...")
        } catch (e: Exception) {
            onStatusChanged?.invoke("Error: ${e.localizedMessage ?: "unknown"}")
            e.printStackTrace()
        }
    }

    fun setAspectRatio(ratio: String) {
        try {
            mediaPlayer.setAspectRatio(ratio)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
