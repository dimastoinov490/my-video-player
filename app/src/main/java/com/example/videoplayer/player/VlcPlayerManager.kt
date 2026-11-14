package com.example.videoplayer.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.SurfaceView
import com.example.videoplayer.archive.ArchiveStreamServer
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IVLCVout
import com.example.videoplayer.ui.VideoScaleMode

data class PlaybackTrack(val id: Int, val name: String)

class VlcPlayerManager(private val context: Context) {
    private var mediaRepository: MediaRepository? = MediaRepository(context)
    val hardwareProfile: VlcEngineConfigurator.HardwareProfile =
        mediaRepository?.hardwareProfile ?: VlcEngineConfigurator.getHardwareProfile(context.applicationContext)

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var isReleased = false
    private var currentPfd: ParcelFileDescriptor? = null
    private var currentScaleMode: VideoScaleMode = VideoScaleMode.FIT

    var onStatusChanged: ((String) -> Unit)? = null
    var onTimeChanged: ((Long, Long) -> Unit)? = null
    var onVideoAspectChanged: ((Float) -> Unit)? = null
    var onTracksChanged: (() -> Unit)? = null

    val isEngineReady: Boolean
        get() = libVLC != null && mediaPlayer != null && !isReleased

    private val videoLayoutListener = IVLCVout.OnNewVideoLayoutListener { _, _, _, visibleWidth, visibleHeight, sarNum, sarDen ->
        if (visibleHeight > 0 && visibleWidth > 0) {
            val sar = if (sarNum > 0 && sarDen > 0) sarNum.toFloat() / sarDen.toFloat() else 1f
            val ar = (visibleWidth.toFloat() / visibleHeight.toFloat()) * sar
            if (ar > 0.1f && ar < 10f) {
                onVideoAspectChanged?.invoke(ar)
            }
        }
    }

    private val eventListener = MediaPlayer.EventListener { event ->
        if (isReleased) return@EventListener
        when (event.type) {
            MediaPlayer.Event.Buffering -> onStatusChanged?.invoke("Buffering: ${event.buffering}%")
            MediaPlayer.Event.Playing -> {
                setVideoScale(currentScaleMode)
                val mp = mediaPlayer
                if (mp != null) {
                    try {
                        val track = mp.currentVideoTrack
                        if (track != null && track.height > 0 && track.width > 0) {
                            val sar = if (track.sarNum > 0 && track.sarDen > 0) track.sarNum.toFloat() / track.sarDen.toFloat() else 1f
                            val ar = (track.width.toFloat() / track.height.toFloat()) * sar
                            if (ar > 0.1f && ar < 10f) {
                                onVideoAspectChanged?.invoke(ar)
                            }
                        }
                    } catch (_: Throwable) {}
                }
                onStatusChanged?.invoke("Playing")
            }
            MediaPlayer.Event.Paused -> onStatusChanged?.invoke("Paused")
            MediaPlayer.Event.Stopped -> onStatusChanged?.invoke("Stopped")
            MediaPlayer.Event.EndReached -> onStatusChanged?.invoke("EndReached")
            MediaPlayer.Event.EncounteredError -> onStatusChanged?.invoke("Error")
            MediaPlayer.Event.ESAdded,
            MediaPlayer.Event.ESDeleted -> onTracksChanged?.invoke()
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
        val repo = mediaRepository
        val vlc = repo?.getLibVlc()
        val mp = repo?.getPlayer()
        if (vlc != null && mp != null) {
            mp.setEventListener(eventListener)
            libVLC = vlc
            mediaPlayer = mp
        } else {
            repo?.release()
            mediaRepository = null
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
            try {
                vout.attachViews(videoLayoutListener)
            } catch (_: Throwable) {
                vout.attachViews()
            }
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
                VideoScaleMode.FIT,
                VideoScaleMode.FILL -> {
                    try {
                        mp.videoScale = MediaPlayer.ScaleType.SURFACE_BEST_FIT
                    } catch (_: Throwable) {
                        mp.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT)
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
            } else if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://") || targetUrl.startsWith("rtsp://") || targetUrl.startsWith("ftp://") || targetUrl.startsWith("file://")) {
                Media(vlc, Uri.parse(targetUrl))
            } else {
                Media(vlc, targetUrl)
            }

            // Let VLC select the available hardware/software decoder.
            mp.media = media
            media.release()
            mp.play()
            setVideoScale(currentScaleMode)
        } catch (e: Throwable) {
            Log.e("VlcPlayerManager", "playUrl error", e)
            onStatusChanged?.invoke("Error: ${e.message}")
        }
    }

