package com.glasscast.app.tv

import com.glasscast.app.data.Episode
import com.glasscast.app.ui.tr
import com.glasscast.app.data.EpisodeSort
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.glasscast.app.ui.CookieShape
import com.glasscast.app.ui.cookiePath
import com.glasscast.app.ui.chromeBar
import com.glasscast.app.ui.chromeButton
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Inbox
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.Settings
import com.glasscast.app.player.PlayerConnection
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.artworkGround
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.rememberArtworkColors
import kotlinx.coroutines.launch
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

private enum class TvTab(val label: String, val icon: ImageVector) {
    /**
     * Not a tab in the routing sense — selecting it opens the player. It lives
     * in the rail because on a TV the mini bar was the wrong idea: a strip
     * along the bottom is a thumb affordance, and reaching it by D-pad meant
     * traveling past everything else on the page. One rail stop, always in the
     * same place, is the whole interaction.
     */
    PLAYING(tr("Playing"), Icons.Filled.PlayArrow),
    LIBRARY(tr("Library"), Icons.Outlined.GridView),
    LATEST(tr("Latest"), Icons.Outlined.Inbox),
    DISCOVER(tr("Discover"), Icons.Outlined.Explore),
    SEARCH(tr("Search"), Icons.Filled.Search),
    SETTINGS(tr("Settings"), Icons.Filled.Tune)
}

/**
 * The TV shell: a nav rail on the left, content to the right, a now-playing bar
 * along the bottom, and the full player over everything.
 *
 * A rail rather than the phone's bottom bar. A remote moves in four directions,
 * and the horizontal axis is the cheap one — pressing left from anywhere in the
 * content lands on navigation, which is one press from anywhere on the screen.
 * A bottom bar would need a long run of Down presses through whatever list
 * happens to be open.
 */
