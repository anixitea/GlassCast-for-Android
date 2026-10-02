package com.glasscast.app.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * What a touch meant, not what it should feel like.
 *
 * Screens name the intent; this file decides the shape of the buzz and what the
 * motor under it can actually reproduce. That split is the point — otherwise
 * every call site ends up hardcoding a vibration pattern and they drift apart.
 *
 * Everything here is short. The longest pattern is three beats inside ~50ms; a
 * haptic that outlasts the finger stops reading as a response to the tap and
 * starts reading as the phone buzzing at you.
 */
enum class Haptic {
    /** Lightest beat, for something that repeats while a finger is still down. */
    Tick,

    /** A plain press with no state behind it. */
    Tap,

    /** A discrete choice landing: a tab, a filter pill, the end of a scrub. */
    Select,

    /** Turning something on — a light lead-in rising into a firm beat. */
    ToggleOn,

    /** Turning it off — the same pair mirrored, so it falls away. */
    ToggleOff,

    /** Playback starting: swells into the beat that lands. */
    Resume,

    /** Playback stopping: lands first, then releases. */
    Pause,

    /** Jumping forward — an accelerating triplet. */
    SkipForward,

    /** Jumping back — [SkipForward] reversed, which is what makes the pair legible. */
    SkipBack,

    /** Something growing to fill the screen: the mini player opening. */
    Expand
}

class Haptics internal constructor(context: Context) {
    private val app = context.applicationContext
    fun play(haptic: Haptic) = HapticMotor.of(app)?.play(haptic)
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context) }
}

/**
 * One beat: which short primitive to strike, how hard relative to its nominal
 * strength, and how long to wait after the previous beat.
 *
 * Only genuinely short primitives are used. The platform also offers rises,
 * falls and thuds, and those run 80–500ms — long enough that a two-beat pattern
 * built from them would still be going after the screen had finished
 * responding.
 */
private class Beat(val kind: Kind, val scale: Float, val gapMs: Long) {
    enum class Kind(val pulseMs: Long, val amplitude: Int) {
        Tick(8, 110),
        LowTick(10, 95),
        Click(14, 210)
    }
}

private fun rhythmOf(haptic: Haptic): List<Beat> = when (haptic) {
    Haptic.Tick -> listOf(Beat(Beat.Kind.Tick, 0.35f, 0))
    Haptic.Tap -> listOf(Beat(Beat.Kind.Click, 0.50f, 0))
    Haptic.Select -> listOf(
        Beat(Beat.Kind.Tick, 0.40f, 0),
        Beat(Beat.Kind.Click, 0.75f, 18)
    )
    Haptic.ToggleOn -> listOf(
        Beat(Beat.Kind.Tick, 0.40f, 0),
        Beat(Beat.Kind.Click, 0.90f, 14)
    )
    Haptic.ToggleOff -> listOf(
        Beat(Beat.Kind.Click, 0.75f, 0),
        Beat(Beat.Kind.Tick, 0.30f, 14)
    )
    Haptic.Resume -> listOf(
        Beat(Beat.Kind.LowTick, 0.50f, 0),
        Beat(Beat.Kind.Click, 0.85f, 22)
    )
    Haptic.Pause -> listOf(
        Beat(Beat.Kind.Click, 0.85f, 0),
        Beat(Beat.Kind.LowTick, 0.40f, 22)
    )
    Haptic.SkipForward -> listOf(
        Beat(Beat.Kind.Tick, 0.40f, 0),
        Beat(Beat.Kind.Tick, 0.55f, 16),
        Beat(Beat.Kind.Click, 0.70f, 16)
    )
    Haptic.SkipBack -> listOf(
        Beat(Beat.Kind.Click, 0.70f, 0),
        Beat(Beat.Kind.Tick, 0.55f, 16),
        Beat(Beat.Kind.Tick, 0.40f, 16)
    )
    Haptic.Expand -> listOf(
        Beat(Beat.Kind.Tick, 0.30f, 0),
        Beat(Beat.Kind.Tick, 0.45f, 12),
        Beat(Beat.Kind.Click, 0.60f, 12)
    )
}

