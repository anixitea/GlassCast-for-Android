package com.glasscast.app.ui.seaglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.glasscast.app.ui.ArtworkColors
import kotlin.math.abs

/*
 * Sea Glass: the second look (Settings > Appearance).
 *
 * The rendering is adapted from the Sea Glass library for Compose
 * (github.com/mttmcknn/sea-glass, Apache License 2.0; see SEA-GLASS-LICENSE.txt):
 * its recipes, its palettes, its body shader's optics and its ground formulas.
 * What's different here: the shape is worked out analytically (a rounded box
 * with a tumbled edge) instead of from precomputed geometry textures, so a
 * piece can be any size and can turn over; the ground comes from cached blurred
 * masks; and there's a drawn fallback for phones without runtime shaders.
 * The library itself isn't a dependency: it needs a newer Kotlin, Compose and
 * Android Gradle Plugin than GlassCast builds with.
 */

/**
 * How a piece of glass is made: how frosted, how grainy, how far its edge
 * wanders, how thick and domed it is, how high it sits, and how much light
 * comes through it onto the ground. Light comes from the upper left (315°).
 */
@Immutable
class GlassRecipe(
    val frost: Float,
    val grain: Float,
    val tumble: Float,
    val thickness: Float,
    val bulge: Float,
    val elevation: Float,
    val transmission: Float,
    val light: Float = 315f
) {
    companion object {
        /** The everyday piece: buttons, the tab bar's selection, badges. */
        val Pebble = GlassRecipe(0.55f, 0.48f, 0.025f, 6f, 0.65f, 8f, 0.58f)
        /** Worn by the sea for longer: the play button. */
        val Tumbled = GlassRecipe(0.72f, 0.68f, 0.055f, 9f, 0.80f, 7f, 0.48f)
        /** Thin and nearly flat: bars, capsules and anything wide. */
        val Quiet = GlassRecipe(0.38f, 0.23f, 0.012f, 3f, 0.38f, 4f, 0.66f)
    }
}

/** A glass color: its body, the brighter tone at its edges, and what reads on it. */
@Immutable
data class Pigment(val body: Color, val edge: Color, val ink: Color)

/** The library's five, for light grounds. */
object Pigments {
    val Lagoon = Pigment(Color(0xFFB5D0EB), Color(0xFFD6E9FF), Color(0xFF2F647C))
    val Lilac = Pigment(Color(0xFFE1CDE8), Color(0xFFFBE8F7), Color(0xFF71388B))
    val Rose = Pigment(Color(0xFFEECED6), Color(0xFFFFF0F1), Color(0xFF963F59))
    val Amber = Pigment(Color(0xFFF0D9BE), Color(0xFFFFFCE0), Color(0xFF865733))
    val Mint = Pigment(Color(0xFFC0DED0), Color(0xFFE6FFF0), Color(0xFF396C5A))

    /** Frosted clear glass: for controls that aren't about any one show. */
    val FogLight = Pigment(Color(0xFFE6E6DE), Color(0xFFFFFFFF), Color(0xFF233C37))
    // At night: darker in the body than by day, so it reads as glass held up
    // to the dark rather than a pale stone, with its edges still catching light.
    val FogDark = Pigment(Color(0xFF33403D), Color(0xFF7F8D89), Color(0xFFE8EEEA))
}

/**
 * Glass in a cover's color (or any color). Pastel on the paper ground, deep
 * and lit from its edges on the dark one, with ink that reads on it either
 * way. A near-gray gives clear glass rather than a tinted one, and yellows
 * turn toward gold, as they do everywhere else in the app.
 */
fun seaPigment(source: Color, dark: Boolean): Pigment {
    val chroma = maxOf(source.red, source.green, source.blue) - minOf(source.red, source.green, source.blue)
    if (chroma < 0.14f || source.alpha < 0.5f) return if (dark) Pigments.FogDark else Pigments.FogLight
    val hsl = FloatArray(3).also { ColorUtils.colorToHSL(source.toArgb(), it) }
    var hue = hsl[0]
    if (hue in 45f..75f) hue = 40f + (hue - 45f) * 0.3f
    fun at(lightness: Float, c: Float): Color {
        val room = 1f - abs(2f * lightness - 1f)
        val s = if (room > 0.001f) (c / room).coerceIn(0f, 1f) else 0f
        return Color(ColorUtils.HSLToColor(floatArrayOf(hue, s, lightness)))
    }
    return if (!dark) {
        val c = (chroma * 0.65f).coerceIn(0.12f, 0.24f)
        Pigment(body = at(0.84f, c), edge = at(0.93f, c * 0.85f), ink = at(0.30f, 0.30f))
    } else {
        val c = (chroma * 0.60f).coerceIn(0.12f, 0.21f)
        Pigment(body = at(0.42f, c), edge = at(0.72f, c * 1.1f), ink = at(0.90f, 0.16f))
    }
}

