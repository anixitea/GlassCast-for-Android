package com.glasscast.app.ui

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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtwork(key: String, scope: AnimatedVisibilityScope?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = scope ?: return this
    return with(shared) {
        this@sharedArtwork.sharedElement(
            state = rememberSharedContentState(key),
            animatedVisibilityScope = visibility,
            boundsTransform = ArtBounds
        )
    }
}