/**
 * Works out once what this phone can do, then renders every [Haptic] into the
 * best effect available to it:
 *
 *  1. **Composition** (API 30+ with primitive support). Real rhythmic haptics —
 *     the beats go to the vibrator as primitives and it reproduces their
 *     character, not just their timing.
 *  2. **Waveform with amplitude control.** The same rhythm as on-pulses of
 *     varying strength. Cruder, still clearly a pattern.
 *  3. **Plain waveform.** One volume, so only timing survives, and the pulses
 *     have to be longer to be felt at all — which is why this tier drops a
 *     three-beat pattern to its outer two rather than running past ~80ms.
 *
 * Effects are immutable, so each is compiled on first use and kept.
 */
private class HapticMotor private constructor(
    private val vibrator: Vibrator,
    private val canCompose: Boolean,
    private val canScaleAmplitude: Boolean
) {
    private val compiled = HashMap<Haptic, VibrationEffect>()

    fun play(haptic: Haptic) {
        val effect = synchronized(compiled) {
            compiled.getOrPut(haptic) { compile(rhythmOf(haptic)) }
        }
        runCatching { vibrator.vibrate(effect) }
    }

    private fun compile(beats: List<Beat>): VibrationEffect = when {
        canCompose && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> composed(beats)
        canScaleAmplitude -> waveform(beats, coarse = false)
        else -> waveform(beats, coarse = true)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private fun composed(beats: List<Beat>): VibrationEffect {
        val builder = VibrationEffect.startComposition()
        beats.forEach { beat ->
            val primitive = when (beat.kind) {
                Beat.Kind.Tick -> VibrationEffect.Composition.PRIMITIVE_TICK
                Beat.Kind.LowTick -> VibrationEffect.Composition.PRIMITIVE_LOW_TICK
                Beat.Kind.Click -> VibrationEffect.Composition.PRIMITIVE_CLICK
            }
            builder.addPrimitive(primitive, beat.scale, beat.gapMs.toInt())
        }
        return builder.compose()
    }

    private fun waveform(beats: List<Beat>, coarse: Boolean): VibrationEffect {
        // On a single-volume motor an 8ms pulse is below the threshold of
        // feeling, so pulses are stretched — and a three-beat pattern loses its
        // middle beat rather than becoming a long rumble.
        val used = if (coarse && beats.size > 2) listOf(beats.first(), beats.last()) else beats

        val timings = ArrayList<Long>()
        val amplitudes = ArrayList<Int>()
        used.forEachIndexed { index, beat ->
            if (index > 0 || beat.gapMs > 0) {
                timings.add(beat.gapMs.coerceAtLeast(if (index == 0) 0 else 1))
                amplitudes.add(0)
            }
            timings.add(if (coarse) beat.kind.pulseMs * 2 else beat.kind.pulseMs)
            amplitudes.add(
                if (coarse) 255
                else (beat.kind.amplitude * beat.scale).toInt().coerceIn(1, 255)
            )
        }

        return if (canScaleAmplitude && !coarse) {
            VibrationEffect.createWaveform(timings.toLongArray(), amplitudes.toIntArray(), -1)
        } else {
            VibrationEffect.createWaveform(timings.toLongArray(), -1)
        }
    }

    companion object {
        private var cached: HapticMotor? = null
        private var probed = false

        fun of(context: Context): HapticMotor? {
            if (probed) return cached
            probed = true

            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }

            if (vibrator == null || !vibrator.hasVibrator()) return null

            val canCompose = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                runCatching {
                    vibrator.areAllPrimitivesSupported(
                        VibrationEffect.Composition.PRIMITIVE_TICK,
                        VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
                        VibrationEffect.Composition.PRIMITIVE_CLICK
                    )
                }.getOrDefault(false)

            cached = HapticMotor(vibrator, canCompose, vibrator.hasAmplitudeControl())
            return cached
        }
    }
}
