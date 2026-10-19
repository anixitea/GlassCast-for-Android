package com.glasscast.app.ui.seaglass

import androidx.compose.ui.graphics.Path
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The outline of one piece, in dp: a rounded box whose edge wanders in and
 * out, the way a piece of glass the sea has worn is never quite symmetrical.
 *
 * The wander is two slow waves around the piece (twice and three times round),
 * with phases from [seed], so the same seed always gives the same pebble and
 * neighbours with different seeds don't match. [turn] moves the phases at
 * different rates, so animating it makes the pebble change shape as if it had
 * been turned over in the hand, rather than rotating.
 *
 * The body shader gets the same numbers (half extents, corner, amplitude,
 * phases) and works out its distance from this edge analytically, so the
 * shading and the outline agree at any size.
 */
internal class GlassContour(
    val width: Float,
    val height: Float,
    corner: Float,
    tumble: Float,
    seed: Int,
    turn: Float = 0f
) {
    val phase0: Float
    val phase1: Float
    val grainSeed: Float
    val amplitude: Float
    val halfWidth: Float
    val halfHeight: Float
    val radius: Float
    val smoothing: Float
    val maxDistance: Float

    init {
        val random = Mulberry(seed)
        phase0 = random.next() * TAU + turn * PI.toFloat()
        phase1 = random.next() * TAU + turn * (TAU / 3f)
        grainSeed = random.next()
        val short = min(width, height)
        amplitude = short * tumble
        val reach = amplitude * 0.95f
        halfWidth = (width / 2f - reach).coerceAtLeast(0.5f)
        halfHeight = (height / 2f - reach).coerceAtLeast(0.5f)
        val half = min(halfWidth, halfHeight)
        radius = half * corner.coerceIn(0f, 1f)
        smoothing = (half * 0.5f).coerceAtLeast(0.01f)
        maxDistance = half + reach * 0.5f
    }

    /** How much the edge stands in or out at [angle] around the center, before scaling by the amplitude. */
    private fun ripple(angle: Float): Float =
        sin(2f * angle + phase0) * 0.75f + sin(3f * angle + phase1) * 0.2f

    /**
     * The outline in pixels, into [path]: points round the rounded box, each
     * pushed out along its normal by the ripple, then joined by quadratic
     * curves through their midpoints so there are no corners.
     */
    fun outline(density: Float, path: Path, points: Int = 96) {
        path.reset()
        val cx = width / 2f
        val cy = height / 2f
        val hx = halfWidth
        val hy = halfHeight
        val r = radius
        val straightX = (hx - r).coerceAtLeast(0f) * 2f
        val straightY = (hy - r).coerceAtLeast(0f) * 2f
        val arc = (PI * r / 2.0).toFloat()
        val lengths = floatArrayOf(straightX, arc, straightY, arc, straightX, arc, straightY, arc)
        val total = lengths.sum().coerceAtLeast(0.001f)
        val xs = FloatArray(points)
        val ys = FloatArray(points)
        for (i in 0 until points) {
            var along = i.toFloat() / points * total
            var segment = 0
            while (segment < 7 && along >= lengths[segment]) {
                along -= lengths[segment]
                segment++
            }
            var x: Float
            var y: Float
            var nx: Float
            var ny: Float
            when (segment) {
                0 -> { x = cx - hx + r + along; y = cy - hy; nx = 0f; ny = -1f }
                2 -> { x = cx + hx; y = cy - hy + r + along; nx = 1f; ny = 0f }
                4 -> { x = cx + hx - r - along; y = cy + hy; nx = 0f; ny = 1f }
                6 -> { x = cx - hx; y = cy + hy - r - along; nx = -1f; ny = 0f }
                else -> {
                    val cornerIndex = (segment - 1) / 2
                    val sweep = if (lengths[segment] > 0f) along / lengths[segment] else 0f
                    val a = (-PI / 2 + cornerIndex * PI / 2 + sweep * PI / 2).toFloat()
                    val ccx = cx + (hx - r) * (if (cornerIndex == 0 || cornerIndex == 1) 1f else -1f)
                    val ccy = cy + (hy - r) * (if (cornerIndex == 1 || cornerIndex == 2) 1f else -1f)
                    nx = cos(a)
                    ny = sin(a)
                    x = ccx + nx * r
                    y = ccy + ny * r
                }
            }
            val d = amplitude * ripple(atan2(y - cy, x - cx))
            xs[i] = (x + nx * d) * density
            ys[i] = (y + ny * d) * density
        }
        val last = points - 1
        path.moveTo((xs[last] + xs[0]) / 2f, (ys[last] + ys[0]) / 2f)
        for (i in 0 until points) {
            val j = (i + 1) % points
            path.quadraticTo(xs[i], ys[i], (xs[i] + xs[j]) / 2f, (ys[i] + ys[j]) / 2f)
        }
        path.close()
    }

    private companion object {
        const val TAU = (2.0 * PI).toFloat()
    }
}

/** A small seeded random number generator (mulberry32), so a seed is always the same pebble. */
private class Mulberry(seed: Int) {
    private var state = seed

    fun next(): Float {
        state += 0x6D2B79F5
        var t = state
        t = (t xor (t ushr 15)) * (t or 1)
        t = t xor (t + (t xor (t ushr 7)) * (t or 61))
        return ((t xor (t ushr 14)).toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}
