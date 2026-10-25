package com.example.videoplayer.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class PlayerControlStyle {
    CLASSIC_VLC,
    MODERN_FLOAT,
    MINIMAL_COMPACT,
    CINEMATIC_FULL
}

enum class ButtonSizeOption {
    COMPACT,
    STANDARD,
    LARGE
}

data class AppCustomTheme(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val cardBorder: Color,
    val glow: Color,
    val isDark: Boolean = true,
    val playerControlStyle: PlayerControlStyle = PlayerControlStyle.CLASSIC_VLC,
    val buttonSize: ButtonSizeOption = ButtonSizeOption.STANDARD,
    val cornerRadiusDp: Int = 16,
    val controlPanelBg: Color? = null
)

object ThemePresets {
    val NexoraNeon = AppCustomTheme(
        id = "nexora_neon",
        name = "Ночной Неон (Оригинал)",
        description = "Ваше оригинальное оформление Nexora: неоновый оранжевый, теплый янтарь и глубокий темный фон",
        primary = Color(0xFFFF8A00),
        secondary = Color(0xFFFF9500),
        background = Color(0xFF0D0D0D),
        surface = Color(0xFF1E1E1E),
        cardBorder = Color(0x33FF8A00),
        glow = Color(0x66FF8A00)
    )

    val Cyberpunk = AppCustomTheme(
        id = "cyberpunk",
        name = "Cyberpunk 2077",
        description = "Футуристический стиль с неоновым цианом и неоновым розовым",
        primary = Color(0xFF00F0FF),
        secondary = Color(0xFFFF0055),
        background = Color(0xFF080D14),
        surface = Color(0xFF101926),
        cardBorder = Color(0x4400F0FF),
        glow = Color(0x6600F0FF)
    )

    val Emerald = AppCustomTheme(
        id = "emerald",
        name = "Изумрудный Неон",
        description = "Энергичный лаймово-изумрудный стиль с глубоким темным фоном",
        primary = Color(0xFF00E676),
        secondary = Color(0xFF76FF03),
        background = Color(0xFF061209),
        surface = Color(0xFF0E2214),
        cardBorder = Color(0x4400E676),
        glow = Color(0x6600E676)
    )

    val DeepOled = AppCustomTheme(
        id = "deep_oled",
        name = "Глубокий OLED",
        description = "Ультра-минималистичный контрастный монохром для экономии батареи",
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF94A3B8),
        background = Color(0xFF000000),
        surface = Color(0xFF141414),
        cardBorder = Color(0x33FFFFFF),
        glow = Color(0x44FFFFFF)
    )

    val Sunset = AppCustomTheme(
        id = "sunset",
        name = "Закат в Неваде",
        description = "Теплый коралловый и янтарный градиент закатного неба",
        primary = Color(0xFFFF5722),
        secondary = Color(0xFFFFC107),
        background = Color(0xFF120808),
        surface = Color(0xFF221111),
        cardBorder = Color(0x44FF5722),
        glow = Color(0x66FF5722)
    )

    val MidnightSapphire = AppCustomTheme(
        id = "sapphire",
        name = "Полночный Сапфир",
        description = "Глубокий синий индиго с лазурным электрическим акцентом",
        primary = Color(0xFF2979FF),
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF070B19),
        surface = Color(0xFF0F1733),
        cardBorder = Color(0x442979FF),
        glow = Color(0x662979FF)
    )

    val Amethyst = AppCustomTheme(
        id = "amethyst",
        name = "Аметистовый Шторм",
        description = "Таинственный фиолетово-маджентовый стиль с глубокими тенями",
        primary = Color(0xFFD500F9),
        secondary = Color(0xFF7C4DFF),
        background = Color(0xFF0E0717),
        surface = Color(0xFF1D102E),
        cardBorder = Color(0x44D500F9),
        glow = Color(0x66D500F9)
    )

    val allPresets = listOf(NexoraNeon, Cyberpunk, Emerald, DeepOled, Sunset, MidnightSapphire, Amethyst)
}

val LocalAppTheme = compositionLocalOf { ThemePresets.NexoraNeon }

