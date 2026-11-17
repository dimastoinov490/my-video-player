package com.example.videoplayer.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class EqualizerState(
    val enabled: Boolean = false,
    val presetName: String = "Рок",
    // 5 bands: 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-12f .. +12f dB)
    val bands: List<Float> = listOf(4.5f, 2.0f, -1.5f, 3.5f, 5.5f)
)

data class SubtitleStyleState(
    val enabled: Boolean = true,
    val fontSizeSp: Int = 18,
    val colorHex: String = "#ECECF0"
)

data class RecentMediaEntry(
    val uri: String,
    val title: String,
    val subtitle: String,
    val isAudio: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

object PlaybackPreferences {
    private const val PREF_NAME = "nexora_playback_ui_prefs"
    private const val KEY_EQ_ENABLED = "eq_enabled"
    private const val KEY_EQ_PRESET = "eq_preset"
    private const val KEY_EQ_BANDS = "eq_bands"

    private const val KEY_SUB_ENABLED = "sub_enabled"
    private const val KEY_SUB_SIZE = "sub_size"
    private const val KEY_SUB_COLOR = "sub_color"

    private const val KEY_RECENT_LIST = "recent_media_json"
    private const val KEY_WELCOME_SEEN = "welcome_seen"
    private const val KEY_ACCENT_HEX = "accent_hex"

    val EQ_PRESETS: Map<String, List<Float>> = linkedMapOf(
        "Обычный" to listOf(0f, 0f, 0f, 0f, 0f),
        "Рок" to listOf(4.5f, 2.0f, -1.5f, 3.5f, 5.5f),
        "Поп" to listOf(-1.0f, 2.5f, 4.0f, 2.5f, -0.5f),
        "Джаз" to listOf(3.0f, 1.5f, -1.0f, 2.5f, 3.5f),
        "Классика" to listOf(4.0f, 2.0f, -1.5f, 2.0f, 4.5f),
        "Бас" to listOf(7.5f, 4.5f, 0.5f, 0f, 1.0f),
        "Вокал" to listOf(-2.5f, 0f, 4.5f, 4.0f, 1.0f)
    )

    fun getEqualizerState(context: Context): EqualizerState {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_EQ_ENABLED, false)
        val preset = prefs.getString(KEY_EQ_PRESET, "Рок") ?: "Рок"
        val rawBands = prefs.getString(KEY_EQ_BANDS, null)
        val bands = if (!rawBands.isNullOrBlank()) {
            try {
                val arr = JSONArray(rawBands)
                if (arr.length() == 5) {
                    (0 until 5).map { arr.optDouble(it, 0.0).toFloat().coerceIn(-12f, 12f) }
                } else {
                    EQ_PRESETS[preset] ?: listOf(4.5f, 2.0f, -1.5f, 3.5f, 5.5f)
                }
            } catch (_: Exception) {
                EQ_PRESETS[preset] ?: listOf(4.5f, 2.0f, -1.5f, 3.5f, 5.5f)
            }
        } else {
            EQ_PRESETS[preset] ?: listOf(4.5f, 2.0f, -1.5f, 3.5f, 5.5f)
        }
        return EqualizerState(enabled = enabled, presetName = preset, bands = bands)
    }

    fun saveEqualizerState(context: Context, state: EqualizerState) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        state.bands.take(5).forEach { arr.put(it.toDouble()) }
        prefs.edit()
            .putBoolean(KEY_EQ_ENABLED, state.enabled)
            .putString(KEY_EQ_PRESET, state.presetName)
            .putString(KEY_EQ_BANDS, arr.toString())
            .apply()
    }

    fun getSubtitleStyle(context: Context): SubtitleStyleState {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return SubtitleStyleState(
            enabled = prefs.getBoolean(KEY_SUB_ENABLED, true),
            fontSizeSp = prefs.getInt(KEY_SUB_SIZE, 18).coerceIn(12, 32),
            colorHex = prefs.getString(KEY_SUB_COLOR, "#ECECF0") ?: "#ECECF0"
        )
    }

    fun saveSubtitleStyle(context: Context, state: SubtitleStyleState) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_SUB_ENABLED, state.enabled)
            .putInt(KEY_SUB_SIZE, state.fontSizeSp.coerceIn(12, 32))
            .putString(KEY_SUB_COLOR, state.colorHex)
            .apply()
    }

    fun recordRecentMedia(
        context: Context,
        uri: String,
        title: String,
        subtitle: String,
        isAudio: Boolean,
        positionMs: Long,
        durationMs: Long
    ) {
        if (uri.isBlank()) return
        val current = getRecentMedia(context).toMutableList()
        current.removeAll { it.uri == uri }
        current.add(
            0,
            RecentMediaEntry(
                uri = uri,
                title = title.ifBlank { "Медиафайл" },
                subtitle = subtitle,
                isAudio = isAudio,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L),
                updatedAt = System.currentTimeMillis()
            )
        )
        val trimmed = current.take(15)
        val arr = JSONArray()
        trimmed.forEach { item ->
            arr.put(JSONObject().apply {
                put("uri", item.uri)
                put("title", item.title)
                put("subtitle", item.subtitle)
                put("isAudio", item.isAudio)
                put("positionMs", item.positionMs)
                put("durationMs", item.durationMs)
                put("updatedAt", item.updatedAt)
            })
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_LIST, arr.toString())
            .apply()
    }

    fun getRecentMedia(context: Context): List<RecentMediaEntry> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_RECENT_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { idx ->
                val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
                val uri = obj.optString("uri")
                if (uri.isBlank()) return@mapNotNull null
                RecentMediaEntry(
                    uri = uri,
                    title = obj.optString("title", "Медиафайл"),
                    subtitle = obj.optString("subtitle", ""),
                    isAudio = obj.optBoolean("isAudio", false),
                    positionMs = obj.optLong("positionMs", 0L),
                    durationMs = obj.optLong("durationMs", 0L),
                    updatedAt = obj.optLong("updatedAt", 0L)
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isWelcomeSeen(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getBoolean(KEY_WELCOME_SEEN, false)

    fun setWelcomeSeen(context: Context, seen: Boolean) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_WELCOME_SEEN, seen).apply()
    }

    fun getAccentHex(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCENT_HEX, "#FF9A1A") ?: "#FF9A1A"

    fun setAccentHex(context: Context, hex: String) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT_HEX, hex).apply()
    }
}
