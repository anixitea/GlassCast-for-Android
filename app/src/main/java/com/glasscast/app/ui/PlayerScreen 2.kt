package com.glasscast.app.ui

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Dp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.Cast
import androidx.compose.runtime.SideEffect
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.player.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val OnPlayer = Color(0xFFF7F7F9)
private val PlayGlyph = Color(0xFF141418)

/**
 * The full player, rebuilt on Cider's construction.
 *
 * **The background is the artwork, blurred.** Every previous version tried to
 * *derive* a background from the cover — a palette, then a mesh of four
 * swatches, then clamps on the mesh — and each one had a seam somewhere,
 * because a derived colour is an approximation of the picture and an
 * approximation always disagrees with the original along some edge.
 *
 * A blurred copy of the same image cannot disagree with it. The sharp artwork
 * sits at the top and dissolves (an alpha mask, as before) into a blurred,
 * enlarged version of itself, so the transition is the picture losing focus
 * rather than one surface meeting another. There is no seam to hide because
 * there is only one image.
 *
 * A dark scrim deepens toward the foot so the controls are always white on
 * something dark enough — which is how Cider gets away with a pale cover and
 * white type on the same screen.
 */
@Composable
fun PlayerScreen(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    buffering: Boolean,
    positionMs: Long,
    durationMs: Long,
    speed: Float,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    upNextCount: Int,
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    onPlayFromUpNext: (Int) -> Unit,
    onRemoveFromQueue: (Episode) -> Unit,
    onClearQueue: () -> Unit,
    onRestartEpisode: () -> Unit,
    onSkipNext: () -> Unit,
    shakeToRestart: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    onCollapse: () -> Unit
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val (colors, _) = rememberArtworkColors(art)
    val accent = colors.meshAccent
    // The panel is dark in every case, so its accent is the pale tone of the
    // cover's accent — the same one the mini player's play button uses.
    val panelColor by animateColorAsState(colors.panelSurface, tween(500), label = "panelSurface")
    val panelAccent by animateColorAsState(colors.chromeButton, tween(500), label = "panelAccent")
    val haptics = rememberHaptics()

    // 0 = panel collapsed to its strip, 1 = pulled all the way up.
    val expand = remember { Animatable(0f) }
    var panelTab by remember { mutableStateOf(PanelTab.UP_NEXT) }
    var showDevices by remember { mutableStateOf(false) }
    var panelTravel by remember { mutableStateOf(1f) }
    var draggingPanel by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableStateOf(0f) }

    val endsAt by SleepTimer.endsAtMs.collectAsStateWithLifecycle()
    val endOfEpisode by SleepTimer.endOfEpisode.collectAsStateWithLifecycle()
    var timerRemaining by remember { mutableStateOf(SleepTimer.remainingMs()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            timerRemaining = SleepTimer.remainingMs()
            delay(1_000)
        }
        timerRemaining = null
    }
    val timerArmed = endsAt != null || endOfEpisode
    val timerLabel = when {
        endOfEpisode -> "End of episode"
        timerRemaining != null -> formatTime(timerRemaining ?: 0L)
        else -> "Timer"
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = if (scrubbing && durationMs > 0) (scrubValue * durationMs).toLong() else positionMs

    // Always light icons: the top of this screen is the artwork under a top
    // scrim, and the foot is darkened by the bottom one.
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars ?: false
        val wasLightNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            controller?.isAppearanceLightStatusBars = wasLight
            controller?.isAppearanceLightNavigationBars = wasLightNav
        }
    }

    /*
     * Drag down to dismiss. Unchanged from before, including the one rule that
     * mattered: the offset is never reset after onCollapse(), because the
     * composable outlives that call by the length of the exit transition.
     */
    val dragOffset = remember { Animatable(0f) }
    val dismissThreshold = with(LocalDensity.current) { 140.dp.toPx() }
    val screenHeight = with(LocalDensity.current) {
        LocalConfiguration.current.screenHeightDp.dp.toPx()
    }
    val scope = rememberCoroutineScope()

    fun settle(velocity: Float) {
        scope.launch {
            if (dragOffset.value > dismissThreshold || velocity > 1800f) {
                haptics.play(Haptic.Select)
                // Straight to collapse, from wherever the finger left it: the
                // cover then flies from its dragged position back into the
                // mini player while the rest fades. Animating the whole player
                // off-screen first would send the cover below the bottom edge
                // and then fly it back up — backwards.
                onCollapse()
            } else {
                dragOffset.animateTo(0f, spring(dampingRatio = 0.82f))
            }
        }
    }
    LaunchedEffect(Unit) { dragOffset.snapTo(0f) }

    fun dragPanel(amount: Float) {
        scope.launch { expand.snapTo((expand.value - amount / panelTravel).coerceIn(0f, 1f)) }
    }

    /*
     * Open or closed, decided on release by velocity first and position second:
     * a flick either way wins, a slow drag goes wherever it was left nearest.
     * Slightly underdamped so it lands with a little life rather than stopping
     * dead — the same feel as the pill in the tab bar.
     */
    fun settlePanel(velocity: Float) {
        val open = when {
            velocity < -1200f -> true
            velocity > 1200f -> false
            else -> expand.value > 0.5f
        }
        if (open && expand.value < 0.5f) haptics.play(Haptic.Expand)
        scope.launch {
            expand.animateTo(
                targetValue = if (open) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f)
            )
        }
    }

    fun openPanel(tab: PanelTab) {
        if (expand.value > 0.5f && panelTab == tab) {
            settlePanel(2000f)
        } else {
            panelTab = tab
            haptics.play(Haptic.Expand)
            scope.launch {
                expand.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 380f))
            }
        }
    }

    BackHandler(enabled = expand.value > 0.5f) { settlePanel(2000f) }

    CompositionLocalProvider(LocalContentColor provides OnPlayer) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value
                    val p = (dragOffset.value / screenHeight).coerceIn(0f, 1f)
                    val shrink = 1f - p * 0.06f
                    scaleX = shrink
                    scaleY = shrink
                }
                .background(Color.Black)
                .pointerInput(Unit) {
                    /*
                     * One gesture, two meanings, decided by direction at the
                     * start and then held: pulling up anywhere on the player
                     * raises the panel, as Cider's does; pulling down dismisses
                     * the player. Once the panel is partly open, all vertical
                     * movement belongs to it, so it can't be dismissed from
                     * underneath the queue.
                     */
                    var velocity = 0f
                    detectVerticalDragGestures(
                        onDragStart = {
                            velocity = 0f
                            draggingPanel = expand.value > 0f
                        },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            velocity = amount
                            if (!draggingPanel && dragOffset.value == 0f && amount < 0f) {
                                draggingPanel = true
                            }
                            if (draggingPanel) {
                                dragPanel(amount)
                            } else {
                                val next = (dragOffset.value + amount).coerceAtLeast(0f)
                                scope.launch { dragOffset.snapTo(next) }
                            }
                        },
                        onDragEnd = {
                            if (draggingPanel) settlePanel(velocity * 60f) else settle(velocity * 60f)
                            draggingPanel = false
                        },
                        onDragCancel = {
                            if (draggingPanel) settlePanel(0f) else settle(0f)
                            draggingPanel = false
                        }
                    )
                }
                .pointerInput(Unit) {}
        ) {
            // Zoomed a little past square, as Cider's is: the cover owns more
            // of the screen, and the dissolve lands where the title begins.
            val artHeight = maxWidth * 1.2f

            PlayerBackdrop(url = art, artHeight = artHeight, modifier = Modifier.fillMaxSize())

            // ---- sharp artwork, dissolving into its own blur ----
            PlayerArtwork(
                url = art,
                modifier = Modifier
                    .sharedArtwork(NowPlayingArtKey, LocalPlayerScope.current)
                    .fillMaxWidth()
                    .height(artHeight)
            )

            // Top scrim for the status bar and the handle; the cover up there
            // could be anything, including white.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent)
                        )
                    )
            )

            // The handle is the dismiss affordance now, in place of a chevron:
            // the gesture is drag-down, so the hint should look draggable.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(OnPlayer.copy(alpha = 0.55f))
            )

            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val w = maxWidth
            val collapsedTop = maxHeight - PanelHeaderHeight - navInset
            val compactTop = statusInset + 40.dp
            val expandedTop = compactTop + 104.dp
            val density = LocalDensity.current
            SideEffect {
                panelTravel = with(density) { (collapsedTop - expandedTop).toPx() }.coerceAtLeast(1f)
            }
            val e = expand.value
            // Things with no place in the compact layout leave early, by the
            // time the panel is 40% open, so they never overlap the moving parts.
            val fade = (1f - e * 2.5f).coerceIn(0f, 1f)

            // The cover dims as the panel rises, so the compact controls that
            // arrive over it stay legible on a bright cover.
            if (e > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.46f * e))
                )
            }

            /*
             * ---- the morph ----
             *
             * The title, the transport and the wave are drawn once each, and
             * every one has two rectangles: where it sits in the full player,
             * and where it sits in the compact header above the open panel.
             * Their position, size, corners and icon sizes are interpolated by
             * `e` — how far the panel is open — and `e` follows the finger. So
             * they travel with the drag, every frame, the way Cider's do,
             * instead of fading out below and fading back in above.
             *
             * The full layout is computed from the bottom up, anchored to the
             * panel's collapsed top, so it holds its shape on any screen height.
             */
            val side = 26.dp
            val volumeTop = collapsedTop - 18.dp - 32.dp
            val groupTop = volumeTop - 22.dp - 76.dp
            val timesTop = groupTop - 16.dp - 18.dp
            val sliderTop = timesTop - 6.dp - 30.dp
            val titleTop = sliderTop - 14.dp - 60.dp

            // Title and show name, scaled down into the header.
            val titleFull = DpRect(side, titleTop, w - side * 2 - 100.dp, 60.dp)
            val titleCompact = DpRect(24.dp, compactTop, w - 48.dp - 178.dp - 12.dp, 48.dp)
            val titleScale = mix(1f, 0.8f, e)
            Column(
                Modifier
                    .offset(mix(titleFull.x, titleCompact.x, e), mix(titleFull.y, titleCompact.y, e))
                    .width(mix(titleFull.w, titleCompact.w / 0.8f, e))
                    .graphicsLayer {
                        scaleX = titleScale
                        scaleY = titleScale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnPlayer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feed?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnPlayer.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Cast, and the moon — but only while a timer is running. It's a
            // status as much as a button: tapping it opens the timer tab.
            if (fade > 0f) {
                Row(
                    Modifier
                        .offset(w - side - 96.dp, titleTop + 8.dp)
                        .width(96.dp)
                        .graphicsLayer { alpha = fade },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (timerArmed) {
                        PlayerIconButton(Icons.Filled.Bedtime, "Timer: $timerLabel", tint = panelAccent) {
                            openPanel(PanelTab.TIMER)
                        }
                    }
                    PlayerIconButton(Icons.Filled.Cast, "Play on another device") {
                        haptics.play(Haptic.Tap)
                        showDevices = true
                    }
                }
            }

            // The wave, thinning into the header's.
            val sliderRect = mix(
                DpRect(side, sliderTop, w - side * 2, 30.dp),
                DpRect(24.dp, compactTop + 60.dp, w - 48.dp, 18.dp),
                e
            )
            WavySlider(
                progress = if (scrubbing) scrubValue else progress,
                playing = isPlaying,
                color = OnPlayer,
                enabled = durationMs > 0,
                height = sliderRect.h,
                amplitude = mix(4.dp, 3.dp, e),
                wavelength = mix(30.dp, 26.dp, e),
                strokeWidth = mix(4.5.dp, 3.5.dp, e),
                showThumb = e < 0.5f,
                onScrubStart = {
                    scrubbing = true
                    scrubValue = progress
                },
                onScrub = { scrubValue = it },
                onScrubEnd = {
                    if (durationMs > 0) onSeekTo((it * durationMs).toLong())
                    haptics.play(Haptic.Tick)
                    scrubbing = false
                },
                modifier = Modifier
                    .offset(sliderRect.x, sliderRect.y)
                    .width(sliderRect.w)
            )

            if (fade > 0f) {
                Row(
                    Modifier
                        .offset(side, timesTop)
                        .width(w - side * 2)
                        .graphicsLayer { alpha = fade }
                ) {
                    Text(
                        text = formatTime(shownPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPlayer.copy(alpha = 0.66f)
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemaining(shownPosition, durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnPlayer.copy(alpha = 0.66f)
                    )
                }
            }

            // The transport: five buttons at rest, three in the header. Back
            // and forward 30 and play travel to their compact slots; restart
            // and next narrow away into the group's ends as they fade.
            val fullSlots = DpRect(side, groupTop, w - side * 2, 76.dp)
                .split(listOf(0.78f, 1f, 1.7f, 1f, 0.78f), 4.dp)
            val compactSlots = DpRect(w - 24.dp - 178.dp, compactTop, 178.dp, 48.dp)
                .split(listOf(1f, 1.3f, 1f), 4.dp)
            val playCorner by animateDpAsState(
                targetValue = if (isPlaying) 22.dp else 38.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
                label = "playShape"
            )
            val playCornerSmall by animateDpAsState(
                targetValue = if (isPlaying) 14.dp else 24.dp,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
                label = "playShapeSmall"
            )
            val quiet = OnPlayer.copy(alpha = 0.16f)
            val leftEdge = compactSlots[0].copy(w = 0.dp)
            val rightEdge = compactSlots[2].copy(x = compactSlots[2].x + compactSlots[2].w, w = 0.dp)

            if (fade > 0f) {
                MorphButton(
                    rect = mix(fullSlots[0], leftEdge, e),
                    corners = Corners(30.dp, 10.dp, 10.dp, 30.dp),
                    container = quiet, content = OnPlayer,
                    icon = Icons.Filled.SkipPrevious, description = "Restart episode",
                    iconSize = 24.dp, alpha = fade
                ) {
                    haptics.play(Haptic.SkipBack)
                    onRestartEpisode()
                }
            }
            MorphButton(
                rect = mix(fullSlots[1], compactSlots[0], e),
                corners = mix(Corners(10.dp, 10.dp, 10.dp, 10.dp), Corners(20.dp, 8.dp, 8.dp, 20.dp), e),
                container = quiet, content = OnPlayer,
                icon = Icons.Filled.Replay30, description = "Back 30 seconds",
                iconSize = mix(28.dp, 20.dp, e)
            ) {
                haptics.play(Haptic.SkipBack)
                onSeekBy(-30_000)
            }
            Box(
                Modifier
                    .offset(mix(fullSlots[2].x, compactSlots[1].x, e), mix(fullSlots[2].y, compactSlots[1].y, e))
                    .size(mix(fullSlots[2].w, compactSlots[1].w, e), mix(fullSlots[2].h, compactSlots[1].h, e))
            ) {
                val c = mix(playCorner, playCornerSmall, e)
                MorphButton(
                    rect = DpRect(0.dp, 0.dp, mix(fullSlots[2].w, compactSlots[1].w, e), mix(fullSlots[2].h, compactSlots[1].h, e)),
                    corners = Corners(c, c, c, c),
                    container = OnPlayer, content = PlayGlyph,
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (isPlaying) "Pause" else "Play",
                    iconSize = mix(38.dp, 24.dp, e)
                ) {
                    haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                    onPlayPause()
                }
                if (buffering) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = PlayGlyph.copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(mix(52.dp, 32.dp, e))
                    )
                }
            }
            MorphButton(
                rect = mix(fullSlots[3], compactSlots[2], e),
                corners = mix(Corners(10.dp, 10.dp, 10.dp, 10.dp), Corners(8.dp, 20.dp, 20.dp, 8.dp), e),
                container = quiet, content = OnPlayer,
                icon = Icons.Filled.Forward30, description = "Forward 30 seconds",
                iconSize = mix(28.dp, 20.dp, e)
            ) {
                haptics.play(Haptic.SkipForward)
                onSeekBy(30_000)
            }
            if (fade > 0f) {
                MorphButton(
                    rect = mix(fullSlots[4], rightEdge, e),
                    corners = Corners(10.dp, 30.dp, 30.dp, 10.dp),
                    container = quiet, content = OnPlayer,
                    icon = Icons.Filled.SkipNext,
                    description = if (upNextCount > 0) "Next episode" else "Nothing queued",
                    iconSize = 24.dp, alpha = fade, enabled = upNextCount > 0
                ) {
                    haptics.play(Haptic.SkipForward)
                    onSkipNext()
                }
            }

            if (fade > 0f) {
                Box(
                    Modifier
                        .offset(side, volumeTop)
                        .width(w - side * 2)
                        .graphicsLayer { alpha = fade }
                ) {
                    VolumeRow(accent = OnPlayer, onGround = OnPlayer.copy(alpha = 0.66f))
                }
            }

            // ---- the panel: strip at rest, the four tabs when pulled up ----
            PlayerPanel(
                expand = e,
                tab = panelTab,
                art = art,
                upNextCount = upNextCount,
                speed = speed,
                timerArmed = timerArmed,
                surface = panelColor,
                accent = panelAccent,
                onTab = { openPanel(it) },
                onDrag = { dragPanel(it) },
                onDragEnd = { settlePanel(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(maxHeight - expandedTop)
                    .offset(y = mix(collapsedTop, expandedTop, e))
            ) { tab ->
                when (tab) {
                    PanelTab.UP_NEXT -> PanelQueue(
                        upNext = upNext,
                        feedFor = feedFor,
                        surface = panelColor,
                        onPlayAt = onPlayFromUpNext,
                        onRemove = onRemoveFromQueue,
                        onClear = onClearQueue,
                        bottomInset = navInset
                    )
                    PanelTab.INFO -> EpisodeInfoContent(
                        episode = episode,
                        feed = feed,
                        accent = panelAccent,
                        positionMs = positionMs,
                        onSeekTo = onSeekTo,
                        showHeader = false,
                        listModifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = navInset)
                    )
                    PanelTab.SPEED -> SpeedPanel(
                        speed = speed,
                        accent = panelAccent,
                        onSpeedChange = onSpeedChange,
                        bottomInset = navInset
                    )
                    PanelTab.TIMER -> TimerPanel(
                        armed = timerArmed,
                        remainingLabel = timerLabel,
                        accent = panelAccent,
                        shakeToRestart = shakeToRestart,
                        onShakeToggle = onShakeToggle,
                        bottomInset = navInset
                    )
                }
            }
        }
    }

    if (showDevices) {
        DeviceSheet(
            surface = panelColor,
            accent = panelAccent,
            onDismiss = { showDevices = false }
        )
    }
}

