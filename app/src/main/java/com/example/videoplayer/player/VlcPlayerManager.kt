package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceView
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import com.example.videoplayer.ui.VideoScaleMode

class VlcPlayerManager(private val context: Context) {
    private val options = arrayListOf(
        "--no-drop-late-frames",
        "--no-skip-frames",
        "--rtsp-tcp",
        "-vvv"
    )

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isReleased = false
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentScaleMode: VideoScaleMode = VideoScaleMode.FIT

    var onStatusChanged: ((String) -> Unit)? = null
    var onTimeChanged: ((Long, Long) -> Unit)? = null

    val isEngineReady: Boolean
        get() = libVLC != null && mediaPlayer != null && !isReleased

    private val eventListener = MediaPlayer.EventListener { event ->
        if (isReleased) return@EventListener
        when (event.type) {
            MediaPlayer.Event.Buffering -> onStatusChanged?.invoke("Buffering: ${event.buffering}%")
            MediaPlayer.Event.Playing -> {
                setVideoScale(currentScaleMode)
                onStatusChanged?.invoke("Playing")
            }
            MediaPlayer.Event.Paused -> onStatusChanged?.invoke("Paused")
            MediaPlayer.Event.Stopped -> onStatusChanged?.invoke("Stopped")
            MediaPlayer.Event.EndReached -> onStatusChanged?.invoke("EndReached")
            MediaPlayer.Event.EncounteredError -> onStatusChanged?.invoke("Error")
            MediaPlayer.Event.TimeChanged -> {
                val mp = mediaPlayer ?: return@EventListener
                try {
                    val time = mp.time
                    val length = mp.length
                    onTimeChanged?.invoke(time, length)
                } catch (_: Throwable) {}
            }
        }
    }

    init {
        try {
            val vlc = LibVLC(context.applicationContext, options)
            val mp = MediaPlayer(vlc)
            mp.setEventListener(eventListener)
            libVLC = vlc
            mediaPlayer = mp
        } catch (e: Throwable) {
            Log.e("VlcPlayerManager", "Failed to initialize LibVLC engine", e)
            libVLC = null
            mediaPlayer = null
            onStatusChanged?.invoke("Error: LibVLC unavailable")
        }
    }

