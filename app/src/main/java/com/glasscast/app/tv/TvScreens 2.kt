package com.glasscast.app.tv

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

        itemsIndexed(ordered, key = { _, feed -> feed.url }) { index, feed ->
            Column(
                Modifier
                    .then(if (index == 0) Modifier.focusRequester(focusRequester) else Modifier)
                    .tvFocusable(accent = colors.accent) { onOpenFeed(feed) }
            ) {
                Artwork(url = feed.imageUrl, sizeDp = 260.dp, corner = 14.dp, fill = true)
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
    onPlay: (Episode) -> Unit
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

        items(latest, key = { it.guid }) { episode ->
            TvEpisodeRow(
                episode = episode,
                feed = feedsByUrl[episode.feedUrl],
                colors = colors,
                showArtwork = true,
                onClick = { onPlay(episode) }
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
    onQueue: (Episode) -> Unit
) {
    val ordered = remember(episodes, sort) {
        when (sort) {
            EpisodeSort.NEWEST_FIRST -> episodes.sortedByDescending { it.pubDate }
            EpisodeSort.OLDEST_FIRST -> episodes.sortedBy { it.pubDate }
        }
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                Artwork(url = feed.imageUrl, sizeDp = 230.dp, corner = 16.dp)
                Spacer(Modifier.width(28.dp))
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
                        text = buildString {
                            append("${ordered.size} EPISODES")
                            val latest = ordered.maxOfOrNull { it.pubDate } ?: 0L
                            if (latest > 0) append(" · UPDATED ${formatDate(latest).uppercase()}")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.contentVariant
                    )

                    if (feed.description.isNotBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stripHtml(feed.description),
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
                            label = "Play",
                            icon = Icons.Filled.PlayArrow,
                            colors = colors,
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
                            icon = Icons.Filled.Check,
                            colors = colors,
                            onClick = onBack
                        )
                    }
                }
            }
        }

        items(ordered, key = { it.guid }) { episode ->
            TvEpisodeRow(
                episode = episode,
                feed = feed,
                colors = colors,
                showArtwork = false,
                onClick = { onPlay(episode) },
                onLongClick = { onQueue(episode) }
            )
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
@Composable
fun TvEpisodeRow(
    episode: Episode,
    feed: Feed?,
    colors: ArtworkColors,
    showArtwork: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val played = episode.effectivelyPlayed
    val started = episode.positionMs > 1_000 && !played && episode.durationMs > 0

    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusableRow(accent = colors.accent, surface = colors.content, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showArtwork) {
            Artwork(
                url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                sizeDp = 74.dp,
                corner = 10.dp
            )
            Spacer(Modifier.width(20.dp))
        }

        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    append(formatDate(episode.pubDate).uppercase())
                    if (showArtwork) {
                        feed?.title?.takeIf { it.isNotBlank() }?.let { append(" · ${it.uppercase()}") }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (played) colors.contentVariant else colors.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
                    .size(44.dp)
                    .tvFocusable(
                        shape = RoundedCornerShape(percent = 50),
                        accent = colors.accent,
                        scale = 1.12f,
                        onClick = onLongClick
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.QueueMusic,
                    contentDescription = "Add to Up Next",
                    tint = colors.contentVariant,
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