// ---- morph geometry ----

private data class DpRect(val x: Dp, val y: Dp, val w: Dp, val h: Dp) {
    /** Split horizontally by [weights], with [gap] between parts — a button group's slots. */
    fun split(weights: List<Float>, gap: Dp): List<DpRect> {
        val unit = (w - gap * (weights.size - 1)) / weights.sum()
        var cursor = x
        return weights.map { weight ->
            val part = DpRect(cursor, y, unit * weight, h)
            cursor += unit * weight + gap
            part
        }
    }
}

private data class Corners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp)

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
private fun mix(a: Dp, b: Dp, t: Float) = a + (b - a) * t
private fun mix(a: DpRect, b: DpRect, t: Float) =
    DpRect(mix(a.x, b.x, t), mix(a.y, b.y, t), mix(a.w, b.w, t), mix(a.h, b.h, t))
private fun mix(a: Corners, b: Corners, t: Float) = Corners(
    mix(a.topStart, b.topStart, t), mix(a.topEnd, b.topEnd, t),
    mix(a.bottomEnd, b.bottomEnd, t), mix(a.bottomStart, b.bottomStart, t)
)

/**
 * A transport button placed by rectangle rather than by a Row — so it can be
 * anywhere between its full and compact slots. Squashes on press like the
 * group buttons.
 */
