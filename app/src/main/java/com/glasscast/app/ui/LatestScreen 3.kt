package com.glasscast.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed

/**
 * Everything new across every subscription, newest first.
 *
 * The Library answers "what do I listen to"; this answers "what should I play
 * now", which is the question people actually open a podcast app with. Without
 * it, hearing a new episode means visiting shows one at a time to check.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LatestScreen(
    feeds: List<Feed>,
    episodeMap: Map<String, List<Episode>>,
    bottomInset: Dp,
    hidePlayed: Boolean,
    onHidePlayedChange: (Boolean) -> Unit,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onPlay: (Episode) -> Unit,
    onEpisodeActions: (Episode) -> Unit,
    onPlayNext: (Episode) -> Unit = {},
    onAddToQueue: (Episode) -> Unit = {},
    onTogglePlayed: (Episode) -> Unit = {},
    playingGuid: String? = null,
    isPlaying: Boolean = false,
    downloads: Map<String, com.glasscast.app.data.DownloadEntry> = emptyMap()
) {
    val haptics = rememberHaptics()
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    val feedsByUrl = remember(feeds) { feeds.associateBy { it.url } }

    val all = remember(episodeMap, feeds) {
        episodeMap
            .filterKeys { feedsByUrl.containsKey(it) }
            .values
            .flatten()
            .sortedByDescending { it.pubDate }
            // A cap, not a page: past a couple of hundred this stops being a
            // list of what's new and starts being the whole library again.
            .take(200)
    }

    val latest = remember(all, hidePlayed, downloadedOnly, downloads) {
        (if (hidePlayed) all.filterNot { it.effectivelyPlayed } else all)
            .let { list -> if (downloadedOnly) list.filter { downloads[it.guid]?.state == com.glasscast.app.data.DownloadState.DONE } else list }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.statusBarsPadding().height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Latest",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                // Refreshing belongs where the new episodes appear, not only on
                // the Library tab — this is the screen you check for them.
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .clickable(enabled = !refreshing) {
                            haptics.play(Haptic.Tap)
                            onRefresh()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (refreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(19.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh all shows",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (all.isEmpty()) "Nothing yet"
                else "${all.count { !it.effectivelyPlayed }} unplayed across ${feeds.size} shows",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    label = "Hide played",
                    active = hidePlayed,
                    onClick = { onHidePlayedChange(!hidePlayed) }
                )
                if (downloads.values.any { it.state == com.glasscast.app.data.DownloadState.DONE }) {
                    Pill(
                        label = "Downloaded",
                        active = downloadedOnly,
                        onClick = { downloadedOnly = !downloadedOnly }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        LazyColumn(contentPadding = PaddingValues(bottom = bottomInset + 24.dp)) {
            items(latest, key = { it.guid }) { episode ->
                SwipeToQueue(
                    accent = MaterialTheme.colorScheme.primary,
                    onAddToQueue = { onAddToQueue(episode) },
                    played = episode.effectivelyPlayed,
                    onTogglePlayed = { onTogglePlayed(episode) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                ) {
                    LatestRow(
                        episode = episode,
                        feed = feedsByUrl[episode.feedUrl],
                        playing = episode.guid == playingGuid,
                        isPlaying = isPlaying,
                        download = downloads[episode.guid],
                        onClick = { onPlay(episode) },
                        onLongClick = { onEpisodeActions(episode) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LatestRow(
    episode: Episode,
    feed: Feed?,
    playing: Boolean,
    isPlaying: Boolean,
    download: com.glasscast.app.data.DownloadEntry? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val played = episode.effectivelyPlayed
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val started = episode.positionMs > 1_000 && !played && episode.durationMs > 0

    // Opaque card, fade on contents only — the swipe reveal behind must stay
    // hidden until the card moves. See SwipeToQueue.
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
    Row(
        Modifier
            .weight(1f)
            .alpha(if (played && !playing) 0.55f else 1f),
        horizontalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        // Cover on every row, unlike the show page — here the list spans shows,
        // so the artwork is what tells you which one you're looking at.
        Artwork(url = art, sizeDp = 56.dp, corner = 11.dp)

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (playing) {
                    Equalizer(
                        color = MaterialTheme.colorScheme.primary,
                        playing = isPlaying,
                        size = 12.dp
                    )
                    Spacer(Modifier.width(7.dp))
                }
                Text(
                    text = buildString {
                        append(formatDate(episode.pubDate).uppercase())
                        feed?.title?.takeIf { it.isNotBlank() }?.let { append(" · ${it.uppercase()}") }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    label = if (started) {
                        formatCompact(episode.durationMs - episode.positionMs) + " left"
                    } else {
                        formatCompact(episode.durationMs)
                    }
                )
                if (download != null) {
                    Spacer(Modifier.width(8.dp))
                    DownloadBadge(download)
                }
                if (started) {
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    LinearProgressIndicator(
                        progress = { episode.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                }
            }
        }
    }
    }
}