object ThemeManager {
    private const val PREFS_NAME = "nexora_theme_prefs"
    private const val KEY_THEME_ID = "active_theme_id"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary"
    private const val KEY_CUSTOM_SECONDARY = "custom_secondary"
    private const val KEY_CUSTOM_BG = "custom_bg"
    private const val KEY_CUSTOM_SURFACE = "custom_surface"
    private const val KEY_CUSTOM_NAME = "custom_name"
    private const val KEY_CUSTOM_DESC = "custom_desc"
    private const val KEY_CUSTOM_CTRL_STYLE = "custom_ctrl_style"
    private const val KEY_CUSTOM_BTN_SIZE = "custom_btn_size"
    private const val KEY_CUSTOM_CORNER_RADIUS = "custom_corner_radius"
    private const val KEY_CUSTOM_CTRL_BG = "custom_ctrl_bg"

    var activeTheme by mutableStateOf<AppCustomTheme>(ThemePresets.NexoraNeon)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME_ID, "nexora_neon") ?: "nexora_neon"

        if (themeId == "custom_ai") {
            val primary = prefs.getInt(KEY_CUSTOM_PRIMARY, 0xFFFF8A00.toInt())
            val secondary = prefs.getInt(KEY_CUSTOM_SECONDARY, 0xFFFF9500.toInt())
            val bg = prefs.getInt(KEY_CUSTOM_BG, 0xFF0D0D0D.toInt())
            val surface = prefs.getInt(KEY_CUSTOM_SURFACE, 0xFF1E1E1E.toInt())
            val name = prefs.getString(KEY_CUSTOM_NAME, "ИИ Стиль") ?: "ИИ Стиль"
            val desc = prefs.getString(KEY_CUSTOM_DESC, "Сгенерировано искусственным интеллектом") ?: ""
            val ctrlStyleStr = prefs.getString(KEY_CUSTOM_CTRL_STYLE, PlayerControlStyle.CLASSIC_VLC.name)
            val btnSizeStr = prefs.getString(KEY_CUSTOM_BTN_SIZE, ButtonSizeOption.STANDARD.name)
            val cornerRadius = prefs.getInt(KEY_CUSTOM_CORNER_RADIUS, 16)
            val ctrlBgInt = prefs.getInt(KEY_CUSTOM_CTRL_BG, 0)

            val ctrlStyle = try {
                PlayerControlStyle.valueOf(ctrlStyleStr ?: PlayerControlStyle.CLASSIC_VLC.name)
            } catch (_: Exception) {
                PlayerControlStyle.CLASSIC_VLC
            }

            val btnSize = try {
                ButtonSizeOption.valueOf(btnSizeStr ?: ButtonSizeOption.STANDARD.name)
            } catch (_: Exception) {
                ButtonSizeOption.STANDARD
            }

            activeTheme = AppCustomTheme(
                id = "custom_ai",
                name = name,
                description = desc,
                primary = Color(primary),
                secondary = Color(secondary),
                background = Color(bg),
                surface = Color(surface),
                cardBorder = Color(primary).copy(alpha = 0.35f),
                glow = Color(primary).copy(alpha = 0.5f),
                playerControlStyle = ctrlStyle,
                buttonSize = btnSize,
                cornerRadiusDp = cornerRadius,
                controlPanelBg = if (ctrlBgInt != 0) Color(ctrlBgInt) else null
            )
        } else {
            activeTheme = ThemePresets.allPresets.find { it.id == themeId } ?: ThemePresets.NexoraNeon
        }
    }

    fun applyTheme(context: Context, theme: AppCustomTheme) {
        activeTheme = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    fun resetToOriginalNeon(context: Context) {
        applyTheme(context, ThemePresets.NexoraNeon)
    }

    fun saveAndApplyAiTheme(
        context: Context,
        name: String,
        description: String,
        primary: Color,
        secondary: Color,
        background: Color,
        surface: Color,
        playerControlStyle: PlayerControlStyle = PlayerControlStyle.CLASSIC_VLC,
        buttonSize: ButtonSizeOption = ButtonSizeOption.STANDARD,
        cornerRadiusDp: Int = 16,
        controlPanelBg: Color? = null
    ): AppCustomTheme {
        val custom = AppCustomTheme(
            id = "custom_ai",
            name = name,
            description = description,
            primary = primary,
            secondary = secondary,
            background = background,
            surface = surface,
            cardBorder = primary.copy(alpha = 0.35f),
            glow = primary.copy(alpha = 0.5f),
            playerControlStyle = playerControlStyle,
            buttonSize = buttonSize,
            cornerRadiusDp = cornerRadiusDp,
            controlPanelBg = controlPanelBg
        )
        activeTheme = custom

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_ID, "custom_ai")
            .putInt(KEY_CUSTOM_PRIMARY, primary.value.toInt())
            .putInt(KEY_CUSTOM_SECONDARY, secondary.value.toInt())
            .putInt(KEY_CUSTOM_BG, background.value.toInt())
            .putInt(KEY_CUSTOM_SURFACE, surface.value.toInt())
            .putString(KEY_CUSTOM_NAME, name)
            .putString(KEY_CUSTOM_DESC, description)
            .putString(KEY_CUSTOM_CTRL_STYLE, playerControlStyle.name)
            .putString(KEY_CUSTOM_BTN_SIZE, buttonSize.name)
            .putInt(KEY_CUSTOM_CORNER_RADIUS, cornerRadiusDp)
            .putInt(KEY_CUSTOM_CTRL_BG, controlPanelBg?.value?.toInt() ?: 0)
            .apply()

        return custom
    }

    suspend fun generateAiDesign(
        prompt: String,
        apiKey: String? = null
    ): AppCustomTheme = withContext(Dispatchers.IO) {
        val key = apiKey?.trim()?.ifBlank { null }
        if (!key.isNullOrBlank()) {
            try {
                val result = callGeminiForTheme(prompt, key)
                if (result != null) return@withContext result
            } catch (e: Exception) {
                Log.w("ThemeManager", "Gemini API call failed, using intelligent AI synthesis", e)
            }
        }

        synthesizeAiThemeFromPrompt(prompt)
    }

    private fun callGeminiForTheme(prompt: String, apiKey: String): AppCustomTheme? {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.doOutput = true

        val systemPrompt = "You are a UI color palette designer for a video player app. Given a user theme prompt, output ONLY a JSON object with: { \"name\": string, \"description\": string, \"primary\": \"#hex\", \"secondary\": \"#hex\", \"background\": \"#hex\", \"surface\": \"#hex\" }. The background must be dark (e.g. #080808 to #151515) for high video contrast. Do not include markdown codeblocks."
        val reqBody = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", "$systemPrompt\nUser prompt: $prompt")
                        })
                    })
                })
            })
        }

        conn.outputStream.use { it.write(reqBody.toString().toByteArray(Charsets.UTF_8)) }
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
                val cleanJson = textPart.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val parsed = JSONObject(cleanJson)
                val name = parsed.optString("name", "ИИ Дизайн")
                val desc = parsed.optString("description", prompt)
                val primary = parseHexColor(parsed.optString("primary", "#FF8A00"))
                val secondary = parseHexColor(parsed.optString("secondary", "#FFAE33"))
                val bg = parseHexColor(parsed.optString("background", "#0D0D0D"))
                val surface = parseHexColor(parsed.optString("surface", "#1E1E1E"))

                return AppCustomTheme(
                    id = "custom_ai",
                    name = name,
                    description = desc,
                    primary = primary,
                    secondary = secondary,
                    background = bg,
                    surface = surface,
                    cardBorder = primary.copy(alpha = 0.35f),
                    glow = primary.copy(alpha = 0.5f)
                )
            }
        }
        return null
    }

    private fun synthesizeAiThemeFromPrompt(prompt: String): AppCustomTheme {
        val p = prompt.lowercase()

        return when {
            p.contains("фиолетов") || p.contains("сирен") || p.contains("неон") && p.contains("розов") || p.contains("синтвейв") || p.contains("synthwave") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Синтвейв 80-х",
                    description = "Ретро-футуризм с неоновым пурпуром и розовым закатом",
                    primary = Color(0xFFFF007F),
                    secondary = Color(0xFF9D00FF),
                    background = Color(0xFF0F0618),
                    surface = Color(0xFF1E0E2E),
                    cardBorder = Color(0x55FF007F),
                    glow = Color(0x66FF007F)
                )
            }
            p.contains("зелен") || p.contains("лайм") || p.contains("матриц") || p.contains("matrix") || p.contains("хакер") || p.contains("toxic") || p.contains("лес") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Matrix Кибер-Лайм",
                    description = "Кислотный цифровой лайм с глубоким темным терминалом",
                    primary = Color(0xFF00FF66),
                    secondary = Color(0xFF76FF03),
                    background = Color(0xFF051008),
                    surface = Color(0xFF0C1E10),
                    cardBorder = Color(0x5500FF66),
                    glow = Color(0x6600FF66)
                )
            }
            p.contains("син") || p.contains("голуб") || p.contains("лед") || p.contains("арктик") || p.contains("мороз") || p.contains("холод") || p.contains("океан") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Арктический Неон",
                    description = "Ледяной ультрамарин и кристальный бирюзовый акцент",
                    primary = Color(0xFF00E5FF),
                    secondary = Color(0xFF2979FF),
                    background = Color(0xFF060F1A),
                    surface = Color(0xFF0C1D2E),
                    cardBorder = Color(0x5500E5FF),
                    glow = Color(0x6600E5FF)
                )
            }
            p.contains("красн") || p.contains("огон") || p.contains("плам") || p.contains("lava") || p.contains("рубин") || p.contains("дракон") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Огненный Рубин",
                    description = "Агрессивный рубиново-красный и пламенный янтарь",
                    primary = Color(0xFFFF1744),
                    secondary = Color(0xFFFF9100),
                    background = Color(0xFF140808),
                    surface = Color(0xFF241010),
                    cardBorder = Color(0x55FF1744),
                    glow = Color(0x66FF1744)
                )
            }
            p.contains("золот") || p.contains("люкс") || p.contains("премиум") || p.contains("royal") || p.contains("gold") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Королевское Золото",
                    description = "Премиальное шампанское золото и глубокий обсидиан",
                    primary = Color(0xFFFFD700),
                    secondary = Color(0xFFFFA000),
                    background = Color(0xFF0F0E0B),
                    surface = Color(0xFF1E1C15),
                    cardBorder = Color(0x55FFD700),
                    glow = Color(0x66FFD700)
                )
            }
            p.contains("бел") || p.contains("минимал") || p.contains("сер") || p.contains("графит") || p.contains("монохром") -> {
                AppCustomTheme(
                    id = "custom_ai",
                    name = "Скандинавский Графит",
                    description = "Лаконичный матовый графит с чистым жемчужным свечением",
                    primary = Color(0xFFE2E8F0),
                    secondary = Color(0xFF94A3B8),
                    background = Color(0xFF0A0C10),
                    surface = Color(0xFF161B22),
                    cardBorder = Color(0x44E2E8F0),
                    glow = Color(0x44E2E8F0)
                )
            }
            else -> {
                val hash = prompt.hashCode()
                val hue = kotlin.math.abs(hash % 360).toFloat()
                val primary = Color.hsv(hue, 0.85f, 1.0f)
                val secondary = Color.hsv((hue + 40f) % 360f, 0.75f, 0.95f)
                val bg = Color.hsv(hue, 0.25f, 0.06f)
                val surface = Color.hsv(hue, 0.20f, 0.12f)

                AppCustomTheme(
                    id = "custom_ai",
                    name = "ИИ Дизайн: ${prompt.take(18).trim().replaceFirstChar { it.uppercase() }}",
                    description = "Уникальная генерация палитры на основе вашего описания",
                    primary = primary,
                    secondary = secondary,
                    background = bg,
                    surface = surface,
                    cardBorder = primary.copy(alpha = 0.35f),
                    glow = primary.copy(alpha = 0.5f)
                )
            }
        }
    }

    fun parseHexColor(hex: String): Color {
        return try {
            val clean = hex.removePrefix("#").trim()
            val colorInt = if (clean.length == 6) {
                ("FF$clean").toLong(16).toInt()
            } else {
                clean.toLong(16).toInt()
            }
            Color(colorInt)
        } catch (_: Exception) {
            Color(0xFFFF8A00)
        }
    }
}
