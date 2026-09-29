package com.glasscast.app.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.artworkGround
import com.glasscast.app.ui.formatRemaining
import com.glasscast.app.ui.formatTime
import com.glasscast.app.ui.stripHtml

/**
 * Now playing, full screen.
 *
 * The phone player's gestures all had to be replaced. What took their place:
 *
 *  - **Left and right seek by 30 seconds without moving focus.** The remote's
 *    horizontal axis is the natural scrub axis, and making people walk focus
 *    onto a seek button to move through an episode would be exhausting. The
 *    key handler consumes those presses, so focus stays on the play button.
 *  - **Back closes**, rather than a swipe down.
 *  - **The play button holds focus from the moment it opens.** On a TV the
 *    thing you most likely want is the thing under the cursor already.
 */
@Composable
fun TvPlayerScreen(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    upNextCount: Int,
    colors: ArtworkColors,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onRestart: () -> Unit,
    onClose: () -> Unit
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val playFocus = remember { FocusRequester() }
    val ground = remember(colors) { artworkGround(colors) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    var showNotes by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { playFocus.requestWhenReady() }

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // Left and right are NOT intercepted any more. Consuming
                    // them meant focus could never travel along the control
                    // row, so the seek buttons were visible, focusable in
                    // principle, and unreachable in practice. The remote's
                    // dedicated media keys still seek; the D-pad now moves
                    // focus, which is what a D-pad is for.
                    Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> { onPlayPause(); true }
                    Key.MediaFastForward -> { onSeekBy(30_000); true }
                    Key.MediaRewind -> { onSeekBy(-30_000); true }
                    Key.MediaNext -> { onSkipNext(); true }
                    Key.MediaPrevious -> { onRestart(); true }
                    else -> false
                }
            }
    ) {
        // Notes live behind one button in the corner. Rarely wanted, and on a
        // ten-foot screen there is room to keep it out of the control row
        // rather than adding a fifth stop people must pass through.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(TvSpacing.overscanV)
        ) {
            TvControl(Icons.Outlined.Info, "Episode notes", colors, 52.dp) { showNotes = true }
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TvSpacing.overscanH * 1.15f, vertical = TvSpacing.overscanV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork large and left, controls right. On a landscape panel a
            // centred stack wastes two thirds of the width.
            Artwork(url = art, sizeDp = 360.dp, corner = 20.dp)

            Spacer(Modifier.width(44.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = feed?.title.orEmpty().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.contentVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = colors.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(34.dp))

                // Read-only on TV: the left/right keys are the scrubber, so this
                // is a progress display and never takes focus. A focusable bar
                // would be one more stop between the play button and everything
                // else, for an action the D-pad already does.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(colors.content.copy(alpha = 0.20f), CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(colors.accent, CircleShape)
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemaining(positionMs, durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentVariant
                    )
                }

                Spacer(Modifier.height(30.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    /*
                     * Sized to fit, deliberately.
                     *
                     * These were 56/64/86 with 14dp gaps — 382dp of controls in
                     * roughly 350dp of column, so the last button was squeezed
                     * by the layout and rendered as an oval rather than a
                     * circle. Modifier.size sets a preferred size, not a
                     * guaranteed one; when the parent runs out of width the
                     * child is compressed on that axis alone.
                     */
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvControl(Icons.Filled.SkipPrevious, "Restart episode", colors, 52.dp, onRestart)
                    TvControl(Icons.Filled.Replay30, "Back 30 seconds", colors, 60.dp) {
                        onSeekBy(-30_000)
                    }
                    TvControl(
                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        description = if (isPlaying) "Pause" else "Play",
                        colors = colors,
                        size = 82.dp,
                        filled = true,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onPlayPause
                    )
                    TvControl(Icons.Filled.Forward30, "Forward 30 seconds", colors, 60.dp) {
                        onSeekBy(30_000)
                    }
                    TvControl(
                        icon = Icons.Filled.SkipNext,
                        description = if (upNextCount > 0) "Next episode" else "Nothing queued",
                        colors = colors,
                        size = 52.dp,
                        enabled = upNextCount > 0,
                        onClick = onSkipNext
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "BACK to close",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showNotes) {
        TvNotesPanel(
            episode = episode,
            feed = feed,
            colors = colors,
            onClose = { showNotes = false }
        )
    }
}

/**
 * Episode notes as a panel over the player rather than a separate screen: the
 * player is still playing behind it and nothing about the session changed, so
 * leaving the screen would overstate what just happened.
 */
@Composable
private fun TvNotesPanel(
    episode: Episode,
    feed: Feed?,
    colors: ArtworkColors,
    onClose: () -> Unit
) {
    val notes = remember(episode.description) { stripHtml(episode.description) }
    val scroll = rememberScrollState()
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { closeFocus.requestWhenReady() }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f)
                .tvPanel(RoundedCornerShape(24.dp), colors.elevated)
                .padding(36.dp)
        ) {
            Text(
                text = feed?.title.orEmpty().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = notes.ifBlank { "This episode has no description." },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentVariant,
                modifier = Modifier
                    .weight(1f)
                    // Focusable so the D-pad can scroll it; without this, long
                    // notes are simply unreachable past the first screenful.
                    .focusable()
                    .verticalScroll(scroll)
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.focusRequester(closeFocus)) {
                TvControl(Icons.Filled.Close, "Close notes", colors, 52.dp, onClose)
            }
        }
    }
}

@Composable
private fun TvControl(
    icon: ImageVector,
    description: String,
    colors: ArtworkColors,
    size: Dp,
    onClick: () -> Unit
) = TvControl(icon, description, colors, size, false, true, Modifier, onClick)

@Composable
private fun TvControl(
    icon: ImageVector,
    description: String,
    colors: ArtworkColors,
    size: Dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .tvFocusable(
                shape = CircleShape,
                accent = colors.accent,
                scale = 1.14f,
                enabled = enabled,
                onClick = onClick
            )
            .background(
                if (filled) colors.accent else colors.content.copy(alpha = 0.12f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                filled -> colors.onAccent
                enabled -> colors.content
                else -> colors.content.copy(alpha = 0.30f)
            },
            modifier = Modifier.size(size * 0.46f)
        )
    }
}
