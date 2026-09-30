package com.glasscast.app.ui

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
 * [clip] shapes the cover *while it flies*. In flight it's drawn on the
 * transition's overlay, above everything, and the clip its home gives it
 * doesn't reach there — so without this, the mini player's cover travelled as
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
}

/**
 * The now-playing cover's shape in flight, between the player and the mini
 * player. Worked out from its size at each frame: square-cornered at the
 * player's size (the player's cover is full-bleed), rounding as it shrinks,
 * and arriving at exactly the mini player's current shape — the card's
 * rounded square or the bubble's circle. The rounding is eased (squared), so
 * it stays square while large and rounds as it arrives, rather than turning
 * into a soft blob halfway down the screen. The same clip serves the opening
 * flight, in reverse.
 */
internal val FlightClip: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val side = minOf(size.width, size.height)
        val small = with(density) { 48.dp.toPx() }
        val big = with(density) { 280.dp.toPx() }
        val t = ((big - side) / (big - small)).coerceIn(0f, 1f)
        val radius = MiniArtShape.roundness * side * t * t
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius)))
    }
}