    fun attachSurface(surfaceView: SurfaceView) {
        if (isReleased) return
        try {
            val vout = mediaPlayer?.vlcVout ?: return
            vout.setVideoView(surfaceView)
            vout.attachViews()
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "attachSurface error", e)
        }
    }

    fun detachSurface() {
        if (isReleased) return
        try {
            mediaPlayer?.vlcVout?.detachViews()
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "detachSurface error", e)
        }
    }

    fun updateWindowSize(width: Int, height: Int) {
        if (isReleased) return
        try {
            mediaPlayer?.vlcVout?.setWindowSize(width, height)
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "updateWindowSize error", e)
        }
    }

    fun setVideoScale(mode: VideoScaleMode) {
        currentScaleMode = mode
        if (isReleased) return
        val mp = mediaPlayer ?: return
        try {
            when (mode) {
                VideoScaleMode.FIT -> {
                    try {
                        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    } catch (_: Throwable) {
                        mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
                    }
                    try { mp.aspectRatio = null } catch (_: Throwable) {}
                    try { mp.scale = 0f } catch (_: Throwable) {}
                }
                VideoScaleMode.FILL -> {
                    try {
                        mp.videoScale = MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
                    } catch (_: Throwable) {
                        mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_FIT_SCREEN)
                    }
                    try { mp.aspectRatio = null } catch (_: Throwable) {}
                    try { mp.scale = 0f } catch (_: Throwable) {}
                }
                VideoScaleMode.SIXTEEN_NINE -> {
                    try {
                        mp.videoScale = MediaPlayer.ScaleType.SURFACE_16_9
                    } catch (_: Throwable) {
                        mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_16_9)
                    }
                    try { mp.aspectRatio = "16:9" } catch (_: Throwable) {}
                }
                VideoScaleMode.FOUR_THREE -> {
                    try {
                        mp.videoScale = MediaPlayer.ScaleType.SURFACE_4_3
                    } catch (_: Throwable) {
                        mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_4_3)
                    }
                    try { mp.aspectRatio = "4:3" } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "setVideoScale error", e)
        }
    }

    fun playUrl(urlOrUri: String) {
        if (isReleased) return
        val vlc = libVLC
        val mp = mediaPlayer
        if (vlc == null || mp == null) {
            onStatusChanged?.invoke("Error: LibVLC unavailable")
            return
        }

        val targetUrl = if (urlOrUri.startsWith("sample://")) {
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        } else {
            urlOrUri
        }

        try {
            try {
                currentPfd?.close()
            } catch (_: Throwable) {}
            currentPfd = null

            val media = if (targetUrl.startsWith("content://")) {
                val uri = Uri.parse(targetUrl)
                val pfd = try {
                    context.contentResolver.openFileDescriptor(uri, "r")
                } catch (e: Throwable) {
                    null
                }
                if (pfd != null) {
                    currentPfd = pfd
                    Media(vlc, pfd.fileDescriptor)
                } else {
                    Media(vlc, uri)
                }
            } else if (urlOrUri.startsWith("http://") || urlOrUri.startsWith("https://") || urlOrUri.startsWith("rtsp://") || urlOrUri.startsWith("ftp://") || urlOrUri.startsWith("file://")) {
                Media(vlc, Uri.parse(urlOrUri))
            } else {
                Media(vlc, urlOrUri)
            }

            // Software decoding prevents emulator / GPU SIGSEGV crashes
            media.setHWDecoderEnabled(false, false)
            mp.media = media
            media.release()
            mp.play()
            setVideoScale(currentScaleMode)
        } catch (e: Throwable) {
            Log.e("VlcPlayerManager", "playUrl error", e)
            onStatusChanged?.invoke("Error: ${e.message}")
        }
    }

    fun seekTo(timeMs: Long) {
        if (isReleased) return
        try {
            val mp = mediaPlayer ?: return
            if (mp.isSeekable) {
                mp.time = timeMs.coerceAtLeast(0)
            }
        } catch (_: Throwable) {}
    }

    fun seekRelative(offsetMs: Long) {
        if (isReleased) return
        try {
            val mp = mediaPlayer ?: return
            if (mp.isSeekable) {
                val newTime = (mp.time + offsetMs).coerceIn(0, mp.length)
                mp.time = newTime
            }
        } catch (_: Throwable) {}
    }

    fun setRate(rate: Float) {
        if (isReleased) return
        try {
            mediaPlayer?.rate = rate
        } catch (_: Throwable) {}
    }

    fun pause() {
        if (isReleased) return
        try {
            val mp = mediaPlayer ?: return
            if (mp.isPlaying) {
                mp.pause()
            }
        } catch (_: Throwable) {}
    }

    fun resume() {
        if (isReleased) return
        try {
            val mp = mediaPlayer ?: return
            if (!mp.isPlaying) {
                mp.play()
            }
        } catch (_: Throwable) {}
    }

    fun stop() {
        if (isReleased) return
        try {
            mediaPlayer?.stop()
        } catch (_: Throwable) {}
    }

    @Synchronized
    fun release() {
        if (isReleased) return
        isReleased = true
        try {
            currentPfd?.close()
        } catch (_: Throwable) {}
        currentPfd = null

        try {
            mediaPlayer?.let { mp ->
                mp.setEventListener(null)
                if (mp.isPlaying) {
                    mp.stop()
                }
                mp.vlcVout?.detachViews()
                mp.release()
            }
        } catch (_: Throwable) {}
        mediaPlayer = null

        try {
            libVLC?.release()
        } catch (_: Throwable) {}
        libVLC = null
    }
}

