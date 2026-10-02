package com.glasscast.app.ui

import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.PI
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Shared-element plumbing.
 *
 * A shared element needs two things wherever it appears: the SharedTransition
 * layout that spans both screens, and the AnimatedVisibility scope of whichever
 * screen it belongs to — the one that is entering or leaving. Both are
 * provided here as composition locals, so any artwork in the app can opt in
 * with one modifier and no screen has to thread scopes through its parameters.
 *
 * When either is missing — a preview, the TV build, a screen outside any
 * transition — the modifier does nothing, rather than crashing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The page-level scope: the AnimatedContent that swaps library, shows, search… */
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** The player's scope: the mini player and the full player hand the cover between them. */
val LocalPlayerScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Keys. The now-playing cover has one fixed key so whichever of the card, the
 * bubble or the full player is visible hands off to the others. Show covers
 * are keyed by feed URL, so a library tile, a search result and a Discover card
 * all fly into the same show page.
 */
const val NowPlayingArtKey = "now-playing-art"

/*
 * The now-playing cover's key. The card and the bubble are one element now
 * (MiniPlayer morphs between them), so there is one cover, one key, and the
 * full player grows from wherever that cover currently is. When they were two
 * elements sharing a key, every scroll-driven swap also ran a shared-element
 * flight between them.
 */
const val MiniArtKey = "now-playing-art-card"
fun coverKey(feedUrl: String) = "cover:${feedUrl.lowercase()}"

/**
 * Slightly underdamped: the cover should *arrive*, overshooting a hair and
 * settling, the way the tab pill does. A tween here reads as a zoom effect
 * rather than an object moving.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private val ArtBounds = BoundsTransform { _: Rect, _: Rect ->
    spring(dampingRatio = 0.86f, stiffness = 320f)
}

/**
 * Draws this above the flying cover while the player opens and closes.
 *
 * A shared element flies in the overlay, above the whole window — so the
 * full player's top scrim and handle sat under the cover for the entire
 * flight and only showed up when it landed, a beat after the player had
 * opened. Rendered in the overlay one layer up, they're there from the
 * start. The overlay doesn't inherit the player's own fade, so the alpha
 * follows the player's enter/exit transition here.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.aboveFlyingArtwork(scope: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = scope ?: return this
    val shown = visibility.transition.animateFloat(label = "aboveFlyingArtwork") { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    return with(shared) {
        this@aboveFlyingArtwork
            .renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
            .graphicsLayer { alpha = shown.value }
    }
}

/**
 * [clip] shapes the cover *while it flies*. In flight it's drawn on the
 * transition's overlay, above everything, and the clip its home gives it
 * doesn't reach there — so without this, the mini player's cover traveled as
 * a hard-cornered square and only took its shape once it landed.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String, scope: AnimatedVisibilityScope?, clip: Shape? = null): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = scope ?: return this
    return with(shared) {
        if (clip != null) {
            this@sharedArtwork.sharedElement(
                state = rememberSharedContentState(key),
                animatedVisibilityScope = visibility,
                boundsTransform = ArtBounds,
                clipInOverlayDuringTransition = OverlayClip(clip)
            )
        } else {
            this@sharedArtwork.sharedElement(
                state = rememberSharedContentState(key),
                animatedVisibilityScope = visibility,
                boundsTransform = ArtBounds
            )
        }
    }
}

/**
 * The mini player cover's shape right now, for the flight to land on. Written
 * by MiniPlayer each time it draws — a plain field, so writing it costs
 * nothing and recomposes nothing; the flight's clip reads it every frame.
 */
internal object MiniArtShape {
    /** Corner radius as a fraction of the cover's side: 0.25 on the card, 0.5 in the bubble. */
    @JvmField var roundness: Float = 0.25f
    /** How far into the bubble it is: 0 card, 1 bubble (scalloped). */
    @JvmField var bubble: Float = 0f
    /** The bubble's current spin, so the scallops line up on landing. */
    @JvmField var rotation: Float = 0f
}

/**
 * The now-playing cover's shape in flight, between the player and the mini
 * player. Worked out from its size at each frame: square-cornered at the
 * player's size (the player's cover is full-bleed), and arriving at exactly the
 * mini player's current shape. The same clip serves the opening flight, in
 * reverse.
 *
 * To the card it rounds into a 12dp-cornered square. To the bubble it can't
 * just round off, or it lands as a plain circle and the scallops pop in after:
 * it's drawn as a superellipse — near-rectangular while large, relaxing into a
 * circle as it shrinks — with the scallops growing in over the last part of the
 * trip at the bubble's own spin. On landing it is, point for point, the path
 * the bubble draws (see cookiePath).
 */
internal val FlightClip: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = minOf(size.width, size.height)
        val small = with(density) { 48.dp.toPx() }
        val big = with(density) { 280.dp.toPx() }
        val t = ((big - side) / (big - small)).coerceIn(0f, 1f)
        if (MiniArtShape.bubble >= 1f) return Outline.Generic(bubbleFlightPath(size, t))
        val radius = MiniArtShape.roundness * side * t * t
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}

private fun bubbleFlightPath(size: Size, t: Float): Path {
    // Squareness: a superellipse exponent from 24 (all but a rectangle) down
    // to 2 (an ellipse — a circle once the bounds are square), done by 70%.
    val relax = (t / 0.7f).coerceIn(0f, 1f)
    val n = 24.0 + (2.0 - 24.0) * (1f - (1f - relax) * (1f - relax))
    // Scallops grow in over the last 40%, eased at both ends.
    val g = ((t - 0.6f) / 0.4f).coerceIn(0f, 1f)
    val depth = CookieDepth * g * g * (3f - 2f * g)
    val a = size.width / 2.0
    val b = size.height / 2.0
    val steps = 96
    val path = Path()
    for (i in 0..steps) {
        val theta = -PI / 2 + 2 * PI * i / steps
        val c = abs(cos(theta))
        val s = abs(sin(theta))
        // Superellipse radius in this direction; written as a·b / (…)^(1/n)
        // so the powers stay in range at any size.
        val se = a * b / ((b * c).pow(n) + (a * s).pow(n)).pow(1.0 / n)
        // As cookiePath: shrunk by the depth so the crests land on the edge.
        val r = se * (1.0 + depth * cos(CookieLobes * (theta + MiniArtShape.rotation))) / (1.0 + depth)
        val x = (a + r * cos(theta)).toFloat()
        val y = (b + r * sin(theta)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}
