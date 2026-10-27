package com.example.videoplayer.ui.ai

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.palette.graphics.Palette
import org.json.JSONObject
import java.util.Locale

/**
 * Объект данных (Data Class) для параметров UI плеера,
 * извлеченных из скриншота с помощью AndroidX Palette API.
 */
data class ExtractedPlayerUiConfig(
    val backgroundColorHex: String,       // Цвет фона экрана/контейнера
    val primaryAccentColorHex: String,   // Основной акцент для активных кнопок и ползунка
    val secondaryAccentColorHex: String, // Вторичный цвет для бейджей и меток
    val textColorHex: String,            // Основной контрастный цвет текста
    val surfaceColorHex: String,         // Цвет карточек и подложки панели
    val controlPanelPosition: String = "bottom", // Положение панели: "bottom", "floating_bottom", "overlay"
    val buttonSize: String = "standard", // Размер кнопок: "compact", "standard", "large"
    val buttonShape: String = "rounded", // Форма кнопок: "circle", "rounded", "square"
    val cornerRadiusDp: Int = 16         // Радиус скругления углов в dp
) {
    /**
     * Преобразование объекта настроек в форматированную JSON-строку
     */
    fun toJsonString(): String {
        return JSONObject().apply {
            put("backgroundColorHex", backgroundColorHex)
            put("primaryAccentColorHex", primaryAccentColorHex)
            put("secondaryAccentColorHex", secondaryAccentColorHex)
            put("textColorHex", textColorHex)
            put("surfaceColorHex", surfaceColorHex)
            put("controlPanelPosition", controlPanelPosition)
            put("buttonSize", buttonSize)
            put("buttonShape", buttonShape)
            put("cornerRadiusDp", cornerRadiusDp)
        }.toString(2)
    }
}

/**
 * Утилита для извлечения палитры и UI-параметров из Bitmap с помощью AndroidX Palette API.
 */
object ScreenshotPaletteExtractor {

    /**
     * Анализирует переданный Bitmap скриншота с помощью AndroidX Palette:
     * 1. Извлекает доминирующие цвета (фон, акцент для кнопок, цвет текста, карточки).
     * 2. Вычисляет геометрию интерфейса (положение панели, размер кнопок, скругление).
     * 3. Формирует Data Class [ExtractedPlayerUiConfig] и JSON-строку.
     *
     * @param bitmap Скриншот экрана для анализа.
     * @return Пара: готовый объект [ExtractedPlayerUiConfig] и его JSON-представление.
     */
    fun extractUiConfigFromScreenshot(bitmap: Bitmap): Pair<ExtractedPlayerUiConfig, String> {
        // Создаем Palette из входного Bitmap
        val palette = Palette.from(bitmap)
            .maximumColorCount(32)
            .generate()

        // 1. Извлекаем доминирующий цвет фона
        // Предпочитаем темный приглушенный или доминирующий цвет (типичный для медиаплееров)
        val bgSwatch = palette.darkMutedSwatch
            ?: palette.dominantSwatch
            ?: palette.darkVibrantSwatch
        val bgInt = bgSwatch?.rgb ?: AndroidColor.parseColor("#0D0D0D")

        // Вычисляем цвет подложки (surface): немного светлее фона для контраста
        val surfInt = palette.mutedSwatch?.rgb ?: run {
            val r = (AndroidColor.red(bgInt) + 20).coerceAtMost(255)
            val g = (AndroidColor.green(bgInt) + 20).coerceAtMost(255)
            val b = (AndroidColor.blue(bgInt) + 24).coerceAtMost(255)
            AndroidColor.rgb(r, g, b)
        }

        // 2. Извлекаем основной акцентный цвет для кнопок
        // Ищем яркий (vibrant) или светлый акцент, контрастирующий с фоном
        val vibrantSwatch = palette.vibrantSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
        val primaryInt = vibrantSwatch?.rgb ?: AndroidColor.parseColor("#FF9F00")

        // Вторичный акцент для бейджей и вторичных иконок
        val secondSwatch = palette.lightMutedSwatch
            ?: palette.lightVibrantSwatch
            ?: palette.dominantSwatch
        val secondaryInt = secondSwatch?.rgb ?: run {
            // Если второго акцента нет, сдвигаем оттенок основного
            val hsv = FloatArray(3)
            AndroidColor.colorToHSV(primaryInt, hsv)
            hsv[0] = (hsv[0] + 35f) % 360f
            AndroidColor.HSVToColor(hsv)
        }

        // 3. Извлекаем цвет текста
        // Используем встроенный расчет контрастного текста из Palette
        val textInt = bgSwatch?.bodyTextColor
            ?: bgSwatch?.titleTextColor
            ?: if (isColorDark(bgInt)) AndroidColor.WHITE else AndroidColor.BLACK

        // Преобразуем цвета в HEX-строки (#RRGGBB)
        val bgHex = toHexColor(bgInt)
        val primaryHex = toHexColor(primaryInt)
        val secondaryHex = toHexColor(secondaryInt)
        val textHex = toHexColor(textInt)
        val surfHex = toHexColor(surfInt)

        // 4. Определение геометрии: положение панели управления, размер кнопок и форма
        // В мобильных видеоплеерах панель обычно расположена снизу
        val position = if (bitmap.height > bitmap.width) "floating_bottom" else "bottom"

        // Размер кнопок зависит от плотности и разрешения скриншота
        val btnSize = when {
            bitmap.width >= 1080 -> "large"
            bitmap.width >= 720 -> "standard"
            else -> "compact"
        }

        val btnShape = "circle"
        val cornerRadius = if (position == "floating_bottom") 22 else 14

        // 5. Создаем Data Class
        val config = ExtractedPlayerUiConfig(
            backgroundColorHex = bgHex,
            primaryAccentColorHex = primaryHex,
            secondaryAccentColorHex = secondaryHex,
            textColorHex = textHex,
            surfaceColorHex = surfHex,
            controlPanelPosition = position,
            buttonSize = btnSize,
            buttonShape = btnShape,
            cornerRadiusDp = cornerRadius
        )

        // 6. Преобразуем в JSON-строку
        val jsonString = config.toJsonString()

        return Pair(config, jsonString)
    }

    /**
     * Преобразует целочисленный цвет Android (Int) в шестнадцатеричную строку #RRGGBB
     */
    private fun toHexColor(colorInt: Int): String {
        return String.format(
            Locale.US,
            "#%02X%02X%02X",
            AndroidColor.red(colorInt),
            AndroidColor.green(colorInt),
            AndroidColor.blue(colorInt)
        )
    }

    /**
     * Проверяет, является ли цвет темным (для расчета контраста)
     */
    private fun isColorDark(colorInt: Int): Boolean {
        val darkness = 1 - (0.299 * AndroidColor.red(colorInt) +
                0.587 * AndroidColor.green(colorInt) +
                0.114 * AndroidColor.blue(colorInt)) / 255
        return darkness >= 0.5
    }
}
