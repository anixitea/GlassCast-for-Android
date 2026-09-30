package com.glasscast.app.player

import androidx.media3.common.C
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * How loud the voice is right now — so the wave can move with the speech.
 *
 * Cider bounces its wave to a song's beat. Podcasts have no beat; their pulse
 * is the voice. This reads the audio as it passes through ExoPlayer's own
 * pipeline (a TeeAudioProcessor: the audio goes through untouched, this gets a
 * read-only copy) and turns it into a 0–1 level. No microphone permission,
 * which Android's Visualizer would need — a bad look for a podcast app.
 *
 * Shaped for speech: −40 dBFS reads as silence, −12 as full; syllables rise
 * fast and fall a little slower, so the wave follows the words and breathes
 * rather than jitters.
 *
 * Timing: the pipeline sees audio about a quarter-second before it leaves the
 * speaker (the output buffer), so each reading is stamped with when it will
 * actually be heard, and [current] returns the newest one whose moment has
 * come. The wave moves with the voice, not just ahead of it.
 *
 * Written on the audio thread, read on the UI thread. The races are benign —
 * a reading one buffer old — and it's only ever used for drawing.
 */
object VoiceLevel : TeeAudioProcessor.AudioBufferSink {

    private const val LATENCY_NS = 250_000_000L
    /** No fresh audio for this long (paused, or casting): report a neutral level. */
    private const val STALE_NS = 1_500_000_000L
    private const val NEUTRAL = 0.6f
    private const val SLOTS = 64

    private val times = LongArray(SLOTS)
    private val levels = FloatArray(SLOTS)
    @Volatile private var head = 0
    @Volatile private var encoding = C.ENCODING_PCM_16BIT
    private var smoothed = 0f

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        this.encoding = encoding
        smoothed = 0f
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val rms = when (encoding) {
            C.ENCODING_PCM_16BIT -> rms16(buffer)
            C.ENCODING_PCM_FLOAT -> rmsFloat(buffer)
            else -> return
        }
        val db = if (rms > 1e-6f) 20f * log10(rms) else -96f
        val target = ((db + 40f) / 28f).coerceIn(0f, 1f)
        smoothed += (target - smoothed) * (if (target > smoothed) 0.6f else 0.35f)
        val index = head
        val slot = index % SLOTS
        times[slot] = System.nanoTime() + LATENCY_NS
        levels[slot] = smoothed
        head = index + 1
    }

    /** The level of what's audible now, 0–1. */
    fun current(): Float {
        val now = System.nanoTime()
        val newest = head - 1
        if (newest < 0) return NEUTRAL
        if (now - times[newest % SLOTS] > STALE_NS) return NEUTRAL
        for (back in 0 until SLOTS) {
            val index = newest - back
            if (index < 0) break
            val slot = index % SLOTS
            if (times[slot] <= now) return levels[slot]
        }
        return levels[(newest - SLOTS + 1).coerceAtLeast(0) % SLOTS]
    }

    // Every 4th sample is plenty for a loudness envelope, and quarter the work.
    private fun rms16(buffer: ByteBuffer): Float {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        var i = b.position()
        val end = b.limit() - 1
        while (i < end) {
            val s = b.getShort(i) / 32768.0
            sum += s * s
            n++
            i += 8
        }
        return if (n == 0) 0f else sqrt(sum / n).toFloat()
    }

    private fun rmsFloat(buffer: ByteBuffer): Float {
        val b = buffer.duplicate().order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        var i = b.position()
        val end = b.limit() - 3
        while (i < end) {
            val s = b.getFloat(i).toDouble()
            sum += s * s
            n++
            i += 16
        }
        return if (n == 0) 0f else sqrt(sum / n).toFloat()
    }
}
