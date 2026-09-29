package com.glasscast.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.Episode
import com.glasscast.app.data.FeedFetch
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.ImageStore
import com.glasscast.app.data.Settings
import com.glasscast.app.data.ShowSort
import com.glasscast.app.player.PlayerConnection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

private enum class Tab { LIBRARY, LATEST, DISCOVER, SEARCH }

/**
 * Where the content area is. [tab] orders the horizontal slide between tabs;
 * [depth] tells a push (into a show) from a pop (back out of one).
 */
private sealed interface Dest {
    val tab: Int
    val depth: Int
    val key: String

    data object Library : Dest { override val tab = 0; override val depth = 0; override val key = "library" }
    data class Show(val url: String) : Dest {
        override val tab = 0; override val depth = 1; override val key = "show:$url"
    }
    data object Latest : Dest { override val tab = 1; override val depth = 0; override val key = "latest" }
    data object Discover : Dest { override val tab = 2; override val depth = 0; override val key = "discover" }
    data object Search : Dest { override val tab = 3; override val depth = 0; override val key = "search" }
    /** A show you don't follow yet, opened from [tab] — Discover or Search. */
    data class Preview(val fetch: com.glasscast.app.data.FeedFetch, override val tab: Int) : Dest {
        override val depth = 1; override val key = "preview:${fetch.feed.url}"
    }
}

/** Near-critically damped: pages should arrive firmly, not bounce like the tab pill. */
private fun <T> pageSpring() = androidx.compose.animation.core.spring<T>(
    dampingRatio = 0.92f,
    stiffness = 380f
)

