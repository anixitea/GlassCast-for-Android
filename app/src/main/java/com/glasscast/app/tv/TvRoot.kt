package com.glasscast.app.tv

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

private enum class TvTab(val label: String, val icon: ImageVector) {
    /**
     * Not a tab in the routing sense — selecting it opens the player. It lives
     * in the rail because on a TV the mini bar was the wrong idea: a strip
     * along the bottom is a thumb affordance, and reaching it by D-pad meant
     * travelling past everything else on the page. One rail stop, always in the
     * same place, is the whole interaction.
     */
    PLAYING("Playing", Icons.Filled.PlayArrow),
    LIBRARY("Library", Icons.Outlined.GridView),
    LATEST("Latest", Icons.Outlined.Inbox),
    DISCOVER("Discover", Icons.Outlined.Explore),
    SEARCH("Search", Icons.Filled.Search),
    SETTINGS("Settings", Icons.Filled.Tune)
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

    // The whole shell takes its colour from whatever is playing, exactly as the
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
    LaunchedEffect(tab, selectedFeedUrl) { contentFocus.requestWhenReady() }

    // Brushes rebuilt only when the palette changes. Without remember these are
    // reallocated on every recomposition of the shell, which on TV means every
    // focus move.
    val ground = remember(colors) { artworkGround(colors) }
    // The rail is the phone tab bar's counterpart: a floating panel in the
    // cover's hue, a step darker than the page.
    val railSurface by animateColorAsState(colors.chromeBar, tween(600), label = "railSurface")
    val railAccent by animateColorAsState(colors.chromeButton, tween(600), label = "railAccent")
    val dim by animateFloatAsState(if (railFocused) 1f else 0f, tween(220), label = "railDim")

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        Row(Modifier.fillMaxSize()) {

            // Constant. This was the rail's *animated* width, so every frame of
            // the rail opening re-laid-out the page under it — a five-column
            // grid re-measured thirty times a second. The page now starts after
            // the collapsed rail and the expanded rail slides over it.
            Spacer(Modifier.width(TvSpacing.railCollapsed))

            Box(Modifier.weight(1f).fillMaxHeight()) {
                when {
                    tab == TvTab.LIBRARY && selectedFeed != null -> TvShowScreen(
                        feed = selectedFeed,
                        episodes = episodeMap[selectedFeed.url].orEmpty(),
                        sort = sort,
                        colors = colors,
                        refreshing = refreshing,
                        hasNowPlaying = nowPlaying != null,
                        focusRequester = contentFocus,
                        onRefresh = { scope.launch { feedStore.refresh(selectedFeed) } },
                        onBack = { selectedFeedUrl = null },
                        onPlay = { episode ->
                            player.play(episode, feedStore.feedFor(episode))
                            playerOpen = true
                        },
                        onQueue = { episode ->
                            player.addToQueue(episode, feedStore.feedFor(episode))
                        },
                        playingGuid = nowPlaying?.guid
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
                        playingGuid = nowPlaying?.guid
                    )

                    tab == TvTab.DISCOVER -> TvDiscoverScreen(
                        feeds = feeds,
                        store = feedStore,
                        colors = colors,
                        hasNowPlaying = nowPlaying != null,
                        focusRequester = contentFocus,
                        onSubscribed = { url ->
                            tab = TvTab.LIBRARY
                            selectedFeedUrl = url
                        },
                        hidden = discoverHidden
                    )

                    tab == TvTab.SEARCH -> TvSearchScreen(
                        store = feedStore,
                        subscribed = feeds,
                        colors = colors,
                        hasNowPlaying = nowPlaying != null,
                        focusRequester = contentFocus,
                        onSubscribed = { url ->
                            tab = TvTab.LIBRARY
                            selectedFeedUrl = url
                        }
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
                        onClose = { playerOpen = false }
                    )
                }
            }
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && selectedFeedUrl != null) { selectedFeedUrl = null }
    BackHandler(enabled = !playerOpen && selectedFeedUrl == null && tab != TvTab.LIBRARY) {
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
                    text = if (isPlaying) "NOW PLAYING" else "PAUSED",
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
