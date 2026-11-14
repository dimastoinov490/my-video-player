package com.example.videoplayer.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import java.util.UUID

object DolbyAudioEnhancer {
    private const val TAG = "DolbyAudioEnhancer"

    // Исправленный корректный UUID-дескриптор для системных Dolby / Atmos эффектов
    private val DOLBY_EFFECT_TYPE_UUID: UUID = UUID.fromString("43777461-262f-45ba-a3f9-79015c907a11")

    data class SoftwareFallbackBundle(
        val equalizer: Equalizer? = null,
        val bassBoost: BassBoost? = null,
        val virtualizer: Virtualizer? = null
    ) {
        fun release() {
            runCatching { equalizer?.release() }
            runCatching { bassBoost?.release() }
            runCatching { virtualizer?.release() }
        }
    }

    fun applyDolbyAtmosEffect(audioSessionId: Int): AudioEffect? {
        try {
            // Проверяем доступные аудио-эффекты в системе
            val effects = AudioEffect.queryEffects()
            if (effects != null) {
                for (descriptor in effects) {
                    val name = descriptor.name ?: ""
                    if (name.contains("Dolby", ignoreCase = true) ||
                        name.contains("Atmos", ignoreCase = true) ||
                        descriptor.type == DOLBY_EFFECT_TYPE_UUID
                    ) {
                        val constructor = AudioEffect::class.java.getConstructor(
                            UUID::class.java,
                            UUID::class.java,
                            Int::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        )
                        val effect = constructor.newInstance(
                            descriptor.type,
                            descriptor.uuid,
                            0,
                            audioSessionId
                        )
                        effect.enabled = true
                        return effect
                    }
                }
            }
        } catch (e: Throwable) {
            Log.d(TAG, "Вендорский Dolby/Atmos эффект недоступен, используем программный fallback", e)
        }

        // Если вендорский эффект недоступен, используем стандартный программный Multi-band / BassBoost
        return applySoftwareFallback(audioSessionId).equalizer
    }

    fun applySoftwareFallback(audioSessionId: Int): SoftwareFallbackBundle {
        var eq: Equalizer? = null
        var bass: BassBoost? = null
        var virtualizer: Virtualizer? = null

        try {
            eq = Equalizer(0, audioSessionId).apply {
                val bands = numberOfBands.toInt()
                val range = bandLevelRange
                val minLevel = range[0].toInt()
                val maxLevel = range[1].toInt()
                val boostLow = ((maxLevel - minLevel) * 0.25f).toInt().coerceIn(0, maxLevel)
                val boostHigh = ((maxLevel - minLevel) * 0.30f).toInt().coerceIn(0, maxLevel)
                for (band in 0 until bands) {
                    val level = when {
                        band == 0 -> boostLow
                        band == bands - 1 -> boostHigh
                        else -> (boostLow / 3)
                    }.coerceIn(minLevel, maxLevel).toShort()
                    setBandLevel(band.toShort(), level)
                }
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer fallback error", e)
            runCatching { eq?.release() }
            eq = null
        }

        try {
            bass = BassBoost(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(350.toShort())
                }
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost fallback error", e)
            runCatching { bass?.release() }
            bass = null
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) {
                    setStrength(450.toShort())
                }
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Virtualizer fallback error", e)
            runCatching { virtualizer?.release() }
            virtualizer = null
        }

        return SoftwareFallbackBundle(
            equalizer = eq,
            bassBoost = bass,
            virtualizer = virtualizer
        )
    }
}
