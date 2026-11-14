package com.example.videoplayer.player

import android.content.Context
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer

class MediaRepository(context: Context) {

    val hardwareProfile: VlcEngineConfigurator.HardwareProfile

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    init {
        val appContext = context.applicationContext ?: context
        val prefs = appContext.getSharedPreferences("video_prefs", Context.MODE_PRIVATE)
        val hwAccelEnabled = prefs.getBoolean("hw_accel", true)
        val selectedEngine = AppPreferences.getRenderEngine(appContext)

        // Получаем профиль железа для текущего устройства и выбранного режима рендеринга
        val profile = VlcEngineConfigurator.getHardwareProfile(appContext, selectedEngine)
        hardwareProfile = profile

        val engineOptions = ArrayList(profile.vlcOptions).apply {
            add("--rtsp-tcp")
            if (!hwAccelEnabled && selectedEngine != AppPreferences.RenderEngine.SOFTWARE) {
                removeAll { it.startsWith("--avcodec-hw=") }
                add("--avcodec-hw=none")
            }
        }

        // Логируем для отладки выбранный движок, поддержку Vulkan и количество потоков CPU
        Log.d(
            "VLC_INIT",
            "Engine: ${selectedEngine.value}, Vulkan active: ${profile.isVulkanUsed} (supported=${profile.isVulkanSupported}), CPU Threads: ${profile.cpuCores}"
        )

        // Инициализируем LibVLC с нашими динамическими параметрами
        var createdVlc: LibVLC? = null
        var createdPlayer: MediaPlayer? = null
        try {
            createdVlc = LibVLC(appContext, engineOptions)
            createdPlayer = MediaPlayer(createdVlc)
            libVLC = createdVlc
            mediaPlayer = createdPlayer
        } catch (e: Throwable) {
            Log.e("VLC_INIT", "Failed to initialize LibVLC engine", e)
            try {
                createdPlayer?.setEventListener(null)
                createdPlayer?.release()
            } catch (_: Throwable) {}
            try {
                createdVlc?.release()
            } catch (_: Throwable) {}
            libVLC = null
            mediaPlayer = null
        }
    }

    fun getLibVlc(): LibVLC? = libVLC

    fun getPlayer(): MediaPlayer? = mediaPlayer

    fun release() {
        val mp = mediaPlayer
        mediaPlayer = null
        val vlc = libVLC
        libVLC = null
        try {
            mp?.setEventListener(null)
            if (mp?.isPlaying == true) {
                mp.stop()
            }
            mp?.release()
        } catch (_: Throwable) {}
        try {
            vlc?.release()
        } catch (_: Throwable) {}
    }
}
