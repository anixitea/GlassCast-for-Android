package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed

/**
 * The queue, without a container — it lives inside the player's pull-up panel
 * now rather than in a sheet of its own.
 *
 * [showNowPlaying] is off in the panel: the compact header above it already
 * names what's playing, and naming it twice reads as two different things.
 */
@Composable
fun QueueContent(
    nowPlaying: Episode?,
    nowPlayingFeed: Feed?,
    isPlaying: Boolean,
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    accent: Color,
    onPlayAt: (Int) -> Unit,
    onRemove: (Episode) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    showNowPlaying: Boolean = true,
    listModifier: Modifier = Modifier.heightIn(max = 420.dp)
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        // What's playing, pinned above the list. Without it the sheet is a
        // list of things that come after something it never names.
        if (showNowPlaying && nowPlaying != null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Artwork(
                    url = nowPlaying.imageUrl.ifBlank { nowPlayingFeed?.imageUrl.orEmpty() },
                    sizeDp = 52.dp,
                    corner = 10.dp
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (isPlaying) "PLAYING" else "PAUSED",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent
                    )
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlayingFeed?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Hairline(Modifier.padding(horizontal = 24.dp), alpha = 0.6f)
            Spacer(Modifier.height(16.dp))
        }

        Row(
            Modifier.padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when (upNext.size) {
                        0 -> "Nothing queued"
                        1 -> "1 episode"
                        else -> "${upNext.size} episodes"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (upNext.isNotEmpty()) Pill(label = "Clear", accent = accent, onClick = onClear)
        }

        Spacer(Modifier.height(14.dp))

        if (upNext.isEmpty()) {
            Text(
                text = "Swipe any episode left to add it here, or long-press it to play it next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(listModifier) {
                itemsIndexed(upNext, key = { _, ep -> ep.guid }) { index, episode ->
                    SwipeRow(
                        episode = episode,
                        feed = feedFor(episode),
                        onClick = { onPlayAt(index) },
                        onRemove = { onRemove(episode) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeRow(
    episode: Episode,
    feed: Feed?,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val state = rememberSwipeToDismissBoxState()

    // Removing during the swipe would yank the row out from under the gesture,
    // so the state settles first and the removal happens on the next frame.
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove()
    }

    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.16f))
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = "Remove",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    ) {
        QueueRow(episode = episode, feed = feed, onClick = onClick)
    }
}

@Composable
private fun QueueRow(
    episode: Episode,
    feed: Feed?,
    onClick: () -> Unit
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }

    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Artwork(url = art, sizeDp = 46.dp, corner = 9.dp)
        Column(Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(
                    feed?.title?.takeIf { it.isNotBlank() },
                    formatCompact(
                        if (episode.positionMs > 1_000 && episode.durationMs > 0) {
                            episode.durationMs - episode.positionMs
                        } else {
                            episode.durationMs
                        }
                    ).takeIf { it.isNotBlank() }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
