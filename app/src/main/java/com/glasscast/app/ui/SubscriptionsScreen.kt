package com.glasscast.app.ui

import com.glasscast.app.ui.liquid.liquidBackdrop
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import com.glasscast.app.ui.seaglass.chromePill
import com.glasscast.app.ui.seaglass.seaCoverPool
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    store: FeedStore,
    feeds: List<Feed>,
    refreshing: Boolean,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenFeed: (Feed) -> Unit,
    /** Opens the library filter — the Search tab is where the internet lives. */
    onSearch: () -> Unit,
    hazeState: HazeState,
    /** Started and not finished (FeedStore.inProgress), minus what's playing. */
    continueListening: List<com.glasscast.app.data.Episode> = emptyList(),
    feedFor: (com.glasscast.app.data.Episode) -> Feed? = { null },
    onResume: (com.glasscast.app.data.Episode) -> Unit = {},
    onEpisodeActions: (com.glasscast.app.data.Episode) -> Unit = {},
    onOpenHistory: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Feed?>(null) }

    val haptics = rememberHaptics()

    /*
     * Pull down to refresh the whole library.
     *
     * This gesture used to open Search, which is where the "ghost" search pill
     * came from: scrolling back up past the top overscrolls, the overscroll
     * counted as a pull, and the indicator faded in over the bar. Pulling a
     * list down means refresh to nearly everyone, so it means refresh here too
     * — and the search field stays where it always was, under the title.
     */
    val pullState = rememberPullToRefreshState()

    // A tick as the pull crosses the threshold, so you know releasing will
    // refresh before you let go.
    LaunchedEffect(pullState) {
        var armed = false
        snapshotFlow { pullState.distanceFraction >= 1f }.collect { now ->
            if (now && !armed) haptics.play(Haptic.Tick)
            armed = now
        }
    }

    // Large title collapses into the bar over 100dp of scroll. Kept as a
    // State and read in layers, not in composition: read directly, it rebuilt
    // this whole screen on every frame of the scroll.
    val collapse = rememberTitleCollapse(
        { gridState.firstVisibleItemIndex },
        { gridState.firstVisibleItemScrollOffset }
    )
    // The search field has scrolled away: its icon joins the pill. A boolean,
    // so this recomposes once at the crossing, not per frame.
    val searchInBar by remember { androidx.compose.runtime.derivedStateOf { collapse.value > 0.6f } }

    val pageGlass = com.glasscast.app.ui.liquid.rememberPageBackdrop(MaterialTheme.colorScheme.background)
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            haptics.play(Haptic.Select)
            scope.launch { store.refreshAll() }
        },
        state = pullState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            CookieRefreshIndicator(
                state = pullState,
                refreshing = refreshing,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
            )
        }
    ) {

        LazyVerticalGrid(
            // As many columns as fit at 160dp or more: two on a phone, as
            // before; four or five on a tablet instead of two giant covers.
            columns = GridCells.Adaptive(minSize = 160.dp),
            state = gridState,
            modifier = Modifier
                .fillMaxSize()
                // Frosted Glass: recorded for the bar buttons' glass.
                .liquidBackdrop(pageGlass),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp, bottom = bottomInset + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Spacer(Modifier.statusBarsPadding().height(56.dp))
                    Text(
                        text = tr("Podcasts"),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        // Gone before the bar's title arrives, so the two
                        // never show at once.
                        modifier = Modifier.bigTitleFade { collapse.value }
                    )
                    Spacer(Modifier.height(14.dp))
                    // Below the title, not above it: the page should still say
                    // what it is before it offers to leave.
                    RestingSearchPill(onClick = onSearch)
                    Spacer(Modifier.height(18.dp))
                }
            }

            // What you're part way through, before the shows: a short row
            // of covers with how far in each one is.
            if (continueListening.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "continue", contentType = "continue") {
                    ContinueListeningRow(
                        episodes = continueListening,
                        feedFor = feedFor,
                        onResume = onResume,
                        onEpisodeActions = onEpisodeActions
                    )
                }
            }

            if (feeds.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyLibrary(onAdd = { showAdd = true })
                }
            }

            items(feeds, key = { it.url }, contentType = { "show" }) { feed ->
                ShowTile(
                    feed = feed,
                    onClick = { onOpenFeed(feed) },
                    onLongClick = { pendingDelete = feed }
                )
            }
        }

        // Glass under the bar instead of a solid background that switches on at
        // some scroll offset. The ramp is always there; what changes is only
        // whether there is anything behind it to blur.
        val scrolled by remember {
            derivedStateOf { gridState.firstVisibleItemIndex > 0 || gridState.firstVisibleItemScrollOffset > 0 }
        }
        TopGlassFade(
            hazeState = hazeState,
            pageColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
            active = scrolled
        )

        /*
         * The bar, as Cider draws it: controls sit in pills rather than
         * straight on the content, so they read over any cover scrolling
         * beneath. Left, the title pill — Cider's "Home" — which arrives only
         * once the big "Podcasts" heading has scrolled away (the two used to
         * overlap mid-scroll). Right, the icons, always in their pill.
         */
        val pillColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f)
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val shown = ((collapse.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        alpha = shown
                        translationY = (1f - shown) * 8.dp.toPx()
                    }
                    // Liquid Glass: Apple's bar title, plain on the page's fade, no pill.
                    .then(
                        if (com.glasscast.app.ui.liquid.LocalLiquid.current != null) Modifier.padding(horizontal = 8.dp, vertical = 11.dp)
                        else Modifier.chromePill(pillColor, seed = 12).padding(horizontal = 20.dp, vertical = 11.dp)
                    )
            ) {
                Text(
                    text = tr("Podcasts"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.weight(1f))
            Row(
                Modifier
                    .chromePill(pillColor, seed = 13, backdrop = pageGlass)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = searchInBar,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    BarButton(Icons.Filled.Search, tr("Search library"), onClick = onSearch)
                }
                BarButton(Icons.Outlined.History, tr("History"), onClick = onOpenHistory)
                BarButton(Icons.Filled.Refresh, tr("Refresh all"), spinning = refreshing) {
                    scope.launch { store.refreshAll() }
                }
                BarButton(Icons.Filled.Add, tr("Add by RSS")) { showAdd = true }
            }
        }
    }

    if (showAdd) {
        AddFeedSheet(
            store = store,
            onDismiss = { showAdd = false }
        )
    }

    pendingDelete?.let { feed ->
        ConfirmSheet(
            artUrl = feed.imageUrl,
            title = feed.title,
            subtitle = feed.author,
            meta = store.episodesFor(feed.url).size.takeIf { it > 0 }?.let { tr("{0} EPISODES", it) },
            action = tr("Unsubscribe"),
            icon = Icons.Outlined.DeleteOutline,
            onConfirm = { scope.launch { store.unsubscribe(feed) } },
            onDismiss = { pendingDelete = null }
        )
    }
}

