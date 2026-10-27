package com.example.videoplayer.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import org.json.JSONObject

/**
 * Конфигурация отдельной кнопки плеера.
 */
data class ButtonConfig(
    val visible: Boolean = true,
    val sizeDp: Int = 40,
    val paddingDp: Int = 4
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("visible", visible)
        put("sizeDp", sizeDp)
        put("paddingDp", paddingDp)
    }

    companion object {
        fun fromJson(json: JSONObject?): ButtonConfig {
            if (json == null) return ButtonConfig()
            return ButtonConfig(
                visible = json.optBoolean("visible", true),
                sizeDp = json.optInt("sizeDp", 40),
                paddingDp = json.optInt("paddingDp", 4)
            )
        }
    }
}

/**
 * Полная динамическая конфигурация UI видеоплеера,
 * сериализуемая в JSON и мгновенно применяемая без пересборки APK.
 */
data class DynamicPlayerConfig(
    // 1. Цветовая палитра: глубокий темный фон (#05050D), неоновый сиреневый (#9D00FF), ярко-голубой (#00E5FF)
    val backgroundHex: String = "#05050D",
    val primaryAccentHex: String = "#9D00FF",
    val secondaryAccentHex: String = "#00E5FF",
    val surfaceHex: String = "#0E101A",
    val controlPanelBgHex: String = "#121422",
    val progressBarStartHex: String = "#9D00FF",
    val progressBarEndHex: String = "#00E5FF",
    val textColorHex: String = "#FFFFFF",

    // 2. Геометрия и стиль: без неонового свечения/glow-эффектов
    val buttonShape: String = "circle", // "circle", "rounded", "square", "pill"
    val cornerRadiusDp: Int = 18,
    val controlPanelPaddingDp: Int = 12,
    val glowEnabled: Boolean = false, // Отключено по ТЗ (без размытия и свечения)
    val centerPlayBorderWidthDp: Int = 2,
    val spotlightBeamEnabled: Boolean = true, // Вертикальный луч за центральной кнопкой Play со скриншота

    // 3. Top Bar Buttons
    val logo: ButtonConfig = ButtonConfig(visible = true, sizeDp = 28, paddingDp = 2),
    val title: ButtonConfig = ButtonConfig(visible = true, sizeDp = 0, paddingDp = 4),
    val castButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4),
    val overflowMenu: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4),

    // 4. Center Controls
    val bigPlayButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 72, paddingDp = 0),

    // 5. Bottom Bar Buttons (все кнопки со скриншота)
    val subtitlesListButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4),
    val repeatButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4),
    val prevButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 44, paddingDp = 4),
    val playPauseButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 56, paddingDp = 2),
    val nextButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 44, paddingDp = 4),
    val pipButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4),
    val moreMenuButton: ButtonConfig = ButtonConfig(visible = true, sizeDp = 40, paddingDp = 4)
) {
    // Helper color getters for Compose
    val backgroundColor: Color get() = parseHex(backgroundHex, Color(0xFF05050D))
    val primaryAccentColor: Color get() = parseHex(primaryAccentHex, Color(0xFF9D00FF))
    val secondaryAccentColor: Color get() = parseHex(secondaryAccentHex, Color(0xFF00E5FF))
    val surfaceColor: Color get() = parseHex(surfaceHex, Color(0xFF0E101A))
    val controlPanelBgColor: Color get() = parseHex(controlPanelBgHex, Color(0xFF121422))
    val progressBarStartColor: Color get() = parseHex(progressBarStartHex, Color(0xFF9D00FF))
    val progressBarEndColor: Color get() = parseHex(progressBarEndHex, Color(0xFF00E5FF))
    val textColor: Color get() = parseHex(textColorHex, Color.White)

    fun toJson(indent: Int = 2): String {
        val root = JSONObject().apply {
            put("backgroundHex", backgroundHex)
            put("primaryAccentHex", primaryAccentHex)
            put("secondaryAccentHex", secondaryAccentHex)
            put("surfaceHex", surfaceHex)
            put("controlPanelBgHex", controlPanelBgHex)
            put("progressBarStartHex", progressBarStartHex)
            put("progressBarEndHex", progressBarEndHex)
            put("textColorHex", textColorHex)

            put("buttonShape", buttonShape)
            put("cornerRadiusDp", cornerRadiusDp)
            put("controlPanelPaddingDp", controlPanelPaddingDp)
            put("glowEnabled", glowEnabled)
            put("centerPlayBorderWidthDp", centerPlayBorderWidthDp)
            put("spotlightBeamEnabled", spotlightBeamEnabled)

            val topBarJson = JSONObject().apply {
                put("logo", logo.toJson())
                put("title", title.toJson())
                put("castButton", castButton.toJson())
                put("overflowMenu", overflowMenu.toJson())
            }
            put("topBar", topBarJson)

            val centerJson = JSONObject().apply {
                put("bigPlayButton", bigPlayButton.toJson())
            }
            put("center", centerJson)

            val bottomBarJson = JSONObject().apply {
                put("subtitlesListButton", subtitlesListButton.toJson())
                put("repeatButton", repeatButton.toJson())
                put("prevButton", prevButton.toJson())
                put("playPauseButton", playPauseButton.toJson())
                put("nextButton", nextButton.toJson())
                put("pipButton", pipButton.toJson())
                put("moreMenuButton", moreMenuButton.toJson())
            }
            put("bottomBar", bottomBarJson)
        }
        return root.toString(indent)
    }

    companion object {
        fun defaultNeon(): DynamicPlayerConfig = screenshotPreset()

        fun screenshotPreset(): DynamicPlayerConfig = DynamicPlayerConfig(
            backgroundHex = "#05050D",
            primaryAccentHex = "#9D00FF",
            secondaryAccentHex = "#00E5FF",
            surfaceHex = "#0E101A",
            controlPanelBgHex = "#05050D",
            progressBarStartHex = "#00E5FF",
            progressBarEndHex = "#9D00FF",
            textColorHex = "#FFFFFF",
            buttonShape = "circle",
            cornerRadiusDp = 18,
            controlPanelPaddingDp = 12,
            glowEnabled = false,
            centerPlayBorderWidthDp = 2,
            spotlightBeamEnabled = true
        )

        fun neonOrangePreset(): DynamicPlayerConfig = DynamicPlayerConfig(
            backgroundHex = "#0A0C10",
            primaryAccentHex = "#FF7A00",
            secondaryAccentHex = "#FFAA00",
            surfaceHex = "#141822",
            controlPanelBgHex = "#121620",
            progressBarStartHex = "#FF7A00",
            progressBarEndHex = "#FFD600",
            textColorHex = "#FFFFFF",
            buttonShape = "circle",
            cornerRadiusDp = 20,
            controlPanelPaddingDp = 12,
            glowEnabled = false,
            centerPlayBorderWidthDp = 2,
            spotlightBeamEnabled = false
        )

        fun cyberpunkPreset(): DynamicPlayerConfig = DynamicPlayerConfig(
            backgroundHex = "#04060C",
            primaryAccentHex = "#FF0055",
            secondaryAccentHex = "#00F0FF",
            surfaceHex = "#0B1220",
            controlPanelBgHex = "#080D1A",
            progressBarStartHex = "#00F0FF",
            progressBarEndHex = "#FF0055",
            textColorHex = "#FFFFFF",
            buttonShape = "rounded",
            cornerRadiusDp = 14,
            controlPanelPaddingDp = 10,
            glowEnabled = false,
            centerPlayBorderWidthDp = 2,
            spotlightBeamEnabled = true
        )

        fun oledPreset(): DynamicPlayerConfig = DynamicPlayerConfig(
            backgroundHex = "#000000",
            primaryAccentHex = "#E0E0E0",
            secondaryAccentHex = "#90CAF9",
            surfaceHex = "#0A0A0A",
            controlPanelBgHex = "#000000",
            progressBarStartHex = "#90CAF9",
            progressBarEndHex = "#E0E0E0",
            textColorHex = "#FFFFFF",
            buttonShape = "circle",
            cornerRadiusDp = 12,
            controlPanelPaddingDp = 10,
            glowEnabled = false,
            centerPlayBorderWidthDp = 1,
            spotlightBeamEnabled = false
        )

        fun fromJson(jsonStr: String): DynamicPlayerConfig {
            val root = JSONObject(jsonStr)
            val topBar = root.optJSONObject("topBar")
            val center = root.optJSONObject("center")
            val bottomBar = root.optJSONObject("bottomBar")

            return DynamicPlayerConfig(
                backgroundHex = root.optString("backgroundHex", "#05050D"),
                primaryAccentHex = root.optString("primaryAccentHex", "#9D00FF"),
                secondaryAccentHex = root.optString("secondaryAccentHex", "#00E5FF"),
                surfaceHex = root.optString("surfaceHex", "#0E101A"),
                controlPanelBgHex = root.optString("controlPanelBgHex", "#05050D"),
                progressBarStartHex = root.optString("progressBarStartHex", "#00E5FF"),
                progressBarEndHex = root.optString("progressBarEndHex", "#9D00FF"),
                textColorHex = root.optString("textColorHex", "#FFFFFF"),

                buttonShape = root.optString("buttonShape", "circle"),
                cornerRadiusDp = root.optInt("cornerRadiusDp", 18),
                controlPanelPaddingDp = root.optInt("controlPanelPaddingDp", 12),
                glowEnabled = root.optBoolean("glowEnabled", false),
                centerPlayBorderWidthDp = root.optInt("centerPlayBorderWidthDp", 2),
                spotlightBeamEnabled = root.optBoolean("spotlightBeamEnabled", true),

                logo = ButtonConfig.fromJson(topBar?.optJSONObject("logo")),
                title = ButtonConfig.fromJson(topBar?.optJSONObject("title")),
                castButton = ButtonConfig.fromJson(topBar?.optJSONObject("castButton")),
                overflowMenu = ButtonConfig.fromJson(topBar?.optJSONObject("overflowMenu")),

                bigPlayButton = ButtonConfig.fromJson(center?.optJSONObject("bigPlayButton")),

                subtitlesListButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("subtitlesListButton")),
                repeatButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("repeatButton")),
                prevButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("prevButton")),
                playPauseButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("playPauseButton")),
                nextButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("nextButton")),
                pipButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("pipButton")),
                moreMenuButton = ButtonConfig.fromJson(bottomBar?.optJSONObject("moreMenuButton"))
            )
        }

        /**
         * Автоматическая конвертация детекции офлайн-детектора YOLO в DynamicPlayerConfig.
         */
        fun fromYoloDetection(yoloOutputJson: String): DynamicPlayerConfig {
            return try {
                val root = JSONObject(yoloOutputJson)
                val detectedElements = root.optJSONArray("detected_classes")
                val classes = mutableSetOf<String>()
                if (detectedElements != null) {
                    for (i in 0 until detectedElements.length()) {
                        classes.add(detectedElements.optString(i).lowercase())
                    }
                }

                // Извлечение палитры из YOLO детектора если она есть
                val paletteObj = root.optJSONObject("palette")
                val primary = paletteObj?.optString("primary_color", "#9D00FF") ?: "#9D00FF"
                val secondary = paletteObj?.optString("secondary_color", "#00E5FF") ?: "#00E5FF"
                val bg = paletteObj?.optString("background_color", "#05050D") ?: "#05050D"

                DynamicPlayerConfig(
                    backgroundHex = bg,
                    primaryAccentHex = primary,
                    secondaryAccentHex = secondary,
                    progressBarStartHex = primary,
                    progressBarEndHex = secondary,
                    glowEnabled = false,
                    logo = ButtonConfig(visible = classes.isEmpty() || classes.contains("logo")),
                    castButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("cast") || classes.contains("chromecast")),
                    bigPlayButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("big_play") || classes.contains("center_play")),
                    subtitlesListButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("subtitles") || classes.contains("cc")),
                    repeatButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("repeat") || classes.contains("loop")),
                    prevButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("prev")),
                    playPauseButton = ButtonConfig(visible = true),
                    nextButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("next")),
                    pipButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("pip") || classes.contains("picture_in_picture")),
                    moreMenuButton = ButtonConfig(visible = classes.isEmpty() || classes.contains("more") || classes.contains("menu"))
                )
            } catch (_: Exception) {
                defaultNeon()
            }
        }

        private fun parseHex(hex: String, fallback: Color): Color {
            return try {
                val clean = hex.removePrefix("#").trim()
                val colorInt = if (clean.length == 6) {
                    ("FF$clean").toLong(16).toInt()
                } else if (clean.length == 8) {
                    clean.toLong(16).toInt()
                } else {
                    return fallback
                }
                Color(colorInt)
            } catch (_: Exception) {
                fallback
            }
        }
    }
}

