package com.glasscast.app.tv

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import com.glasscast.app.ui.liquid.GlassFinish
import com.glasscast.app.ui.liquid.LocalLiquid

/*
 * Glass on the TV (2.0).
 *
 * The phone's Glass bends what's behind it with a lens shader over a
 * recording of the page. On the Streamer that's a full-screen readback every
 * frame something moves, which is what the TV dropped Haze for (see
 * TvGlass.kt), so the TV's glass is drawn: a body that lets the living cover
 * behind show through, light lying along its top, and a rim of light, bright
 * where it faces the top left. It costs a few draw calls.
 *
 * Focus on a remote has to read from across a room. A control lights up as
 * glass in the cover's color and rises a little, with a soft shadow under it;
 * a cover or a card lifts and catches the light (a sheen over it and a bright
 * rim) instead of the other looks' ring. The finish (Frosted or Liquid) is the
 * phone's setting: Liquid makes the resting glass clearer and its light
 * brighter.
 */

/** True in the Glass look (the phone's LIQUID_GLASS, now on the TV too). */
@Composable
internal fun tvLiquid(): Boolean = LocalLiquid.current != null

/** Black or white, whichever reads on [accent] lit as glass. */
internal fun inkOn(accent: Color): Color = if (accent.luminance() > 0.5f) Color(0xFF15121A) else Color.White

/**
 * A control's glass. [amount]: 0 clear, 1 lit in [accent]. [glow] brightens
 * it (a press, or focus that doesn't change its color). [lift] raises it (the
 * shadow under it). [visible] fades the whole piece (glass that only appears
 * on focus). All read while drawing, so focus never recomposes the control.
 */
internal fun Modifier.tvClearGlass(
    shape: Shape,
    accent: Color,
    amount: () -> Float,
    glow: () -> Float = { 0f },
    lift: () -> Float = { 0f },
    visible: () -> Float = { 1f }
): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val rimWidth = 1.5.dp.toPx()
    val drop = 3.dp.toPx()
    onDrawBehind {
        val v = visible().coerceIn(0f, 1f)
        if (v <= 0.001f) return@onDrawBehind
        val a = amount().coerceIn(0f, 1f)
        val g = glow().coerceIn(0f, 1f)
        val l = lift().coerceIn(0f, 1f)
        val clearFinish = GlassFinish.liquid
        // The shadow it casts once it's raised.
        if (l > 0f) {
            for (i in 1..3) {
                translate(0f, drop * i * 0.7f) {
                    drawOutline(outline, Color.Black.copy(alpha = 0.12f * l * v / i))
                }
            }
        }
        val clear = Color.White.copy(alpha = if (clearFinish) 0.07f else 0.12f)
        val lit = accent.copy(alpha = 0.92f)
        drawOutline(outline, lerp(clear, lit, a).let { it.copy(alpha = it.alpha * v) })
        if (g > 0f) drawOutline(outline, Color.White.copy(alpha = 0.16f * g * v))
        // Light along the top.
        val sheen = (if (clearFinish) 0.16f else 0.10f) + 0.10f * a
        drawOutline(
            outline,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = sheen * v),
                0.5f to Color.White.copy(alpha = 0f),
                startY = 0f,
                endY = size.height
            )
        )
        // The rim, bright toward the top left.
        val rim = (if (clearFinish) 0.55f else 0.40f) + 0.35f * maxOf(a, l)
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = rim.coerceAtMost(1f) * v),
                0.5f to Color.White.copy(alpha = 0.04f * v),
                1f to Color.White.copy(alpha = rim * 0.4f * v),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            style = Stroke(rimWidth)
        )
    }
}

/**
 * A panel of TV glass (the rail, menus, the side panel, the toast): the
 * panel's color as a body the cover shows faintly through, with the glass's
 * light. Solid enough to read on: it doesn't blur.
 */
internal fun Modifier.tvGlassPane(shape: Shape, container: Color): Modifier = this.drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val rimWidth = 1.dp.toPx()
    onDrawBehind {
        val clearFinish = GlassFinish.liquid
        drawOutline(outline, container.copy(alpha = container.alpha * if (clearFinish) 0.78f else 0.9f))
        drawOutline(
            outline,
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (clearFinish) 0.12f else 0.07f),
                0.35f to Color.White.copy(alpha = 0f),
                startY = 0f,
                endY = size.height
            )
        )
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = if (clearFinish) 0.42f else 0.30f),
                0.5f to Color.White.copy(alpha = 0.03f),
                1f to Color.White.copy(alpha = if (clearFinish) 0.20f else 0.14f),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            ),
            style = Stroke(rimWidth)
        )
    }
}

/** A panel's body in the Glass look: charcoal with a breath of the cover's color. */
internal fun tvGlassPanelColor(meshAccent: Color): Color = lerp(Color(0xFF1C1C1E), meshAccent, 0.10f).copy(alpha = 0.94f)
