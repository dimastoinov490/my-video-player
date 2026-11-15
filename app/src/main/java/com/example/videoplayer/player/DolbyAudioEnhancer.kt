package com.example.videoplayer.player

import android.media.audiofx.AudioEffect
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log
import java.util.UUID

/**
 * Owns every Android audio effect created for one audio session.
 * The caller must close this handle when playback stops or the session changes.
 */
class AudioEnhancementSession internal constructor(
    val usesVendorEffect: Boolean,
    private val effects: List<AudioEffect>
) : AutoCloseable {
    @Volatile
    private var closed = false

    override fun close() {
        synchronized(this) {
            if (closed) return
            closed = true
            effects.asReversed().forEach { effect ->
                runCatching { effect.enabled = false }
                runCatching { effect.release() }
            }
        }
    }
}

object DolbyAudioEnhancer {
    private const val TAG = "DolbyAudioEnhancer"

    // Vendor-specific effect type descriptor; availability varies by device and OEM.
    private val DOLBY_EFFECT_TYPE_UUID: UUID =
        UUID.fromString("43777461-262f-45ba-a3f9-79015c907a11")

    data class SoftwareFallbackBundle(
        val equalizer: Equalizer? = null,
        val bassBoost: BassBoost? = null,
        val virtualizer: Virtualizer? = null
    ) : AutoCloseable {
        override fun close() {
            // Disable and release every effect, not just the equalizer.
            listOfNotNull(equalizer, bassBoost, virtualizer).asReversed().forEach { effect ->
                runCatching { effect.enabled = false }
                runCatching { effect.release() }
            }
        }
    }

    /**
     * Creates a managed effect set for the supplied Android audio session.
     * The returned handle owns all effects and MUST be closed by the caller.
     *
     * The software fallback is an EQ/bass/virtualizer preset, not Dolby Atmos.
     */
    fun applyDolbyAtmosEffect(audioSessionId: Int): AudioEnhancementSession? {
        if (audioSessionId <= 0) {
            Log.w(TAG, "Cannot attach audio effects to invalid session id: $audioSessionId")
            return null
        }

        var vendorEffect: AudioEffect? = null
        try {
            val descriptors = AudioEffect.queryEffects().orEmpty()
            val descriptor = descriptors.firstOrNull { item ->
                val name = item.name.orEmpty()
                name.contains("Dolby", ignoreCase = true) ||
                    name.contains("Atmos", ignoreCase = true) ||
                    item.type == DOLBY_EFFECT_TYPE_UUID
            }

            if (descriptor != null) {
                val constructor = AudioEffect::class.java.getConstructor(
                    UUID::class.java,
                    UUID::class.java,
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType
                )
                vendorEffect = constructor.newInstance(
                    descriptor.type,
                    descriptor.uuid,
                    0,
                    audioSessionId
                )
                vendorEffect?.enabled = true
                return AudioEnhancementSession(
                    usesVendorEffect = true,
                    effects = listOfNotNull(vendorEffect)
                )
            }
        } catch (e: Throwable) {
            runCatching { vendorEffect?.enabled = false }
            runCatching { vendorEffect?.release() }
            Log.d(TAG, "Vendor Dolby/Atmos effect unavailable; using software EQ fallback", e)
        }

        val fallback = applySoftwareFallback(audioSessionId)
        val ownedEffects = listOfNotNull(
            fallback.equalizer,
            fallback.bassBoost,
            fallback.virtualizer
        )
        if (ownedEffects.isEmpty()) return null

        return AudioEnhancementSession(
            usesVendorEffect = false,
            effects = ownedEffects
        )
    }

    fun applySoftwareFallback(audioSessionId: Int): SoftwareFallbackBundle {
        if (audioSessionId <= 0) return SoftwareFallbackBundle()

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
                        else -> boostLow / 3
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
                if (strengthSupported) setStrength(350.toShort())
                enabled = true
            }
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost fallback error", e)
            runCatching { bass?.release() }
            bass = null
        }

        try {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                if (strengthSupported) setStrength(450.toShort())
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
