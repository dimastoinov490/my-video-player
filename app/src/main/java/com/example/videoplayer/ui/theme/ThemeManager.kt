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

enum class PlayerButtonType {
    PLAY_PAUSE,
    SEEK_BACK,
    SEEK_FORWARD,
    ASPECT_RATIO,
    REPEAT_MODE,
    LOCK_CONTROLS,
    SPEED_MENU,
    AUDIO_TRACK,
    BOOKMARK
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
    val controlPanelBg: Color? = null,
    val buttonShape: String = "circle",
    val activeButtons: List<PlayerButtonType> = listOf(
        PlayerButtonType.SEEK_BACK,
        PlayerButtonType.PLAY_PAUSE,
        PlayerButtonType.SEEK_FORWARD,
        PlayerButtonType.ASPECT_RATIO,
        PlayerButtonType.REPEAT_MODE,
        PlayerButtonType.LOCK_CONTROLS
    )
)

object ThemePresets {
    val NexoraOriginalOrange = AppCustomTheme(
        id = "nexora_orange_original",
        name = "Оригинальная тема (Оранжевый неон)",
        description = "Фирменный оранжевый неон Nexora / VLC: янтарный акцент (#FF7A00), яркий оранжевый FAB и теплые контуры",
        primary = Color(0xFFFF7A00),
        secondary = Color(0xFFFFAA00),
        background = Color(0xFF0A0C10),
        surface = Color(0xFF141822),
        cardBorder = Color(0x55FF7A00),
        glow = Color(0x66FF7A00),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 20,
        controlPanelBg = Color(0xFF121620)
    )