@Composable
private fun BarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    spinning: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (spinning) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(19.dp)
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShowTile(
    feed: Feed,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
    ) {
        // Sea Glass: rounder covers, each lying in a pool of its own color,
        // and the show's name in the serif.
        val sea = com.glasscast.app.ui.seaglass.LocalSea.current != null
        // 18dp in every look: about a ninth of the cover, the same share as
        // Discover's covers (16dp on 148dp). 12dp read as nearly square.
        Artwork(
            url = feed.imageUrl,
            sizeDp = 180.dp,
            corner = 18.dp,
            fill = true,
            modifier = Modifier.seaCoverPool(feed.imageUrl, 18.dp, strength = 0.9f)
        )
        Spacer(Modifier.height(if (sea) 12.dp else 9.dp))
        Text(
            text = feed.title,
            style = com.glasscast.app.ui.seaglass.rowTitleStyle(),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (feed.author.isNotBlank()) {
            Text(
                text = feed.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Continue Listening, at the top of Library, laid out the way Apple Music
 * lays out a shelf of songs: columns of three rows that page sideways, the
 * next column peeking in at the edge. Each row is the cover, the episode and
 * its show, and under them how far in you are (a line, and the time left),
 * with a menu button for the episode's actions; a hairline between rows
 * starts where the text does. Tapping a row carries on from where you were.
 * The grid's side margins are undone so the shelf runs to the screen's edges.
 */
@Composable
private fun ContinueListeningRow(
    episodes: List<com.glasscast.app.data.Episode>,
    feedFor: (com.glasscast.app.data.Episode) -> Feed?,
    onResume: (com.glasscast.app.data.Episode) -> Unit,
    onEpisodeActions: (com.glasscast.app.data.Episode) -> Unit
) {
    val sea = com.glasscast.app.ui.seaglass.LocalSea.current != null
    val haptics = rememberHaptics()
    val columns = remember(episodes) { episodes.chunked(3) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val corner = if (sea) 12.dp else 10.dp
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
    Column(Modifier.padding(bottom = 10.dp)) {
        // The section's name in the bold the other shelves use (Discover's).
        Text(
            text = tr("Continue Listening"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        androidx.compose.foundation.layout.BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .layout { measurable, constraints ->
                    // Out past the grid's 20dp margins, so the shelf pages edge to edge.
                    val extra = 20.dp.roundToPx()
                    val wide = constraints.maxWidth + extra * 2
                    val p = measurable.measure(constraints.copy(maxWidth = wide, minWidth = wide))
                    layout(constraints.maxWidth, p.height) { p.place(-extra, 0) }
                }
        ) {
            // A column is the screen less a peek of the next one; one column
            // alone takes the width.
            val columnWidth = if (columns.size > 1) maxWidth - 20.dp - 44.dp else maxWidth - 40.dp
            androidx.compose.foundation.lazy.LazyRow(
                state = listState,
                flingBehavior = androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior(
                    listState,
                    androidx.compose.foundation.gestures.snapping.SnapPosition.Start
                ),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(columns.size, key = { "continue-col:" + columns[it].first().guid }) { c ->
                    Column(Modifier.width(columnWidth)) {
                        columns[c].forEachIndexed { i, episode ->
                            val feed = feedFor(episode)
                            val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
                            val left = (episode.durationMs - episode.positionMs).coerceAtLeast(0L)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(86.dp)
                                    .clickable {
                                        haptics.play(Haptic.Resume)
                                        onResume(episode)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(url = art, sizeDp = 64.dp, corner = corner)
                                Spacer(Modifier.width(13.dp))
                                Box(Modifier.weight(1f).fillMaxHeight()) {
                                    Column(Modifier.align(Alignment.CenterStart)) {
                                        Text(
                                            text = episode.title,
                                            style = com.glasscast.app.ui.seaglass.rowTitleStyle(),
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = feed?.title.orEmpty(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(7.dp))
                                        // How far in: the played part of a short
                                        // line, and the time that's left.
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                Modifier
                                                    .width(56.dp)
                                                    .height(4.dp)
                                                    .clip(CircleShape)
                                                    .background(track)
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxWidth(episode.progress.coerceIn(0.05f, 1f))
                                                        .fillMaxHeight()
                                                        .clip(CircleShape)
                                                        .background(accent)
                                                )
                                            }
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = if (left > 0) tr("{0} left", formatCompact(left)) else formatCompact(episode.durationMs),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = accent,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                    if (i < columns[c].lastIndex) {
                                        Box(
                                            Modifier
                                                .align(Alignment.BottomStart)
                                                .fillMaxWidth()
                                                .height(0.5.dp)
                                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
                                        )
                                    }
                                }
                                Box(
                                    Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable { onEpisodeActions(episode) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = tr("More"),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(22.dp)
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

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = tr("Nothing here yet"),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = tr("Paste an RSS feed URL to add your first show. Directory search arrives in the next build."),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(20.dp))
        Pill(label = tr("Add a show"), active = true, onClick = onAdd)
    }
}


@Composable
private fun RestingSearchPill(onClick: () -> Unit) {
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .glassField(MaterialTheme.colorScheme.surfaceContainerHigh, 25.dp)
            // Sea Glass: a well in the paper, edged with a hairline.
            .then(if (tokens != null) Modifier.border(0.75.dp, tokens.hairline, RoundedCornerShape(25.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = tr("Search your library"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