@Composable
private fun MorphButton(
    rect: DpRect,
    corners: Corners,
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    iconSize: Dp,
    alpha: Float = 1f,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "morphPress"
    )
    val shape = RoundedCornerShape(corners.topStart, corners.topEnd, corners.bottomEnd, corners.bottomStart)
    Box(
        Modifier
            .offset(rect.x, rect.y)
            .size(rect.w, rect.h)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
                this.alpha = alpha
            }
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = container.alpha * 0.5f))
            .clickable(
                enabled = enabled && alpha > 0.3f,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (enabled) content else content.copy(alpha = 0.35f),
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * The field behind everything — built the way Cider builds it.
 *
 * The first version blurred a separate, full-screen copy of the cover. That
 * copy was framed differently from the sharp artwork above it (zoomed to the
 * screen's height, centred), so whatever sat just below the cover's bottom edge
 * came from the *middle* of the image. On a cover whose centre differs from its
 * foot, the colour broke at the seam and settled into a mud of the whole.
 *
 * Cider mirrors the cover vertically underneath itself. Then the first thing
 * below the bottom edge is that same edge, reflected, and every colour carries
 * straight on down — the legs in a photo continue as their own reflection, a
 * coloured band at the foot of the art continues as the same band. The pair
 * (cover over its reflection) is blurred as one image, so the blur runs across
 * the seam instead of stopping at it, and the sharp cover laid on top dissolves
 * into its own blurred self before the reflection begins.
 *
 * Underneath, a tiny full-screen blur fills anything below the reflection on
 * very tall screens, and a scrim deepens toward the controls so white type
 * always reads.
 */
@Composable
private fun PlayerBackdrop(url: String, artHeight: Dp, modifier: Modifier = Modifier) {
    val store = LocalImageStore.current
    var field by remember(url) { mutableStateOf(store.peek(url, 96)) }
    var mirror by remember(url) { mutableStateOf(store.peek(url, 480)) }
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        if (field == null) field = store.load(url, 96)
        if (mirror == null) mirror = store.load(url, 480)
    }

    Box(modifier) {
        field?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.High,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
                    .blur(64.dp)
            )
        }

        mirror?.let { bmp ->
            val image = bmp.asImageBitmap()
            // Cover and reflection as one column, blurred together. The blur
            // clamps at the column's own edges, so nothing darkens at the seam.
            Column(
                Modifier
                    .fillMaxWidth()
                    .blur(30.dp)
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                )
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .graphicsLayer { scaleY = -1f }
                )
            }
        }

        // Clear over the cover, deepening through the reflection toward the
        // controls — the "common colour" the foot settles into is the blurred
        // reflection darkened, so it's always this cover's own colour.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.06f),
                        0.42f to Color.Black.copy(alpha = 0.16f),
                        0.62f to Color.Black.copy(alpha = 0.34f),
                        1.00f to Color.Black.copy(alpha = 0.62f)
                    )
                )
        )
    }
}

