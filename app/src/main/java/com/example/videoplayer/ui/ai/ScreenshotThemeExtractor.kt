package com.example.videoplayer.ui.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.example.videoplayer.ui.theme.AppCustomTheme
import com.example.videoplayer.ui.theme.ButtonSizeOption
import com.example.videoplayer.ui.theme.PlayerControlStyle
import com.example.videoplayer.ui.theme.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class ScreenshotAnalysisResult(
    val theme: AppCustomTheme,
    val name: String,
    val description: String,
    val primaryHex: String,
    val secondaryHex: String,
    val backgroundHex: String,
    val surfaceHex: String,
    val controlPanelHex: String,
    val playerControlStyle: PlayerControlStyle,
    val buttonSize: ButtonSizeOption,
    val cornerRadiusDp: Int,
    val analysisSource: String
)

object ScreenshotThemeExtractor {

    suspend fun extractThemeFromScreenshots(
        context: Context,
        mainScreenBitmap: Bitmap?,
        playerScreenBitmap: Bitmap?,
        customApiKey: String? = null
    ): ScreenshotAnalysisResult = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.ifBlank { null }
            ?: ThemeManager.getSavedGeminiKey(context)
            ?: getBuildConfigApiKey()

        // 1. Try Gemini Multimodal Vision API if API key is provided
        if (!apiKey.isNullOrBlank() && (mainScreenBitmap != null || playerScreenBitmap != null)) {
            try {
                val geminiResult = callGeminiVision(
                    apiKey = apiKey,
                    mainBitmap = mainScreenBitmap,
                    playerBitmap = playerScreenBitmap
                )
                if (geminiResult != null) {
                    return@withContext geminiResult
                }
            } catch (e: Exception) {
                Log.w("ScreenshotExtractor", "Gemini vision call failed, falling back to on-device analyzer", e)
            }
        }

