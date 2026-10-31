package com.glasscast.app.tv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.chromeSurface
import com.glasscast.app.ui.rememberArtworkColors
import com.glasscast.app.ui.seaBubbleOutline
import com.glasscast.app.ui.seaglass.GlassRecipe
import com.glasscast.app.ui.seaglass.LocalSea
import com.glasscast.app.ui.seaglass.Pigment
import com.glasscast.app.ui.seaglass.SeaTokens
import com.glasscast.app.ui.seaglass.clearGlass
import com.glasscast.app.ui.seaglass.seaGlass
import com.glasscast.app.ui.seaglass.seaPigment
import com.glasscast.app.ui.seaglass.seaWash

/*
 * Sea Glass on the TV.
 *
 * The TV is always dark, so Sea Glass here is the tide: the sea-green night
 * ground, washed with the cover's colors, Newsreader for type, and the
 * controls as pieces of glass. Focus on a remote has to be unmistakable from
 * across a room, so a focused piece lights up in the cover's color and grows
 * a little, and the glass is the focus: there is no ring drawn around it, so
 * the highlight is always the shape of the piece itself.
 *
 * Covers and episode cards keep their rings. They're rectangles, and a ring
 * the shape of a rectangle around one is right.
 */

/**
 * The cover's colors as the TV draws with them: in Sea Glass, the tide's
 * grounds and ink, with the cover's glass color through them; the cover's own
 * otherwise. The mesh colors stay the cover's, so the focus rings and the
 * scrubber keep its hue.
 */
@Composable
internal fun rememberTvColors(url: String): ArtworkColors {
    val (colors, _) = rememberArtworkColors(url)
    val tokens = LocalSea.current
    return remember(colors, tokens) { if (tokens == null) colors else colors.forSea(tokens) }
}

internal fun ArtworkColors.forSea(tokens: SeaTokens): ArtworkColors {
    val pigment = seaPigment(meshAccent, dark = true)
    val wash = seaWash(this, pigment, tokens)
    return copy(
        background = wash.ground,
        wash = wash.ground,
        elevated = lerp(tokens.raised, pigment.body, 0.22f),
        accent = pigment.edge,
        content = tokens.ink,
        contentVariant = tokens.muted,
        divider = tokens.hairline,
        card = lerp(tokens.card, pigment.body, 0.16f)
    )
}

/** A panel's color (the side panel, the menus, the rail): the cover's dark chrome, or in Sea Glass a card of the tide tinted with its glass. */
@Composable
internal fun tvPanelColor(colors: ArtworkColors): Color {
    if (com.glasscast.app.ui.liquid.LocalLiquid.current != null) return remember(colors) { tvGlassPanelColor(colors.meshAccent) }
    val tokens = LocalSea.current ?: return colors.chromeSurface
    return remember(colors, tokens) { lerp(tokens.card, seaPigment(colors.meshAccent, dark = true).body, 0.24f) }
}

private fun lerpPigment(a: Pigment, b: Pigment, t: Float): Pigment =
    if (t <= 0f) a else if (t >= 1f) b else Pigment(lerp(a.body, b.body, t), lerp(a.edge, b.edge, t), lerp(a.ink, b.ink, t))

/** A stable pebble for a label, so the same button is the same piece every time. */
internal fun glassSeed(of: String): Int = (of.hashCode() and 0x7FFF) + 1

/**
 * A TV control as a piece of sea glass.
 *
 * At rest it's clear glass, tinted by the cover ([accent]); focused, it lights
 * up as the cover's glass and grows a little; pressed, it gives and glows.
 * [filled] keeps it lit (a selected option); [rest] lights it part of the way
 * at rest (play, the player's main button). [bare] leaves nothing at rest at
 * all, for rows where only the focused or selected one is glass (the rail).
 *
 * Where lit glass already means "chosen" (a row of options, the rail's
 * current tab), [focusLights] is off: focus grows the piece and brightens it
 * without changing its color, so the chosen one and the focused one never
 * look alike. [content] gets the ink to draw in.
 */
