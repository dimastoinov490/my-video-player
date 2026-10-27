package com.example.videoplayer.ui.ai

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.palette.graphics.Palette
import com.example.videoplayer.ui.theme.PlayerButtonType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

/**
 * Объект данных (Data Class) для параметров UI плеера,
 * извлеченных из скриншота с помощью AndroidX Palette и нейро-анализатора.
 */
data class ExtractedPlayerUiConfig(
    val backgroundColorHex: String,
    val primaryAccentColorHex: String,
    val secondaryAccentColorHex: String,
    val textColorHex: String,
    val surfaceColorHex: String,
    val controlPanelBgHex: String,
    val controlPanelPosition: String = "floating_bottom",
    val buttonSize: String = "standard",
    val buttonShape: String = "circle",
    val cornerRadiusDp: Int = 18,
    val activeButtons: List<PlayerButtonType> = listOf(
        PlayerButtonType.SEEK_BACK,
        PlayerButtonType.PLAY_PAUSE,
        PlayerButtonType.SEEK_FORWARD,
        PlayerButtonType.ASPECT_RATIO,
        PlayerButtonType.REPEAT_MODE,
        PlayerButtonType.LOCK_CONTROLS
    )
) {
    fun toJsonString(): String {
        return JSONObject().apply {
            put("backgroundColorHex", backgroundColorHex)
            put("primaryAccentColorHex", primaryAccentColorHex)
            put("secondaryAccentColorHex", secondaryAccentColorHex)
            put("textColorHex", textColorHex)
            put("surfaceColorHex", surfaceColorHex)
            put("controlPanelBgHex", controlPanelBgHex)
            put("controlPanelPosition", controlPanelPosition)
            put("buttonSize", buttonSize)
            put("buttonShape", buttonShape)
            put("cornerRadiusDp", cornerRadiusDp)
            val btnArray = JSONArray()
            activeButtons.forEach { btnArray.put(it.name) }
            put("activeButtons", btnArray)
        }.toString(2)
    }
}

/**
 * Утилита для извлечения палитры и точных параметров UI плеера
 */
object ScreenshotPaletteExtractor {

