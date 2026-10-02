package com.glasscast.app.ui

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
    onOpenSettings: () -> Unit,
    /** Opens the library filter — the Search tab is where the internet lives. */
    onSearch: () -> Unit,
    hazeState: HazeState
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

    // Large title collapses into the bar. Fully faded by ~72dp of scroll.
    // Kept as a State and read in layers, not in composition: read directly,
    // it rebuilt this whole screen on every frame of the first 190px of scroll.
    val collapse = remember {
        androidx.compose.runtime.derivedStateOf {
            if (gridState.firstVisibleItemIndex > 0) 1f
            else (gridState.firstVisibleItemScrollOffset / 190f).coerceIn(0f, 1f)
        }
    }
    // The search field has scrolled away: its icon joins the pill. A boolean,
    // so this recomposes once at the crossing, not per frame.
    val searchInBar by remember { androidx.compose.runtime.derivedStateOf { collapse.value > 0.6f } }

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
            modifier = Modifier.fillMaxSize(),
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
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(14.dp))
                    // Below the title, not above it: the page should still say
                    // what it is before it offers to leave.
                    RestingSearchPill(onClick = onSearch)
                    Spacer(Modifier.height(18.dp))
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
                    .clip(RoundedCornerShape(percent = 50))
                    .background(pillColor)
                    .padding(horizontal = 20.dp, vertical = 11.dp)
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
                    .clip(RoundedCornerShape(percent = 50))
                    .background(pillColor)
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
                BarButton(Icons.Filled.Refresh, tr("Refresh all"), spinning = refreshing) {
                    scope.launch { store.refreshAll() }
                }
                BarButton(Icons.Filled.Add, tr("Add by RSS")) { showAdd = true }
                BarButton(Icons.Filled.Tune, tr("Settings"), onClick = onOpenSettings)
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
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(tr("Unsubscribe?"), style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    tr("{0} and its downloaded episode list will be removed.", feed.title),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { store.unsubscribe(feed) }
                    pendingDelete = null
                }) {
                    Text(tr("Unsubscribe"), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(tr("Cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
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
        Artwork(
            url = feed.imageUrl,
            sizeDp = 180.dp,
            corner = 12.dp,
            fill = true
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = feed.title,
            style = MaterialTheme.typography.titleSmall,
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
    Row(
        Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(25.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
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

