package com.example.videoplayer.player

import android.content.Context

object AppPreferences {
    private const val PREF_NAME = "app_power_prefs"
    private const val KEY_RENDER_ENGINE = "render_engine_mode"
    private const val KEY_LEGACY_FORCE_VULKAN = "force_vulkan_enabled"

    enum class RenderEngine(val title: String, val description: String, val value: String) {
        AUTO(
            "Автоматически (Рекомендуется)",
            "Система сама определит оптимальный GPU/CPU драйвер под устройство.",
            "auto"
        ),
        VULKAN(
            "Vulkan (Максимальный GPU)",
            "Принудительный низкоуровневый графический конвейер Vulkan.",
            "vulkan"
        ),
        GLES2(
            "OpenGL ES 2.0 (Стабильный)",
            "Проверенный и совместимый графический API для любых чипсетов.",
            "gles2"
        ),
        SOFTWARE(
            "Software (Чистый CPU)",
            "Отключение аппаратного ускорения, вся нагрузка идёт на все ядра CPU.",
            "software"
        )
    }

    fun getRenderEngine(context: Context): RenderEngine {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedValue = prefs.getString(KEY_RENDER_ENGINE, null)
        if (savedValue != null) {
            return RenderEngine.entries.find { it.value == savedValue } ?: RenderEngine.AUTO
        }
        return if (prefs.getBoolean(KEY_LEGACY_FORCE_VULKAN, false)) {
            RenderEngine.VULKAN
        } else {
            RenderEngine.AUTO
        }
    }

    fun setRenderEngine(context: Context, engine: RenderEngine) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RENDER_ENGINE, engine.value)
            .putBoolean(KEY_LEGACY_FORCE_VULKAN, engine == RenderEngine.VULKAN)
            .apply()
    }

    fun isForceVulkanEnabled(context: Context): Boolean =
        getRenderEngine(context) == RenderEngine.VULKAN

    fun setForceVulkanEnabled(context: Context, enabled: Boolean) {
        setRenderEngine(context, if (enabled) RenderEngine.VULKAN else RenderEngine.AUTO)
    }
}