/** The sharp cover, erased at its foot — a mask, never a colour overlay. */
@Composable
private fun PlayerArtwork(url: String, modifier: Modifier = Modifier) {
    val store = LocalImageStore.current
    var bitmap by remember(url) { mutableStateOf(store.peek(url, 1080)) }
    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) bitmap = store.load(url, 1080)
    }

    Box(
        modifier
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.00f to Color.Black,
                        0.56f to Color.Black,
                        0.74f to Color.Black.copy(alpha = 0.70f),
                        0.88f to Color.Black.copy(alpha = 0.28f),
                        1.00f to Color.Transparent
                    ),
                    blendMode = BlendMode.DstIn
                )
            }
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = OnPlayer,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
    }
}


/**
 * System volume, not a per-app gain.
 *
 * Observed, not polled. A poll was showing the hardware keys a second or two
 * late — and the interval can't just be shortened, because a fast poll of an
 * always-visible control is a wakeup every frame for a value that changes twice
 * an hour. A ContentObserver on the system settings URI fires the moment the
 * volume actually changes and costs nothing in between; the DisposableEffect
 * unregisters it when the player closes.
 */
@Composable
private fun VolumeRow(accent: Color, onGround: Color) {
    val context = LocalContext.current
    val audio = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    }
    val maxVolume = remember {
        audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volume by remember {
        mutableStateOf(audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC))
    }

    DisposableEffect(Unit) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.System.CONTENT_URI,
            true,
            observer
        )
        // Catch anything that moved between composing and registering.
        volume = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    fun apply(fraction: Float) {
        val target = (fraction * maxVolume).roundToInt().coerceIn(0, maxVolume)
        if (target != volume) {
            volume = target
            runCatching {
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, target, 0)
            }
        }
    }

    Row(
        // Inset from the scrubber's full width — the volume line is shorter,
        // not thinner.
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Filled.VolumeDown,
            contentDescription = null,
            tint = onGround,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        ThinSlider(
            progress = volume.toFloat() / maxVolume,
            accent = accent.copy(alpha = 0.85f),
            trackColor = onGround.copy(alpha = 0.18f),
            // Same weight as the scrubber. Differentiating by thickness was a
            // reasonable idea that read as inconsistency instead — two bars of
            // different heights on one screen looks like a mistake, not a
            // hierarchy. The inset and the larger icons carry the distinction.
            restHeight = 6.dp,
            activeHeight = 12.dp,
            enabled = true,
            onScrubStart = {},
            onScrub = { apply(it) },
            onScrubEnd = { apply(it) },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = onGround,
            modifier = Modifier.size(22.dp)
        )
    }
}