/** This cover's glass: from its most colorful family, clear glass for a gray cover. */
fun ArtworkColors.seaPigment(dark: Boolean): Pigment = seaPigment(meshAccent, dark)

/**
 * The look's colors. [ground] is the page, [card] what sits on it, [ink] and
 * [muted] the text, [hairline] the rules; [fog] is clear glass and [brand]
 * the accent's glass (GlassCast's lilac unless dynamic or custom color says
 * otherwise).
 */
@Immutable
class SeaTokens(
    val dark: Boolean,
    val ground: Color,
    val card: Color,
    val raised: Color,
    val ink: Color,
    val muted: Color,
    val hairline: Color,
    val fog: Pigment,
    val brand: Pigment
)

/** Null in the Original look; everything that changes for Sea Glass asks this. */
val LocalSea = staticCompositionLocalOf<SeaTokens?> { null }

/**
 * The glass color of the page underneath, where a page has one (a show, the
 * player). Clear glass on that page takes some of it, as clear glass picks up
 * the color of what's around it.
 */
val LocalSeaTint = staticCompositionLocalOf<Pigment?> { null }

/** Clear glass, tinted by [tint] when there is one. */
fun SeaTokens.clearGlass(tint: Pigment?): Pigment = if (tint == null) fog else Pigment(
    body = androidx.compose.ui.graphics.lerp(fog.body, tint.body, if (dark) 0.45f else 0.5f),
    edge = androidx.compose.ui.graphics.lerp(fog.edge, tint.edge, 0.5f),
    ink = if (dark) ink else tint.ink
)

/** The Sea Glass tokens when that look is on. */
val sea: SeaTokens?
    @Composable @ReadOnlyComposable get() = LocalSea.current

// Paper and tide: sampled from the library's reference screens (light) and
// worked out to match them at night (dark). The window and launch colors are
// the Original look's; the theme repaints the window when Sea Glass is on.
val SeaPaper = Color(0xFFF4F5F0)
val SeaTide = Color(0xFF0E1716)

fun seaTokens(dark: Boolean, accent: Color): SeaTokens = if (!dark) {
    SeaTokens(
        dark = false,
        ground = SeaPaper,
        card = Color(0xFFFBFAF5),
        raised = Color(0xFFFFFFFF),
        ink = Color(0xFF233C37),
        muted = Color(0xFF5F6C65),
        hairline = Color(0xFFDCE2D8),
        fog = Pigments.FogLight,
        brand = seaPigment(accent, dark = false)
    )
} else {
    SeaTokens(
        dark = true,
        ground = SeaTide,
        card = Color(0xFF16211F),
        raised = Color(0xFF1C2826),
        ink = Color(0xFFE8EEEA),
        muted = Color(0xFF9AA8A1),
        hairline = Color(0xFF27332F),
        fog = Pigments.FogDark,
        brand = seaPigment(accent, dark = true)
    )
}

/** Glass for this color on the current ground, worked out once per color. */
@Composable
fun rememberPigment(color: Color, dark: Boolean): Pigment = remember(color, dark) { seaPigment(color, dark) }

/** The same pigment, crossfading when it changes (a new cover). */
@Composable
fun animatePigment(target: Pigment, millis: Int = 600): Pigment {
    val body by animateColorAsState(target.body, tween(millis), label = "pigmentBody")
    val edge by animateColorAsState(target.edge, tween(millis), label = "pigmentEdge")
    val ink by animateColorAsState(target.ink, tween(millis), label = "pigmentInk")
    return Pigment(body, edge, ink)
}

/**
 * Glass for a control that used to be a filled shape in [container]: clear
 * glass for a quiet (translucent or gray) container, colored glass for a
 * colored one. Lets the shared controls (Pill, GroupButton) turn to glass
 * without every caller choosing a pigment.
 */
fun pigmentFor(container: Color, tokens: SeaTokens): Pigment =
    if (container.alpha < 0.5f) {
        val chroma = maxOf(container.red, container.green, container.blue) - minOf(container.red, container.green, container.blue)
        if (chroma >= 0.1f) seaPigment(container.copy(alpha = 1f), tokens.dark) else tokens.fog
    } else {
        seaPigment(container, tokens.dark)
    }