@Composable
fun TvRoot(
    feedStore: FeedStore,
    settings: Settings,
    player: PlayerConnection
) {
    val feeds by feedStore.feeds.collectAsStateWithLifecycle()
    val episodeMap by feedStore.episodes.collectAsStateWithLifecycle()
    val refreshing by feedStore.refreshing.collectAsStateWithLifecycle()
    val sort by settings.sort.collectAsStateWithLifecycle()
    val showSort by settings.showSort.collectAsStateWithLifecycle()

    val nowPlaying by player.currentEpisode.collectAsStateWithLifecycle()
    val nowPlayingFeed by player.currentFeed.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    // Position is NOT collected here. It ticks every 0.4s while playing; read
    // at the shell it rebuilt the whole TV interface at that rhythm — on the
    // Streamer's chip, a steady stutter under every focus move. Only the
    // player reads it, inside TvWithPosition.
    val durationMs by player.durationMs.collectAsStateWithLifecycle()
    val upNext by player.upNext.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val discoverHidden by settings.discoverHidden.collectAsStateWithLifecycle()
    val episodeOrder by settings.episodeOrder.collectAsStateWithLifecycle()
    val skipSilence by settings.skipSilence.collectAsStateWithLifecycle()
    val voiceBoost by settings.voiceBoost.collectAsStateWithLifecycle()
    val skipAds by settings.skipAds.collectAsStateWithLifecycle()
    fun orderFor(url: String) = episodeOrder[url] ?: sort
    fun flipOrder(url: String): EpisodeSort {
        val next = if (orderFor(url) == EpisodeSort.NEWEST_FIRST) EpisodeSort.OLDEST_FIRST else EpisodeSort.NEWEST_FIRST
        settings.setEpisodeOrder(url, next)
        return next
    }

    // A show opened from Discover or Search is previewed, not followed:
    // Follow on its page adds it. (Opening one used to subscribe on the spot.)
    var previewUrl by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<com.glasscast.app.data.FeedFetch?>(null) }
    var menuFor by remember { mutableStateOf<Episode?>(null) }
    var showMenu by remember { mutableStateOf<TvShowMenuRequest?>(null) }
    var confirmUnfollow by remember { mutableStateOf<Feed?>(null) }
    val toast = remember { TvToastState() }
    // Skip ads says so when it skips, as the phone does.
    val adLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(adLifecycle) {
        adLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            com.glasscast.app.player.AdSkipper.skipped.collect { guid ->
                val ep = feedStore.episodeByGuid(guid)
                val art = ep?.let { it.imageUrl.ifBlank { feedStore.feedFor(it)?.imageUrl.orEmpty() } }.orEmpty()
                toast.show(tr("Ad skipped"), art)
            }
        }
    }
    // Sort is an icon on the show page now, so a flip says which way it went.
    fun toggleSort(url: String) {
        toast.show(if (flipOrder(url) == EpisodeSort.NEWEST_FIRST) tr("Newest first") else tr("Oldest first"))
    }
    // Kept as a State and read only inside the rail bubble's draw lambda — so
    // its ring advances without the shell ever recomposing.
    val positionState = player.positionMs.collectAsStateWithLifecycle()

    var tab by remember { mutableStateOf(TvTab.LIBRARY) }
    var selectedFeedUrl by remember { mutableStateOf<String?>(null) }
    var playerOpen by remember { mutableStateOf(false) }
    var railFocused by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val contentFocus = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }

    val selectedFeed = feeds.firstOrNull { it.url == selectedFeedUrl }
    fun openShow(url: String) {
        val known = feeds.firstOrNull { it.url.equals(url, ignoreCase = true) }
        if (known != null) {
            tab = TvTab.LIBRARY
            selectedFeedUrl = known.url
            previewUrl = null
        } else {
            previewUrl = url
        }
    }
    LaunchedEffect(previewUrl) {
        preview = null
        val url = previewUrl ?: return@LaunchedEffect
        val fetched = runCatching { feedStore.preview(url) }.getOrNull()
        if (fetched == null) {
            toast.show(tr("Couldn't open that show"))
            previewUrl = null
        } else {
            preview = fetched
        }
    }

    // The whole shell takes its color from whatever is playing, exactly as the
    // phone's show page does. On a large panel this matters more, not less.
    val art = nowPlaying?.imageUrl?.ifBlank { nowPlayingFeed?.imageUrl.orEmpty() }.orEmpty()
    val (colors, _) = rememberArtworkColors(art)

    val railWidth by animateDpAsState(
        targetValue = if (railFocused) TvSpacing.railExpanded else TvSpacing.railCollapsed,
        animationSpec = spring(),
        label = "railWidth"
    )

    // Updates: a quiet check at launch (at most twice a day); a dot on the
    // Settings stop when one is waiting.
    val updater = (androidx.compose.ui.platform.LocalContext.current.applicationContext
        as com.glasscast.app.GlassCastApp).updates
    val updateWaiting by updater.banner.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { updater.checkIfDue() }

    // Focus has to start somewhere. Without this the first D-pad press goes
    // nowhere and the screen looks frozen.
    LaunchedEffect(Unit) { railFocus.requestWhenReady() }

    // Claimed by the new screen a frame after the tab changes, rather than by
    // the rail during its own click — see requestWhenReady.
    // Focus goes to the page whenever what's on it changes — including a show
    // opened from Discover or Search, which is a preview, not a Library pick.
    // That case was missing: the clicked card vanished, nothing took focus,
    // and Android gave it to the only thing left on screen, the rail. Closing
    // the player drops focus the same way, so it's caught here too.
    LaunchedEffect(tab, selectedFeedUrl, previewUrl, preview != null, playerOpen) {
        if (!playerOpen) contentFocus.requestWhenReady()
    }

    // Brushes rebuilt only when the palette changes. Without remember these are
    // reallocated on every recomposition of the shell, which on TV means every
    // focus move.
    val ground = remember(colors) { artworkGround(colors) }
    // The rail is the phone tab bar's counterpart: a floating panel in the
    // cover's hue, a step darker than the page.
    val railSurface by animateColorAsState(colors.chromeBar, tween(600), label = "railSurface")
    val railAccent by animateColorAsState(colors.chromeButton, tween(600), label = "railAccent")
    val dim by animateFloatAsState(if (railFocused) 1f else 0f, tween(220), label = "railDim")

    val showPage = previewUrl != null || (tab == TvTab.LIBRARY && selectedFeed != null)
    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        // The browse pages move too: the now-playing cover's living blur,
        // darkened evenly (see TvCoverBackdrop). A show page draws its own, so
        // this one steps aside for it; nothing playing leaves the still ground.
        // Under the open player every backdrop holds still.
        CompositionLocalProvider(LocalTvBackdropMoving provides !playerOpen) {
        if (!showPage && art.isNotBlank()) {
            TvCoverBackdrop(url = art, colors = colors, even = true)
        }
        // A show page paints its own backdrop, so it's laid out edge to edge
        // and runs under the rail — otherwise the strip beside the rail stays
        // the shell's now-playing color while the page is another. Only its
        // content is inset. Every other page starts after the rail.
        //
        // The inset is the *collapsed* rail, and constant. It used to be the
        // rail's animated width, so every frame of the rail opening re-laid-out
        // the page under it — a five-column grid re-measured thirty times a
        // second. The expanded rail slides over the page instead.
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (showPage) 0.dp else TvSpacing.railCollapsed)
        ) {
            when {
                previewUrl != null -> {
                    val p = preview
                    if (p == null) {
                        // Holds focus while the show loads, so it can't fall to the rail.
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(start = TvSpacing.railCollapsed)
                                .focusRequester(contentFocus)
                                .focusable(),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(color = colors.chromeButton)
                        }
                    } else {
                        TvShowScreen(
                            feed = p.feed,
                            episodes = p.episodes,
                            sort = orderFor(p.feed.url),
                            refreshing = false,
                            hasNowPlaying = nowPlaying != null,
                            focusRequester = contentFocus,
                            onRefresh = {},
                            onPlay = { episode ->
                                player.play(episode, p.feed)
                                playerOpen = true
                            },
                            onEpisodeMenu = { menuFor = it },
                            playingGuid = nowPlaying?.guid,
                            subscribed = false,
                            onFollow = {
                                scope.launch {
                                    val reason = feedStore.subscribe(previewUrl ?: p.feed.url)
                                    if (reason == null) {
                                        toast.show(tr("Added to library"), p.feed.imageUrl)
                                        val added = feedStore.feeds.value.lastOrNull()
                                        tab = TvTab.LIBRARY
                                        selectedFeedUrl = added?.url ?: p.feed.url
                                        previewUrl = null
                                    } else {
                                        toast.show(reason)
                                    }
                                }
                            },
                            onToggleSort = { toggleSort(p.feed.url) },
                            leadingInset = TvSpacing.railCollapsed
                        )
                    }
                }
                tab == TvTab.LIBRARY && selectedFeed != null -> TvShowScreen(
                    feed = selectedFeed,
                    episodes = episodeMap[selectedFeed.url].orEmpty(),
                    sort = orderFor(selectedFeed.url),
                    refreshing = refreshing,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus,
                    onRefresh = { scope.launch { feedStore.refresh(selectedFeed) } },
                    onPlay = { episode ->
                        player.play(episode, feedStore.feedFor(episode))
                        playerOpen = true
                    },
                    onEpisodeMenu = { menuFor = it },
                    playingGuid = nowPlaying?.guid,
                    subscribed = true,
                    onUnfollow = { confirmUnfollow = selectedFeed },
                    onToggleSort = { toggleSort(selectedFeed.url) },
                    leadingInset = TvSpacing.railCollapsed
                )

                tab == TvTab.LIBRARY -> TvLibraryScreen(
                    feeds = feeds,
                    showSort = showSort,
                    latestAt = { feedStore.latestEpisodeAt(it) },
                    colors = colors,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus,
                    onOpenFeed = { selectedFeedUrl = it.url }
                )

                tab == TvTab.LATEST -> TvLatestScreen(
                    feeds = feeds,
                    episodeMap = episodeMap,
                    colors = colors,
                    refreshing = refreshing,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus,
                    onRefresh = { scope.launch { feedStore.refreshAll() } },
                    onPlay = { episode ->
                        player.play(episode, feedStore.feedFor(episode))
                        playerOpen = true
                    },
                    playingGuid = nowPlaying?.guid,
                    onEpisodeMenu = { menuFor = it }
                )

                tab == TvTab.DISCOVER -> TvDiscoverScreen(
                    feeds = feeds,
                    store = feedStore,
                    colors = colors,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus,
                    onSubscribed = { url -> openShow(url) },
                    hidden = discoverHidden,
                    onShowMenu = { showMenu = it }
                )

                tab == TvTab.SEARCH -> TvSearchScreen(
                    store = feedStore,
                    subscribed = feeds,
                    colors = colors,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus,
                    onSubscribed = { url -> openShow(url) }
                )

                tab == TvTab.SETTINGS -> TvSettingsScreen(
                    settings = settings,
                    sort = sort,
                    showSort = showSort,
                    colors = colors,
                    hasNowPlaying = nowPlaying != null,
                    focusRequester = contentFocus
                )

                // PLAYING opens the player rather than routing, so it never
                // reaches here; Library is the safe resting state.
                else -> Unit
            }
        }
        }

        // The page dims while the rail is open, so the panel reads as on top.
        // Alpha is applied in the layer, so the animation only redraws.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dim }
                .background(Color.Black.copy(alpha = 0.38f))
        )

        Box(
            Modifier
                .fillMaxHeight()
                .width(railWidth)
        ) {
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp)
                    .width(railWidth - 24.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(railSurface.copy(alpha = 0.96f))
                    .focusGroup()
                    .onFocusChanged { railFocused = it.hasFocus }
                    // Right always leads back to the page. requestFocus() doesn't
                    // say whether focus moved — only that the target exists — so
                    // this checks that the rail actually let go. If it didn't,
                    // the key isn't consumed and the ordinary move to the right
                    // gets its turn. (Consuming it regardless was the trap: on
                    // a show opened from Search or Discover, right did nothing.)
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && e.key == Key.DirectionRight) {
                            runCatching { contentFocus.requestFocus() }
                            !railFocused
                        } else {
                            false
                        }
                    }
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Nothing playing, no Playing entry — a stop that opens an
                // empty screen is worse than one that isn't there.
                val episode = nowPlaying
                if (episode != null) {
                    TvRailNowPlaying(
                        artworkUrl = episode.imageUrl.ifBlank { nowPlayingFeed?.imageUrl.orEmpty() },
                        title = episode.title,
                        show = nowPlayingFeed?.title.orEmpty(),
                        isPlaying = isPlaying,
                        progress = {
                            if (durationMs > 0) positionState.value.toFloat() / durationMs else 0f
                        },
                        expanded = railFocused,
                        accent = railAccent,
                        onClick = { playerOpen = true }
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 22.dp, vertical = 4.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.10f))
                    )
                }

                TvTab.entries.forEach { entry ->
                    if (entry == TvTab.PLAYING) return@forEach
                    TvRailItem(
                        label = entry.label,
                        icon = entry.icon,
                        badge = entry == TvTab.SETTINGS && updateWaiting != null,
                        selected = tab == entry,
                        expanded = railFocused,
                        accent = railAccent,
                        modifier = if (entry == TvTab.LIBRARY) {
                            Modifier.focusRequester(railFocus)
                        } else {
                            Modifier
                        },
                        onClick = {
                            if (tab == entry && entry == TvTab.LIBRARY) selectedFeedUrl = null
                            previewUrl = null
                            tab = entry
                        }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = playerOpen && nowPlaying != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160))
        ) {
            nowPlaying?.let { episode ->
                TvWithPosition(player) { positionMs ->
                    TvPlayerScreen(
                        episode = episode,
                        feed = nowPlayingFeed,
                        isPlaying = isPlaying,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        upNext = upNext,
                        feedFor = { feedStore.feedFor(it) },
                        speed = speed,
                        colors = colors,
                        onPlayPause = player::togglePlayPause,
                        onSeekBy = player::seekBy,
                        onSeekTo = player::seekTo,
                        onSkipNext = player::skipToNext,
                        onRestart = player::restartEpisode,
                        onSpeedChange = player::setSpeed,
                        onPlayFromUpNext = player::playFromUpNext,
                        onRemoveFromUpNext = { player.removeFromQueue(it.guid) },
                        onClose = { playerOpen = false },
                        skipSilence = skipSilence,
                        voiceBoost = voiceBoost,
                        onSkipSilenceChange = settings::setSkipSilence,
                        onVoiceBoostChange = settings::setVoiceBoost,
                        skipAds = skipAds,
                        onSkipAdsChange = settings::setSkipAds
                    )
                }
            }
        }

        menuFor?.let { ep ->
            val feed = feedStore.feedFor(ep) ?: preview?.feed?.takeIf { it.url == ep.feedUrl }
            val art = ep.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
            TvEpisodeMenu(
                episode = ep,
                feed = feed,
                colors = colors,
                onDismiss = { menuFor = null },
                onPlay = {
                    player.play(ep, feed)
                    playerOpen = true
                },
                onPlayNext = {
                    player.playNext(ep, feed)
                    toast.show(tr("Playing next"), art)
                },
                onAddToQueue = {
                    player.addToQueue(ep, feed)
                    toast.show(tr("Added to Up Next"), art)
                },
                onTogglePlayed = {
                    val played = ep.effectivelyPlayed
                    feedStore.setPlayed(ep, !played)
                    toast.show(if (played) tr("Marked as unplayed") else tr("Marked as played"), art)
                }
            )
        }
        confirmUnfollow?.let { feed ->
            TvConfirmDialog(
                title = tr("Unfollow?"),
                message = tr("{0} will be removed from your library.", feed.title),
                confirmLabel = tr("Unfollow"),
                colors = colors,
                onConfirm = {
                    scope.launch { feedStore.unsubscribe(feed) }
                    selectedFeedUrl = null
                    toast.show(tr("Removed from library"), feed.imageUrl)
                },
                onDismiss = { confirmUnfollow = null }
            )
        }
        showMenu?.let { req ->
            val result = req.result
            TvShowMenu(
                result = result,
                onDismiss = {
                    showMenu = null
                    req.onClosed(false)
                },
                onFollow = {
                    showMenu = null
                    req.onClosed(true)
                    scope.launch {
                        val reason = feedStore.subscribe(result.feedUrl)
                        if (reason == null) {
                            toast.show(tr("Added to library"), result.artworkUrl)
                        } else {
                            req.onRestore()
                            toast.show(reason)
                        }
                    }
                },
                onNotInterested = {
                    showMenu = null
                    req.onClosed(true)
                    settings.hideFromDiscover(result.feedUrl, result.title, result.artworkUrl)
                    toast.show(tr("Removed from recommendations"), result.artworkUrl)
                }
            )
        }
        TvToastHost(toast, accent = colors.chromeButton)
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && (previewUrl != null || selectedFeedUrl != null)) {
        if (previewUrl != null) previewUrl = null else selectedFeedUrl = null
    }
    BackHandler(enabled = !playerOpen && previewUrl == null && selectedFeedUrl == null && tab != TvTab.LIBRARY) {
        tab = TvTab.LIBRARY
    }
}

