package com.glasscast.app.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

const val CookieLobes = 9
const val CookieDepth = 0.06f

/**
 * A scalloped circle — Material 3 Expressive's "cookie".
 *
 * Built by one function for both jobs, the clip and the progress ring, so the
 * two can never disagree. The first version drew its progress as a plain circle
 * around a scalloped cover, and the mismatch showed all the way round; now the
 * ring is the same curve as the clip, just larger.
 *
 * The path starts at twelve o'clock and runs clockwise, so a PathMeasure
 * segment from 0 reads as progress from the top. [rotation] turns the lobes
 * only — the start point stays at the top, so the progress arc doesn't wander
 * as the edge spins.
 */
fun cookiePath(
    width: Float,
    height: Float,
    rotation: Float = 0f,
    inset: Float = 0f,
    lobes: Int = CookieLobes,
    depth: Float = CookieDepth
): Path {
    val cx = width / 2f
    val cy = height / 2f
    // Shrunk by the depth so the lobes' crests land on the (inset) bounds.
    val base = (min(cx, cy) - inset) / (1f + depth)
    val path = Path()
    val steps = 216
    for (i in 0..steps) {
        val t = (-PI / 2 + 2 * PI * i / steps).toFloat()
        val r = base * (1f + depth * cos(lobes * (t + rotation)))
        val x = cx + r * cos(t)
        val y = cy + r * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

class CookieShape(
    private val rotation: Float = 0f,
    private val inset: Float = 0f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = cookiePath(size.width, size.height, rotation, inset)
        path.close()
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is CookieShape && other.rotation == rotation && other.inset == inset

    override fun hashCode(): Int = rotation.hashCode() * 31 + inset.hashCode()
}
