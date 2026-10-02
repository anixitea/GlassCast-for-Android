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
import androidx.compose.ui.graphics.takeOrElse
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.outlined.Info
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.fillMaxHeight

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(
    feed: Feed,
    episodes: List<Episode>,
    sort: EpisodeSort,
    onToggleSort: () -> Unit = {},
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
    var showInfo by remember { mutableStateOf(false) }
    var coverBitmap by remember(feed.url) { mutableStateOf<android.graphics.Bitmap?>(null) }

    val collapse by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) 1f
            else (listState.firstVisibleItemScrollOffset / 220f).coerceIn(0f, 1f)
        }
    }

    // The header and the episode list, shared by the two layouts below.
    @Composable
    fun ShowHeader(compact: Boolean) {
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
                        sizeDp = if (compact) 220.dp else 268.dp,
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
                            append(tr("{0} EPISODES", sorted.size))
                            val latest = sorted.maxOfOrNull { it.pubDate } ?: 0L
                            if (latest > 0) append(tr(" · UPDATED {0}", formatDate(latest).uppercase()))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = washColors.contentVariant
                    )
                }
    }

    // The actions under the header: Cider's button group and the filters.
    @Composable
    fun ShowActions() {

        /*
         * One connected group rather than three separate buttons —
         * Cider's album actions. Play takes the wide center slot and the
         * library/refresh actions the narrow ends, so the thing you came
         * here to do is also the biggest target.
         */
        val next = ordered.firstOrNull { !it.effectivelyPlayed } ?: ordered.firstOrNull()

        // The cover's own background color, checked against the page
        // the button sits on (see showPlayColors).
        val darkPage = com.glasscast.app.ui.theme.LocalIsDark.current
        val (playFill, playGlyph) = remember(washColors.accent, washColors.background, coverBitmap, darkPage) {
            showPlayColors(coverBitmap, washColors.accent, washColors.background, darkPage)
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
                description = if (subscribed) tr("In your library") else tr("Add to library"),
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
                label = tr("Play"),
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
                description = if (refreshing) "Refreshing" else tr("Refresh"),
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
            // Hide played, Downloaded (once there is one) and sort, in one
            // row that always fits: sort keeps its size and the filters
            // shorten with an ellipsis if a translation runs long. The
            // "N unplayed" count is gone — Hide played covers it, and it
            // was what pushed the row past the edge.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Pill(
                    label = tr("Hide played"),
                    active = hidePlayed,
                    accent = washColors.accent,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = { onHidePlayedChange(!hidePlayed) }
                )
                if (downloads.values.any { it.feedUrl == feed.url && it.state == com.glasscast.app.data.DownloadState.DONE }) {
                    Pill(
                        label = tr("Downloaded"),
                        active = downloadedOnly,
                        accent = washColors.accent,
                        modifier = Modifier.weight(1f, fill = false),
                        onClick = { downloadedOnly = !downloadedOnly }
                    )
                }
                SortPill(sort = sort, onToggle = onToggleSort)
            }
        }
    }

    fun LazyListScope.showEpisodes() {


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

    // Landscape on a tablet: the header holds still on the left over a
    // backdrop fixed behind both panes; the episodes scroll on the right.
    // Portrait is as it always was — the header scrolls with the list.
    val config = LocalConfiguration.current
    val twoPane = config.screenWidthDp > config.screenHeightDp && config.screenHeightDp >= 480

    Box(
        Modifier
            .fillMaxSize()
            .background(artworkGround(washColors))
    ) {

        if (twoPane) {
            ArtworkBackdrop(url = feed.imageUrl, colors = washColors, modifier = Modifier.fillMaxSize())
            Row(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(0.42f)
                        .fillMaxHeight()
                        .padding(bottom = bottomInset)
                        .verticalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Column {
                        ShowHeader(compact = true)
                        ShowActions()
                    }
                }
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 72.dp, end = 12.dp, bottom = bottomInset + 24.dp),
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                ) {
                    showEpisodes()
                }
            }
        } else {
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

                    ShowHeader(compact = false)
                }

                ShowActions()
            }
            showEpisodes()
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
                contentDescription = tr("Back"),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Show info, the back button's twin on the right.
        if (feed.description.isNotBlank() || feed.categories.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(end = 14.dp, top = 6.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.34f))
                    .clickable { showInfo = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = tr("Info"),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (showInfo) ShowInfoSheet(feed, episodes.size, washColors) { showInfo = false }
    }
}

/**
 * Show info, from the (i) on the show page: cover, name and publisher, the
 * episode count and categories, and the feed's description in full — the
 * header has room for a few lines of it at most. In the show's own colors.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ShowInfoSheet(feed: Feed, episodeCount: Int, colors: ArtworkColors, onDismiss: () -> Unit) {
    val ink = if (colors.background.luminance() > 0.5f) Color(0xFF16141A) else Color.White
    val description = remember(feed.description) { stripHtml(feed.description) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.background,
        contentColor = ink
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(url = feed.imageUrl, sizeDp = 76.dp, corner = 16.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        feed.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.author.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            feed.author,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.accent,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                tr("{0} EPISODES", episodeCount),
                style = MaterialTheme.typography.labelMedium,
                color = ink.copy(alpha = 0.6f)
            )
            if (feed.categories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    feed.categories.distinct().take(8).forEach { category ->
                        Text(
                            category,
                            style = MaterialTheme.typography.labelLarge,
                            color = ink,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(ink.copy(alpha = 0.08f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            if (description.isNotBlank()) {
                Spacer(Modifier.height(18.dp))
                Text(description, style = MaterialTheme.typography.bodyLarge, color = ink.copy(alpha = 0.86f))
            }
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
            .background(colors.card.takeOrElse { colors.elevated })
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
                    contentDescription = tr("Played"),
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
