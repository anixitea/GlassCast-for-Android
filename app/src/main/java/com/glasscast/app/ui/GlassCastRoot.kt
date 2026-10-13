package com.glasscast.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.outlined.LibraryAddCheck
import com.glasscast.app.data.EpisodeSort
import com.glasscast.app.data.CrashLog
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.clickable
import kotlin.math.roundToInt
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Done
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.outlined.Settings
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
import kotlinx.coroutines.flow.first
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.foundation.layout.widthIn

private enum class Tab { LIBRARY, LATEST, DOWNLOADS, DISCOVER, SEARCH, SETTINGS }

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
    data object Downloads : Dest { override val tab = 2; override val depth = 0; override val key = "downloads" }
    data object Discover : Dest { override val tab = 3; override val depth = 0; override val key = "discover" }
    data object Search : Dest { override val tab = 4; override val depth = 0; override val key = "search" }
    data object Settings : Dest { override val tab = 5; override val depth = 0; override val key = "settings" }
    /** A show you don't follow yet, opened from [tab] — Discover or Search. */
    data class Preview(val fetch: com.glasscast.app.data.FeedFetch, override val tab: Int) : Dest {
        override val depth = 1; override val key = "preview:${fetch.feed.url}"
    }
}

/** The mini player card's box (MiniPlayer). */
private val MiniPlayerHeight = 76.dp