    fun playArchiveZip(archivePath: String, entryPath: String) {
        if (isReleased) return
        val vlc = libVLC
        val mp = mediaPlayer
        if (vlc == null || mp == null) {
            onStatusChanged?.invoke("Error: LibVLC unavailable")
            return
        }
        try {
            try {
                currentPfd?.close()
            } catch (_: Throwable) {}
            currentPfd = null

            val zipUri = ArchiveStreamServer.buildDirectZipUri(archivePath, entryPath)
            val media = Media(vlc, zipUri)
            mp.media = media
            media.release()
            mp.play()
            setVideoScale(currentScaleMode)
        } catch (e: Throwable) {
            Log.e("VlcPlayerManager", "playArchiveZip error", e)
            onStatusChanged?.invoke("Error: ${e.message}")
        }
    }

    fun getAudioTracks(): List<PlaybackTrack> {
        if (isReleased) return emptyList()
        return try {
            mediaPlayer?.getAudioTracks()?.map { PlaybackTrack(it.id, it.name.orEmpty()) }.orEmpty()
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "Unable to read audio tracks", e)
            emptyList()
        }
    }

    fun getSubtitleTracks(): List<PlaybackTrack> {
        if (isReleased) return emptyList()
        return try {
            mediaPlayer?.getSpuTracks()?.map { PlaybackTrack(it.id, it.name.orEmpty()) }.orEmpty()
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "Unable to read subtitle tracks", e)
            emptyList()
        }
    }

    fun getSelectedAudioTrack(): Int = try {
        if (isReleased) -1 else mediaPlayer?.getAudioTrack() ?: -1
    } catch (_: Throwable) { -1 }

    fun getSelectedSubtitleTrack(): Int = try {
        if (isReleased) -1 else mediaPlayer?.getSpuTrack() ?: -1
    } catch (_: Throwable) { -1 }

    fun selectAudioTrack(trackId: Int): Boolean = try {
        !isReleased && (mediaPlayer?.setAudioTrack(trackId) == true)
    } catch (e: Throwable) {
        Log.w("VlcPlayerManager", "Unable to select audio track $trackId", e)
        false
    }

    fun selectSubtitleTrack(trackId: Int): Boolean = try {
        !isReleased && (mediaPlayer?.setSpuTrack(trackId) == true)
    } catch (e: Throwable) {
        Log.w("VlcPlayerManager", "Unable to select subtitle track $trackId", e)
        false
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

    fun setRate(rate: Float): Boolean {
        if (isReleased || rate <= 0f || rate > 4f) return false
        return try {
            val player = mediaPlayer ?: return false
            player.rate = rate
            // LibVLC's setter returns Unit, so verify the effective native value
            // before the UI reports that the speed change succeeded.
            kotlin.math.abs(player.rate - rate) < 0.01f
        } catch (e: Throwable) {
            Log.w("VlcPlayerManager", "Unable to change playback rate to $rate", e)
            false
        }
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
        val pfdToClose = currentPfd
        currentPfd = null
        val repoToRelease = mediaRepository
        mediaRepository = null
        val mpToRelease = mediaPlayer
        mediaPlayer = null
        libVLC = null

        onStatusChanged = null
        onTimeChanged = null
        onVideoAspectChanged = null
        onTracksChanged = null

        try {
            mpToRelease?.setEventListener(null)
            mpToRelease?.vlcVout?.detachViews()
        } catch (_: Throwable) {}

        Thread({
            try {
                pfdToClose?.close()
            } catch (_: Throwable) {}
            try {
                repoToRelease?.release()
            } catch (_: Throwable) {}
        }, "VlcReleaseThread").apply {
            isDaemon = true
            start()
        }
    }
}