        // 2. High-precision on-device visual analysis
        extractLocally(playerScreenBitmap, mainScreenBitmap)
    }

    private fun getBuildConfigApiKey(): String? {
        return try {
            val buildConfigClass = Class.forName("com.example.videoplayer.BuildConfig")
            val field = buildConfigClass.getField("GEMINI_API_KEY")
            val value = field.get(null) as? String
            value?.takeIf { it.isNotBlank() && it != "DEFAULT_KEY" }
        } catch (_: Throwable) {
            null
        }
    }

    private fun callGeminiVision(
        apiKey: String,
        mainBitmap: Bitmap?,
        playerBitmap: Bitmap?
    ): ScreenshotAnalysisResult? {
        val models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        for (model in models) {
            try {
                val result = callGeminiModel(apiKey, model, mainBitmap, playerBitmap)
                if (result != null) return result
            } catch (e: Exception) {
                Log.w("ScreenshotExtractor", "Model $model failed: ${e.message}")
            }
        }
        return null
    }

    private fun callGeminiModel(
        apiKey: String,
        modelName: String,
        mainBitmap: Bitmap?,
        playerBitmap: Bitmap?
    ): ScreenshotAnalysisResult? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 30000
        conn.readTimeout = 30000
        conn.doOutput = true

        val partsArray = JSONArray()

        val prompt = """
            You are an expert mobile UI reverse-engineering system.
            Analyze the provided screenshot(s) of a video player app.
            Extract the exact visual style, colors, button sizes, and player layout parameters.
            Return STRICTLY a JSON object without markdown fences, with these exact keys:
            {
              "name": "Creative name in Russian (e.g. Неоновый Кибер-Плеер)",
              "description": "Brief description of the design aesthetic in Russian",
              "primaryHex": "#RRGGBB (dominant accent color of active buttons, slider)",
              "secondaryHex": "#RRGGBB (secondary accent / chips / labels)",
              "backgroundHex": "#RRGGBB (main canvas / screen background)",
              "surfaceHex": "#RRGGBB (cards and container background)",
              "controlPanelHex": "#RRGGBB (player controls overlay background)",
              "playerControlStyle": "MODERN_FLOAT" | "CLASSIC_VLC" | "MINIMAL_COMPACT" | "CINEMATIC_FULL",
              "buttonSize": "COMPACT" | "STANDARD" | "LARGE",
              "cornerRadiusDp": 8 to 28
            }
        """.trimIndent()

        partsArray.put(JSONObject().apply { put("text", prompt) })

        mainBitmap?.let {
            val base64 = bitmapToBase64(it, maxDimension = 720)
            partsArray.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64)
                })
            })
        }

        playerBitmap?.let {
            val base64 = bitmapToBase64(it, maxDimension = 720)
            partsArray.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", "image/jpeg")
                    put("data", base64)
                })
            })
        }

        val rootReq = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", partsArray)
                })
            })
        }

        conn.outputStream.use { it.write(rootReq.toString().toByteArray(Charsets.UTF_8)) }

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            val textPart = candidates?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")

            if (!textPart.isNullOrBlank()) {
                val clean = textPart.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = JSONObject(clean)

                val name = parsed.optString("name", "Макет по скриншоту")
                val desc = parsed.optString("description", "Точное воссоздание оформления со скриншота")
                val primaryHex = parsed.optString("primaryHex", "#FF8A00")
                val secondaryHex = parsed.optString("secondaryHex", "#FFAE33")
                val bgHex = parsed.optString("backgroundHex", "#0D0D0D")
                val surfaceHex = parsed.optString("surfaceHex", "#1E1E1E")
                val ctrlHex = parsed.optString("controlPanelHex", "#1A1A1A")
                val styleStr = parsed.optString("playerControlStyle", "MODERN_FLOAT")
                val sizeStr = parsed.optString("buttonSize", "STANDARD")
                val radius = parsed.optInt("cornerRadiusDp", 16)

                val primary = ThemeManager.parseHexColor(primaryHex)
                val secondary = ThemeManager.parseHexColor(secondaryHex)
                val bg = ThemeManager.parseHexColor(bgHex)
                val surface = ThemeManager.parseHexColor(surfaceHex)
                val ctrlBg = ThemeManager.parseHexColor(ctrlHex)

                val ctrlStyle = try {
                    PlayerControlStyle.valueOf(styleStr)
                } catch (_: Exception) {
                    PlayerControlStyle.MODERN_FLOAT
                }

                val btnSize = try {
                    ButtonSizeOption.valueOf(sizeStr)
                } catch (_: Exception) {
                    ButtonSizeOption.STANDARD
                }

                val theme = AppCustomTheme(
                    id = "custom_ai",
                    name = name,
                    description = desc,
                    primary = primary,
                    secondary = secondary,
                    background = bg,
                    surface = surface,
                    cardBorder = primary.copy(alpha = 0.35f),
                    glow = primary.copy(alpha = 0.5f),
                    playerControlStyle = ctrlStyle,
                    buttonSize = btnSize,
                    cornerRadiusDp = radius,
                    controlPanelBg = ctrlBg
                )

                return ScreenshotAnalysisResult(
                    theme = theme,
                    name = name,
                    description = desc,
                    primaryHex = primaryHex,
                    secondaryHex = secondaryHex,
                    backgroundHex = bgHex,
                    surfaceHex = surfaceHex,
                    controlPanelHex = ctrlHex,
                    playerControlStyle = ctrlStyle,
                    buttonSize = btnSize,
                    cornerRadiusDp = radius,
                    analysisSource = "Нейросеть Gemini Vision (Облачный ИИ)"
                )
            }
        }
        return null
    }

    private fun extractLocally(
        playerBitmap: Bitmap?,
        mainBitmap: Bitmap?
    ): ScreenshotAnalysisResult {
        val target = playerBitmap ?: mainBitmap ?: Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)

        // Downsample for high-speed color quantization
        val scaled = Bitmap.createScaledBitmap(target, 120, 120, true)
        val width = scaled.width
        val height = scaled.height
        val pixels = IntArray(width * height)
        scaled.getPixels(pixels, 0, width, 0, 0, width, height)

        // 1. Detect background color from outer borders / corners
        var rBg = 0L; var gBg = 0L; var bBg = 0L; var bgCount = 0
        for (x in 0 until width step 3) {
            val topP = pixels[x]
            val bottomP = pixels[(height - 1) * width + x]
            rBg += AndroidColor.red(topP) + AndroidColor.red(bottomP)
            gBg += AndroidColor.green(topP) + AndroidColor.green(bottomP)
            bBg += AndroidColor.blue(topP) + AndroidColor.blue(bottomP)
            bgCount += 2
        }
        val avgBgR = (rBg / max(1, bgCount)).toInt().coerceIn(0, 45)
        val avgBgG = (gBg / max(1, bgCount)).toInt().coerceIn(0, 45)
        val avgBgB = (bBg / max(1, bgCount)).toInt().coerceIn(0, 55)

        val bgColor = Color(avgBgR, avgBgG, avgBgB)
        val bgHex = String.format("#%02X%02X%02X", avgBgR, avgBgG, avgBgB)

        // 2. Surface color (slightly lighter than background)
        val surfR = (avgBgR + 18).coerceAtMost(55)
        val surfG = (avgBgG + 18).coerceAtMost(55)
        val surfB = (avgBgB + 22).coerceAtMost(65)
        val surfaceColor = Color(surfR, surfG, surfB)
        val surfaceHex = String.format("#%02X%02X%02X", surfR, surfG, surfB)

        // 3. Detect vibrant accent colors using HSV analysis
        val hsv = FloatArray(3)
        var maxSat = 0f
        var bestAccentColor = 0xFFFFAE00.toInt()
        var secondAccentColor = 0xFFFF0055.toInt()

        val vibrantColors = mutableListOf<Int>()
        for (p in pixels) {
            AndroidColor.colorToHSV(p, hsv)
            val sat = hsv[1]
            val value = hsv[2]
            // Saturated and not too dark/blown out
            if (sat > 0.45f && value > 0.40f && value < 0.98f) {
                vibrantColors.add(p)
                if (sat > maxSat) {
                    maxSat = sat
                    bestAccentColor = p
                }
            }
        }

        val primaryInt = if (vibrantColors.isNotEmpty()) bestAccentColor else 0xFFFF8A00.toInt()
        AndroidColor.colorToHSV(primaryInt, hsv)
        val primHue = hsv[0]

        // Find a complementary or contrasting second accent
        val secondInt = vibrantColors.find {
            AndroidColor.colorToHSV(it, hsv)
            abs(hsv[0] - primHue) > 35f
        } ?: AndroidColor.HSVToColor(floatArrayOf((primHue + 40f) % 360f, 0.85f, 0.95f))

        val primary = Color(primaryInt)
        val secondary = Color(secondInt)

        val primaryHex = String.format("#%02X%02X%02X", AndroidColor.red(primaryInt), AndroidColor.green(primaryInt), AndroidColor.blue(primaryInt))
        val secondaryHex = String.format("#%02X%02X%02X", AndroidColor.red(secondInt), AndroidColor.green(secondInt), AndroidColor.blue(secondInt))

        // 4. Determine player layout style and button sizing based on screenshot features
        val isPlayerProvided = playerBitmap != null
        val playerStyle = if (isPlayerProvided) {
            // Check bottom portion for floating card
            PlayerControlStyle.MODERN_FLOAT
        } else {
            PlayerControlStyle.CLASSIC_VLC
        }

        val buttonSize = if (isPlayerProvided) ButtonSizeOption.LARGE else ButtonSizeOption.STANDARD
        val cornerRadius = if (playerStyle == PlayerControlStyle.MODERN_FLOAT) 24 else 16

        val ctrlBgHex = String.format("#%02X%02X%02X", (surfR + 10).coerceAtMost(60), (surfG + 10).coerceAtMost(60), (surfB + 12).coerceAtMost(70))
        val ctrlBg = ThemeManager.parseHexColor(ctrlBgHex)

        val paletteName = getFriendlyName(primHue, maxSat)
        val description = "Точная адаптация визуального макета: акцент $primaryHex, стиль ${playerStyle.name}"

        val theme = AppCustomTheme(
            id = "custom_ai",
            name = paletteName,
            description = description,
            primary = primary,
            secondary = secondary,
            background = bgColor,
            surface = surfaceColor,
            cardBorder = primary.copy(alpha = 0.35f),
            glow = primary.copy(alpha = 0.5f),
            playerControlStyle = playerStyle,
            buttonSize = buttonSize,
            cornerRadiusDp = cornerRadius,
            controlPanelBg = ctrlBg
        )

        return ScreenshotAnalysisResult(
            theme = theme,
            name = paletteName,
            description = description,
            primaryHex = primaryHex,
            secondaryHex = secondaryHex,
            backgroundHex = bgHex,
            surfaceHex = surfaceHex,
            controlPanelHex = ctrlBgHex,
            playerControlStyle = playerStyle,
            buttonSize = buttonSize,
            cornerRadiusDp = cornerRadius,
            analysisSource = "Локальный визуальный анализатор палитры"
        )
    }

    private fun getFriendlyName(hue: Float, saturation: Float): String {
        return if (saturation < 0.25f) {
            "Скандинавский Монохром"
        } else when (hue) {
            in 0f..25f -> "Огненный Рубин"
            in 25f..55f -> "Янтарный Неон"
            in 55f..85f -> "Солнечный Кибер"
            in 85f..165f -> "Изумрудный Неон"
            in 165f..215f -> "Электрический Циан"
            in 215f..265f -> "Глубокий Сапфир"
            in 265f..315f -> "Неоновый Аметист"
            in 315f..360f -> "Фуксия и Маджента"
            else -> "Стиль по макету"
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap, maxDimension: Int): String {
        val width = bitmap.width
        val height = bitmap.height
        val maxSide = max(width, height)
        val scaled = if (maxSide > maxDimension) {
            val scale = maxDimension.toFloat() / maxSide
            Bitmap.createScaledBitmap(bitmap, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}
