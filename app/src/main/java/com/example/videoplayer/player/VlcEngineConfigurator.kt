package com.example.videoplayer.player

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object VlcEngineConfigurator {

    data class HardwareProfile(
        val renderEngine: AppPreferences.RenderEngine = AppPreferences.RenderEngine.AUTO,
        val isVulkanSupported: Boolean,
        val isVulkanUsed: Boolean = isVulkanSupported,
        val isForced: Boolean = false,
        val cpuCores: Int,
        val vlcOptions: ArrayList<String>
    )

    fun getOptimalConfig(context: Context): ArrayList<String> {
        return getHardwareProfile(context).vlcOptions
    }

    fun getHardwareProfile(
        context: Context,
        engineMode: AppPreferences.RenderEngine = AppPreferences.getRenderEngine(context)
    ): HardwareProfile {
        val isVulkanSupported = checkVulkanSupport(context)
        val cpuCores = Runtime.getRuntime().availableProcessors().coerceIn(2, 16)

        val useVulkan = when (engineMode) {
            AppPreferences.RenderEngine.AUTO -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isVulkanSupported
            AppPreferences.RenderEngine.VULKAN -> true
            AppPreferences.RenderEngine.GLES2,
            AppPreferences.RenderEngine.SOFTWARE -> false
        }

        val options = ArrayList<String>().apply {
            // Задействуем все доступные ядра процессора для декодирования
            add("--avcodec-threads=$cpuCores")
            add("--swscale-threads=$cpuCores")

            // Буферизация и кэш
            add("--file-caching=300")
            add("--network-caching=1000")
            add("--live-caching=1000")

            // Применяем выбранный графический/декодерный движок
            when (engineMode) {
                AppPreferences.RenderEngine.AUTO -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isVulkanSupported) {
                        add("--avcodec-hw=any")
                        add("--vout=android-display")
                    } else {
                        add("--avcodec-hw=any")
                        add("--vout=gles2")
                    }
                }
                AppPreferences.RenderEngine.VULKAN -> {
                    add("--avcodec-hw=any")
                    add("--vout=android-display")
                }
                AppPreferences.RenderEngine.GLES2 -> {
                    add("--avcodec-hw=any")
                    add("--vout=gles2")
                }
                AppPreferences.RenderEngine.SOFTWARE -> {
                    // Полное отключение аппаратного ускорения (чистый софт на CPU)
                    add("--avcodec-hw=none")
                    add("--vout=android-display")
                }
            }

            add("--clock-jitter=0")
            add("--clock-synchro=0")
        }

        return HardwareProfile(
            renderEngine = engineMode,
            isVulkanSupported = isVulkanSupported,
            isVulkanUsed = useVulkan,
            isForced = engineMode == AppPreferences.RenderEngine.VULKAN && !isVulkanSupported,
            cpuCores = cpuCores,
            vlcOptions = options
        )
    }

    fun checkVulkanSupport(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_VULKAN_HARDWARE_VERSION)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
