package com.glasscast.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.EpisodeSort
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    feed: Feed,
    episodes: List<Episode>,
    sort: EpisodeSort,
    store: FeedStore,
    refreshing: Boolean,
    bottomInset: Dp,
    /** False when this is a preview of a show that isn't in the library yet. */
    subscribed: Boolean = true,
    subscribing: Boolean = false,
    hidePlayed: Boolean = false,
    onHidePlayedChange: (Boolean) -> Unit = {},
    onSubscribe: () -> Unit = {},
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onEpisodeActions: (Episode) -> Unit,
    onPlayNext: (Episode) -> Unit = {},
    onAddToQueue: (Episode) -> Unit = {},
    onTogglePlayed: (Episode) -> Unit = {},
    playingGuid: String? = null,
    isPlaying: Boolean = false,
    downloads: Map<String, com.glasscast.app.data.DownloadEntry> = emptyMap(),
    hazeState: HazeState
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val sorted = remember(episodes, sort) {
        when (sort) {
            EpisodeSort.NEWEST_FIRST -> episodes.sortedByDescending { it.pubDate }
            EpisodeSort.OLDEST_FIRST -> episodes.sortedBy { it.pubDate }
        }
    }

    // Only for shows you actually subscribe to — a preview has no played state
    // worth filtering, and hiding rows there would just look like a short feed.
    // "Downloaded" narrows the list to what's on the phone — for a flight, or
    // a commute underground. Not saved: it's a moment's filter, not a setting.
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    val ordered = remember(sorted, hidePlayed, subscribed, downloadedOnly, downloads) {
        (if (hidePlayed && subscribed) sorted.filterNot { it.effectivelyPlayed } else sorted)
            .let { list -> if (downloadedOnly) list.filter { downloads[it.guid]?.state == com.glasscast.app.data.DownloadState.DONE } else list }
    }

    val (washColors, _) = rememberArtworkColors(feed.imageUrl)
    var coverBitmap by remember(feed.url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 220f).coerceIn(0f, 1f)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(artworkGround(washColors))
    ) {

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                /*
                 * Top-anchored, not bottom-anchored in a tall box.
                 *
                 * The old header reserved 580dp and pinned its content to the
                 * bottom of it, which put the cover at the vertical middle of
                 * the screen with dead space above — it read as a layout
                 * mistake. Sizing the header to its content and starting it
                 * just under the bar brings the cover, the title and the
                 * actions all into the first screenful.
                 */
                Box(Modifier.fillMaxWidth()) {
                    ArtworkBackdrop(
                        url = feed.imageUrl,
                        colors = washColors,
                        modifier = Modifier
                            .matchParentSize()
                    )

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.statusBarsPadding().height(60.dp))
                        Artwork(
                            url = feed.imageUrl,
                            sizeDp = 268.dp,
                            corner = 22.dp,
                            onBitmap = { coverBitmap = it }
                        )
                        Spacer(Modifier.height(18.dp))

                        // Marquee rather than three wrapped lines: show titles
                        // run long, and a header that changes height between
                        // shows makes the whole page feel unstable.
                        Text(
                            text = feed.title,
                            style = MaterialTheme.typography.headlineMedium,
                            color = washColors.content,
                            maxLines = 1,
                            softWrap = false,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    initialDelayMillis = 2000,
                                    repeatDelayMillis = 2000
                                )
                        )
                        if (feed.author.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = feed.author,
                                style = MaterialTheme.typography.titleSmall,
                                color = washColors.accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = buildString {
                                append("${sorted.size} EPISODES")
                                val latest = sorted.maxOfOrNull { it.pubDate } ?: 0L
                                if (latest > 0) append(" · UPDATED ${formatDate(latest).uppercase()}")
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = washColors.contentVariant
                        )
                    }
                }

                /*
                 * One connected group rather than three separate buttons —
                 * Cider's album actions. Play takes the wide centre slot and the
                 * library/refresh actions the narrow ends, so the thing you came
                 * here to do is also the biggest target.
                 */
                val next = ordered.firstOrNull { !it.effectivelyPlayed } ?: ordered.firstOrNull()

                // The accent comes from the whole cover, so a bright corner
                // under this button gives a yellow button on a yellow stripe.
                // Checked against the region it actually covers.
                val (playFill, playGlyph) = remember(washColors.accent, coverBitmap) {
                    visibleOn(washColors.accent, cornerLuminance(coverBitmap))
                }
                val quiet = washColors.content.copy(alpha = 0.12f)

                ButtonGroup(
                    height = 58.dp,
                    modifier = Modifier
                        .padding(horizontal = 40.dp)
                        .padding(bottom = 20.dp)
                ) {
                    GroupButton(
                        position = GroupPosition.Start,
                        container = if (subscribed) washColors.accent.copy(alpha = 0.22f) else quiet,
                        content = if (subscribed) washColors.accent else washColors.content,
                        icon = if (subscribed) Icons.Filled.Check else Icons.Filled.Add,
                        description = if (subscribed) "In your library" else "Add to library",
                        iconSize = 24.dp,
                        enabled = subscribed || !subscribing
                    ) {
                        if (!subscribed && !subscribing) onSubscribe()
                    }
                    GroupButton(
                        position = GroupPosition.Middle,
                        container = playFill,
                        content = playGlyph,
                        icon = Icons.Filled.PlayArrow,
                        label = "Play",
                        weight = 2.1f,
                        iconSize = 24.dp,
                        enabled = next != null
                    ) {
                        next?.let(onPlay)
                    }
                    GroupButton(
                        position = GroupPosition.End,
                        container = quiet,
                        content = washColors.content,
                        icon = Icons.Filled.Refresh,
                        description = if (refreshing) "Refreshing" else "Refresh",
                        iconSize = 24.dp,
                        // Dimmed while running rather than spinning: the list
                        // itself shows new episodes arriving, which is the
                        // feedback that matters.
                        enabled = subscribed && !refreshing
                    ) {
                        scope.launch { store.refresh(feed) }
                    }
                }

                if (subscribed) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Pill(
                            label = "Hide played",
                            active = hidePlayed,
                            accent = washColors.accent,
                            onClick = { onHidePlayedChange(!hidePlayed) }
                        )
                        if (downloads.values.any { it.feedUrl == feed.url && it.state == com.glasscast.app.data.DownloadState.DONE }) {
                            Pill(
                                label = "Downloaded",
                                active = downloadedOnly,
                                accent = washColors.accent,
                                onClick = { downloadedOnly = !downloadedOnly }
                            )
                        }
                        val unplayed = sorted.count { !it.effectivelyPlayed }
                        if (unplayed > 0) Pill(label = "$unplayed unplayed")
                    }
                }
            }

            items(ordered, key = { it.guid }) { episode ->
                SwipeToQueue(
                    accent = washColors.accent,
                    onAddToQueue = { onAddToQueue(episode) },
                    played = episode.effectivelyPlayed,
                    onTogglePlayed = { onTogglePlayed(episode) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                    enabled = subscribed
                ) {
                    EpisodeRow(
                        episode = episode,
                        colors = washColors,
                        playing = episode.guid == playingGuid,
                        isPlaying = isPlaying,
                        download = downloads[episode.guid],
                        onClick = { onPlay(episode) },
                        onLongClick = { onEpisodeActions(episode) }
                    )
                }
            }
        }

        // The soft blur only once the header has scrolled away, as Cider does —
        // over the header itself it would just smear the artwork.
        val feedScrolled by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
        }
        TopGlassFade(
            hazeState = hazeState,
            pageColor = washColors.wash,
            active = feedScrolled,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .alpha(collapse)
        )

        /*
         * A floating back button, not a bar.
         *
         * The bar used to fade the show's title in as the header's title
         * scrolled away — and because the bar had no opaque surface, both
         * titles were visible at once for most of that scroll, one sliding
         * under the other. The header already names the show; the button
         * only needs to get you back.
         */
        Box(
            Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 6.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.34f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(
    episode: Episode,
    colors: ArtworkColors,
    playing: Boolean,
    isPlaying: Boolean,
    download: com.glasscast.app.data.DownloadEntry? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val played = episode.effectivelyPlayed
    val summary = remember(episode.description) { stripHtml(episode.description) }

    /*
     * A card, and an opaque one. It has to be: the swipe reveal sits behind
     * it and must stay hidden until the card actually moves. The played fade
     * is applied to the contents only for the same reason — fading the card
     * itself would let the reveal show through every played row.
     */
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.elevated)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
    Column(Modifier.alpha(if (played && !playing) 0.55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (playing) {
                Equalizer(color = colors.accent, playing = isPlaying, size = 13.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = formatDate(episode.pubDate).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentVariant
            )
            if (played) {
                Spacer(Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Played",
                    tint = colors.contentVariant,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (summary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val label = if (episode.positionMs > 1_000 && !played && episode.durationMs > 0) {
                formatCompact(episode.durationMs - episode.positionMs) + " left"
            } else {
                formatCompact(episode.durationMs)
            }
            if (label.isNotBlank()) Pill(label = label)
            if (download != null) {
                Spacer(Modifier.width(8.dp))
                DownloadBadge(download)
            }

            if (episode.positionMs > 1_000 && !played && episode.durationMs > 0) {
                Spacer(Modifier.width(12.dp))
                LinearProgressIndicator(
                    progress = { episode.progress },
                    modifier = Modifier
                        .weight(1f)
                        .height(2.dp),
                    color = colors.accent,
                    trackColor = colors.content.copy(alpha = 0.12f),
                    gapSize = 0.dp,
                    drawStopIndicator = {}
                )
            }
        }
    }
    }
}
