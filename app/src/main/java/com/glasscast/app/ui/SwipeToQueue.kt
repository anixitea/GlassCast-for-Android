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

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
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
            false
        },
        // A quarter of the row: this is a flick, not a dismissal.
        positionalThreshold = { distance -> distance * 0.25f }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = enabled && onTogglePlayed != null,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            when (state.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> RevealTile(
                    state = state,
                    alignment = Alignment.CenterEnd,
                    accent = accent,
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    description = tr("Add to Up Next")
                )
                SwipeToDismissBoxValue.StartToEnd -> RevealTile(
                    state = state,
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
 * same quarter-row flick, the same tile growing in from the edge, in the
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
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.play(Haptic.ToggleOn)
                latestDelete()
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.25f }
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                RevealTile(
                    state = state,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RevealTile(
    state: SwipeToDismissBoxState,
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
                    // Grows toward full size as the drag nears the threshold.
                    val grow = 0.55f + 0.45f * (state.progress * 4f).coerceIn(0f, 1f)
                    scaleX = grow
                    scaleY = grow
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
