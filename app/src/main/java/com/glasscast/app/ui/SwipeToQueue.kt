package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.MaterialTheme

/**
 * The two swipes on an episode row: **left adds it to Up Next**, **right marks
 * it played** (or unplayed, if it already is).
 *
 * Right-swipe came back with a clear job. It was once "play next", behind an
 * icon nobody could read, and went; marking played is the thing people reach
 * for most after queueing (a Pocket Casts user asked for exactly this), and a
 * check mark says what it does.
 *
 * Neither swipe commits: `confirmValueChange` runs the action and returns
 * false, so the row springs back by its own animation instead of sliding out
 * and snapping home. The reveal tile grows toward the threshold, and its scale
 * is read in the layer — a swipe redraws the tile, it doesn't recompose it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueue(
    accent: Color,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Off for shows that aren't in the library: there is no queue to put them in. */
    enabled: Boolean = true,
    /** Whether the episode is played now — decides which way the right swipe flips it. */
    played: Boolean = false,
    /** Null disables the right swipe. */
    onTogglePlayed: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    val latestAdd by rememberUpdatedState(onAddToQueue)
    val latestToggle by rememberUpdatedState(onTogglePlayed)
    val pull = rememberDeliberatePull()

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            // Only a real pull counts, not a flick or a scroll that drifted
            // sideways (see rememberDeliberatePull).
            if (pull.far()) {
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> {
                        haptics.play(Haptic.ToggleOn)
                        latestAdd()
                    }
                    SwipeToDismissBoxValue.StartToEnd -> {
                        haptics.play(Haptic.ToggleOn)
                        latestToggle?.invoke()
                    }
                    else -> Unit
                }
            }
            false
        },
        positionalThreshold = { distance -> distance * DeliberatePull }
    )
    pull.state = state

    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { pull.width = it.width.toFloat() },
        enableDismissFromStartToEnd = enabled && onTogglePlayed != null,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> RevealTile(
                    pull = pull,
                    alignment = Alignment.CenterEnd,
                    accent = accent,
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    description = tr("Add to Up Next")
                )
                SwipeToDismissBoxValue.StartToEnd -> RevealTile(
                    pull = pull,
                    alignment = Alignment.CenterStart,
                    accent = accent,
                    icon = if (played) Icons.Filled.RemoveDone else Icons.Filled.Done,
                    description = if (played) tr("Mark as unplayed") else tr("Mark as played")
                )
                else -> Unit
            }
        },
        content = { content() }
    )
}

/**
 * Swipe left to delete — the Downloads list's version of swipe-to-queue: the
 * same deliberate pull, the same tile growing in from the edge, in the
 * error color with a bin. The row snaps back and is then removed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    val latestDelete by rememberUpdatedState(onDelete)
    val pull = rememberDeliberatePull()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart && pull.far()) {
                haptics.play(Haptic.ToggleOn)
                latestDelete()
            }
            false
        },
        positionalThreshold = { distance -> distance * DeliberatePull }
    )
    pull.state = state
    SwipeToDismissBox(
        state = state,
        modifier = modifier.onSizeChanged { pull.width = it.width.toFloat() },
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                RevealTile(
                    pull = pull,
                    alignment = Alignment.CenterEnd,
                    accent = MaterialTheme.colorScheme.error,
                    icon = Icons.Outlined.DeleteOutline,
                    description = tr("Delete download")
                )
            }
        },
        content = { content() }
    )
}

/**
 * How far a row has to be pulled before a swipe does anything: 40% of its
 * width. It was a quarter, and the box also takes a fast flick as a swipe at
 * any distance, so scrolling a list with a little sideways drift queued
 * episodes and marked them played (2.0, from Jonathan's testing). Now the
 * row has to have actually traveled that far when it's let go, flick or not.
 */
private const val DeliberatePull = 0.40f

@OptIn(ExperimentalMaterial3Api::class)
private class DeliberatePullState {
    var width = 0f
    var state: SwipeToDismissBoxState? = null
    fun far(): Boolean = reach() >= 1f

    /**
     * How far the row is out, 0 at rest to 1 at the pull that counts, from
     * where it actually is. Read in a graphics layer, so the tile follows the
     * row back in and is gone with it (see RevealTile).
     */
    fun reach(): Float {
        val offset = runCatching { state?.requireOffset() ?: 0f }.getOrDefault(0f)
        if (width <= 0f) return 0f
        return (kotlin.math.abs(offset) / (width * DeliberatePull)).coerceIn(0f, 1f)
    }
}

@Composable
private fun rememberDeliberatePull() = androidx.compose.runtime.remember { DeliberatePullState() }

/**
 * The tile behind a swiped row. It grows and fades with how far the row is
 * out, not with the swipe's progress: once a swipe is let go the box's
 * progress jumps to complete, so the tile used to sit there full size while
 * the row sprang back, and through the frosted cards of the glass look it
 * stayed visible until the spring had fully settled (2.0).
 */
@Composable
private fun RevealTile(
    pull: DeliberatePullState,
    alignment: Alignment,
    accent: Color,
    icon: ImageVector,
    description: String
) {
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(84.dp)
                .graphicsLayer {
                    // Grows toward full size as the row nears the pull that
                    // counts, and fades out as it comes back.
                    val reach = pull.reach()
                    val grow = 0.55f + 0.45f * reach
                    scaleX = grow
                    scaleY = grow
                    alpha = (reach * 2.5f).coerceIn(0f, 1f)
                }
                .clip(RoundedCornerShape(22.dp))
                .background(accent.copy(alpha = 0.30f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = accent,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
