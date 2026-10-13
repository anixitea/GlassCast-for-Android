package com.glasscast.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The wavy scrubber.
 *
 * Worth being precise about what the wave means, because last round I argued
 * against a "waveform" and this is not one. A waveform claims to show the audio
 * — where the speech is, where the silences fall — and we have no such data.
 * This is Material 3 Expressive's wavy *progress* indicator: the wave's only
 * meaning is "this is playing". It moves while audio plays and settles flat the
 * moment it pauses, which makes it the most legible play-state signal on the
 * screen, not a decoration pretending to be data.
 *
 * Played portion: a sine stroke. Remaining: a thinner flat line with a small
 * end dot. Between them, a vertical bar marks the playhead and is what the
 * finger grabs.
 */
@Composable
fun WavySlider(
    progress: Float,
    playing: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 30.dp,
    amplitude: Dp = 4.dp,
    wavelength: Dp = 30.dp,
    strokeWidth: Dp = 4.5.dp,
    showThumb: Boolean = true,
    onScrubStart: () -> Unit = {},
    onScrub: (Float) -> Unit = {},
    onScrubEnd: (Float) -> Unit = {},
    /** Wave updates per second; 0 = every frame. */
    frameRate: Int = 0,
    /**
     * Loudness of what's playing, 0–1 (see VoiceLevel). When given, the wave's
     * height follows the voice — up on the words, down to a gentle ripple in
     * the pauses — instead of a constant swell. Read only while drawing.
     */
    voice: (() -> Float)? = null
) {
    var dragging by remember { mutableStateOf(false) }
    var local by remember { mutableStateOf(0f) }
    val shown = if (dragging) local else progress.coerceIn(0f, 1f)

    // The wave calms to a line when paused rather than freezing mid-crest. A
    // frozen wave reads as "stalled"; a flat one reads as "stopped".
    val amp by animateFloatAsState(
        targetValue = if (playing && !dragging) 1f else 0f,
        animationSpec = spring(),
        label = "waveAmplitude"
    )

    /*
     * The wave's travel runs only while playing, from a clock that stops
     * dead when paused (the flat line doesn't redraw at all). [frameRate]
     * caps how often it advances: the mini player uses 30 so a list with
     * something playing isn't redrawn 144 times a second for a slow wave;
     * the full player leaves it uncapped.
     */
    val clock = rememberFrameClock(running = playing, fps = frameRate)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                // One gesture loop for tap and drag — see ThinSlider for why two
                // separate detectors silently lose the tap.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragging = true
                    local = (down.position.x / size.width).coerceIn(0f, 1f)
                    onScrubStart()
                    onScrub(local)
                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            pointer.consume()
                            break
                        }
                        if (pointer.positionChanged()) {
                            local = (pointer.position.x / size.width).coerceIn(0f, 1f)
                            onScrub(local)
                            pointer.consume()
                        }
                    }
                    dragging = false
                    onScrubEnd(local)
                }
            }
    ) {
        val w = size.width
        val cy = size.height / 2f
        val stroke = strokeWidth.toPx()
        val phase = ((clock.value % 1.4f) / 1.4f) * (2f * PI.toFloat())
        val a = amplitude.toPx() * amp * (if (voice != null) 0.3f + 0.7f * voice() else 1f)
        val k = (2 * PI / wavelength.toPx()).toFloat()
        val head = (w * shown).coerceIn(0f, w)
        val gap = if (showThumb) 7.dp.toPx() else 3.dp.toPx()

        // Played: the wave. Sampled every 2px, which is smooth at any density
        // and cheap enough to redraw on every frame of the animation.
        val playedEnd = (head - gap).coerceAtLeast(0f)
        if (playedEnd > stroke) {
            val path = Path()
            var x = stroke / 2f
            path.moveTo(x, cy + a * sin(k * x - phase))
            while (x < playedEnd) {
                x = (x + 2f).coerceAtMost(playedEnd)
                path.lineTo(x, cy + a * sin(k * x - phase))
            }
            drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        }

        // Remaining: flat and quieter, but the same weight as the wave. A
        // thinner line read as a different element rather than the rest of
        // the same track — the difference between the two parts should be the
        // shape and the brightness, not the stroke.
        val restStart = head + gap
        val dotR = stroke / 2f
        val restEnd = w - dotR * 4
        if (restStart < restEnd) {
            drawLine(
                color = color.copy(alpha = 0.32f),
                start = Offset(restStart, cy),
                end = Offset(restEnd, cy),
                strokeWidth = stroke,
                cap = StrokeCap.Round
            )
        }
        drawCircle(color.copy(alpha = 0.55f), radius = dotR, center = Offset(w - dotR, cy))

        // The playhead bar — the part a finger aims at.
        if (showThumb) {
            val barH = size.height * (if (dragging) 1f else 0.86f)
            drawLine(
                color = color,
                start = Offset(head, cy - barH / 2f),
                end = Offset(head, cy + barH / 2f),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