/** The bar's strip above the navigation bar: the 60dp pill, 8dp under it, 8dp of air over it. */
private val TabBarStripHeight = 76.dp

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
 * source of truth about what's on screen for no behavior we need.
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
    onOpenFeedHandled: () -> Unit = {},
    /** A launcher shortcut or the widget's card (see Shortcuts). */
    pendingShortcut: String? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val feeds by feedStore.feeds.collectAsStateWithLifecycle()
    val episodeMap by feedStore.episodes.collectAsStateWithLifecycle()
    val refreshing by feedStore.refreshing.collectAsStateWithLifecycle()
    val sort by settings.sort.collectAsStateWithLifecycle()
    val showSort by settings.showSort.collectAsStateWithLifecycle()
    val hidePlayed by settings.hidePlayedInLatest.collectAsStateWithLifecycle()
    val hidePlayedInShows by settings.hidePlayedInShows.collectAsStateWithLifecycle()
    val shakeToRestart by settings.shakeToRestart.collectAsStateWithLifecycle()
    val skipSilence by settings.skipSilence.collectAsStateWithLifecycle()
    val discoverHidden by settings.discoverHidden.collectAsStateWithLifecycle()
    val episodeOrder by settings.episodeOrder.collectAsStateWithLifecycle()
    fun orderFor(url: String) = episodeOrder[url] ?: sort
    fun flipOrder(url: String) = settings.setEpisodeOrder(
        url,
        if (orderFor(url) == EpisodeSort.NEWEST_FIRST) EpisodeSort.OLDEST_FIRST else EpisodeSort.NEWEST_FIRST
    )
    val voiceBoost by settings.voiceBoost.collectAsStateWithLifecycle()
    val skipAds by settings.skipAds.collectAsStateWithLifecycle()
    val skipSeconds by settings.skipSeconds.collectAsStateWithLifecycle()
    val glassCastApp = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.glasscast.app.GlassCastApp
    val bookmarkStore = glassCastApp.bookmarks
    val allBookmarks by bookmarkStore.all.collectAsStateWithLifecycle()
    val autoDownloadShows by settings.autoDownload.collectAsStateWithLifecycle()
    val adaptiveTabBar by settings.adaptiveTabBar.collectAsStateWithLifecycle()

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

    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as com.glasscast.app.GlassCastApp

    // The launch mark: drawn over the app until it has faded, once per
    // process (see GlassCastApp.splashPlayed). "Opened" is when it starts to
    // fade; anything that waits for the app to be showing keys off that.
    var splashDone by remember { mutableStateOf(app.splashPlayed) }
    var opened by remember { mutableStateOf(app.splashPlayed) }

    /*
     * Android 13+ needs permission to post notifications. Asked once, after the
     * splash rather than over it, and only while new-episode notifications are
     * on. Denying leaves the setting on and simply silent; Settings says so.
     */
    val updater = app.updates
    val downloadStore = (context.applicationContext as com.glasscast.app.GlassCastApp).downloads
    val gpodder = (context.applicationContext as com.glasscast.app.GlassCastApp).gpodder
    val syncStatus by gpodder.status.collectAsStateWithLifecycle()
    var syncOpen by remember { mutableStateOf(false) }
    val downloads by downloadStore.entries.collectAsStateWithLifecycle()
    val updateBanner by updater.banner.collectAsStateWithLifecycle()
    var updateSheetOpen by remember { mutableStateOf(false) }
    // A quiet check once the app has opened — at most twice a day.
    LaunchedEffect(opened) {
        if (opened) updater.checkIfDue()
    }
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(opened) {
        if (!opened) return@LaunchedEffect
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
    // Launcher shortcuts and the widget: a tab, or the player.
    LaunchedEffect(pendingShortcut) {
        val action = pendingShortcut ?: return@LaunchedEffect
        when (action) {
            com.glasscast.app.Shortcuts.RESUME, com.glasscast.app.Shortcuts.OPEN_PLAYER -> {
                if (action == com.glasscast.app.Shortcuts.RESUME) player.resume()
                // From a cold start the episode appears once the library has
                // loaded; nothing saved, and the app just opens.
                val episode = kotlinx.coroutines.withTimeoutOrNull(3_000) {
                    player.currentEpisode.first { it != null }
                }
                if (episode != null) playerOpen = true
            }
            else -> {
                playerOpen = false
                tab = when (action) {
                    com.glasscast.app.Shortcuts.LATEST -> Tab.LATEST
                    com.glasscast.app.Shortcuts.SEARCH -> Tab.SEARCH
                    else -> Tab.DOWNLOADS
                }
            }
        }
        onShortcutHandled()
    }

    // The chrome takes the playing cover's hue. Animated, so moving to an
    // episode with different art re-tints the bar rather than snapping it.
    val nowArt = currentEpisode?.imageUrl?.ifBlank { currentFeed?.imageUrl.orEmpty() }.orEmpty()
    val (nowColors, _) = rememberArtworkColors(nowArt)
    // States, read only where they're drawn: a cover change or a page change
    // fades the bar without recomposing the whole app every frame.
    val chromeTint = animateColorAsState(nowColors.chromeBar, tween(600), label = "chromeBar")
    val chromeTintPale = animateColorAsState(nowColors.chromeBarPale, tween(600), label = "chromeBarPale")

    // One confirmation pill for the whole app; every queueing path goes
    // through queueEpisode so none of them can forget to answer.
    val toast = remember { ToastState() }
    // Skip ads says so when it skips — only while the app is on screen; the
    // skip itself happens in the service either way.
    val adLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(adLifecycle) {
        adLifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            com.glasscast.app.player.AdSkipper.skipped.collect { guid ->
                val ep = feedStore.episodeByGuid(guid)
                val art = ep?.let { it.imageUrl.ifBlank { feedStore.feedFor(it)?.imageUrl.orEmpty() } }.orEmpty()
                toast.show(text = tr("Ad skipped"), artUrl = art, icon = Icons.Filled.FastForward)
            }
        }
    }
    // Right-swipe: flip played, and say which way it went.
    fun togglePlayed(episode: com.glasscast.app.data.Episode) {
        val nowPlayed = !episode.effectivelyPlayed
        feedStore.setPlayed(episode, nowPlayed)
        toast.show(
            text = if (nowPlayed) tr("Marked as played") else tr("Marked as unplayed"),
            artUrl = episode.imageUrl.ifBlank { feedStore.feedFor(episode)?.imageUrl.orEmpty() },
            icon = if (nowPlayed) Icons.Filled.Done else Icons.Filled.RemoveDone
        )
    }

    fun queueEpisode(episode: com.glasscast.app.data.Episode, next: Boolean) {
        val feed = feedStore.feedFor(episode)
        if (next) player.playNext(episode, feed) else player.addToQueue(episode, feed)
        toast.show(
            text = if (next) tr("Playing next") else tr("Added to Up Next"),
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
    // Stays as it is across pages: bubble on Library stays a bubble on
    // Discover. Only scrolling changes it (it used to reset to the card on
    // every page change).
    var chromeCollapsed by remember { mutableStateOf(false) }
    /*
     * Collapse on a deliberate scroll, not a twitch. This flipped on any 8px of
     * movement, so nudging a short list back and forth swapped card and bubble
     * again and again — a full transition each time. Now it takes ~48dp of
     * travel in one direction, and changing direction starts the count over.
     */
    val collapseThreshold = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.toPx() }
    val collapseOnScroll = remember(collapseThreshold) {
        object : NestedScrollConnection {
            var travel = 0f
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if ((dy < 0f && travel > 0f) || (dy > 0f && travel < 0f)) travel = 0f
                travel += dy
                if (travel < -collapseThreshold) {
                    chromeCollapsed = true
                    travel = 0f
                } else if (travel > collapseThreshold) {
                    chromeCollapsed = false
                    travel = 0f
                }
                return Offset.Zero
            }
        }
    }
    // Card ⇄ bubble as one animated value, read only while placing and drawing
    // (see MiniPlayer). The bar's measured bounds place the bubble on it.
    val chromeIsCollapsed = chromeCollapsed && currentEpisode != null
    // Critically damped: it must never overshoot. An underdamped spring dipped
    // just below 0 on the way back, the bubble slot below read that as a
    // negative width, and Compose crashed on it.
    val collapseAnim = animateFloatAsState(
        targetValue = if (chromeIsCollapsed) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f),
        label = "chromeCollapse"
    )
    var barBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

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

    /*
     * The mini player contrasts with the page under it, as Cider's does: dark
     * over a light page, pale over a dark one. The page is the one actually
     * showing: a show (or a preview) has its own ground from its cover, every
     * other tab the app's. [chromeMix] crossfades between the two versions when
     * the page changes. The tab bar stays dark unless Adaptive tab bar is on
     * (Settings > Appearance); then it moves with the card.
     */
    val pageArt = when {
        tab == Tab.LIBRARY && selectedFeed != null -> selectedFeed.imageUrl
        preview != null && tab == previewTab -> preview?.feed?.imageUrl
        else -> null
    }
    val (pageColors, _) = rememberArtworkColors(pageArt.orEmpty())
    val pageGround = if (pageArt != null) pageColors.background else MaterialTheme.colorScheme.background
    val chromeMix = animateFloatAsState(
        targetValue = if (pageGround.luminance() < 0.4f) 1f else 0f,
        animationSpec = tween(450),
        label = "chromeMix"
    )

    val orderedFeeds = remember(feeds, episodeMap, showSort) {
        when (showSort) {
            ShowSort.RECENTLY_UPDATED -> feeds.sortedByDescending { feedStore.latestEpisodeAt(it) }
            ShowSort.RECENTLY_PLAYED -> feeds.sortedByDescending { it.lastPlayedAt }
        }
    }

    // Which screen is up, for a crash report (CrashLog.noteState): a hang
    // report then says where it happened.
    val inPreviewNow = preview != null && tab == previewTab
    LaunchedEffect(tab, selectedFeedUrl != null, inPreviewNow, playerOpen, isPlaying, splashDone) {
        CrashLog.noteState(
            context,
            buildString {
                append("phone ").append(tab.name.lowercase())
                if (selectedFeedUrl != null && tab == Tab.LIBRARY) append(" show")
                if (inPreviewNow) append(" preview")
                if (playerOpen) append(" player")
                if (isPlaying) append(" playing")
                if (!splashDone) append(" splash")
            }
        )
    }

    // Also provided above the theme in MainActivity, for show-colors mode.
    // Providing it again here keeps this composable usable on its own.
    CompositionLocalProvider(LocalImageStore provides imageStore, LocalToast provides toast) {
        Box(Modifier.fillMaxSize()) {
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
                            tab == Tab.DOWNLOADS -> Dest.Downloads
                            tab == Tab.DISCOVER -> Dest.Discover
                            tab == Tab.SETTINGS -> Dest.Settings
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
                                sort = orderFor(shown.url),
                                onToggleSort = { flipOrder(shown.url) },
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
                                onTogglePlayed = { togglePlayed(it) },
                                playingGuid = currentEpisode?.guid,
                                isPlaying = isPlaying,
                                hazeState = hazeState,
                                downloads = downloads,
                                autoDownload = shown.url in autoDownloadShows,
                                onAutoDownloadChange = { on ->
                                    settings.setAutoDownload(shown.url, on)
                                    // An episode from the last half day starts now (on Wi-Fi).
                                    if (on) scope.launch { glassCastApp.autoDownload.run() }
                                }
                            ) }

                            Dest.Library -> SubscriptionsScreen(
                                store = feedStore,
                                feeds = orderedFeeds,
                                refreshing = refreshing,
                                bottomInset = bottomInset,
                                onOpenFeed = { selectedFeedUrl = it.url },
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
                                onTogglePlayed = { togglePlayed(it) },
                                playingGuid = currentEpisode?.guid,
                                isPlaying = isPlaying,
                                downloads = downloads
                            )

                            is Dest.Preview -> {
                                val previewed = d.fetch
                                val alreadyIn = feeds.any { it.url.equals(previewed.feed.url, true) }
                                FeedScreen(
                                    feed = previewed.feed,
                                    episodes = previewed.episodes,
                                    sort = orderFor(previewed.feed.url),
                                    onToggleSort = { flipOrder(previewed.feed.url) },
                                    store = feedStore,
                                    refreshing = false,
                                    bottomInset = bottomInset,
                                    subscribed = alreadyIn,
                                    subscribing = addingUrl == previewed.feed.url,
                                    onSubscribe = {
                                        addingUrl = previewed.feed.url
                                        scope.launch {
                                            val reason = feedStore.subscribe(previewed.feed.url)
                                            addingUrl = null
                                            if (reason == null) toast.show(tr("Added to library"), previewed.feed.imageUrl, Icons.Outlined.LibraryAddCheck)
                                        }
                                    },
                                    onBack = { preview = null },
                                    onPlay = { episode ->
                                        // Playing does commit — the queue and the
                                        // resume position need somewhere to live.
                                        scope.launch {
                                            if (!alreadyIn && feedStore.subscribe(previewed.feed.url) == null) {
                                                toast.show(tr("Added to library"), previewed.feed.imageUrl, Icons.Outlined.LibraryAddCheck)
                                            }
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

                            Dest.Downloads -> DownloadsScreen(
                                downloads = downloads,
                                episodeMap = episodeMap,
                                feeds = feeds,
                                bottomInset = bottomInset,
                                playingGuid = currentEpisode?.guid,
                                onPlay = { episode ->
                                    player.play(episode, feedStore.feedFor(episode))
                                    playerOpen = true
                                },
                                onRemove = { guid -> downloadStore.remove(guid) }
                            )

                            Dest.Discover -> DiscoverScreen(
                                feeds = feeds,
                                store = feedStore,
                                bottomInset = bottomInset,
                                previewingUrl = previewingUrl,
                                dismissed = discoverHidden,
                                onNotInterested = { result ->
                                    settings.hideFromDiscover(result.feedUrl, result.title, result.artworkUrl)
                                    toast.show(
                                        text = tr("You won't see this in Discover again"),
                                        artUrl = result.artworkUrl,
                                        icon = Icons.Outlined.ThumbDown
                                    )
                                },
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

                            Dest.Settings -> SettingsScreen(
                                settings = settings,
                                appVersion = updater.currentVersion,
                                onCheckUpdates = {
                                    if (updater.state.value !is com.glasscast.app.update.UpdateState.Available) {
                                        updater.check(manual = true)
                                    }
                                    updateSheetOpen = true
                                },
                                onOpenOpml = { opmlOpen = true },
                                onOpenSync = { syncOpen = true },
                                syncConnected = syncStatus.connected,
                                bottomInset = bottomInset,
                                hazeState = hazeState
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
                                        val reason = feedStore.subscribe(result.feedUrl)
                                        addingUrl = null
                                        if (reason == null) toast.show(tr("Added to library"), result.artworkUrl, Icons.Outlined.LibraryAddCheck)
                                    }
                                }
                            )
                        }
                        }
                        }
                        }


                        }

                        // Chrome floats over the page, outside the haze source.
                        // The blur reaches exactly as high as the chrome: the
                        // bar's strip always, the card only while it's a card,
                        // shrinking with the collapse. It used to stay card-tall
                        // once the card had become the bubble, leaving a band of
                        // frosted page above the bar with nothing in front of it.
                        val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                        val miniVisible = currentEpisode != null && !playerOpen
                        BottomGlassFade(
                            hazeState = hazeState,
                            pageColor = MaterialTheme.colorScheme.background,
                            height = {
                                val card = if (miniVisible) MiniPlayerHeight * (1f - collapseAnim.value.coerceIn(0f, 1f)) else 0.dp
                                navInset + TabBarStripHeight + card
                            },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )

                        // On a tablet the mini player and tab bar stop at 640dp,
                        // centered, rather than spanning the screen. The bubble
                        // is placed from the bar's bounds in root coordinates,
                        // so it follows.
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = 640.dp)
                                .fillMaxWidth()
                        ) {
                            val collapsed = chromeCollapsed && currentEpisode != null

                            // One element, card ⇄ bubble (see MiniPlayer). It only
                            // fades for the player opening and closing; the
                            // collapse itself is its own morph, not a transition.
                            currentEpisode?.let { episode ->
                                AnimatedVisibility(
                                    visible = !playerOpen,
                                    enter = fadeIn(tween(180)),
                                    exit = fadeOut(tween(140))
                                ) {
                                    CompositionLocalProvider(LocalPlayerScope provides this) {
                                    WithPosition(player) { positionMs ->
                                    MiniPlayer(
                                        episode = episode,
                                        feed = currentFeed,
                                        isPlaying = isPlaying,
                                        positionMs = positionMs,
                                        durationMs = durationMs,
                                        collapse = collapseAnim,
                                        collapsed = collapsed,
                                        pale = chromeMix,
                                        barBounds = { barBounds },
                                        onPlayPause = player::togglePlayPause,
                                        onOpen = { playerOpen = true },
                                        // The card takes every touch that lands on
                                        // it, gaps included, so nothing behind it
                                        // opens. As the bubble it takes none: the
                                        // page above the bar is there to be touched.
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (!collapsed) Modifier.pointerInput(Unit) {} else Modifier)
                                    )
                                    }
                                    }
                                }
                            }

                            // The bar's whole strip takes its own touches, the
                            // gutters and the gesture area below it included: a
                            // tap just beside the pill used to open whatever tile
                            // was behind it. A node with pointer input stops the
                            // siblings under it from being hit at all.
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {}
                                    .navigationBarsPadding(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                GlassTabBar(
                                compact = collapsed,
                                modifier = Modifier
                                    .weight(1f)
                                    .onGloballyPositioned { coordinates ->
                                        val bounds = coordinates.boundsInRoot()
                                        // Left too: rotating or resizing a tablet window moves
                                        // the bar sideways. (Never width — the bar animates its
                                        // width while collapsing, and must not write state per
                                        // frame.)
                                        if (bounds.top != barBounds?.top || bounds.height != barBounds?.height || bounds.left != barBounds?.left) {
                                            barBounds = bounds
                                        }
                                    },
                                tabs = listOf(
                                    GlassTab(tr("Library"), Icons.Outlined.GridView),
                                    GlassTab(tr("Latest"), Icons.Outlined.Inbox),
                                    GlassTab(tr("Downloads"), Icons.Outlined.DownloadForOffline),
                                    GlassTab(tr("Discover"), Icons.Outlined.Explore),
                                    GlassTab(tr("Search"), Icons.Filled.Search),
                                    GlassTab(tr("Settings"), Icons.Outlined.Settings)
                                ),
                                selectedIndex = tab.ordinal,
                                // Dark, as Cider's. With Adaptive tab bar on
                                // (Settings > Appearance) it follows the mini
                                // player to its pale version over a dark page.
                                tint = {
                                    if (adaptiveTabBar) androidx.compose.ui.graphics.lerp(chromeTint.value, chromeTintPale.value, chromeMix.value)
                                    else chromeTint.value
                                },
                                content = {
                                    if (adaptiveTabBar) androidx.compose.ui.graphics.lerp(BarContentLight, ChromeInk, chromeMix.value)
                                    else BarContentLight
                                },
                                hazeState = hazeState,
                                onTabSelected = { index ->
                                    when (index) {
                                        0 -> {
                                            if (tab == Tab.LIBRARY) selectedFeedUrl = null
                                            tab = Tab.LIBRARY
                                        }
                                        1 -> tab = Tab.LATEST
                                        2 -> tab = Tab.DOWNLOADS
                                        3 -> {
                                            // Re-tapping Discover while in a show
                                            // it opened goes back to the shelves.
                                            if (tab == Tab.DISCOVER && previewTab == Tab.DISCOVER) preview = null
                                            tab = Tab.DISCOVER
                                        }
                                        4 -> {
                                            if (tab == Tab.SEARCH && previewTab == Tab.SEARCH) preview = null
                                            tab = Tab.SEARCH
                                        }
                                        else -> tab = Tab.SETTINGS
                                    }
                                }
                            )

                                // The room the bar gives up for the bubble: its width
                                // follows the collapse, measured each frame — the
                                // bar narrows with it, nothing recomposes. The
                                // bubble itself is drawn by MiniPlayer; this is
                                // what you tap.
                                if (currentEpisode != null) {
                                    val slotWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
                                        (58.dp + GlassGutter).toPx()
                                    }
                                    val slotHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                                        58.dp.roundToPx()
                                    }
                                    Box(
                                        Modifier
                                            .layout { measurable, _ ->
                                                val w = (slotWidth * collapseAnim.value.coerceIn(0f, 1f))
                                                    .roundToInt().coerceAtLeast(0)
                                                val placeable = measurable.measure(
                                                    androidx.compose.ui.unit.Constraints.fixed(w, slotHeight)
                                                )
                                                layout(w, slotHeight) { placeable.place(0, 0) }
                                            }
                                            .then(
                                                if (collapsed && !playerOpen) {
                                                    Modifier.clickable(
                                                        interactionSource = remember { MutableInteractionSource() },
                                                        indication = null
                                                    ) { playerOpen = true }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    )
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

                        UpdateBanner(
                            release = updateBanner,
                            visible = !playerOpen && !updateSheetOpen,
                            onOpen = { updateSheetOpen = true },
                            onDismiss = { updater.dismissBanner() },
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
                                artKey = MiniArtKey,
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
                                skipSeconds = skipSeconds,
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
                                skipSilence = skipSilence,
                                voiceBoost = voiceBoost,
                                onSkipSilenceChange = settings::setSkipSilence,
                                onVoiceBoostChange = settings::setVoiceBoost,
                                skipAds = skipAds,
                                onSkipAdsChange = settings::setSkipAds,
                                onShakeToggle = settings::setShakeToRestart,
                                onCollapse = { playerOpen = false },
                                bookmarks = remember(allBookmarks, episode.guid) {
                                    allBookmarks.filter { it.guid == episode.guid }.sortedBy { it.positionMs }
                                },
                                onAddBookmark = { bookmarkStore.add(episode.guid, player.positionMs.value).id },
                                onBookmarkMemo = bookmarkStore::setMemo,
                                onRemoveBookmark = bookmarkStore::remove,
                                onOpenShow = {
                                    // Its show's page, under the closing player.
                                    feedStore.feedFor(episode)?.let { show ->
                                        tab = Tab.LIBRARY
                                        selectedFeedUrl = show.url
                                        playerOpen = false
                                    }
                                },
                                download = downloads[episode.guid],
                                onDownload = {
                                    downloadStore.start(episode, feedStore.feedFor(episode))
                                    toast.show(text = tr("Downloading"), artUrl = episode.imageUrl.ifBlank { feedStore.feedFor(episode)?.imageUrl.orEmpty() }, icon = Icons.Outlined.DownloadForOffline)
                                },
                                onRemoveDownload = {
                                    val wasDone = downloads[episode.guid]?.state == com.glasscast.app.data.DownloadState.DONE
                                    downloadStore.remove(episode.guid)
                                    toast.show(text = if (wasDone) tr("Download removed") else tr("Download canceled"), artUrl = episode.imageUrl.ifBlank { feedStore.feedFor(episode)?.imageUrl.orEmpty() }, icon = Icons.Outlined.DownloadForOffline)
                                }
                            )
                            }
                        }
                        }
                    }
                                    }
                    }
                }

            // Last, so it's drawn over the app that's already composed under it.
            if (!splashDone) {
                SplashScreen(
                    onOpening = { opened = true },
                    onFinished = { splashDone = true }
                )
                LaunchedEffect(Unit) { app.splashPlayed = true }
            }
        }

        if (syncOpen) {
            SyncSheet(sync = gpodder, onDismiss = { syncOpen = false })
        }

        if (updateSheetOpen) {
            UpdateSheet(updater = updater, onDismiss = { updateSheetOpen = false })
        }

        // A shared OPML opens this by itself — arriving from AntennaPod's share
        // sheet should land on the import, not on the library.
        if (opmlOpen || (pendingOpml != null && opened)) {
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
                download = downloads[episode.guid],
                onDownload = {
                    downloadStore.start(episode, feedStore.feedFor(episode))
                    toast.show(
                        text = tr("Downloading"),
                        artUrl = episode.imageUrl.ifBlank { feedStore.feedFor(episode)?.imageUrl.orEmpty() },
                        icon = Icons.Outlined.DownloadForOffline
                    )
                },
                onRemoveDownload = { downloadStore.remove(episode.guid) },
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