    val NexoraCyberScreenshot = AppCustomTheme(
        id = "nexora_neon_screenshot",
        name = "Моя тема Неон (со скриншота)",
        description = "Точный дизайн со скриншота: глубокий фон (#05050D), неоновый сиреневый (#9D00FF) и циан (#00E5FF), градиентный бар и луч",
        primary = Color(0xFF9D00FF),
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF05050D),
        surface = Color(0xFF0E101A),
        cardBorder = Color(0x559D00FF),
        glow = Color(0x6600E5FF),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 18,
        controlPanelBg = Color(0xFF05050D)
    )

    val NexoraScreenshot = NexoraCyberScreenshot
    val NexoraNeon = NexoraOriginalOrange

    val Cyberpunk = AppCustomTheme(
        id = "cyberpunk",
        name = "Cyberpunk Night City",
        description = "Футуристический стиль 2077 с неоновым цианом, неоновой фуксией и широкоформатным кино-плеером",
        primary = Color(0xFF00F0FF),
        secondary = Color(0xFFFF0055),
        background = Color(0xFF04060C),
        surface = Color(0xFF0B1220),
        cardBorder = Color(0x4400F0FF),
        glow = Color(0x6600F0FF),
        playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 14,
        controlPanelBg = Color(0xFF080D1A)
    )

    val DeepOled = AppCustomTheme(
        id = "deep_oled",
        name = "Obsidian AMOLED Luxury",
        description = "Ультра-минималистичный чистый черный фон с акцентом электрик-циан для OLED экранов",
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF00E5FF),
        background = Color(0xFF000000),
        surface = Color(0xFF111111),
        cardBorder = Color(0x33FFFFFF),
        glow = Color(0x44FFFFFF),
        playerControlStyle = PlayerControlStyle.MINIMAL_COMPACT,
        buttonSize = ButtonSizeOption.STANDARD,
        cornerRadiusDp = 24,
        controlPanelBg = Color(0xFF161616)
    )

    val TokyoSakura = AppCustomTheme(
        id = "tokyo_sakura",
        name = "Токио Сакура Аниме",
        description = "Нежный неоново-розовый стиль цветущей сакуры с плавающим интерфейсом и крупными кнопками",
        primary = Color(0xFFFF6584),
        secondary = Color(0xFFFFB3C6),
        background = Color(0xFF0D0712),
        surface = Color(0xFF1B1024),
        cardBorder = Color(0x44FF6584),
        glow = Color(0x66FF6584),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.STANDARD,
        cornerRadiusDp = 26,
        controlPanelBg = Color(0xFF1E1228)
    )

    val Steampunk = AppCustomTheme(
        id = "steampunk",
        name = "Стимпанк Медь и Латунь",
        description = "Винтажный викторианский стиль с полированной медью, теплой латунью и классической панелью VLC",
        primary = Color(0xFFF4A261),
        secondary = Color(0xFFE76F51),
        background = Color(0xFF120E0A),
        surface = Color(0xFF221A14),
        cardBorder = Color(0x44F4A261),
        glow = Color(0x55F4A261),
        playerControlStyle = PlayerControlStyle.CLASSIC_VLC,
        buttonSize = ButtonSizeOption.STANDARD,
        cornerRadiusDp = 10,
        controlPanelBg = Color(0xFF1E1712)
    )

    val Synthwave = AppCustomTheme(
        id = "synthwave",
        name = "Синтвейв Outrun 1984",
        description = "Ностальгический неоновый закат: лазерный пурпур, ретро-маджента и яркая плавающая панель",
        primary = Color(0xFFFF2A85),
        secondary = Color(0xFF05D9E8),
        background = Color(0xFF0E041A),
        surface = Color(0xFF1C0933),
        cardBorder = Color(0x44FF2A85),
        glow = Color(0x66FF2A85),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 20,
        controlPanelBg = Color(0xFF1F0A38)
    )

    val ArcticSapphire = AppCustomTheme(
        id = "arctic_sapphire",
        name = "Ледяной Сапфир Арктики",
        description = "Холодный кристальный аквамарин и глубокий океанский индиго с кинематографичным плеером",
        primary = Color(0xFF00D2FF),
        secondary = Color(0xFF3A7BD5),
        background = Color(0xFF040A14),
        surface = Color(0xFF0A162B),
        cardBorder = Color(0x4400D2FF),
        glow = Color(0x6600D2FF),
        playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 20,
        controlPanelBg = Color(0xFF0D1D3A)
    )

    val Matrix = AppCustomTheme(
        id = "matrix",
        name = "Терминал Матрицы",
        description = "Кислотный фосфорный зеленый терминал хакера с классическим плотным управлением",
        primary = Color(0xFF00FF66),
        secondary = Color(0xFF39FF14),
        background = Color(0xFF020904),
        surface = Color(0xFF071B0B),
        cardBorder = Color(0x4400FF66),
        glow = Color(0x6600FF66),
        playerControlStyle = PlayerControlStyle.CLASSIC_VLC,
        buttonSize = ButtonSizeOption.COMPACT,
        cornerRadiusDp = 8,
        controlPanelBg = Color(0xFF051709)
    )

    val CrimsonDragon = AppCustomTheme(
        id = "crimson_dragon",
        name = "Красный Дракон (Рубин)",
        description = "Агрессивный огненный рубин и магматический оранжевый с контрастной плавающей панелью",
        primary = Color(0xFFFF1744),
        secondary = Color(0xFFFF5252),
        background = Color(0xFF120305),
        surface = Color(0xFF24070B),
        cardBorder = Color(0x44FF1744),
        glow = Color(0x66FF1744),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 18,
        controlPanelBg = Color(0xFF2A090D)
    )

    val RoyalEmerald = AppCustomTheme(
        id = "royal_emerald",
        name = "Королевский Изумруд",
        description = "Благородный изумрудный нефрит с золотым тиснением и просторными скругленными карточками",
        primary = Color(0xFF00E676),
        secondary = Color(0xFFFFD700),
        background = Color(0xFF031208),
        surface = Color(0xFF082612),
        cardBorder = Color(0x4400E676),
        glow = Color(0x6600E676),
        playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
        buttonSize = ButtonSizeOption.STANDARD,
        cornerRadiusDp = 24,
        controlPanelBg = Color(0xFF0A2E16)
    )

    val WarmMocha = AppCustomTheme(
        id = "warm_mocha",
        name = "Теплый Мокко и Карамель",
        description = "Уютные кофейно-карамельные тона с мягким минималистичным управлением",
        primary = Color(0xFFD4A373),
        secondary = Color(0xFFFAEDCD),
        background = Color(0xFF100C09),
        surface = Color(0xFF211914),
        cardBorder = Color(0x44D4A373),
        glow = Color(0x55D4A373),
        playerControlStyle = PlayerControlStyle.MINIMAL_COMPACT,
        buttonSize = ButtonSizeOption.STANDARD,
        cornerRadiusDp = 18,
        controlPanelBg = Color(0xFF1D1612)
    )

    val CosmicNebula = AppCustomTheme(
        id = "cosmic_nebula",
        name = "Космическая Туманность",
        description = "Глубокий межзвездный ультрафиолет и сияющий аметист с широкоэкранным кино-плеером",
        primary = Color(0xFFB388FF),
        secondary = Color(0xFF7C4DFF),
        background = Color(0xFF090514),
        surface = Color(0xFF140D2B),
        cardBorder = Color(0x44B388FF),
        glow = Color(0x66B388FF),
        playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
        buttonSize = ButtonSizeOption.LARGE,
        cornerRadiusDp = 22,
        controlPanelBg = Color(0xFF170F32)
    )

    val allPresets = listOf(
        NexoraOriginalOrange,
        NexoraCyberScreenshot,
        Cyberpunk,
        DeepOled,
        TokyoSakura,
        Steampunk,
        Synthwave,
        ArcticSapphire,
        Matrix,
        CrimsonDragon,
        RoyalEmerald,
        WarmMocha,
        CosmicNebula
    )
}

