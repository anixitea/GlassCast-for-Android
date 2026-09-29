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

/**
 * Swipe a row left to add it to Up Next.
 *
 * One direction now, not two. The right-swipe "play next" used
 * QueuePlayNext, an icon that reads as a monitor with a plus on it and told
 * nobody what it did; one gesture with one obvious icon beats two where one is
 * a riddle. Play next is still in the long-press sheet.
 *
 * **Why it used to freeze and snap.** The row was allowed to *commit* to its
 * dismissed position — slid fully out — and then a LaunchedEffect called
 * `snapTo(Settled)`, which is an instant jump with no animation at all. Hence
 * the stall at the end of the swipe followed by a teleport.
 *
 * Now the commit is refused: `confirmValueChange` runs the action and returns
 * false, so the state never settles at "dismissed". The box then springs back
 * to rest by its own animation, which is what a flick that did something (but
 * removed nothing) should look like.
 *
 * The reveal tile scales up with the drag so the threshold feels approached
 * rather than hit, and it only draws while a drag is in progress — the row
 * above it is an opaque card, so there's nothing to hide at rest.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeToQueue(
    accent: Color,
    onAddToQueue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Off for shows that aren't in the library: there is no queue to put them in. */
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val haptics = rememberHaptics()
    val latestAdd by rememberUpdatedState(onAddToQueue)

    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                haptics.play(Haptic.ToggleOn)
                latestAdd()
            }
            false
        },
        // A quarter of the row: this is a flick, not a dismissal.
        positionalThreshold = { distance -> distance * 0.25f }
    )

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = enabled,
        backgroundContent = {
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                // Grows toward full size as the drag nears the threshold.
                val grow = 0.55f + 0.45f * (state.progress * 4f).coerceIn(0f, 1f)
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier
                            .fillMaxHeight()
                            .width(84.dp)
                            .graphicsLayer {
                                scaleX = grow
                                scaleY = grow
                            }
                            .clip(RoundedCornerShape(22.dp))
                            .background(accent.copy(alpha = 0.30f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Up Next",
                            tint = accent,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        },
        content = { content() }
    )
}