/**
 * Глобальный реактивный менеджер конфигурации плеера.
 */
object ThemeConfigManager {
    private const val PREFS_NAME = "dynamic_player_theme_config"
    private const val KEY_CONFIG_JSON = "saved_dynamic_json_config"

    var currentConfig by mutableStateOf(DynamicPlayerConfig.defaultNeon())
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedJson = prefs.getString(KEY_CONFIG_JSON, null)
        if (!savedJson.isNullOrBlank()) {
            try {
                currentConfig = DynamicPlayerConfig.fromJson(savedJson)
            } catch (_: Exception) {
                currentConfig = DynamicPlayerConfig.defaultNeon()
            }
        }
    }

    fun applyConfig(config: DynamicPlayerConfig, context: Context? = null) {
        currentConfig = config
        context?.let { ctx ->
            ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CONFIG_JSON, config.toJson(indent = 0))
                .apply()
        }
    }

    fun applyJson(jsonString: String, context: Context? = null): Result<DynamicPlayerConfig> {
        return try {
            val config = DynamicPlayerConfig.fromJson(jsonString)
            applyConfig(config, context)
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun applyYoloJson(yoloJson: String, context: Context? = null): Result<DynamicPlayerConfig> {
        return try {
            val config = DynamicPlayerConfig.fromYoloDetection(yoloJson)
            applyConfig(config, context)
            Result.success(config)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun resetToNeon(context: Context? = null) {
        applyConfig(DynamicPlayerConfig.defaultNeon(), context)
    }
}