/**
 * A rail stop: the icon at rest, the icon and its label while the rail is
 * open. The current tab keeps a soft pill; focus adds the ring on top.
 */
@Composable
private fun TvRailItem(
    label: String,
    icon: ImageVector,
    badge: Boolean = false,
    selected: Boolean,
    expanded: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .tvFocusableRow(
                accent = accent,
                surface = Color.White,
                shape = RoundedCornerShape(percent = 50),
                onClick = onClick
            )
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .padding(horizontal = 9.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else Color.White.copy(alpha = 0.78f),
                modifier = Modifier.size(24.dp)
            )
            if (badge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accent else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * The phone's bubble, in the rail: the cover in the scalloped cookie shape,
 * with a progress ring tracing its outline. Open, it adds the title and show.
 *
 * The ring reads `progress` inside the draw lambda only, and its path and
 * PathMeasure are built once per size in drawWithCache — so the 0.4s position
 * tick redraws one small ring and recomposes nothing. It doesn't spin as the
 * phone's does: a perpetual animation would keep every browse screen
 * redrawing at 60fps for decoration.
 */
@Composable
private fun TvRailNowPlaying(
    artworkUrl: String,
    title: String,
    show: String,
    isPlaying: Boolean,
    progress: () -> Float,
    expanded: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp)
            .tvFocusableRow(
                accent = accent,
                surface = Color.White,
                shape = RoundedCornerShape(26.dp),
                onClick = onClick
            )
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .drawWithCache {
                    val strokePx = 3.dp.toPx()
                    val outline = cookiePath(size.width, size.height, inset = strokePx / 2f)
                    outline.close()
                    val measure = PathMeasure().apply { setPath(outline, true) }
                    val length = measure.length
                    val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                    val arc = Path()
                    onDrawWithContent {
                        drawContent()
                        drawPath(outline, Color.White.copy(alpha = 0.18f), style = stroke)
                        val fraction = progress().coerceIn(0f, 1f)
                        if (fraction > 0f) {
                            arc.reset()
                            measure.getSegment(0f, length * fraction, arc, true)
                            drawPath(arc, accent, style = stroke)
                        }
                    }
                }
        ) {
            Box(
                Modifier
                    .padding(6.dp)
                    .fillMaxSize()
                    .clip(CookieShape())
            ) {
                Artwork(url = artworkUrl, sizeDp = 48.dp, corner = 0.dp, fill = true)
            }
        }
        if (expanded) {
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (isPlaying) tr("NOW PLAYING") else tr("PAUSED"),
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = show,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** A recomposition boundary around the playback position; see TvRoot. */
@Composable
private fun TvWithPosition(
    player: PlayerConnection,
    content: @Composable (Long) -> Unit
) {
    val position by player.positionMs.collectAsStateWithLifecycle()
    content(position)
}