/**
 * Library and Search, a mini player welded to the top of the bar, and the full
 * player as a sheet over everything — Apple Podcasts' shape, drawn in Material.
 *
 * Up Next is not a tab: it belongs to what's playing, so it opens as a sheet
 * from the player alongside speed and the timer.
 *
 * Still plain state rather than navigation-compose: the whole graph is two tabs
 * plus one detail screen, and a nav library would add a dependency and a second
 * source of truth about what's on screen for no behaviour we need.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GlassCastRoot(
    feedStore: FeedStore,
    imageStore: ImageStore,
    settings: Settings,
    player: PlayerConnection,
    pendingOpml: android.net.Uri? = null,
    onOpmlHandled: () -> Unit = {},
    /** A show to open from a new-episode notification. */
    pendingOpenFeed: String? = null,
    onOpenFeedHandled: () -> Unit = {}
) {
    val feeds by feedStore.feeds.collectAsStateWithLifecycle()
    val episodeMap by feedStore.episodes.collectAsStateWithLifecycle()
    val refreshing by feedStore.refreshing.collectAsStateWithLifecycle()
    val sort by settings.sort.collectAsStateWithLifecycle()
    val showSort by settings.showSort.collectAsStateWithLifecycle()
    val hidePlayed by settings.hidePlayedInLatest.collectAsStateWithLifecycle()
    val hidePlayedInShows by settings.hidePlayedInShows.collectAsStateWithLifecycle()
    val shakeToRestart by settings.shakeToRestart.collectAsStateWithLifecycle()

    // The service reads this off SleepTimer rather than taking a dependency on
    // Settings, so it has to be mirrored across whenever it changes.
    LaunchedEffect(shakeToRestart) {
        com.glasscast.app.player.SleepTimer.setShakeEnabled(shakeToRestart)
    }
    val theme by settings.theme.collectAsStateWithLifecycle()

    val currentEpisode by player.currentEpisode.collectAsStateWithLifecycle()
    val currentFeed by player.currentFeed.collectAsStateWithLifecycle()
    val isPlaying by player.isPlaying.collectAsStateWithLifecycle()
    val buffering by player.buffering.collectAsStateWithLifecycle()
    // Position is deliberately NOT collected here. It ticks every 0.4s while
    // playing; read at this level it rebuilt every screen at that rhythm — a
    // steady hitch under every scroll. Only the three things that show it read
    // it, through WithPosition, so a tick recomposes them and nothing else.
    val durationMs by player.durationMs.collectAsStateWithLifecycle()
    val speed by player.speed.collectAsStateWithLifecycle()
    val upNext by player.upNext.collectAsStateWithLifecycle()

    var showSplash by remember { mutableStateOf(true) }

    /*
     * Android 13+ needs permission to post notifications. Asked once, after the
     * splash rather than over it, and only while new-episode notifications are
     * on. Denying leaves the setting on and simply silent; Settings says so.
     */
    val context = androidx.compose.ui.platform.LocalContext.current
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(showSplash) {
        if (showSplash) return@LaunchedEffect
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            settings.newEpisodeNotifications.value &&
            !settings.notificationPromptShown &&
            !com.glasscast.app.background.NewEpisodeNotifier.canPost(context)
        ) {
            settings.markNotificationPromptShown()
            notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    var tab by remember { mutableStateOf(Tab.LIBRARY) }
    val pageState = rememberSaveableStateHolder()
    var selectedFeedUrl by remember { mutableStateOf<String?>(null) }
    var playerOpen by remember { mutableStateOf(false) }
    // A notification tap lands on that show's page, over whatever was open.
    LaunchedEffect(pendingOpenFeed) {
        val url = pendingOpenFeed ?: return@LaunchedEffect
        playerOpen = false
        tab = Tab.LIBRARY
        selectedFeedUrl = url
        onOpenFeedHandled()
    }

    // The chrome takes the playing cover's hue. Animated, so moving to an
    // episode with different art re-tints the bar rather than snapping it.
    val nowArt = currentEpisode?.imageUrl?.ifBlank { currentFeed?.imageUrl.orEmpty() }.orEmpty()
    val (nowColors, _) = rememberArtworkColors(nowArt)
    val chromeTint by animateColorAsState(nowColors.chromeBar, tween(600), label = "chromeBar")

    // One confirmation pill for the whole app; every queueing path goes
    // through queueEpisode so none of them can forget to answer.
    val toast = remember { ToastState() }
    fun queueEpisode(episode: com.glasscast.app.data.Episode, next: Boolean) {
        val feed = feedStore.feedFor(episode)
        if (next) player.playNext(episode, feed) else player.addToQueue(episode, feed)
        toast.show(
            text = if (next) "Playing next" else "Added to Up Next",
            artUrl = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
        )
    }

    /*
     * The mini player gets out of the way while you scroll, the way Cider's
     * does: scrolling down collapses it to a bubble beside the tab bar,
     * scrolling back up restores it. Read from the nested-scroll stream rather
     * than any one list's state, so every screen gets it without knowing.
     *
     * The 8px dead band keeps a resting finger's jitter from flapping it.
     */
    var chromeCollapsed by remember { mutableStateOf(false) }
    LaunchedEffect(tab, selectedFeedUrl) { chromeCollapsed = false }
    val collapseOnScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                when {
                    available.y < -8f -> chromeCollapsed = true
                    available.y > 8f -> chromeCollapsed = false
                }
                return Offset.Zero
            }
        }
    }
    var settingsOpen by remember { mutableStateOf(false) }
    var actionEpisode by remember { mutableStateOf<Episode?>(null) }

    // A show being browsed from search, held outside the library until added.
    var preview by remember { mutableStateOf<FeedFetch?>(null) }
    // Which tab the preview was opened from. It belongs to that tab: switching
    // away and back should find it where you left it, not on the other tab.
    var previewTab by remember { mutableStateOf(Tab.SEARCH) }
    var previewingUrl by remember { mutableStateOf<String?>(null) }
    var searchAutoFocus by remember { mutableStateOf(false) }
    var librarySearchOpen by remember { mutableStateOf(false) }
    var opmlOpen by remember { mutableStateOf(false) }

    // Everything the glass samples from. Only the scrolling page is tagged —
    // tagging the chrome too would have the panels blurring each other.
    val hazeState = remember { HazeState() }
    var addingUrl by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val selectedFeed = feeds.firstOrNull { it.url == selectedFeedUrl }
    val bottomInset = glassBottomInset(miniPlayerVisible = currentEpisode != null)

    val orderedFeeds = remember(feeds, episodeMap, showSort) {
        when (showSort) {
            ShowSort.RECENTLY_UPDATED -> feeds.sortedByDescending { feedStore.latestEpisodeAt(it) }
            ShowSort.RECENTLY_PLAYED -> feeds.sortedByDescending { it.lastPlayedAt }
        }
    }

    // Also provided above the theme in MainActivity, for show-colours mode.
    // Providing it again here keeps this composable usable on its own.
    CompositionLocalProvider(LocalImageStore provides imageStore) {
        Crossfade(targetState = showSplash, animationSpec = tween(420), label = "splash") { splash ->
            if (splash) {
                SplashScreen(onFinished = { showSplash = false })
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.fillMaxSize()
                ) {
                    // One layout spanning every screen and the player, so a
                    // cover can leave one and land in another.
                    SharedTransitionLayout {
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    Box(Modifier.fillMaxSize()) {

                        Box(
                            Modifier
                                .fillMaxSize()
                                .nestedScroll(collapseOnScroll)
                                .hazeSource(hazeState)
                        ) {
                        val dest: Dest = when {
                            tab == Tab.LIBRARY && selectedFeed != null -> Dest.Show(selectedFeed.url)
                            tab == Tab.LIBRARY -> Dest.Library
                            tab == Tab.LATEST -> Dest.Latest
                            preview != null && tab == previewTab ->
                                Dest.Preview(preview!!, previewTab.ordinal)
                            tab == Tab.DISCOVER -> Dest.Discover
                            else -> Dest.Search
                        }

                        /*
                         * Pages move now instead of swapping.
                         *
                         * Between tabs, the page slides a quarter-width in the
                         * direction of travel — left to right follows the bar,
                         * so the motion agrees with the pill. Into a show it's a
                         * push from the right, and back is the reverse with the
                         * outgoing page on top, the way a stack should behave.
                         *
                         * Everything inside renders from `d`, the destination
                         * that page was created for — never the live state.
                         * During a transition the outgoing page is still drawn,
                         * and on the way back from a show the live selection is
                         * already null; reading it would draw nothing, or crash.
                         *
                         * The state holder keeps each page's saveable state, so
                         * the library is still scrolled where you left it after
                         * a round trip into a show.
                         */
                        AnimatedContent(
                            targetState = dest,
                            contentKey = { it.key },
                            transitionSpec = {
                                val from = initialState
                                val to = targetState
                                if (from.tab != to.tab) {
                                    val dir = if (to.tab > from.tab) 1 else -1
                                    (slideInHorizontally(pageSpring()) { w -> dir * w / 4 } +
                                        fadeIn(tween(220))) togetherWith
                                        (slideOutHorizontally(pageSpring()) { w -> -dir * w / 4 } +
                                            fadeOut(tween(140)))
                                } else {
                                    // Into and out of a show, the flying cover is
                                    // the motion to watch — so the pages only drift
                                    // a sixth of the width and cross-fade, rather
                                    // than a full push competing with it.
                                    val push = to.depth > from.depth
                                    (slideInHorizontally(pageSpring()) { w -> if (push) w / 6 else -w / 6 } +
                                        fadeIn(tween(260, delayMillis = 60))) togetherWith
                                        (slideOutHorizontally(pageSpring()) { w -> if (push) -w / 6 else w / 6 } +
                                            fadeOut(tween(180)))
                                }.apply {
                                    targetContentZIndex = if (to.depth >= from.depth) 1f else -1f
                                }
                            },
                            label = "page"
                        ) { d ->
                        // The page's own enter/exit scope, for covers that fly
                        // between a tile and the show page it opens.
                        CompositionLocalProvider(LocalNavScope provides this) {
                        pageState.SaveableStateProvider(d.key) {
                        when (d) {
                            is Dest.Show -> feeds.firstOrNull { it.url == d.url }?.let { shown -> FeedScreen(
                                feed = shown,
                                episodes = episodeMap[shown.url].orEmpty(),
                                sort = sort,
                                store = feedStore,
                                refreshing = refreshing,
                                bottomInset = bottomInset,
                                hidePlayed = hidePlayedInShows,
                                onHidePlayedChange = settings::setHidePlayedInShows,
                                onBack = { selectedFeedUrl = null },
                                onPlay = { episode ->
                                    player.play(episode, feedStore.feedFor(episode))
                                    playerOpen = true
                                },
                                onEpisodeActions = { actionEpisode = it },
                                onPlayNext = { queueEpisode(it, next = true) },
                                onAddToQueue = { queueEpisode(it, next = false) },
                                playingGuid = currentEpisode?.guid,
                                isPlaying = isPlaying,
                                hazeState = hazeState
                            ) }

                            Dest.Library -> SubscriptionsScreen(
                                store = feedStore,
                                feeds = orderedFeeds,
                                refreshing = refreshing,
                                bottomInset = bottomInset,
                                onOpenFeed = { selectedFeedUrl = it.url },
                                onOpenSettings = { settingsOpen = true },
                                onSearch = { librarySearchOpen = true },
                                hazeState = hazeState
                            )

                            Dest.Latest -> LatestScreen(
                                feeds = feeds,
                                episodeMap = episodeMap,
                                bottomInset = bottomInset,
                                hidePlayed = hidePlayed,
                                onHidePlayedChange = settings::setHidePlayedInLatest,
                                refreshing = refreshing,
                                onRefresh = { scope.launch { feedStore.refreshAll() } },
                                onPlay = { episode ->
                                    player.play(episode, feedStore.feedFor(episode))
                                    playerOpen = true
                                },
                                onEpisodeActions = { actionEpisode = it },
                                onPlayNext = { queueEpisode(it, next = true) },
                                onAddToQueue = { queueEpisode(it, next = false) },
                                playingGuid = currentEpisode?.guid,
                                isPlaying = isPlaying
                            )

                            is Dest.Preview -> {
                                val previewed = d.fetch
                                val alreadyIn = feeds.any { it.url.equals(previewed.feed.url, true) }
                                FeedScreen(
                                    feed = previewed.feed,
                                    episodes = previewed.episodes,
                                    sort = sort,
                                    store = feedStore,
                                    refreshing = false,
                                    bottomInset = bottomInset,
                                    subscribed = alreadyIn,
                                    subscribing = addingUrl == previewed.feed.url,
                                    onSubscribe = {
                                        addingUrl = previewed.feed.url
                                        scope.launch {
                                            feedStore.subscribe(previewed.feed.url)
                                            addingUrl = null
                                        }
                                    },
                                    onBack = { preview = null },
                                    onPlay = { episode ->
                                        // Playing does commit — the queue and the
                                        // resume position need somewhere to live.
                                        scope.launch {
                                            if (!alreadyIn) feedStore.subscribe(previewed.feed.url)
                                            val stored = feedStore.episodeByGuid(episode.guid) ?: episode
                                            player.play(stored, feedStore.feedFor(stored))
                                            playerOpen = true
                                        }
                                    },
                                    onEpisodeActions = { /* library-only actions */ },
                                    playingGuid = currentEpisode?.guid,
                                    isPlaying = isPlaying,
                                    hazeState = hazeState
                                )
                            }

                            Dest.Discover -> DiscoverScreen(
                                feeds = feeds,
                                store = feedStore,
                                bottomInset = bottomInset,
                                previewingUrl = previewingUrl,
                                onPreview = { result ->
                                    previewingUrl = result.feedUrl
                                    scope.launch {
                                        val fetched = feedStore.preview(result.feedUrl)
                                        previewingUrl = null
                                        if (fetched != null) {
                                            previewTab = Tab.DISCOVER
                                            preview = fetched
                                        }
                                    }
                                }
                            )

                            Dest.Search -> SearchScreen(
                                store = feedStore,
                                subscribed = feeds,
                                bottomInset = bottomInset,
                                // One spinner for both: from the row's point of
                                // view, fetching to browse and fetching to add
                                // look the same.
                                addingUrl = addingUrl ?: previewingUrl,
                                autoFocus = searchAutoFocus,
                                onAutoFocusHandled = { searchAutoFocus = false },
                                hazeState = hazeState,
                                onPreviewFeed = { result ->
                                    previewingUrl = result.feedUrl
                                    scope.launch {
                                        previewTab = Tab.SEARCH
                                        preview = feedStore.preview(result.feedUrl)
                                        previewingUrl = null
                                    }
                                },
                                onAdd = { result ->
                                    addingUrl = result.feedUrl
                                    scope.launch {
                                        feedStore.subscribe(result.feedUrl)
                                        addingUrl = null
                                    }
                                }
                            )
                        }
                        }
                        }
                        }


                        }

                        // Chrome floats over the page, outside the haze source.
                        BottomGlassFade(
                            hazeState = hazeState,
                            pageColor = MaterialTheme.colorScheme.background,
                            height = bottomInset + 20.dp,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                        ) {
                            val collapsed = chromeCollapsed && currentEpisode != null

                            // The card folds down toward its bottom-right, which
                            // is where the bubble appears — so the two read as
                            // one object changing shape rather than a swap.
                            currentEpisode?.let { episode ->
                                AnimatedVisibility(
                                    visible = !collapsed && !playerOpen,
                                    enter = expandVertically(
                                        expandFrom = Alignment.Bottom,
                                        animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
                                    ) + fadeIn() + scaleIn(
                                        initialScale = 0.7f,
                                        transformOrigin = TransformOrigin(1f, 1f)
                                    ),
                                    exit = shrinkVertically(
                                        shrinkTowards = Alignment.Bottom,
                                        animationSpec = spring(dampingRatio = 0.9f, stiffness = 600f)
                                    ) + fadeOut() + scaleOut(
                                        targetScale = 0.6f,
                                        transformOrigin = TransformOrigin(1f, 1f)
                                    )
                                ) {
                                    CompositionLocalProvider(LocalPlayerScope provides this) {
                                    WithPosition(player) { positionMs ->
                                    MiniPlayer(
                                        episode = episode,
                                        feed = currentFeed,
                                        isPlaying = isPlaying,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        onPlayPause = player::togglePlayPause,
                                        onSkipForward = { player.seekBy(30_000) },
                                        hazeState = hazeState,
                                        onOpen = { playerOpen = true },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    }
                                    }
                                }
                            }

                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTabBar(
                                compact = collapsed,
                                modifier = Modifier.weight(1f),
                                tabs = listOf(
                                    GlassTab("Library", Icons.Outlined.GridView),
                                    GlassTab("Latest", Icons.Outlined.Inbox),
                                    GlassTab("Discover", Icons.Outlined.Explore),
                                    GlassTab("Search", Icons.Filled.Search)
                                ),
                                selectedIndex = tab.ordinal,
                                tint = chromeTint,
                                hazeState = hazeState,
                                onTabSelected = { index ->
                                    when (index) {
                                        0 -> {
                                            if (tab == Tab.LIBRARY) selectedFeedUrl = null
                                            tab = Tab.LIBRARY
                                        }
                                        1 -> tab = Tab.LATEST
                                        2 -> {
                                            // Re-tapping Discover while in a show
                                            // it opened goes back to the shelves.
                                            if (tab == Tab.DISCOVER && previewTab == Tab.DISCOVER) preview = null
                                            tab = Tab.DISCOVER
                                        }
                                        else -> {
                                            if (tab == Tab.SEARCH && previewTab == Tab.SEARCH) preview = null
                                            tab = Tab.SEARCH
                                        }
                                    }
                                }
                            )

                                // Grows in from nothing as the bar narrows to
                                // make room: expandHorizontally is what animates
                                // the tab bar's width, the scale is the pop.
                                currentEpisode?.let { episode ->
                                    AnimatedVisibility(
                                        visible = collapsed && !playerOpen,
                                        enter = expandHorizontally(
                                            expandFrom = Alignment.Start,
                                            animationSpec = spring(dampingRatio = 0.8f, stiffness = 500f)
                                        ) + scaleIn(
                                            initialScale = 0.3f,
                                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f)
                                        ) + fadeIn(),
                                        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) +
                                            scaleOut(targetScale = 0.3f) + fadeOut()
                                    ) {
                                        CompositionLocalProvider(LocalPlayerScope provides this) {
                                        WithPosition(player) { positionMs ->
                                        MiniBubble(
                                            episode = episode,
                                            feed = currentFeed,
                                            isPlaying = isPlaying,
                                            progress = if (durationMs > 0) {
                                                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                                            } else {
                                                0f
                                            },
                                            accent = MaterialTheme.colorScheme.primary,
                                            track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                            onOpen = { playerOpen = true },
                                            modifier = Modifier.padding(end = GlassGutter, bottom = 8.dp)
                                        )
                                        }
                                        }
                                    }
                                }
                            }
                        }

                        // At the top, clear of the mini player: a confirmation
                        // about the queue shouldn't cover the thing it's about.
                        ToastHost(
                            state = toast,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 10.dp)
                        )
                    }

                    /*
                     * The player no longer slides up from the bottom edge. It
                     * fades in behind a cover that *flies*: the mini player's
                     * artwork and the full player's share a key, so the cover
                     * grows out of the card (or the bubble) into place and
                     * shrinks back on the way out — Cider's expand. The fade
                     * is quick so the flight is what you watch.
                     */
                    AnimatedVisibility(
                        visible = playerOpen && currentEpisode != null,
                        enter = fadeIn(tween(240)),
                        exit = fadeOut(tween(200))
                    ) {
                        CompositionLocalProvider(LocalPlayerScope provides this) {
                        currentEpisode?.let { episode ->
                            WithPosition(player) { positionMs ->
                            PlayerScreen(
                                episode = episode,
                                feed = currentFeed,
                                isPlaying = isPlaying,
                                buffering = buffering,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                speed = speed,
                                onPlayPause = player::togglePlayPause,
                                onSeekTo = player::seekTo,
                                onSeekBy = player::seekBy,
                                onSpeedChange = player::setSpeed,
                                upNextCount = upNext.size,
                                upNext = upNext,
                                feedFor = { feedStore.feedFor(it) },
                                onPlayFromUpNext = player::playFromUpNext,
                                onRemoveFromQueue = { player.removeFromQueue(it.guid) },
                                onClearQueue = player::clearUpNext,
                                onRestartEpisode = player::restartEpisode,
                                onSkipNext = player::skipToNext,
                                shakeToRestart = shakeToRestart,
                                onShakeToggle = settings::setShakeToRestart,
                                onCollapse = { playerOpen = false }
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

        if (settingsOpen) {
            SettingsSheet(
                settings = settings,
                theme = theme,
                sort = sort,
                showSort = showSort,
                onOpenOpml = {
                    settingsOpen = false
                    opmlOpen = true
                },
                onDismiss = { settingsOpen = false }
            )
        }

        // A shared OPML opens this by itself — arriving from AntennaPod's share
        // sheet should land on the import, not on the library.
        if (opmlOpen || pendingOpml != null) {
            OpmlSheet(
                store = feedStore,
                pendingUri = pendingOpml,
                onDismiss = {
                    opmlOpen = false
                    onOpmlHandled()
                }
            )
        }

        if (librarySearchOpen) {
            LibrarySearchSheet(
                feeds = feeds,
                episodeMap = episodeMap,
                onOpenFeed = { selectedFeedUrl = it.url },
                onPlayEpisode = { episode ->
                    player.play(episode, feedStore.feedFor(episode))
                    playerOpen = true
                },
                onDismiss = { librarySearchOpen = false }
            )
        }

        actionEpisode?.let { episode ->
            EpisodeActionsSheet(
                episode = episode,
                queued = player.isQueued(episode.guid),
                onPlay = {
                    player.play(episode, feedStore.feedFor(episode))
                    playerOpen = true
                },
                onPlayNext = { queueEpisode(episode, next = true) },
                onAddToQueue = { queueEpisode(episode, next = false) },
                onRemoveFromQueue = { player.removeFromQueue(episode.guid) },
                onTogglePlayed = { feedStore.setPlayed(episode, !episode.effectivelyPlayed) },
                onDismiss = { actionEpisode = null }
            )
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && selectedFeedUrl != null) { selectedFeedUrl = null }
    val inPreview = preview != null && tab == previewTab
    BackHandler(enabled = !playerOpen && tab != Tab.LIBRARY && inPreview) { preview = null }
    BackHandler(
        enabled = !playerOpen && selectedFeedUrl == null && !inPreview && tab != Tab.LIBRARY
    ) { tab = Tab.LIBRARY }
}

/**
 * A recomposition boundary around the playback position. Whatever is inside
 * the lambda re-runs on each position tick; the caller does not.
 */
@Composable
private fun WithPosition(
    player: com.glasscast.app.player.PlayerConnection,
    content: @Composable (Long) -> Unit
) {
    val position by player.positionMs.collectAsStateWithLifecycle()
    content(position)
}