@Composable
internal fun TvGlass(
    /** Sea Glass's tokens; null in the Glass look, which draws its own glass (TvClear.kt). */
    tokens: SeaTokens?,
    accent: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    rest: Float = 0f,
    bare: Boolean = false,
    /** False where lit glass already means something else (the rail's current tab): focus shows clear glass. */
    focusLights: Boolean = true,
    recipe: GlassRecipe = GlassRecipe.Pebble,
    seed: Int = 1,
    corner: Float = 1f,
    grow: Float = 1.06f,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    turn: () -> Float = { 0f },
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(ink: Color) -> Unit
) {
    if (tokens == null) {
        TvClearControl(
            accent = accent, modifier = modifier, filled = filled, rest = rest, bare = bare,
            focusLights = focusLights, corner = corner, grow = grow, enabled = enabled,
            focusRequester = focusRequester, onLongClick = onLongClick, onClick = onClick, content = content
        )
        return
    }
    val lit = remember(accent) { seaPigment(accent, dark = true) }
    val clear = remember(tokens, lit) { tokens.clearGlass(lit) }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focus by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "glassFocus")
    val squash by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "glassSquash"
    )
    // Focus that doesn't change the glass's color brightens it instead, as
    // glass tilted toward the light does.
    val glow by animateFloatAsState(
        when {
            pressed -> 1f
            focused && filled -> 0.35f
            focused && !focusLights -> 0.6f
            else -> 0f
        },
        tween(if (pressed) 90 else 260),
        label = "glassGlow"
    )
    val amount = when {
        filled -> 1f
        focusLights -> rest + (1f - rest) * focus
        else -> rest
    }
    val pigment = lerpPigment(clear, lit, amount)
    val ink = lerp(clear.ink, lit.ink, amount)
    val shows = filled || !bare

    Box(
        modifier
            .graphicsLayer {
                val s = (1f + (grow - 1f) * focus) * squash
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.4f
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onLongClick != null && enabled) Modifier.holdSelect(onClick, onLongClick) else Modifier)
            .focusable(enabled = enabled, interactionSource = interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = if (shows) 1f else focus }
                .seaGlass(
                    pigment = pigment,
                    recipe = recipe,
                    seed = seed,
                    corner = corner,
                    dark = true,
                    groundStrength = { 0.55f + 0.45f * focus },
                    glow = { glow },
                    turn = turn
                )
        )
        content(ink)
    }
}

/** The rail bubble's pebble: the phone's Sea Glass bubble shape, held still. */
internal object SeaBubbleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        seaBubbleOutline(path, size.width, size.height, density.density, e = 1f, cornerDp = 0f, turn = 0f)
        return Outline.Generic(path)
    }
}

/**
 * [TvGlass] in the Glass look: the same states (lit when chosen or focused,
 * [rest] part lit, [bare] nothing until focused, [focusLights] off where lit
 * already means chosen), drawn as the TV's glass (see TvClear.kt) in a
 * capsule ([corner] 1) or a rounded square.
 */
@Composable
private fun TvClearControl(
    accent: Color,
    modifier: Modifier,
    filled: Boolean,
    rest: Float,
    bare: Boolean,
    focusLights: Boolean,
    corner: Float,
    grow: Float,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onLongClick: (() -> Unit)?,
    onClick: () -> Unit,
    content: @Composable BoxScope.(ink: Color) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val focus = animateFloatAsState(if (focused) 1f else 0f, tween(170), label = "clearFocus")
    val squash = animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 700f),
        label = "clearSquash"
    )
    val glow = animateFloatAsState(
        when {
            pressed -> 1f
            focused && (filled || !focusLights) -> 0.6f
            else -> 0f
        },
        tween(if (pressed) 90 else 240),
        label = "clearGlow"
    )
    val ink = remember(accent) { inkOn(accent) }
    val amountOf = {
        when {
            filled -> 1f
            focusLights -> rest + (1f - rest) * focus.value
            else -> rest
        }
    }
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(percent = (50f * corner.coerceIn(0f, 1f)).toInt())
    // The ink is chosen in composition only when lit-ness changes enough to
    // flip it, not every frame of the focus animation.
    val lit by remember(filled, focusLights, rest) { androidx.compose.runtime.derivedStateOf { amountOf() > 0.5f } }
    Box(
        modifier
            .graphicsLayer {
                val s = (1f + (grow - 1f) * focus.value) * squash.value
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.4f
            }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .then(if (onLongClick != null && enabled) Modifier.holdSelect(onClick, onLongClick) else Modifier)
            .tvClearGlass(
                shape = shape,
                accent = accent,
                amount = amountOf,
                glow = { glow.value },
                lift = { focus.value },
                visible = { if (filled || !bare) 1f else focus.value }
            )
            .focusable(enabled = enabled, interactionSource = interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content(if (lit) ink else Color.White)
    }
}
