package com.glasscast.app.ui.seaglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Sea Glass's switch: a shallow channel in the page with a glass bead in it.
 * Off, the channel is a faint groove and the bead is clear glass at its start;
 * on, the channel takes the glass's color and the bead rolls to the end.
 * The bead keeps its own little shadow as it goes.
 */
@Composable
fun SeaSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    tokens: SeaTokens,
    modifier: Modifier = Modifier,
    pigment: Pigment = tokens.brand,
    enabled: Boolean = true
) {
    val travel by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 520f),
        label = "seaSwitch"
    )
    val channel by animateColorAsState(
        targetValue = if (checked) pigment.body else tokens.ink.copy(alpha = if (tokens.dark) 0.10f else 0.07f),
        animationSpec = tween(220),
        label = "seaSwitchChannel"
    )
    val rim = if (checked) pigment.edge.copy(alpha = if (tokens.dark) 0.5f else 0.9f) else tokens.hairline
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onValueChange = onCheckedChange
                    )
                } else {
                    Modifier
                }
            )
            .padding(2.dp)
            .background(channel, CircleShape)
            .border(1.dp, rim, CircleShape),
        contentAlignment = Alignment.CenterStart
    ) {
        // On: clear glass on the colored channel by day; at night a bead lit
        // in the channel's own color, so it doesn't sink into it.
        val bead = when {
            !checked -> tokens.fog
            !tokens.dark -> Pigments.FogLight
            else -> Pigment(pigment.edge, Color(0xFFF6FBF8), tokens.ground)
        }
        Box(
            Modifier
                .offset { IntOffset((travel * 20.dp.toPx()).roundToInt() + 3.dp.roundToPx(), 0) }
                .size(22.dp)
                .seaGlass(
                    pigment = bead,
                    recipe = GlassRecipe.Pebble,
                    seed = 7,
                    dark = tokens.dark,
                    cache = true,
                    // A small bead in a channel: a lighter shadow than a button's.
                    groundStrength = { 0.5f }
                )
        )
    }
}

/** A quiet capsule's fill on the page: a whisper of ink, with a hairline. */
fun quietFill(tokens: SeaTokens): Color = tokens.ink.copy(alpha = if (tokens.dark) 0.07f else 0.045f)

/**
 * A title in a list row: Figtree in the Original look, Newsreader's text cut in
 * Sea Glass, a size up so the serif carries the same weight on the line.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun rowTitleStyle(): androidx.compose.ui.text.TextStyle {
    val base = androidx.compose.material3.MaterialTheme.typography.titleSmall
    return if (LocalSea.current == null) base else base.copy(
        fontFamily = com.glasscast.app.ui.theme.SeaSerif,
        fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
        fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.None,
        fontSize = androidx.compose.ui.unit.TextUnit(16.5f, androidx.compose.ui.unit.TextUnitType.Sp),
        lineHeight = androidx.compose.ui.unit.TextUnit(21f, androidx.compose.ui.unit.TextUnitType.Sp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(0f, androidx.compose.ui.unit.TextUnitType.Sp)
    )
}

/**
 * In Sea Glass, the page under a cover takes a pool of the cover's color (see
 * coverPool). Nothing in the Original look.
 */
@Composable
fun Modifier.seaCoverPool(url: String, corner: androidx.compose.ui.unit.Dp, strength: Float = 1f): Modifier {
    val tokens = LocalSea.current ?: return this
    val (colors, _) = com.glasscast.app.ui.rememberArtworkColors(url)
    val pigment = remember(colors, tokens.dark) { seaPigment(colors.meshAccent, tokens.dark) }
    return this.coverPool(pigment, corner.value, tokens.dark, strength)
}

/**
 * The floating pill that holds a page's bar buttons: [color] filled in the
 * Original look, a capsule of clear glass in Sea Glass.
 */
@Composable
fun Modifier.chromePill(color: Color, seed: Int = 9): Modifier {
    val tokens = LocalSea.current
    return if (tokens != null) {
        this.seaGlass(pigment = tokens.fog, recipe = GlassRecipe.Quiet, seed = seed, dark = tokens.dark)
    } else {
        this
            .clip(CircleShape)
            .background(color)
    }
}

/**
 * A list row's card: [wash] filled in the Original look; in Sea Glass, a
 * paper card with a hairline, the wash (the show's glass color) coming in
 * from the cover's side and fading across. Opaque either way, so a swipe's
 * reveal behind it stays hidden.
 */
@Composable
fun Modifier.rowCard(wash: Color, corner: androidx.compose.ui.unit.Dp = 20.dp): Modifier {
    val tokens = LocalSea.current
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(corner)
    return if (tokens != null) {
        val glassWash = remember(wash, tokens.dark) { seaPigment(wash, tokens.dark).body }
        this
            .clip(shape)
            .background(tokens.card)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    0f to glassWash.copy(alpha = if (tokens.dark) 0.38f else 0.55f),
                    0.55f to glassWash.copy(alpha = 0f)
                )
            )
            .border(0.75.dp, tokens.hairline, shape)
    } else {
        this
            .clip(shape)
            .background(wash)
    }
}

/**
 * A page washed with a cover's color, as light through colored glass would
 * fall on paper: the ground itself tinted, the glass's color pooled near the
 * top, and up to two more of the cover's own colors coming in from the sides.
 */
@androidx.compose.runtime.Immutable
class SeaWash(val ground: Color, val main: Color, val others: List<Color>)

fun seaWash(colors: com.glasscast.app.ui.ArtworkColors, pigment: Pigment, tokens: SeaTokens): SeaWash {
    fun hue(c: Color) = FloatArray(3).also { androidx.core.graphics.ColorUtils.colorToHSL(c.toArgbCompat(), it) }[0]
    fun gap(a: Float, b: Float) = kotlin.math.abs(a - b).let { minOf(it, 360f - it) }
    val mainHue = hue(pigment.body)
    val others = colors.mesh.asSequence()
        .map { seaPigment(it, tokens.dark) }
        .filter { it != Pigments.FogLight && it != Pigments.FogDark }
        .map { it.body }
        .filter { gap(hue(it), mainHue) > 28f }
        .distinctBy { (hue(it) / 30f).toInt() }
        .take(2)
        .toList()
    val ground = androidx.compose.ui.graphics.lerp(tokens.ground, pigment.body, if (tokens.dark) 0.20f else 0.26f)
    return SeaWash(ground, pigment.body, others)
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt()
)

/** Paints [wash] over the whole draw area, its main pool at [center] reaching [reach]. */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeaWash(
    wash: SeaWash,
    dark: Boolean,
    center: androidx.compose.ui.geometry.Offset,
    reach: Float
) {
    drawRect(wash.ground)
    val a = if (dark) 0.40f else 0.88f
    fun pool(color: Color, at: androidx.compose.ui.geometry.Offset, radius: Float, strength: Float) {
        drawRect(
            androidx.compose.ui.graphics.Brush.radialGradient(
                0f to color.copy(alpha = strength),
                0.55f to color.copy(alpha = strength * 0.5f),
                1f to color.copy(alpha = 0f),
                center = at,
                radius = radius
            )
        )
    }
    wash.others.getOrNull(0)?.let { pool(it, androidx.compose.ui.geometry.Offset(size.width * 0.02f, center.y + reach * 0.78f), reach * 0.72f, a * 0.62f) }
    wash.others.getOrNull(1)?.let { pool(it, androidx.compose.ui.geometry.Offset(size.width * 1.0f, center.y + reach * 0.28f), reach * 0.6f, a * 0.5f) }
    pool(wash.main, center, reach, a)
}
