package com.glasscast.app.tv

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
    val positionMs by player.positionMs.collectAsStateWithLifecycle()
    val durationMs by player.durationMs.collectAsStateWithLifecycle()
    val upNext by player.upNext.collectAsStateWithLifecycle()

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
    val railFade = remember(colors) {
        Brush.horizontalGradient(
            0f to colors.background,
            0.7f to colors.background.copy(alpha = 0.92f),
            1f to colors.background.copy(alpha = 0f)
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(ground)
    ) {
        Row(Modifier.fillMaxSize()) {

            Spacer(Modifier.width(railWidth))

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
                        }
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
                        }
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

        // Rail drawn over the content so expanding it doesn't reflow the page —
        // a layout that shifts every time focus enters the rail is unbearable.
        Column(
            Modifier
                .fillMaxHeight()
                .width(railWidth)
                .background(railFade)
                .focusGroup()
                .onFocusChanged { railFocused = it.hasFocus }
                .padding(vertical = TvSpacing.overscanV),
            verticalArrangement = Arrangement.Center
        ) {
            TvTab.entries.forEach { entry ->
                // Nothing playing, no Playing entry — a rail stop that opens an
                // empty screen is worse than one that isn't there.
                if (entry == TvTab.PLAYING && nowPlaying == null) return@forEach

                TvRailItem(
                    label = if (entry == TvTab.PLAYING) {
                        nowPlayingFeed?.title?.takeIf { it.isNotBlank() } ?: entry.label
                    } else {
                        entry.label
                    },
                    icon = entry.icon,
                    // The cover replaces the glyph while something is playing:
                    // it says both "this is the player" and "this is what's in
                    // it" in the space of one icon.
                    artworkUrl = if (entry == TvTab.PLAYING) {
                        nowPlaying?.imageUrl?.ifBlank { nowPlayingFeed?.imageUrl.orEmpty() }.orEmpty()
                    } else {
                        ""
                    },
                    selected = tab == entry,
                    expanded = railFocused,
                    accent = colors.accent,
                    content = colors.content,
                    modifier = if (entry == TvTab.LIBRARY) {
                        Modifier.focusRequester(railFocus)
                    } else {
                        Modifier
                    },
                    onClick = {
                        if (entry == TvTab.PLAYING) {
                            playerOpen = true
                        } else {
                            if (tab == entry && entry == TvTab.LIBRARY) selectedFeedUrl = null
                            tab = entry
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        AnimatedVisibility(
            visible = playerOpen && nowPlaying != null,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(160))
        ) {
            nowPlaying?.let { episode ->
                TvPlayerScreen(
                    episode = episode,
                    feed = nowPlayingFeed,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    upNextCount = upNext.size,
                    colors = colors,
                    onPlayPause = player::togglePlayPause,
                    onSeekBy = player::seekBy,
                    onSeekTo = player::seekTo,
                    onSkipNext = player::skipToNext,
                    onRestart = player::restartEpisode,
                    onClose = { playerOpen = false }
                )
            }
        }
    }

    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && selectedFeedUrl != null) { selectedFeedUrl = null }
    BackHandler(enabled = !playerOpen && selectedFeedUrl == null && tab != TvTab.LIBRARY) {
        tab = TvTab.LIBRARY
    }
}

@Composable
private fun TvRailItem(
    label: String,
    icon: ImageVector,
    artworkUrl: String = "",
    selected: Boolean,
    expanded: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .tvFocusableRow(
                accent = accent,
                surface = content,
                shape = RoundedCornerShape(percent = 50),
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (artworkUrl.isNotBlank()) {
            Artwork(url = artworkUrl, sizeDp = 34.dp, corner = 8.dp)
        } else {
            Box(
                Modifier
                    .size(34.dp)
                    .background(
                        if (selected) accent.copy(alpha = 0.22f)
                        else androidx.compose.ui.graphics.Color.Transparent,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (selected) accent else content.copy(alpha = 0.75f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (selected) accent else content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