    fun extractUiConfigFromScreenshot(
        bitmap: Bitmap,
        userPrompt: String? = null
    ): Pair<ExtractedPlayerUiConfig, String> {
        val width = bitmap.width
        val height = bitmap.height

        // 1. Separate UI control zone (bottom 30% of screenshot) from video frame zone
        val controlBarTop = (height * 0.70f).toInt().coerceIn(0, height - 1)
        val controlBarHeight = (height - controlBarTop).coerceAtLeast(1)

        val bottomSectionBitmap = try {
            Bitmap.createBitmap(bitmap, 0, controlBarTop, width, controlBarHeight)
        } catch (_: Throwable) {
            bitmap
        }

        // Palette for UI bar specifically
        val bottomPalette = Palette.from(bottomSectionBitmap).maximumColorCount(48).generate()
        // Palette for full screen
        val fullPalette = Palette.from(bitmap).maximumColorCount(48).generate()

        // 2. Extract background and control panel colors
        val ctrlBgSwatch = bottomPalette.darkMutedSwatch
            ?: bottomPalette.dominantSwatch
            ?: fullPalette.darkMutedSwatch
        val ctrlBgInt = ctrlBgSwatch?.rgb ?: AndroidColor.parseColor("#12141C")

        val bgSwatch = fullPalette.darkMutedSwatch
            ?: fullPalette.dominantSwatch
            ?: bottomPalette.darkMutedSwatch
        val bgInt = bgSwatch?.rgb ?: AndroidColor.parseColor("#08090C")

        val surfR = (AndroidColor.red(bgInt) + 20).coerceAtMost(255)
        val surfG = (AndroidColor.green(bgInt) + 20).coerceAtMost(255)
        val surfB = (AndroidColor.blue(bgInt) + 24).coerceAtMost(255)
        val surfInt = bottomPalette.mutedSwatch?.rgb ?: AndroidColor.rgb(surfR, surfG, surfB)

        // 3. Extract high-contrast vibrant accent for buttons
        // Specifically look at the bottom section for vibrant play/slider colors
        val buttonVibrantSwatch = bottomPalette.vibrantSwatch
            ?: bottomPalette.lightVibrantSwatch
            ?: fullPalette.vibrantSwatch
            ?: fullPalette.lightVibrantSwatch
            ?: bottomPalette.dominantSwatch

        val primaryInt = buttonVibrantSwatch?.rgb ?: run {
            // Find most saturated pixel in control section
            findMostSaturatedColor(bottomSectionBitmap) ?: AndroidColor.parseColor("#FF9500")
        }

        val secondSwatch = bottomPalette.lightMutedSwatch
            ?: fullPalette.lightMibrantOrMuted()
        val secondaryInt = secondSwatch?.rgb ?: run {
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(primaryInt, hsv)
            hsv[0] = (hsv[0] + 32f) % 360f
            AndroidColor.HSVToColor(hsv)
        }

        val textInt = if (isColorDark(ctrlBgInt)) AndroidColor.WHITE else AndroidColor.BLACK

        // Hex representations
        val bgHex = toHexColor(bgInt)
        val surfHex = toHexColor(surfInt)
        val ctrlHex = toHexColor(ctrlBgInt)
        val primaryHex = toHexColor(primaryInt)
        val secondaryHex = toHexColor(secondaryInt)
        val textHex = toHexColor(textInt)

        // 4. Geometry and Layout
        val position = if (height > width) "floating_bottom" else "bottom"
        val btnSize = when {
            width >= 1080 -> "large"
            width >= 720 -> "standard"
            else -> "compact"
        }
        val cornerRadius = if (position == "floating_bottom") 22 else 14

        // 5. Button Synthesis from user prompt & visual elements
        val promptLower = (userPrompt ?: "").lowercase()
        val buttons = mutableListOf<PlayerButtonType>()

        // Core buttons
        buttons.add(PlayerButtonType.SEEK_BACK)
        buttons.add(PlayerButtonType.PLAY_PAUSE)
        buttons.add(PlayerButtonType.SEEK_FORWARD)

        // Extended buttons from user request
        if (promptLower.contains("повтор") || promptLower.contains("repeat") || promptLower.contains("петл") || promptLower.contains("loop")) {
            buttons.add(PlayerButtonType.REPEAT_MODE)
        }
        if (promptLower.contains("масштаб") || promptLower.contains("aspect") || promptLower.contains("экран") || promptLower.contains("16:9") || promptLower.contains("размер")) {
            buttons.add(PlayerButtonType.ASPECT_RATIO)
        }
        if (promptLower.contains("блок") || promptLower.contains("lock") || promptLower.contains("замок")) {
            buttons.add(PlayerButtonType.LOCK_CONTROLS)
        }
        if (promptLower.contains("скорост") || promptLower.contains("speed")) {
            buttons.add(PlayerButtonType.SPEED_MENU)
        }
        if (promptLower.contains("аудио") || promptLower.contains("дорожк") || promptLower.contains("звук") || promptLower.contains("track")) {
            buttons.add(PlayerButtonType.AUDIO_TRACK)
        }
        if (promptLower.contains("закладк") || promptLower.contains("bookmark") || promptLower.contains("сохран")) {
            buttons.add(PlayerButtonType.BOOKMARK)
        }

        // If user didn't specify prompt, supply the balanced rich suite of functional controls
        if (buttons.size <= 3) {
            buttons.add(PlayerButtonType.ASPECT_RATIO)
            buttons.add(PlayerButtonType.REPEAT_MODE)
            buttons.add(PlayerButtonType.LOCK_CONTROLS)
            buttons.add(PlayerButtonType.BOOKMARK)
        }

        val buttonShape = when {
            promptLower.contains("квадрат") || promptLower.contains("square") -> "square"
            promptLower.contains("скруглен") || promptLower.contains("squircle") || promptLower.contains("rounded") -> "rounded"
            promptLower.contains("капсул") || promptLower.contains("pill") -> "pill"
            else -> "circle"
        }

        val config = ExtractedPlayerUiConfig(
            backgroundColorHex = bgHex,
            primaryAccentColorHex = primaryHex,
            secondaryAccentColorHex = secondaryHex,
            textColorHex = textHex,
            surfaceColorHex = surfHex,
            controlPanelBgHex = ctrlHex,
            controlPanelPosition = position,
            buttonSize = btnSize,
            buttonShape = buttonShape,
            cornerRadiusDp = cornerRadius,
            activeButtons = buttons.distinct()
        )

        return Pair(config, config.toJsonString())
    }

    private fun findMostSaturatedColor(bitmap: Bitmap): Int? {
        val w = bitmap.width
        val h = bitmap.height
        val scaled = Bitmap.createScaledBitmap(bitmap, 64, max(1, (64 * h) / w), true)
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)

        val hsv = FloatArray(3)
        var maxSat = 0f
        var bestColor: Int? = null

        for (p in pixels) {
            AndroidColor.colorToHSV(p, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            if (sat > 0.45f && value > 0.40f && value < 0.98f && sat > maxSat) {
                maxSat = sat
                bestColor = p
            }
        }
        return bestColor
    }

    private fun Palette.lightMibrantOrMuted(): Palette.Swatch? {
        return this.lightVibrantSwatch ?: this.lightMutedSwatch ?: this.vibrantSwatch
    }

    private fun toHexColor(colorInt: Int): String {
        val r = AndroidColor.red(colorInt)
        val g = AndroidColor.green(colorInt)
        val b = AndroidColor.blue(colorInt)
        return String.format(Locale.ROOT, "#%02X%02X%02X", r, g, b)
    }

    private fun isColorDark(colorInt: Int): Boolean {
        val darkness = 1 - (0.299 * AndroidColor.red(colorInt) + 0.587 * AndroidColor.green(colorInt) + 0.114 * AndroidColor.blue(colorInt)) / 255
        return darkness >= 0.5
    }
}
