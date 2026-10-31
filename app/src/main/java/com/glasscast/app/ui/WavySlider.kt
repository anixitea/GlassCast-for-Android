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
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate

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
 *
 * Glass ([glassLine], the Glass look's player): no wave. A rod of glass
 * instead, filled as far as you've listened, with light along its top and a
 * shade along its foot. It swells while a finger is on it, the way Apple's
 * scrubbers magnify, and settles back when let go.
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
    voice: (() -> Float)? = null,
    /** Stretches drawn in [markColor] — ad breaks — as fractions of the whole. */
    marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
    markColor: Color = color,
    /** Points marked with a small dot under the line (bookmarks), as fractions of the whole. */
    ticks: List<Float> = emptyList(),
    /** Sea Glass: the playhead is a bead of this glass instead of a bar. */
    thumbGlass: com.glasscast.app.ui.seaglass.Pigment? = null,
    thumbGlassDark: Boolean = false,
    /** The Glass look: a still rod of glass that swells under the finger, instead of the wave. */
    glassLine: Boolean = false,
    /** Swells the rod as if held: the TV's, while it's focused. */
    held: Boolean = false
) {
    val bead = remember { com.glasscast.app.ui.seaglass.GlassPainter(com.glasscast.app.ui.seaglass.GlassRecipe.Pebble, seed = 4) }
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
    val clock = rememberFrameClock(running = playing && !glassLine, fps = frameRate)
    // The glass rod's swell while it's held: quick to grow, softer to settle.
    val swell by animateFloatAsState(
        targetValue = if ((dragging || held) && glassLine) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = if (dragging || held) 900f else 420f),
        label = "glassLineSwell"
    )

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
        if (glassLine) {
            drawGlassLine(shown, color, strokeWidth.toPx(), swell, marks, markColor, ticks)
            return@Canvas
        }
        val w = size.width
        val cy = size.height / 2f
        val stroke = strokeWidth.toPx()
        val phase = ((clock.value % 1.4f) / 1.4f) * (2f * PI.toFloat())
        val a = amplitude.toPx() * amp * (if (voice != null) 0.3f + 0.7f * voice() else 1f)
        val k = (2 * PI / wavelength.toPx()).toFloat()
        val head = (w * shown).coerceIn(0f, w)
        val beadSize = if (thumbGlass != null) (size.height * 0.64f).coerceAtMost(22.dp.toPx()) * (if (dragging) 1.18f else 1f) else 0f
        val gap = when {
            showThumb && thumbGlass != null -> beadSize / 2f + 3.dp.toPx()
            showThumb -> 7.dp.toPx()
            else -> 3.dp.toPx()
        }

        // Played: the wave. Sampled every 2px, which is smooth at any density
        // and cheap enough to redraw on every frame of the animation.
        val playedEnd = (head - gap).coerceAtLeast(0f)
        var played: Path? = null
        if (playedEnd > stroke) {
            val path = Path()
            played = path
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

        // Ad breaks: the same wave and track, redrawn in their own color
        // inside each break's span.
        marks.forEach { m ->
            val left = (m.start * w).coerceIn(0f, w)
            val right = (m.endInclusive * w).coerceIn(0f, w)
            if (right - left < 1f) return@forEach
            clipRect(left = left, top = 0f, right = right, bottom = size.height) {
                played?.let { drawPath(it, markColor, style = Stroke(width = stroke, cap = StrokeCap.Round)) }
                if (restStart < restEnd) {
                    drawLine(
                        color = markColor.copy(alpha = 0.6f),
                        start = Offset(restStart, cy),
                        end = Offset(restEnd, cy),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // Bookmarks: a dot under the line at each.
        if (ticks.isNotEmpty()) {
            val r = 2.dp.toPx()
            val y = (size.height - r).coerceAtLeast(cy)
            ticks.forEach { t ->
                if (t in 0f..1f) drawCircle(color.copy(alpha = 0.75f), radius = r, center = Offset(t * w, y))
            }
        }

        // The playhead bar — the part a finger aims at. In Sea Glass, a bead.
        if (showThumb && thumbGlass != null) {
            translate(head - beadSize / 2f, cy - beadSize / 2f) {
                bead.ground(this, beadSize, beadSize, thumbGlass, thumbGlassDark, strength = 0.8f)
                bead.draw(this, beadSize, beadSize, thumbGlass, glow = if (dragging) 0.8f else 0f)
            }
        } else if (showThumb) {
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

/**
 * The Glass look's scrubber: a rod of glass. The rest of the episode is clear
 * glass (a faint fill of the ink), what's been heard is filled with it, and
 * over both run a band of light along the top, a shade along the foot and a
 * thin bright rim, which is what makes it read as glass rather than a line.
 * [swell] (0 to 1) magnifies it while it's held: about twice as thick, and
 * the light on it brighter.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlassLine(
    shown: Float,
    color: Color,
    stroke: Float,
    swell: Float,
    marks: List<ClosedFloatingPointRange<Float>>,
    markColor: Color,
    ticks: List<Float>
) {
    val w = size.width
    val cy = size.height / 2f
    val thick = (stroke * (1.25f + 1.05f * swell)).coerceAtMost(size.height)
    val top = cy - thick / 2f
    val r = thick / 2f
    val corner = androidx.compose.ui.geometry.CornerRadius(r)
    val rod = androidx.compose.ui.geometry.Size(w, thick)
    val head = (w * shown).coerceIn(0f, w)

    // A soft shadow under the rod, so it stands off the picture.
    drawRoundRect(
        color = Color.Black.copy(alpha = 0.16f + 0.08f * swell),
        topLeft = Offset(0f, top + thick * 0.18f),
        size = rod,
        cornerRadius = corner
    )
    // The glass, and what's been heard.
    drawRoundRect(color.copy(alpha = 0.24f), topLeft = Offset(0f, top), size = rod, cornerRadius = corner)
    if (head > 0f) {
        drawRoundRect(
            color.copy(alpha = 0.95f),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(head.coerceAtLeast(thick), thick),
            cornerRadius = corner
        )
    }
    // Ad breaks, in their own color, inside the rod.
    marks.forEach { m ->
        val left = (m.start * w).coerceIn(0f, w)
        val right = (m.endInclusive * w).coerceIn(0f, w)
        if (right - left < 1f) return@forEach
        clipRect(left = left, top = 0f, right = right, bottom = size.height) {
            drawRoundRect(markColor.copy(alpha = 0.85f), topLeft = Offset(0f, top), size = rod, cornerRadius = corner)
        }
    }
    // Light along the top, a shade along the foot.
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.55f + 0.2f * swell),
            0.45f to Color.White.copy(alpha = 0f),
            0.7f to Color.Black.copy(alpha = 0f),
            1f to Color.Black.copy(alpha = 0.16f),
            startY = top,
            endY = top + thick
        ),
        topLeft = Offset(0f, top),
        size = rod,
        cornerRadius = corner
    )
    // The rim: bright at the top, fading round to the foot.
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.5f + 0.25f * swell),
            1f to Color.White.copy(alpha = 0.08f),
            startY = top,
            endY = top + thick
        ),
        topLeft = Offset(0f, top),
        size = rod,
        cornerRadius = corner,
        style = Stroke(width = 1.dp.toPx())
    )
    // Bookmarks: a dot under the rod at each.
    if (ticks.isNotEmpty()) {
        val dot = 2.dp.toPx()
        val y = (top + thick + dot + 2.dp.toPx()).coerceAtMost(size.height - dot)
        ticks.forEach { t ->
            if (t in 0f..1f) drawCircle(color.copy(alpha = 0.75f), radius = dot, center = Offset(t * w, y))
        }
    }
}