val LocalAppTheme = compositionLocalOf { ThemePresets.NexoraScreenshot }

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
    private const val KEY_CUSTOM_BTN_SHAPE = "custom_btn_shape"
    private const val KEY_CUSTOM_BUTTONS = "custom_buttons"

    const val MODEL_GEMINI_VISION = "gemini_vision"
    const val MODEL_MOBILENET_VISION = "mobilenet_v3_ui"
    const val MODEL_YOLO_DETECTOR = "yolo_ui_detector"
    const val MODEL_PALETTE_PRO = "palette_pro"

    private const val KEY_ACTIVE_AI_MODEL = "active_ai_model"
    private const val KEY_INSTALLED_MODELS = "installed_ai_models"

    var activeTheme by mutableStateOf<AppCustomTheme>(ThemePresets.NexoraScreenshot)
        private set

    fun getActiveAiModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE_AI_MODEL, MODEL_MOBILENET_VISION) ?: MODEL_MOBILENET_VISION
    }

    fun setActiveAiModel(context: Context, modelId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE_AI_MODEL, modelId)
            .apply()
    }

    fun isModelInstalled(context: Context, modelId: String): Boolean {
        if (modelId == MODEL_PALETTE_PRO || modelId == MODEL_GEMINI_VISION) return true
        val installed = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_INSTALLED_MODELS, emptySet()) ?: emptySet()
        return installed.contains(modelId)
    }

    fun markModelInstalled(context: Context, modelId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val installed = (prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet()) ?: emptySet()).toMutableSet()
        installed.add(modelId)
        prefs.edit().putStringSet(KEY_INSTALLED_MODELS, installed).apply()
    }

    fun colorToInt(color: Color): Int {
        val a = (color.alpha * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val r = (color.red * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val g = (color.green * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255.0f + 0.5f).toInt().coerceIn(0, 255)
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    fun colorFromInt(colorInt: Int): Color {
        val a = ((colorInt ushr 24) and 0xFF) / 255.0f
        val r = ((colorInt ushr 16) and 0xFF) / 255.0f
        val g = ((colorInt ushr 8) and 0xFF) / 255.0f
        val b = (colorInt and 0xFF) / 255.0f
        return Color(red = r, green = g, blue = b, alpha = if (a == 0f) 1.0f else a)
    }

    fun init(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val themeId = prefs.getString(KEY_THEME_ID, "nexora_neon") ?: "nexora_neon"

            if (themeId == "custom_ai") {
                val primaryInt = prefs.getInt(KEY_CUSTOM_PRIMARY, 0xFFFF8A00.toInt()).let { if (it == 0) 0xFFFF8A00.toInt() else it }
                val secondaryInt = prefs.getInt(KEY_CUSTOM_SECONDARY, 0xFFFF9500.toInt()).let { if (it == 0) 0xFFFF9500.toInt() else it }
                val bgInt = prefs.getInt(KEY_CUSTOM_BG, 0xFF0D0D0D.toInt()).let { if (it == 0) 0xFF0D0D0D.toInt() else it }
                val surfaceInt = prefs.getInt(KEY_CUSTOM_SURFACE, 0xFF1E1E1E.toInt()).let { if (it == 0) 0xFF1E1E1E.toInt() else it }
                val name = prefs.getString(KEY_CUSTOM_NAME, "ИИ Стиль") ?: "ИИ Стиль"
                val desc = prefs.getString(KEY_CUSTOM_DESC, "Сгенерировано искусственным интеллектом") ?: ""
                val ctrlStyleStr = prefs.getString(KEY_CUSTOM_CTRL_STYLE, PlayerControlStyle.CLASSIC_VLC.name)
                val btnSizeStr = prefs.getString(KEY_CUSTOM_BTN_SIZE, ButtonSizeOption.STANDARD.name)
                val cornerRadius = prefs.getInt(KEY_CUSTOM_CORNER_RADIUS, 16)
                val ctrlBgInt = prefs.getInt(KEY_CUSTOM_CTRL_BG, 0)
                val btnShape = prefs.getString(KEY_CUSTOM_BTN_SHAPE, "circle") ?: "circle"
                val btnStrList = prefs.getString(KEY_CUSTOM_BUTTONS, null)

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

                val activeButtons = if (!btnStrList.isNullOrBlank()) {
                    btnStrList.split(",").mapNotNull { name ->
                        try { PlayerButtonType.valueOf(name.trim()) } catch (_: Exception) { null }
                    }
                } else {
                    listOf(
                        PlayerButtonType.SEEK_BACK,
                        PlayerButtonType.PLAY_PAUSE,
                        PlayerButtonType.SEEK_FORWARD,
                        PlayerButtonType.ASPECT_RATIO,
                        PlayerButtonType.REPEAT_MODE,
                        PlayerButtonType.LOCK_CONTROLS
                    )
                }

                val primary = colorFromInt(primaryInt)
                val secondary = colorFromInt(secondaryInt)
                val bg = colorFromInt(bgInt)
                val surface = colorFromInt(surfaceInt)

                activeTheme = AppCustomTheme(
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
                    cornerRadiusDp = cornerRadius,
                    controlPanelBg = if (ctrlBgInt != 0) colorFromInt(ctrlBgInt) else null,
                    buttonShape = btnShape,
                    activeButtons = activeButtons
                )
            } else {
                activeTheme = ThemePresets.allPresets.find { it.id == themeId } ?: ThemePresets.NexoraScreenshot
            }
        } catch (e: Throwable) {
            Log.e("ThemeManager", "Error initializing theme, falling back to screenshot design", e)
            activeTheme = ThemePresets.NexoraScreenshot
        }
    }

    fun applyTheme(context: Context, theme: AppCustomTheme) {
        activeTheme = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    fun resetToOriginalNeon(context: Context) {
        applyDefaultPreset(context, ThemePresets.NexoraOriginalOrange)
    }

    fun applyDefaultPreset(context: Context, theme: AppCustomTheme) {
        applyTheme(context, theme)
        when (theme.id) {
            "nexora_neon_screenshot", "nexora_screenshot" -> {
                ThemeConfigManager.applyConfig(DynamicPlayerConfig.screenshotPreset(), context)
            }
            "nexora_orange_original", "nexora_neon" -> {
                ThemeConfigManager.applyConfig(DynamicPlayerConfig.neonOrangePreset(), context)
            }
            "cyberpunk" -> {
                ThemeConfigManager.applyConfig(DynamicPlayerConfig.cyberpunkPreset(), context)
            }
            "deep_oled" -> {
                ThemeConfigManager.applyConfig(DynamicPlayerConfig.oledPreset(), context)
            }
            else -> {
                val hexPrimary = colorToHex(theme.primary)
                val hexSecondary = colorToHex(theme.secondary)
                val hexBg = colorToHex(theme.background)
                val hexSurface = colorToHex(theme.surface)
                ThemeConfigManager.applyConfig(
                    DynamicPlayerConfig(
                        backgroundHex = hexBg,
                        primaryAccentHex = hexPrimary,
                        secondaryAccentHex = hexSecondary,
                        surfaceHex = hexSurface,
                        progressBarStartHex = hexSecondary,
                        progressBarEndHex = hexPrimary
                    ),
                    context
                )
            }
        }
    }

    private fun colorToHex(color: Color): String {
        val r = (color.red * 255f).toInt().coerceIn(0, 255)
        val g = (color.green * 255f).toInt().coerceIn(0, 255)
        val b = (color.blue * 255f).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
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
        controlPanelBg: Color? = null,
        buttonShape: String = "circle",
        activeButtons: List<PlayerButtonType> = listOf(
            PlayerButtonType.SEEK_BACK,
            PlayerButtonType.PLAY_PAUSE,
            PlayerButtonType.SEEK_FORWARD,
            PlayerButtonType.ASPECT_RATIO,
            PlayerButtonType.REPEAT_MODE,
            PlayerButtonType.LOCK_CONTROLS
        )
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
            controlPanelBg = controlPanelBg,
            buttonShape = buttonShape,
            activeButtons = activeButtons
        )
        activeTheme = custom

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_ID, "custom_ai")
            .putInt(KEY_CUSTOM_PRIMARY, colorToInt(primary))
            .putInt(KEY_CUSTOM_SECONDARY, colorToInt(secondary))
            .putInt(KEY_CUSTOM_BG, colorToInt(background))
            .putInt(KEY_CUSTOM_SURFACE, colorToInt(surface))
            .putString(KEY_CUSTOM_NAME, name)
            .putString(KEY_CUSTOM_DESC, description)
            .putString(KEY_CUSTOM_CTRL_STYLE, playerControlStyle.name)
            .putString(KEY_CUSTOM_BTN_SIZE, buttonSize.name)
            .putInt(KEY_CUSTOM_CORNER_RADIUS, cornerRadiusDp)
            .putInt(KEY_CUSTOM_CTRL_BG, controlPanelBg?.let { colorToInt(it) } ?: 0)
            .putString(KEY_CUSTOM_BTN_SHAPE, buttonShape)
            .putString(KEY_CUSTOM_BUTTONS, activeButtons.joinToString(",") { it.name })
            .apply()

        return custom
    }

    private const val KEY_GEMINI_API_KEY = "gemini_api_key"

    fun getSavedGeminiKey(context: Context): String? {
        val key = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_API_KEY, null)?.trim()
        return if (key.isNullOrBlank()) null else key
    }

    fun saveGeminiKey(context: Context, key: String?) {
        val clean = key?.trim()?.ifBlank { null }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_GEMINI_API_KEY, clean)
            .apply()
    }

    fun isGeminiAuthorized(context: Context): Boolean {
        return !getSavedGeminiKey(context).isNullOrBlank()
    }

    suspend fun verifyAndAuthorizeGemini(context: Context, apiKey: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) return@withContext Pair(false, "Ключ не может быть пустым")
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$cleanKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.doOutput = true

            val body = JSONObject().apply {
                put("contents", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", org.json.JSONArray().apply {
                            put(JSONObject().apply { put("text", "ping") })
                        })
                    })
                })
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code == 200) {
                saveGeminiKey(context, cleanKey)
                Pair(true, "Приложение успешно авторизовано в Google Gemini AI!")
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "Код $code"
                Pair(false, "Google API отклонил ключ: $err")
            }
        } catch (e: Exception) {
            Pair(false, "Сбой подключения к сервисам Google: ${e.localizedMessage ?: "Нет сети"}")
        }
    }

    suspend fun generateAiDesign(
        context: Context,
        prompt: String,
        customKey: String? = null
    ): AppCustomTheme = withContext(Dispatchers.IO) {
        val key = customKey?.trim()?.ifBlank { null } ?: getSavedGeminiKey(context)
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
        val models = listOf("gemini-1.5-flash", "gemini-2.0-flash", "gemini-1.5-pro")
        for (model in models) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.doOutput = true

                val systemPrompt = """
                    You are an expert mobile UI/UX designer for a video player. Generate a unique theme based on the user prompt.
                    Output ONLY valid JSON without markdown formatting:
                    {
                      "name": "Creative Russian Title (2-4 words)",
                      "description": "Artistic Russian description of the style and mood",
                      "primary": "#HEX (vivid accent for active controls and seekbar)",
                      "secondary": "#HEX (harmonizing accent for secondary controls)",
                      "background": "#HEX (deep dark background #050505 to #16181F for high video contrast)",
                      "surface": "#HEX (dark card/panel surface #10121A to #222530)",
                      "cardBorder": "#HEX (semi-transparent border)",
                      "glow": "#HEX (accent glow color)",
                      "playerControlStyle": "CLASSIC_VLC" | "MODERN_FLOAT" | "MINIMAL_COMPACT" | "CINEMATIC_FULL",
                      "buttonSize": "COMPACT" | "STANDARD" | "LARGE",
                      "cornerRadiusDp": integer between 0 and 28,
                      "controlPanelBg": "#HEX (optional dark panel background)"
                    }
                """.trimIndent()

                val reqBody = JSONObject().apply {
                    put("contents", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("parts", org.json.JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "$systemPrompt\nUser concept: $prompt")
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
                        val cleanJson = textPart.trim()
                            .removePrefix("```json")
                            .removePrefix("```")
                            .removeSuffix("```")
                            .trim()
                        val parsed = JSONObject(cleanJson)
                        val name = parsed.optString("name", "ИИ Дизайн: $prompt")
                        val desc = parsed.optString("description", "Сгенерировано Google Gemini по запросу «$prompt»")
                        val primary = parseHexColor(parsed.optString("primary", "#FF8A00"))
                        val secondary = parseHexColor(parsed.optString("secondary", "#FFAE33"))
                        val bg = parseHexColor(parsed.optString("background", "#0D0D0D"))
                        val surface = parseHexColor(parsed.optString("surface", "#1E1E1E"))
                        val border = if (parsed.has("cardBorder")) parseHexColor(parsed.getString("cardBorder")) else primary.copy(alpha = 0.35f)
                        val glow = if (parsed.has("glow")) parseHexColor(parsed.getString("glow")) else primary.copy(alpha = 0.5f)

                        val ctrlStyleStr = parsed.optString("playerControlStyle", PlayerControlStyle.MODERN_FLOAT.name)
                        val ctrlStyle = try { PlayerControlStyle.valueOf(ctrlStyleStr) } catch (_: Exception) { PlayerControlStyle.MODERN_FLOAT }

                        val btnSizeStr = parsed.optString("buttonSize", ButtonSizeOption.STANDARD.name)
                        val btnSize = try { ButtonSizeOption.valueOf(btnSizeStr) } catch (_: Exception) { ButtonSizeOption.STANDARD }

                        val cornerRadius = parsed.optInt("cornerRadiusDp", 16).coerceIn(0, 32)
                        val ctrlPanelBg = if (parsed.has("controlPanelBg")) parseHexColor(parsed.getString("controlPanelBg")) else null

                        return AppCustomTheme(
                            id = "custom_ai",
                            name = name,
                            description = desc,
                            primary = primary,
                            secondary = secondary,
                            background = bg,
                            surface = surface,
                            cardBorder = border,
                            glow = glow,
                            playerControlStyle = ctrlStyle,
                            buttonSize = btnSize,
                            cornerRadiusDp = cornerRadius,
                            controlPanelBg = ctrlPanelBg
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w("ThemeManager", "Model $model failed, trying next", e)
            }
        }
        return null
    }

    private fun synthesizeAiThemeFromPrompt(prompt: String): AppCustomTheme {
        val p = prompt.lowercase()

        // 1. Anime / Sakura / Japanese aesthetic
        if (p.contains("сакур") || p.contains("аним") || p.contains("япон") || p.contains("sakura") || p.contains("нежн") || p.contains("розов") && p.contains("бел")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Сакура Аниме",
                description = "Японская эстетика: нежный лепестковый розовый, лунный маджента и плавающие стеклянные элементы",
                primary = Color(0xFFFF69B4),
                secondary = Color(0xFFFFB6C1),
                background = Color(0xFF130910),
                surface = Color(0xFF22111D),
                cardBorder = Color(0x66FF69B4),
                glow = Color(0x66FF69B4),
                playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 22,
                controlPanelBg = Color(0xCC1E0B19)
            )
        }

        // 2. Steampunk / Brass / Copper
        if (p.contains("стимпанк") || p.contains("steampunk") || p.contains("мед") || p.contains("латун") || p.contains("бронз") || p.contains("механизм") || p.contains("викториан")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Стимпанк Виктория",
                description = "Механическая медь, полированная латунь, благородный темный орех и классическая точность",
                primary = Color(0xFFE5A93C),
                secondary = Color(0xFFCD7F32),
                background = Color(0xFF120C07),
                surface = Color(0xFF21170F),
                cardBorder = Color(0x66E5A93C),
                glow = Color(0x55E5A93C),
                playerControlStyle = PlayerControlStyle.CLASSIC_VLC,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 6,
                controlPanelBg = Color(0xE61A120B)
            )
        }

        // 3. Cyberpunk 2077 / Neon High-Tech
        if (p.contains("киберпанк") || p.contains("cyberpunk") || p.contains("неон") && (p.contains("циан") || p.contains("бирюз"))) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Night City Неон",
                description = "Электрический циановый луч, неоновый пурпур и кинематографичная панель управления",
                primary = Color(0xFF00F0FF),
                secondary = Color(0xFFFF0055),
                background = Color(0xFF070B12),
                surface = Color(0xFF0E1624),
                cardBorder = Color(0x6600F0FF),
                glow = Color(0x7700F0FF),
                playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
                buttonSize = ButtonSizeOption.LARGE,
                cornerRadiusDp = 16,
                controlPanelBg = Color(0xDD0A101A)
            )
        }

        // 4. Synthwave / Retrowave 80s
        if (p.contains("синтвейв") || p.contains("synthwave") || p.contains("ретровейв") || p.contains("80") || p.contains("фиолетов") || p.contains("сирен")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Синтвейв 1984",
                description = "Закатный неоновый ультра-пурпур, лазерная фуксия и плавающий плеер",
                primary = Color(0xFFFF007F),
                secondary = Color(0xFFB5179E),
                background = Color(0xFF0E0517),
                surface = Color(0xFF1B0B2B),
                cardBorder = Color(0x66FF007F),
                glow = Color(0x77FF007F),
                playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 20,
                controlPanelBg = Color(0xD9170824)
            )
        }

        // 5. Matrix / Terminal Hacker / Acid Lime
        if (p.contains("матриц") || p.contains("matrix") || p.contains("хакер") || p.contains("лайм") || p.contains("терминал") || p.contains("кислот")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Matrix Терминал",
                description = "Кислотный цифровой лайм, код матрицы и строгий минималистичный интерфейс",
                primary = Color(0xFF00FF66),
                secondary = Color(0xFF76FF03),
                background = Color(0xFF040D06),
                surface = Color(0xFF091A0D),
                cardBorder = Color(0x6600FF66),
                glow = Color(0x7700FF66),
                playerControlStyle = PlayerControlStyle.MINIMAL_COMPACT,
                buttonSize = ButtonSizeOption.COMPACT,
                cornerRadiusDp = 4,
                controlPanelBg = Color(0xF2051208)
            )
        }

        // 6. Arctic Ice / Glacier Sapphire
        if (p.contains("лед") || p.contains("арктик") || p.contains("мороз") || p.contains("снег") || p.contains("холод") || p.contains("голуб")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Ледяной Сапфир",
                description = "Кристальный ледниковый ультрамарин и морозный голубой акцент",
                primary = Color(0xFF00E5FF),
                secondary = Color(0xFF2979FF),
                background = Color(0xFF050E17),
                surface = Color(0xFF0B1C2E),
                cardBorder = Color(0x6600E5FF),
                glow = Color(0x7700E5FF),
                playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 18,
                controlPanelBg = Color(0xD9081524)
            )
        }

        // 7. Fire / Lava / Volcano Ruby
        if (p.contains("огон") || p.contains("плам") || p.contains("лава") || p.contains("рубин") || p.contains("вулкан") || p.contains("красн")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Вулканический Рубин",
                description = "Раскаленная магма, рубиновый огонь и мощные увеличенные кнопки управления",
                primary = Color(0xFFFF1744),
                secondary = Color(0xFFFF9100),
                background = Color(0xFF140606),
                surface = Color(0xFF240E0E),
                cardBorder = Color(0x66FF1744),
                glow = Color(0x77FF1744),
                playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
                buttonSize = ButtonSizeOption.LARGE,
                cornerRadiusDp = 14,
                controlPanelBg = Color(0xE61E0A0A)
            )
        }

        // 8. Royal Gold / Luxury Obsidian
        if (p.contains("золот") || p.contains("люкс") || p.contains("премиум") || p.contains("богат") || p.contains("gold") || p.contains("королев")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Королевское Золото",
                description = "Сияющее 24-каратное золото, темный обсидиан и премиальные закругления",
                primary = Color(0xFFFFD700),
                secondary = Color(0xFFFFA000),
                background = Color(0xFF0F0E0A),
                surface = Color(0xFF1F1B12),
                cardBorder = Color(0x66FFD700),
                glow = Color(0x77FFD700),
                playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 20,
                controlPanelBg = Color(0xE61A160C)
            )
        }

        // 9. Coffee / Chocolate / Caramel Warmth
        if (p.contains("кофе") || p.contains("шоколад") || p.contains("карамел") || p.contains("уют") || p.contains("тепл") || p.contains("коричнев")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Теплый Мокко",
                description = "Ароматный эспрессо, сливочная карамель и бархатные мягкие тона",
                primary = Color(0xFFD4A373),
                secondary = Color(0xFFFAEDCD),
                background = Color(0xFF130E0A),
                surface = Color(0xFF221A13),
                cardBorder = Color(0x66D4A373),
                glow = Color(0x55D4A373),
                playerControlStyle = PlayerControlStyle.MODERN_FLOAT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 18,
                controlPanelBg = Color(0xE61A120D)
            )
        }

        // 10. Cosmic Nebula / Deep Space
        if (p.contains("космос") || p.contains("туманност") || p.contains("звезд") || p.contains("галактик") || p.contains("space") || p.contains("астро")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "Космическая Небула",
                description = "Глубокая межзвездная синева, лазурное сияние звезд и кинотеатральный масштаб",
                primary = Color(0xFF7000FF),
                secondary = Color(0xFF00F0FF),
                background = Color(0xFF060614),
                surface = Color(0xFF0F0E26),
                cardBorder = Color(0x667000FF),
                glow = Color(0x777000FF),
                playerControlStyle = PlayerControlStyle.CINEMATIC_FULL,
                buttonSize = ButtonSizeOption.LARGE,
                cornerRadiusDp = 24,
                controlPanelBg = Color(0xD90C0B1F)
            )
        }

        // 11. Minimalist Monochrome / OLED Pitch Black
        if (p.contains("минимал") || p.contains("монохром") || p.contains("oled") || p.contains("черн") || p.contains("бел") || p.contains("графит") || p.contains("сер")) {
            return AppCustomTheme(
                id = "custom_ai",
                name = "OLED Абсолют",
                description = "Чистый глубокий черный для OLED дисплеев с максимальным контрастом и энергосбережением",
                primary = Color(0xFFFFFFFF),
                secondary = Color(0xFFA0AEC0),
                background = Color(0xFF000000),
                surface = Color(0xFF111111),
                cardBorder = Color(0x44FFFFFF),
                glow = Color(0x33FFFFFF),
                playerControlStyle = PlayerControlStyle.MINIMAL_COMPACT,
                buttonSize = ButtonSizeOption.STANDARD,
                cornerRadiusDp = 12,
                controlPanelBg = Color(0xF2080808)
            )
        }

        // 12. Dynamic Procedural Color-Field Synthesizer for ANY OTHER prompt
        val hash = prompt.hashCode()
        val hue1 = kotlin.math.abs(hash % 360).toFloat()
        val hue2 = (hue1 + 35f + kotlin.math.abs((hash shr 4) % 40)) % 360f

        val primary = Color.hsv(hue1, 0.90f, 1.0f)
        val secondary = Color.hsv(hue2, 0.80f, 0.95f)
        val bg = Color.hsv(hue1, 0.30f, 0.05f)
        val surface = Color.hsv(hue1, 0.25f, 0.11f)
        val border = primary.copy(alpha = 0.45f)
        val glow = primary.copy(alpha = 0.60f)

        val styleOptions = PlayerControlStyle.values()
        val chosenStyle = styleOptions[kotlin.math.abs(hash) % styleOptions.size]

        val sizeOptions = ButtonSizeOption.values()
        val chosenSize = sizeOptions[kotlin.math.abs(hash shr 2) % sizeOptions.size]

        val radius = listOf(8, 14, 18, 24)[kotlin.math.abs(hash shr 3) % 4]

        val titleWord = prompt.trim().split(" ").take(3).joinToString(" ").replaceFirstChar { it.uppercase() }

        return AppCustomTheme(
            id = "custom_ai",
            name = "Стиль «$titleWord»",
            description = "Уникальное авторское оформление, синтезированное ИИ под концепцию «$prompt»",
            primary = primary,
            secondary = secondary,
            background = bg,
            surface = surface,
            cardBorder = border,
            glow = glow,
            playerControlStyle = chosenStyle,
            buttonSize = chosenSize,
            cornerRadiusDp = radius,
            controlPanelBg = surface.copy(alpha = 0.85f)
        )
    }

    fun parseHexColor(hex: String): Color {
        return try {
            val clean = hex.removePrefix("#").trim()
            val colorInt = if (clean.length == 6) {
                ("FF$clean").toLong(16).toInt()
            } else if (clean.length == 8) {
                clean.toLong(16).toInt()
            } else {
                0xFFFF8A00.toInt()
            }
            colorFromInt(colorInt)
        } catch (_: Exception) {
            ThemePresets.NexoraNeon.primary
        }
    }
}
