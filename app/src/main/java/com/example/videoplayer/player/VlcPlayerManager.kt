package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
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

    val libVLC = LibVLC(context, options)
    val mediaPlayer = MediaPlayer(libVLC)

    fun play(url: String) {
        try {
            val media = Media(libVLC, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
        mediaPlayer.stop()
        mediaPlayer.release()
        libVLC.release()
    }
}
