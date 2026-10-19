package com.glasscast.app.ui.seaglass

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.glasscast.app.ui.seaglass.GlassBody.drawBody
import com.glasscast.app.ui.seaglass.GlassGround.drawCoverGround
import com.glasscast.app.ui.seaglass.GlassGround.drawGround

private val NoValue: () -> Float = { 0f }

/**
 * A piece of sea glass behind this element, filling its bounds, with its
 * ground (shadow and pool) on the page around it.
 *
 * [corner] is how round the ends are, 0 (a rounded square-ish slab) to 1 (a
 * pebble, or a capsule when wide). [seed] picks the pebble. [glow] (0–1)
 * brightens it, for a press; [turn] changes its shape, for play and pause.
 * Both are read only while drawing, so animating them repaints the piece and
 * nothing else.
 *
 * With [cache] on, the piece is kept in its own layer, so a list scrolling
 * past it moves a picture instead of running the shader again. The ground is
 * outside that layer: it spreads past the piece's bounds, which a layer would
 * cut off.
 */
fun Modifier.seaGlass(
    pigment: Pigment,
    recipe: GlassRecipe = GlassRecipe.Pebble,
    seed: Int = 1,
    corner: Float = 1f,
    dark: Boolean,
    ground: Boolean = true,
    cache: Boolean = true,
    groundStrength: () -> Float = { 1f },
    glow: () -> Float = NoValue,
    turn: () -> Float = NoValue
): Modifier = this
    .then(
        if (ground) {
            Modifier.drawBehind {
                val w = size.width / density
                val h = size.height / density
                if (w >= 1f && h >= 1f) {
                    val probe = GlassContour(w, h, corner, recipe.tumble, seed)
                    drawGround(
                        width = w,
                        height = h,
                        radius = probe.radius + probe.amplitude * 0.95f,
                        recipe = recipe,
                        pigment = pigment,
                        dark = dark,
                        strength = groundStrength()
                    )
                }
            }
        } else {
            Modifier
        }
    )
    .then(if (cache) Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } else Modifier)
    .drawWithCache {
        val w = size.width / density
        val h = size.height / density
        val path = Path()
        var contour: GlassContour? = null
        var lastTurn = Float.NaN
        onDrawBehind {
            if (w < 1f || h < 1f) return@onDrawBehind
            val t = turn()
            if (contour == null || t != lastTurn) {
                contour = GlassContour(w, h, corner, recipe.tumble, seed, t).also { it.outline(density, path) }
                lastTurn = t
            }
            drawBody(contour!!, path, recipe, pigment, glow().coerceIn(0f, 1f))
        }
    }

/**
 * The page under a cover: a soft shadow and a pool of the cover's color, as
 * if the cover were a sheet of colored glass. [cornerDp] is the cover's corner.
 */
fun Modifier.coverPool(pigment: Pigment, cornerDp: Float, dark: Boolean, strength: Float = 1f): Modifier =
    this.drawBehind {
        drawCoverGround(
            width = size.width / density,
            height = size.height / density,
            radius = cornerDp,
            pigment = pigment,
            dark = dark,
            strength = strength
        )
    }

/**
 * A glass control: a piece of glass that presses down under the finger (it
 * gives a little and brightens, as glass catches more light tilted toward
 * you), with its content (an icon, a label) on top in the pigment's ink.
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    pigment: Pigment,
    dark: Boolean,
    modifier: Modifier = Modifier,
    recipe: GlassRecipe = GlassRecipe.Pebble,
    seed: Int = 1,
    corner: Float = 1f,
    enabled: Boolean = true,
    ground: Boolean = true,
    description: String? = null,
    turn: () -> Float = NoValue,
    content: @Composable BoxScope.() -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "glassPress"
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(if (pressed) 90 else 260),
        label = "glassGlow"
    )
    Box(
        modifier
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
                alpha = if (enabled) 1f else 0.5f
            }
            .seaGlass(
                pigment = pigment,
                recipe = recipe,
                seed = seed,
                corner = corner,
                dark = dark,
                ground = ground,
                glow = { glow },
                turn = turn
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** A piece of glass with something on it, not itself a button (a badge, a bar). */
@Composable
fun GlassSurface(
    pigment: Pigment,
    dark: Boolean,
    modifier: Modifier = Modifier,
    recipe: GlassRecipe = GlassRecipe.Quiet,
    seed: Int = 1,
    corner: Float = 1f,
    ground: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier.seaGlass(pigment = pigment, recipe = recipe, seed = seed, corner = corner, dark = dark, ground = ground),
        contentAlignment = contentAlignment,
        content = content
    )
}

/**
 * Glass drawn by hand, for a piece that moves or resizes every frame inside
 * someone else's drawing: the tab bar's traveling selection, the mini
 * player's bubble, the player's transport as it morphs into the header. It
 * keeps its outline and rebuilds it only when the size or the turn changes.
 */
class GlassPainter(
    private val recipe: GlassRecipe,
    private val seed: Int,
    private val corner: Float = 1f
) {
    private val path = Path()
    private var contour: GlassContour? = null
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var lastTurn = Float.NaN
    private var lastDensity = 0f

    /** The body, [widthPx] × [heightPx] at the current origin. */
    fun draw(scope: DrawScope, widthPx: Float, heightPx: Float, pigment: Pigment, glow: Float = 0f, turn: Float = 0f) {
        with(scope) {
            val w = widthPx / density
            val h = heightPx / density
            if (w < 1f || h < 1f) return
            if (contour == null || w != lastWidth || h != lastHeight || turn != lastTurn || density != lastDensity) {
                contour = GlassContour(w, h, corner, recipe.tumble, seed, turn).also { it.outline(density, path) }
                lastWidth = w
                lastHeight = h
                lastTurn = turn
                lastDensity = density
            }
            drawBody(contour!!, path, recipe, pigment, glow.coerceIn(0f, 1f))
        }
    }

    /** Its ground, for the same size, at the current origin. */
    fun ground(scope: DrawScope, widthPx: Float, heightPx: Float, pigment: Pigment, dark: Boolean, strength: Float = 1f) {
        with(scope) {
            val w = widthPx / density
            val h = heightPx / density
            if (w < 1f || h < 1f) return
            val probe = contour?.takeIf { lastWidth == w && lastHeight == h } ?: GlassContour(w, h, corner, recipe.tumble, seed)
            drawGround(w, h, probe.radius + probe.amplitude * 0.95f, recipe, pigment, dark, strength)
        }
    }
}
