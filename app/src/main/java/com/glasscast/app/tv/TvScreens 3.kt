package com.glasscast.app.tv

import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.fillMaxHeight
import com.glasscast.app.ui.chromeButton
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import com.glasscast.app.ui.requestWhenReady
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.DirectoryResult
import com.glasscast.app.data.Episode
import com.glasscast.app.data.EpisodeSort
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.ITunesDirectory
import com.glasscast.app.data.PodcastGenres
import com.glasscast.app.data.Settings
import com.glasscast.app.data.ShowSort
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.formatCompact
import com.glasscast.app.ui.formatDate
import com.glasscast.app.ui.stripHtml
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Foot of every list. The now-playing bar used to sit here and needed clearing;
 * with it gone this is just overscan plus a little breathing room, which gives
 * the lists back about 130dp of usable height.
 */
@Composable
private fun bottomRoom(hasNowPlaying: Boolean) = TvSpacing.overscanV + 16.dp

@Composable
private fun TvHeading(title: String, subtitle: String?, colors: ArtworkColors) {
    Column(Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.content
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentVariant
            )
        }
    }
}

// ---------------------------------------------------------------- library

@Composable
fun TvLibraryScreen(
    feeds: List<Feed>,
    showSort: ShowSort,
    latestAt: (Feed) -> Long,
    colors: ArtworkColors,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester,
    onOpenFeed: (Feed) -> Unit
) {
    val ordered = remember(feeds, showSort) {
        when (showSort) {
            ShowSort.RECENTLY_UPDATED -> feeds.sortedByDescending(latestAt)
            ShowSort.RECENTLY_PLAYED -> feeds.sortedByDescending { it.lastPlayedAt }
        }
    }

    if (ordered.isEmpty()) {
        // The empty state has to be focusable too. Every screen is handed a
        // FocusRequester and something has to carry it, or focus arrives at a
        // screen with nowhere to land and the remote stops responding.
        TvEmpty(
            title = "Nothing here yet",
            body = "Add shows from the Search tab, or import an OPML file from the phone app.",
            colors = colors,
            focusRequester = focusRequester
        )
        return
    }

    LazyVerticalGrid(
        // Five across on a 16:9 panel: wide enough that a cover is legible from
        // the sofa, tight enough that a row is one sweep of the eye.
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(
            start = TvSpacing.overscanH,
            end = TvSpacing.overscanH,
            top = TvSpacing.overscanV,
            bottom = bottomRoom(hasNowPlaying)
        ),
        horizontalArrangement = Arrangement.spacedBy(TvSpacing.tileGap),
        verticalArrangement = Arrangement.spacedBy(TvSpacing.tileGap),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TvHeading("Podcasts", "${ordered.size} shows", colors)
        }

        itemsIndexed(ordered, key = { _, feed -> feed.url }, contentType = { _, _ -> "show" }) { index, feed ->
            Column(
                Modifier
                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    .tvFocusable(accent = colors.chromeButton) { onOpenFeed(feed) }
            ) {
                // Decoded near the size it's shown: a tile on the Streamer's
                // 1080p interface is about 140dp wide. At 260dp every cover
                // cost four times the pixels to decode and keep in memory.
                Artwork(url = feed.imageUrl, sizeDp = 180.dp, corner = 16.dp, fill = true)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        text = feed.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.author.isNotBlank()) {
                        Text(
                            text = feed.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.contentVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------- discover

/**
 * Discover, for the sofa: the phone's shelves, laid out for a remote.
 *
 * Same recommendations as the phone — the library's own genres, shows played
 * in the last fortnight weighted up, charts minus anything followed — built by
 * the same shared function, so the two can never disagree. Each shelf is a row;
 * the D-pad moves along it, up and down moves between shelves. OK follows the
 * show and opens it, exactly as TV search does: there's no preview page on TV,
 * and following is undone in one step from the show's page.
 */
@Composable
fun TvDiscoverScreen(
    feeds: List<Feed>,
    store: com.glasscast.app.data.FeedStore,
    colors: ArtworkColors,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester,
    onSubscribed: (String) -> Unit,
    /** Marked "Not interested" on the phone; the TV respects it too. */
    hidden: Set<String> = emptySet()
) {
    var shelves by remember { mutableStateOf<List<com.glasscast.app.ui.Shelf>?>(null) }
    var busyUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Libraries from before category parsing need their categories fetched
    // once, or every recommendation falls back to the generic charts.
    LaunchedEffect(Unit) {
        if (feeds.any { it.categories.isEmpty() }) runCatching { store.backfillCategories() }
    }
    val genreKey = feeds.joinToString { it.url + ":" + it.categories.size + ":" + it.lastPlayedAt / 86_400_000 }
    LaunchedEffect(genreKey) {
        shelves = runCatching { com.glasscast.app.ui.buildShelves(feeds) }.getOrDefault(emptyList())
    }

    val loaded = shelves?.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.feedUrl in hidden }) }
        ?.filter { it.items.isNotEmpty() }
    // The loading card held focus; when the shelves replace it, hand focus to
    // the first card, or the remote would be pointing at nothing.
    LaunchedEffect(loaded != null) {
        if (loaded != null) focusRequester.requestWhenReady()
    }

    when {
        loaded == null -> TvEmpty(
            title = "Finding shows…",
            body = "Building picks from the shows you follow.",
            colors = colors,
            focusRequester = focusRequester
        )
        loaded.isEmpty() -> TvEmpty(
            title = "Nothing to suggest right now",
            body = "Couldn't reach the podcast directory. Check the connection and come back.",
            colors = colors,
            focusRequester = focusRequester
        )
        else -> LazyColumn(
            contentPadding = PaddingValues(top = TvSpacing.overscanV, bottom = bottomRoom(hasNowPlaying)),
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Box(Modifier.padding(horizontal = TvSpacing.overscanH)) {
                    TvHeading(
                        "Discover",
                        if (feeds.isEmpty()) "What people are listening to" else "Picked from the shows you follow",
                        colors
                    )
                }
            }
            // The phone's "Top picks for you": the head of each shelf as big
            // cover cards, title over a darkening foot. Not repeated below.
            val picks = loaded.flatMap { it.items.take(2) }.distinctBy { it.feedUrl }.take(6)
            val pickUrls = picks.map { it.feedUrl }.toSet()
            val rows = loaded
                .map { it.copy(items = it.items.filterNot { r -> r.feedUrl in pickUrls }) }
                .filter { it.items.isNotEmpty() }

            if (picks.isNotEmpty()) {
                item(key = "picks", contentType = "picks") {
                    Column {
                        Text(
                            text = "Top picks for you",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.content,
                            modifier = Modifier.padding(horizontal = TvSpacing.overscanH)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = TvSpacing.overscanH, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(TvSpacing.tileGap)
                        ) {
                            itemsIndexed(picks, key = { _, r -> "pick:" + r.feedUrl }) { index, result ->
                                TvPickCard(
                                    result = result,
                                    colors = colors,
                                    busy = busyUrl == result.feedUrl,
                                    modifier = if (index == 0) Modifier.focusRequester(focusRequester) else Modifier
                                ) {
                                    if (busyUrl != null) return@TvPickCard
                                    busyUrl = result.feedUrl
                                    scope.launch {
                                        val reason = store.subscribe(result.feedUrl)
                                        busyUrl = null
                                        if (reason == null) onSubscribed(result.feedUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            itemsIndexed(rows, key = { _, shelf -> shelf.title }, contentType = { _, _ -> "shelf" }) { shelfIndex, shelf ->
                Column {
                    Column(Modifier.padding(horizontal = TvSpacing.overscanH)) {
                        Text(
                            text = shelf.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.content
                        )
                        Text(
                            text = shelf.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.contentVariant
                        )
                    }
                    LazyRow(
                        // Vertical room so a focused card's scale-up isn't clipped.
                        contentPadding = PaddingValues(horizontal = TvSpacing.overscanH, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(TvSpacing.tileGap)
                    ) {
                        itemsIndexed(shelf.items, key = { _, result -> shelf.title + result.feedUrl }) { index, result ->
                            Column(
                                Modifier
                                    .width(210.dp)
                                    .then(
                                        if (picks.isEmpty() && shelfIndex == 0 && index == 0) {
                                            Modifier.focusRequester(focusRequester)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .tvFocusable(accent = colors.chromeButton) {
                                        if (busyUrl != null) return@tvFocusable
                                        busyUrl = result.feedUrl
                                        scope.launch {
                                            val reason = store.subscribe(result.feedUrl)
                                            busyUrl = null
                                            if (reason == null) onSubscribed(result.feedUrl)
                                        }
                                    }
                            ) {
                                Artwork(url = result.artworkUrl, sizeDp = 210.dp, corner = 14.dp)
                                Spacer(Modifier.height(10.dp))
                                Column(Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                                    Text(
                                        text = result.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.content,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (busyUrl == result.feedUrl) "Following…" else result.author,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.contentVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A big cover card: the art full-bleed, the name over a darkening foot. */
@Composable
private fun TvPickCard(
    result: com.glasscast.app.data.DirectoryResult,
    colors: ArtworkColors,
    busy: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(width = 250.dp, height = 310.dp)
            .tvFocusable(
                shape = RoundedCornerShape(24.dp),
                accent = colors.chromeButton,
                onClick = onClick
            )
    ) {
        Artwork(
            url = result.artworkUrl,
            sizeDp = 310.dp,
            corner = 0.dp,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.8f)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (busy) "Following…" else result.author,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ----------------------------------------------------------------- latest

@Composable
fun TvLatestScreen(
    feeds: List<Feed>,
    episodeMap: Map<String, List<Episode>>,
    colors: ArtworkColors,
    refreshing: Boolean,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onPlay: (Episode) -> Unit,
    playingGuid: String? = null
) {
    val feedsByUrl = remember(feeds) { feeds.associateBy { it.url } }
    val latest = remember(episodeMap, feeds) {
        episodeMap.filterKeys(feedsByUrl::containsKey)
            .values.flatten()
            .sortedByDescending { it.pubDate }
            .take(120)
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = TvSpacing.overscanH,
            end = TvSpacing.overscanH,
            top = TvSpacing.overscanV,
            bottom = bottomRoom(hasNowPlaying)
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    TvHeading(
                        "Latest",
                        "${latest.count { !it.effectivelyPlayed }} unplayed across ${feeds.size} shows",
                        colors
                    )
                }
                TvPillButton(
                    label = if (refreshing) "Refreshing…" else "Refresh",
                    icon = Icons.Filled.Refresh,
                    colors = colors,
                    busy = refreshing,
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = onRefresh
                )
            }
        }

        items(latest, key = { it.guid }, contentType = { "episode" }) { episode ->
            TvEpisodeRow(
                episode = episode,
                feed = feedsByUrl[episode.feedUrl],
                colors = colors,
                showArtwork = true,
                onClick = { onPlay(episode) },
                isCurrent = episode.guid == playingGuid
            )
        }
    }
}

// ------------------------------------------------------------------- show

@Composable
fun TvShowScreen(
    feed: Feed,
    episodes: List<Episode>,
    sort: EpisodeSort,
    colors: ArtworkColors,
    refreshing: Boolean,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onPlay: (Episode) -> Unit,
    onQueue: (Episode) -> Unit,
    playingGuid: String? = null
) {
    val ordered = remember(episodes, sort) {
        when (sort) {
            EpisodeSort.NEWEST_FIRST -> episodes.sortedByDescending { it.pubDate }
            EpisodeSort.OLDEST_FIRST -> episodes.sortedBy { it.pubDate }
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Stripped once per description, not on every recomposition of the header.
    val notes = remember(feed.description) { stripHtml(feed.description) }
    val updatedLine = remember(ordered) {
        buildString {
            append("${ordered.size} EPISODES")
            val latest = ordered.maxOfOrNull { it.pubDate } ?: 0L
            if (latest > 0) append(" · UPDATED ${formatDate(latest).uppercase()}")
        }
    }

    Box(Modifier.fillMaxSize()) {
    // The cover's colour washed down from the top, as on the phone's show
    // page — a gradient, not a blur, so it costs one draw.
    Box(
        Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(
                Brush.verticalGradient(
                    0f to colors.wash.copy(alpha = 0.85f),
                    1f to Color.Transparent
                )
            )
    )

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = TvSpacing.overscanH,
            end = TvSpacing.overscanH,
            top = TvSpacing.overscanV,
            bottom = bottomRoom(hasNowPlaying)
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            /*
             * The header is a side-by-side band, not the phone's centred
             * poster. A 16:9 panel has width to spare and no height to waste —
             * stacking cover over title over actions would push the episode
             * list off the bottom of the screen entirely.
             */
            /*
             * Scrolling back up stops short otherwise.
             *
             * A lazy list scrolls only far enough to bring the newly focused
             * item into view. Coming back up, focus lands on the Play button
             * partway down this header, so the list stops there and the cover
             * and title stay clipped off the top — which is exactly what the
             * screenshot shows. Asking for index 0 whenever focus enters the
             * header restores the whole thing.
             */
            Row(
                Modifier
                    .padding(bottom = 22.dp)
                    .onFocusChanged {
                        if (it.hasFocus) scope.launch { listState.animateScrollToItem(0) }
                    }
            ) {
                Artwork(url = feed.imageUrl, sizeDp = 250.dp, corner = 20.dp)
                Spacer(Modifier.width(32.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = feed.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.content,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.author.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = feed.author,
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.accent
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = updatedLine,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.contentVariant
                    )

                    if (notes.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.contentVariant,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val next = ordered.firstOrNull { !it.effectivelyPlayed }
                            ?: ordered.firstOrNull()
                        TvPillButton(
                            label = if (next?.let { it.positionMs > 1_000 && !it.effectivelyPlayed } == true) "Resume" else "Play latest",
                            icon = Icons.Filled.PlayArrow,
                            colors = colors.copy(accent = colors.chromeButton),
                            filled = true,
                            modifier = Modifier.focusRequester(focusRequester),
                            onClick = { next?.let(onPlay) }
                        )
                        TvPillButton(
                            label = if (refreshing) "Refreshing…" else "Refresh",
                            icon = Icons.Filled.Refresh,
                            colors = colors,
                            busy = refreshing,
                            onClick = onRefresh
                        )
                        TvPillButton(
                            label = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            colors = colors,
                            onClick = onBack
                        )
                    }
                }
            }
        }

        items(ordered, key = { it.guid }, contentType = { "episode" }) { episode ->
            TvEpisodeRow(
                episode = episode,
                feed = feed,
                colors = colors,
                showArtwork = false,
                onClick = { onPlay(episode) },
                onLongClick = { onQueue(episode) },
                isCurrent = episode.guid == playingGuid
            )
        }
    }
    }
}

// ----------------------------------------------------------------- search

@Composable
fun TvSearchScreen(
    store: FeedStore,
    subscribed: List<Feed>,
    colors: ArtworkColors,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester,
    onSubscribed: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<DirectoryResult>>(emptyList()) }
    var genre by remember { mutableStateOf(PodcastGenres.first()) }
    var charts by remember { mutableStateOf<List<DirectoryResult>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var adding by remember { mutableStateOf<String?>(null) }

    /*
     * A pasted URL is added, anything else is searched.
     *
     * Typing a feed URL on a remote is miserable, so this doesn't get its own
     * screen — but it does need to exist, because the TV has no other way to
     * add a show that the directory doesn't list. One field that recognises a
     * URL costs nothing and covers it.
     */
    val looksLikeUrl = remember(query) {
        val q = query.trim()
        q.startsWith("http://", true) || q.startsWith("https://", true) ||
            q.startsWith("feed://", true) ||
            (q.contains('.') && !q.contains(' ') && q.length > 6)
    }

    LaunchedEffect(genre.id) {
        busy = true
        charts = runCatching { ITunesDirectory.top(genre.id) }.getOrDefault(emptyList())
        busy = false
    }

    LaunchedEffect(query, looksLikeUrl) {
        if (looksLikeUrl || query.trim().length < 2) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(450)
        busy = true
        results = runCatching { ITunesDirectory.search(query) }.getOrDefault(emptyList())
        busy = false
    }

    val shown = if (!looksLikeUrl && query.trim().length >= 2) results else charts

    LazyColumn(
        contentPadding = PaddingValues(
            start = TvSpacing.overscanH,
            end = TvSpacing.overscanH,
            top = TvSpacing.overscanV,
            bottom = bottomRoom(hasNowPlaying)
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TvHeading("Search", null, colors)

            /*
             * The field itself is the focus target.
             *
             * It was wrapped in a focusable Row, which swallowed focus before
             * the text field could ever receive it — so the D-pad landed on the
             * row, the field was never focused, and the system keyboard had no
             * reason to appear. A focusable container around a focusable child
             * is one focus stop too many on TV.
             *
             * With the field focused, Google TV raises its own IME, which is
             * what people expect: their remote's keyboard, with voice input,
             * rather than a key grid drawn in-app.
             */
            val fieldInteraction = remember { MutableInteractionSource() }
            val fieldFocused by fieldInteraction.collectIsFocusedAsState()
            val keyboard = LocalSoftwareKeyboardController.current

            LaunchedEffect(fieldFocused) {
                if (fieldFocused) keyboard?.show()
            }

            Row(
                Modifier
                    .fillMaxWidth(0.62f)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colors.elevated.copy(alpha = 0.6f))
                    .border(
                        width = if (fieldFocused) 3.dp else 0.dp,
                        color = if (fieldFocused) colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(percent = 50)
                    )
                    .padding(horizontal = 26.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (fieldFocused) colors.accent else colors.contentVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(16.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "Shows, hosts, topics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.contentVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.content),
                        cursorBrush = SolidColor(colors.accent),
                        interactionSource = fieldInteraction,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        // The screen's own requester, attached here — chaining
                        // two focusRequester modifiers on one node is ambiguous
                        // about which wins.
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            if (looksLikeUrl) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvPillButton(
                        label = if (adding != null) "Adding…" else "Add this feed",
                        icon = Icons.Filled.Add,
                        colors = colors,
                        filled = true,
                        busy = adding != null,
                        onClick = {
                            if (adding == null) {
                                adding = query.trim()
                                scope.launch {
                                    val reason = store.subscribe(query.trim())
                                    adding = null
                                    if (reason == null) onSubscribed(query.trim())
                                }
                            }
                        }
                    )
                }
                Spacer(Modifier.height(18.dp))
            }

            if (!looksLikeUrl && query.trim().length < 2) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PodcastGenres.take(7).forEach { g ->
                        TvPillButton(
                            label = g.label,
                            icon = null,
                            colors = colors,
                            filled = g.id == genre.id,
                            onClick = { genre = g }
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        if (busy && shown.isEmpty()) {
            item {
                Text(
                    text = "Loading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.contentVariant
                )
            }
        }

        items(shown, key = { it.feedUrl }) { result ->
            val already = subscribed.any { it.url.equals(result.feedUrl, true) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .tvFocusableRow(accent = colors.accent, surface = colors.content) {
                        if (already) {
                            onSubscribed(result.feedUrl)
                        } else if (adding == null) {
                            adding = result.feedUrl
                            scope.launch {
                                val reason = store.subscribe(result.feedUrl)
                                adding = null
                                if (reason == null) onSubscribed(result.feedUrl)
                            }
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(url = result.artworkUrl, sizeDp = 82.dp, corner = 10.dp)
                Spacer(Modifier.width(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = result.author,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.contentVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(16.dp))
                when {
                    adding == result.feedUrl -> CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = colors.accent,
                        modifier = Modifier.size(22.dp)
                    )
                    already -> Icon(
                        Icons.Filled.Check,
                        contentDescription = "In your library",
                        tint = colors.accent,
                        modifier = Modifier.size(26.dp)
                    )
                    else -> Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add",
                        tint = colors.contentVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------- settings

@Composable
fun TvSettingsScreen(
    settings: Settings,
    sort: EpisodeSort,
    showSort: ShowSort,
    colors: ArtworkColors,
    hasNowPlaying: Boolean,
    focusRequester: FocusRequester
) {
    LazyColumn(
        contentPadding = PaddingValues(
            start = TvSpacing.overscanH,
            end = TvSpacing.overscanH,
            top = TvSpacing.overscanV,
            bottom = bottomRoom(hasNowPlaying)
        ),
        verticalArrangement = Arrangement.spacedBy(22.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item { TvHeading("Settings", null, colors) }

        /*
         * No appearance section.
         *
         * Every surface on TV takes its colour from the artwork, so Dark and
         * Lights out had nothing left to change — which is exactly what you saw
         * when switching to Lights out did nothing at all. An option that
         * visibly does nothing is worse than no option.
         */
        item {
            TvSettingGroup("SHOW ORDER", colors) {
                TvPillButton(
                    label = "Recently updated",
                    icon = null,
                    colors = colors,
                    filled = showSort == ShowSort.RECENTLY_UPDATED,
                    modifier = Modifier.focusRequester(focusRequester),
                    onClick = { settings.setShowSort(ShowSort.RECENTLY_UPDATED) }
                )
                TvPillButton(
                    label = "Recently played",
                    icon = null,
                    colors = colors,
                    filled = showSort == ShowSort.RECENTLY_PLAYED,
                    onClick = { settings.setShowSort(ShowSort.RECENTLY_PLAYED) }
                )
            }
        }

        item {
            TvSettingGroup("EPISODE ORDER", colors) {
                TvPillButton(
                    label = "Newest first",
                    icon = null,
                    colors = colors,
                    filled = sort == EpisodeSort.NEWEST_FIRST,
                    onClick = { settings.setSort(EpisodeSort.NEWEST_FIRST) }
                )
                TvPillButton(
                    label = "Oldest first",
                    icon = null,
                    colors = colors,
                    filled = sort == EpisodeSort.OLDEST_FIRST,
                    onClick = { settings.setSort(EpisodeSort.OLDEST_FIRST) }
                )
            }
        }

        item {
            // In-app updates: the TV is where sideloading hurts most, so the
            // whole path — check, download, verify, install — is one pill.
            val context = androidx.compose.ui.platform.LocalContext.current
            val updater = (context.applicationContext as com.glasscast.app.GlassCastApp).updates
            val update by updater.state.collectAsStateWithLifecycle()
            Column {
                TvSettingGroup("UPDATES", colors) {
                    when (val u = update) {
                        is com.glasscast.app.update.UpdateState.Available -> TvPillButton(
                            label = "Install ${u.release.version}",
                            icon = null,
                            colors = colors,
                            filled = true,
                            onClick = { updater.download(u.release) }
                        )
                        is com.glasscast.app.update.UpdateState.Downloading -> TvPillButton(
                            label = "Downloading ${(u.progress * 100).toInt()}%",
                            icon = null,
                            colors = colors,
                            busy = true,
                            onClick = {}
                        )
                        is com.glasscast.app.update.UpdateState.ReadyToInstall -> TvPillButton(
                            label = "Install",
                            icon = null,
                            colors = colors,
                            filled = true,
                            onClick = { updater.install(u.release, u.file) }
                        )
                        is com.glasscast.app.update.UpdateState.NeedsPermission -> TvPillButton(
                            label = "Allow installs",
                            icon = null,
                            colors = colors,
                            filled = true,
                            onClick = { updater.openInstallPermission() }
                        )
                        is com.glasscast.app.update.UpdateState.Checking -> TvPillButton(
                            label = "Checking…",
                            icon = null,
                            colors = colors,
                            busy = true,
                            onClick = {}
                        )
                        else -> TvPillButton(
                            label = "Check for updates",
                            icon = null,
                            colors = colors,
                            onClick = { updater.check(manual = true) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (val u = update) {
                        is com.glasscast.app.update.UpdateState.UpToDate -> "GlassCast ${updater.currentVersion} — up to date."
                        is com.glasscast.app.update.UpdateState.Available -> "GlassCast ${u.release.version} is out. You have ${updater.currentVersion}."
                        is com.glasscast.app.update.UpdateState.NeedsPermission ->
                            "Allow GlassCast to install apps (asked once), then press Install."
                        is com.glasscast.app.update.UpdateState.ReadyToInstall -> "Downloaded and verified — confirm in the installer."
                        is com.glasscast.app.update.UpdateState.Failed -> u.message
                        else -> "GlassCast ${updater.currentVersion}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.contentVariant
                )
            }
        }

        item {
            // The phone's two sound settings. Same playback service, so they
            // apply here exactly as they do on the phone.
            val skipSilence by settings.skipSilence.collectAsStateWithLifecycle()
            val voiceBoost by settings.voiceBoost.collectAsStateWithLifecycle()
            TvSettingGroup("SOUND", colors) {
                TvPillButton(
                    label = if (skipSilence) "Skip silence: on" else "Skip silence: off",
                    icon = null,
                    colors = colors,
                    filled = skipSilence,
                    onClick = { settings.setSkipSilence(!skipSilence) }
                )
                TvPillButton(
                    label = if (voiceBoost) "Boost voices: on" else "Boost voices: off",
                    icon = null,
                    colors = colors,
                    filled = voiceBoost,
                    onClick = { settings.setVoiceBoost(!voiceBoost) }
                )
            }
        }

        item {
            Text(
                text = "Appearance follows the artwork here. Add shows from Search " +
                    "— by name, or by pasting a feed URL. This box keeps its own " +
                    "library; it does not sync with the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentVariant
            )
        }
    }
}

@Composable
private fun TvSettingGroup(
    label: String,
    colors: ArtworkColors,
    content: @Composable () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.contentVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

// ------------------------------------------------------------------ parts

@OptIn(ExperimentalFoundationApi::class)
/**
 * An episode as a card, like the phone's rows: a resting surface so each row
 * reads as an object, the date line, the title, and state — a progress bar
 * when started, a check when played, a waveform when it's what's playing.
 *
 * The waveform is static. The phone animates its equaliser, but on TV any
 * perpetual animation keeps the whole screen redrawing at 60fps.
 */
@Composable
fun TvEpisodeRow(
    episode: Episode,
    feed: Feed?,
    colors: ArtworkColors,
    showArtwork: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    isCurrent: Boolean = false
) {
    val played = episode.effectivelyPlayed
    val started = episode.positionMs > 1_000 && !played && episode.durationMs > 0
    val ring = colors.chromeButton
    val dateLine = remember(episode.pubDate, feed?.title, showArtwork) {
        buildString {
            append(formatDate(episode.pubDate).uppercase())
            if (showArtwork) feed?.title?.takeIf { it.isNotBlank() }?.let { append(" · ${it.uppercase()}") }
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusableRow(
                accent = ring,
                surface = colors.content,
                shape = RoundedCornerShape(18.dp),
                onClick = onClick
            )
            .background(colors.content.copy(alpha = if (isCurrent) 0.10f else 0.045f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArtwork) {
            Artwork(
                url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                sizeDp = 76.dp,
                corner = 12.dp
            )
            Spacer(Modifier.width(20.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = dateLine,
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (played && !isCurrent) colors.contentVariant else colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (started || played || isCurrent) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isCurrent) {
                        Icon(
                            Icons.Filled.GraphicEq,
                            contentDescription = "Now playing",
                            tint = ring,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    if (started) {
                        Box(
                            Modifier
                                .width(120.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(colors.content.copy(alpha = 0.16f))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(
                                        (episode.positionMs.toFloat() / episode.durationMs).coerceIn(0f, 1f)
                                    )
                                    .background(ring)
                            )
                        }
                    } else if (played) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Played",
                            tint = colors.contentVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Played",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.contentVariant
                        )
                    }
                }
            }
        }

        Spacer(Modifier.width(20.dp))
        Text(
            text = if (started) {
                formatCompact(episode.durationMs - episode.positionMs) + " left"
            } else {
                formatCompact(episode.durationMs)
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.contentVariant
        )

        if (onLongClick != null) {
            Spacer(Modifier.width(18.dp))
            Box(
                Modifier
                    .size(46.dp)
                    .tvFocusable(
                        shape = CircleShape,
                        accent = ring,
                        scale = 1.12f,
                        onClick = onLongClick
                    )
                    .background(colors.content.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Add to Up Next",
                    tint = colors.content.copy(alpha = 0.8f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun TvPillButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    colors: ArtworkColors,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    val fill = if (filled) colors.accent else colors.content.copy(alpha = 0.12f)
    val labelColor = if (filled) colors.onAccent else colors.content

    Row(
        modifier
            .tvFocusable(
                shape = RoundedCornerShape(percent = 50),
                accent = colors.accent,
                scale = 1.06f,
                onClick = onClick
            )
            .background(fill)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (busy) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = labelColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, tint = labelColor, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = labelColor)
    }
}

@Composable
private fun TvEmpty(
    title: String,
    body: String,
    colors: ArtworkColors,
    focusRequester: FocusRequester? = null
) {
    Box(
        Modifier
            .fillMaxSize()
            .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.displaySmall, color = colors.content)
            Spacer(Modifier.height(10.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentVariant
            )
        }
    }
}
