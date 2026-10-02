package com.glasscast.app.player

import android.media.audiofx.LoudnessEnhancer

/**
 * Boost voices: Android's LoudnessEnhancer at +7 dB on the player's audio
 * session — quiet speech comes up, peaks are limited.
 *
 * 1.4 tried a DynamicsProcessing speech chain here (low cut, presence lift,
 * three-band compression, limiter). It sounded worse at the same volume:
 * compression from −30 dB with modest makeup gain flattened the dynamics
 * without making anything louder, and the frequency-domain processing
 * (10 ms frames) smeared consonants. Reverted; this is the 1.3 behavior,
 * which is what people actually noticed working.
 */
internal class VoiceBoost(audioSession: Int) {

    private var effect: LoudnessEnhancer? = runCatching { LoudnessEnhancer(audioSession) }.getOrNull()

    fun setEnabled(on: Boolean) {
        effect?.let {
            runCatching {
                it.setTargetGain(if (on) GAIN_MB else 0)
                it.setEnabled(on)
            }
        }
    }

    fun release() {
        runCatching { effect?.release() }
        effect = null
    }

    private companion object {
        /** +7 dB, in millibels. */
        const val GAIN_MB = 700
    }
}
