package com.glasscast.app.ui

import com.glasscast.app.ui.seaglass.drawSeaWash
import com.glasscast.app.ui.seaglass.clearGlass
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.ui.unit.LayoutDirection
import android.app.Activity
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.runtime.key
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
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
import com.glasscast.app.player.AdSkipper
import com.glasscast.app.data.usable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.glasscast.app.ui.seaglass.coverPool
import com.glasscast.app.ui.seaglass.seaGlass
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.glasscast.app.ui.liquid.liquidBackdrop
import com.glasscast.app.ui.liquid.liquidGlass

private val OnPlayer = Color(0xFFF7F7F9)
private val PlayGlyph = Color(0xFF141418)

/**
 * The full player, rebuilt on Cider's construction.
 *
 * **The background is the artwork, blurred.** Every previous version tried to
 * *derive* a background from the cover — a palette, then a mesh of four
 * swatches, then clamps on the mesh — and each one had a seam somewhere,
 * because a derived color is an approximation of the picture and an
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
    /** The key of whichever the player grows from: the card or the bubble. */
    artKey: String = MiniArtKey,
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
    /** How far back and forward skip (Settings.skipSeconds). */
    skipSeconds: Int = 30,
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
    skipSilence: Boolean = false,
    voiceBoost: Boolean = false,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onVoiceBoostChange: (Boolean) -> Unit = {},
    skipAds: Boolean = false,
    onSkipAdsChange: (Boolean) -> Unit = {},
    onShakeToggle: (Boolean) -> Unit,
    onCollapse: () -> Unit,
    /** The title or show name was tapped: close the player onto the show's page. */
    onOpenShow: () -> Unit = {},
    /** This episode's bookmarks, in order. */
    bookmarks: List<com.glasscast.app.data.Bookmark> = emptyList(),
    /** Adds a bookmark where playback is and returns its id. */
    onAddBookmark: () -> Long = { 0L },
    onBookmarkMemo: (Long, String) -> Unit = { _, _ -> },
    onRemoveBookmark: (Long) -> Unit = {},
    download: com.glasscast.app.data.DownloadEntry? = null,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {}
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val (colors, _) = rememberArtworkColors(art)
    // Ad breaks, colored on the progress line while Skip ads is on.
    val adBreaks by AdSkipper.breaks.collectAsStateWithLifecycle()
    val adMarks = remember(adBreaks, episode.guid, durationMs, skipAds) {
        if (!skipAds || durationMs <= 0 || adBreaks.first != episode.guid) {
            emptyList()
        } else {
            adBreaks.second.usable(durationMs).map {
                (it.startMs.toFloat() / durationMs).coerceIn(0f, 1f)..(it.endMs.toFloat() / durationMs).coerceIn(0f, 1f)
            }
        }
    }
    val accent = colors.meshAccent
    /*
     * Sea Glass turns the player from a poster into a page: the cover inset on
     * paper (or the tide at night) washed with its own color, ink type, and
     * the transport as glass: clear pebbles for the skips and a worn pebble
     * of the cover's glass for play. Everything that moves keeps its geometry
     * (the morph below is the same); only what it's made of changes.
     */
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    val pigment = if (tokens != null) {
        com.glasscast.app.ui.seaglass.animatePigment(
            remember(colors, tokens.dark) { com.glasscast.app.ui.seaglass.seaPigment(colors.meshAccent, tokens.dark) }
        )
    } else {
        null
    }
    /*
     * Liquid Glass keeps the Original player's picture (the cover across the
     * top, dissolving into its own blur) and makes everything that sits on
     * it glass: the panel at the foot, play, the buttons by the title. The
     * picture is recorded as their backdrop (playerGlass) the way the page
     * is for the tab bar.
     */
    val liquidPlayer = com.glasscast.app.ui.liquid.LocalLiquid.current != null
    val playerBackdrop = remember { com.glasscast.app.ui.liquid.LiquidBackdrop() }
    val playerGlass = if (liquidPlayer && tokens == null) playerBackdrop else null
    val onPlayer = tokens?.ink ?: OnPlayer
    val onPlayerDim = tokens?.muted ?: OnPlayer.copy(alpha = 0.66f)
    // Sea Glass: the cover's own ink for the small type, the line and the
    // volume, so the page carries its color past the wash.
    val seaInk = if (tokens != null && pigment != null) (if (tokens.dark) pigment.edge else pigment.ink) else null
    val wash = if (tokens != null && pigment != null) com.glasscast.app.ui.seaglass.seaWash(colors, pigment, tokens) else null
    // The panel is dark in every case, so its accent is the pale tone of the
    // cover's accent — the same one the mini player's play button uses. In Sea
    // Glass it's paper, and the accent is the cover glass's ink.
    val panelColor by animateColorAsState(
        if (tokens != null && pigment != null) androidx.compose.ui.graphics.lerp(tokens.card, pigment.body, if (tokens.dark) 0.22f else 0.30f)
        else colors.panelSurface,
        tween(500),
        label = "panelSurface"
    )
    val panelAccent by animateColorAsState(
        if (tokens != null && pigment != null) (if (tokens.dark) pigment.edge else pigment.ink) else colors.chromeButton,
        tween(500),
        label = "panelAccent"
    )
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
        endOfEpisode -> tr("End of episode")
        timerRemaining != null -> formatTime(timerRemaining ?: 0L)
        else -> tr("Timer")
    }

    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownPosition = if (scrubbing && durationMs > 0) (scrubValue * durationMs).toLong() else positionMs

    // Always light icons: the top of this screen is the artwork under a top
    // scrim, and the foot is darkened by the bottom one. (Sea Glass on paper:
    // dark icons, as on any light page.)
    val view = LocalView.current
    val lightBars = tokens != null && !tokens.dark
    DisposableEffect(lightBars) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars ?: false
        val wasLightNav = controller?.isAppearanceLightNavigationBars ?: false
        controller?.isAppearanceLightStatusBars = lightBars
        controller?.isAppearanceLightNavigationBars = lightBars
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

    // Landscape closes as one sheet sliding away (see settle).
    val landscapeScreen = LocalConfiguration.current.let { it.screenWidthDp > it.screenHeightDp }
    var closing by remember { mutableStateOf(false) }

    fun settle(velocity: Float) {
        scope.launch {
            if (dragOffset.value > dismissThreshold || velocity > 1800f) {
                haptics.play(Haptic.Select)
                if (landscapeScreen) {
                    // Landscape: the player leaves as one sheet, sliding down,
                    // while the mini player fades in. Flying the cover from the
                    // left of the screen to the bubble at the bottom right cut
                    // diagonally across the page, after the drag had already
                    // carried the cover down.
                    closing = true
                    dragOffset.animateTo(screenHeight, tween(240, easing = FastOutLinearInEasing))
                }
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

    // The flight lands on the inset cover's corners in Sea Glass.
    val bigCorner = with(LocalDensity.current) { if (tokens != null) SeaCoverCorner.toPx() else 0f }
    SideEffect { MiniArtShape.bigCorner = bigCorner }

    CompositionLocalProvider(LocalContentColor provides onPlayer) {
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
                .background(tokens?.ground ?: Color.Black)
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
            // Zoomed a little past square, as Cider's is — but never so tall
            // that the title lands on the cover's sharp part. The title sits
            // 292dp above the panel strip (see the morph below); the cover is
            // cut so that's 86% of the way down it, where the dissolve is past
            // 70%. Phones never reach the cap; tall tablet screens did, and
            // their titles sat over the sharp middle of the cover.
            val titleTopPortrait = maxHeight - PanelHeaderHeight -
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() - 292.dp
            // (Floored, so a squat window, a split screen, still gets a
            // real piece of cover.)
            val artHeight = minOf(maxWidth * 1.2f, titleTopPortrait / 0.86f)
                .coerceAtLeast(minOf(maxWidth, maxHeight) * 0.6f)
            // Landscape on a tablet: the portrait player turned on its side.
            // The cover fills the height on the left and dissolves rightward
            // into its blur; the controls keep their order in a column beside
            // it, and the panel rises inside that column. (The portrait cover,
            // maxWidth × 1.2 tall, was taller than a landscape screen: it hid
            // every control, so they seemed to load late while it flew in.)
            //
            // A phone turned sideways gets the same layout, packed for its
            // height (see shortLandscape below). It used to keep the portrait
            // one, which needs about 400dp above the panel strip: on a phone's
            // ~390dp the title went up under the status bar and the cover was
            // only the blur behind the controls.
            val landscape = maxWidth > maxHeight
            val shortLandscape = landscape && maxHeight < 480.dp
            val artWidth = if (landscape) minOf(maxHeight, maxWidth * 0.55f) else maxWidth

            if (tokens != null && wash != null) {
                SeaPlayerGround(
                    wash = wash,
                    dark = tokens.dark,
                    landscape = landscape,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
            // One box, so Liquid Glass can record the whole picture for its glass.
            val screenW = maxWidth
            val screenH = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .liquidBackdrop(playerGlass)
            ) {
            PlayerBackdrop(
                url = art,
                band = if (landscape) artWidth else artHeight,
                length = if (landscape) screenW else screenH,
                sideways = landscape,
                aspect = if (landscape) screenH / artWidth else artHeight / screenW,
                modifier = Modifier.fillMaxSize()
            )

            // ---- sharp artwork, dissolving into its own blur ----
            PlayerArtwork(
                url = art,
                modifier = Modifier
                    .then(
                        if (closing) Modifier
                        else Modifier.sharedArtwork(artKey, LocalPlayerScope.current, clip = FlightClip)
                    )
                    .then(
                        if (landscape) Modifier.width(artWidth).fillMaxHeight()
                        else Modifier.fillMaxWidth().height(artHeight)
                    ),
                sideways = landscape
            )

            // Top scrim for the status bar and the handle; the cover up there
            // could be anything, including white. Above the cover while it
            // flies in, or it only appears once the cover lands.
            Box(
                Modifier
                    .aboveFlyingArtwork(LocalPlayerScope.current)
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent)
                        )
                    )
            )
            // Liquid Glass: the dimming as the panel rises is part of the
            // picture, so the glass sees it too.
            if (playerGlass != null && !landscape) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = expand.value }
                        .background(Color.Black.copy(alpha = 0.46f))
                )
            }
            }
            }

            // The handle is the dismiss affordance now, in place of a chevron:
            // the gesture is drag-down, so the hint should look draggable.
            // A phone on its side lends its place to the open panel's header
            // (see compactTop): it fades as the panel rises, while a drag down
            // would close the panel first anyway.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .aboveFlyingArtwork(LocalPlayerScope.current)
                    .graphicsLayer {
                        if (shortLandscape) alpha = (1f - expand.value * 2.5f).coerceIn(0f, 1f)
                    }
                    .statusBarsPadding()
                    .padding(top = 10.dp)
                    .size(width = 40.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(onPlayer.copy(alpha = if (tokens != null) 0.22f else 0.55f))
            )

            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val w = maxWidth
            val collapsedTop = maxHeight - PanelHeaderHeight - navInset
            // The header over the open panel: the title and the three buttons
            // in a row, the wave under them. A phone on its side has about
            // 400dp of height, and the usual header left the open panel half
            // of it, so the sleep timer's chips and the switch below them were
            // cut off. There the header moves up under the status bar (the
            // handle gives way) and the wave tucks in closer, which gives the
            // panel about 60dp more.
            val compactTop = statusInset + if (shortLandscape) 4.dp else 40.dp
            val compactWaveTop = compactTop + if (shortLandscape) 52.dp else 60.dp
            val expandedTop = compactTop + if (shortLandscape) 80.dp else 104.dp
            val density = LocalDensity.current
            SideEffect {
                panelTravel = with(density) { (collapsedTop - expandedTop).toPx() }.coerceAtLeast(1f)
            }
            val e = expand.value
            // Things with no place in the compact layout leave early, by the
            // time the panel is 40% open, so they never overlap the moving parts.
            val fade = (1f - e * 2.5f).coerceIn(0f, 1f)

            /*
             * The full layout is computed from the bottom up, anchored to the
             * panel's collapsed top, so it holds its shape on any screen
             * height (the morph below has the rest). It comes first so the
             * Sea Glass cover can fit itself above the title.
             */
            val side = 26.dp
            // Sideways, the camera cutout or the three-button bar can be on
            // the right: the column keeps clear of them.
            val endInset = if (landscape) {
                WindowInsets.displayCutout.union(WindowInsets.navigationBars).asPaddingValues()
                    .calculateRightPadding(LayoutDirection.Ltr)
            } else {
                0.dp
            }
            // The controls' column: the full width in portrait (the same
            // numbers as ever); beside the cover in landscape, starting just
            // inside its dissolve. The compact header above an open panel
            // follows the column too.
            val colLeft = if (landscape) artWidth - 24.dp else side
            val colW = if (landscape) w - colLeft - side - endInset else w - side * 2
            val cLeft = if (landscape) colLeft else 24.dp
            val cW = if (landscape) colW else w - 48.dp
            // Landscape: the stack (title to volume, 274dp) centers in the
            // height above the strip, beside a cover that fills it, instead
            // of sitting on the strip as it does under a portrait cover.
            //
            // A phone sideways has no room for that: the volume row goes (the
            // phone's own buttons are right there), the transport is 64dp
            // rather than 76, and the gaps tighten, so title to transport is
            // 196dp and still centers between the status bar and the strip.
            val groupH = if (shortLandscape) 64.dp else 76.dp
            val lift = when {
                shortLandscape -> ((collapsedTop - 14.dp - (statusInset + 12.dp) - 196.dp) / 2).coerceAtLeast(0.dp)
                landscape -> ((collapsedTop - 18.dp - (statusInset + 32.dp) - 274.dp) / 2).coerceAtLeast(0.dp)
                else -> 0.dp
            }
            val volumeTop = collapsedTop - 18.dp - 32.dp - lift
            val groupTop = if (shortLandscape) collapsedTop - 14.dp - groupH - lift else volumeTop - 22.dp - groupH
            val timesTop = groupTop - (if (shortLandscape) 10.dp else 16.dp) - 18.dp
            val sliderTop = timesTop - (if (shortLandscape) 4.dp else 6.dp) - 30.dp
            val titleTop = sliderTop - (if (shortLandscape) 10.dp else 14.dp) - 60.dp

            // Sea Glass: the cover sits on the page, inset and rounded, in the
            // room between the line of small type at the top and the title;
            // in landscape, centered in its half.
            val seaPortrait = tokens != null && !landscape
            if (tokens != null && pigment != null) {
                val coverTop = statusInset + 58.dp
                val coverSize = if (landscape) {
                    minOf(artWidth - 56.dp, maxHeight - statusInset - navInset - 40.dp).coerceAtLeast(120.dp)
                } else {
                    minOf(w - 56.dp, titleTop - 18.dp - coverTop).coerceAtLeast(120.dp)
                }
                val coverX = if (landscape) (artWidth - 24.dp - coverSize) / 2 + 4.dp else (w - coverSize) / 2
                val coverY = if (landscape) statusInset + (maxHeight - statusInset - navInset - coverSize) / 2 else coverTop
                // In portrait it leaves as the panel rises (it would only be a
                // ghost behind the header's controls), its pool with it.
                val coverShown = if (landscape) 1f else (1f - e * 1.6f).coerceIn(0f, 1f)
                SeaPlayerCover(
                    url = art,
                    modifier = Modifier
                        .offset(coverX, coverY)
                        .size(coverSize)
                        .coverPool(pigment, SeaCoverCorner.value, tokens.dark, strength = coverShown)
                        .then(
                            if (closing) Modifier
                            else Modifier.sharedArtwork(artKey, LocalPlayerScope.current, clip = FlightClip)
                        )
                        .graphicsLayer { alpha = coverShown }
                )
            }

            // The cover dims as the panel rises, so the compact controls that
            // arrive over it stay legible on a bright cover.
            // Not in landscape: there the panel covers only the column and
            // the compact controls sit over the blur, never the cover — a
            // dimmed column read as a dark box cut out of the screen. (Sea
            // Glass: the paper comes back over the cover instead.)
            if (e > 0f && !landscape && playerGlass == null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            if (tokens != null) tokens.ground.copy(alpha = 0.94f * e)
                            else Color.Black.copy(alpha = 0.46f * e)
                        )
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
            // Title and show name, scaled down into the header.
            // The icons beside the title: download and cast. (A moon joined
            // them while a timer ran; it took room from the title and repeated
            // the one in the panel bar, which now shows a running timer.)
            val iconsWidth = 96.dp
            val titleFull = DpRect(colLeft, titleTop, if (seaPortrait) colW else colW - iconsWidth - 4.dp, 60.dp)
            val titleCompact = DpRect(cLeft, compactTop, cW - 178.dp - 12.dp, 48.dp)
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
                    // The title and show name lead to the show's page.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        haptics.play(Haptic.Tap)
                        onOpenShow()
                    }
            ) {
                /*
                 * A long title scrolls through once, Apple Music style: a
                 * beat after the player opens it glides across to show the
                 * whole name, comes back round to the start, and stops. Not a
                 * perpetual ticker — it runs again only when the player is
                 * opened again (the player is composed fresh each time), or
                 * when the episode changes (the key). A short title never
                 * moves. The right edge fades rather than cutting the text off
                 * mid-letter, while it scrolls and after it rests.
                 */
                key(episode.guid) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = onPlayer,
                        maxLines = 1,
                        modifier = Modifier
                            // Full width, so the fade sits at the edge of the
                            // space, not the end of the words: a short title
                            // ends well before it and is never touched.
                            .fillMaxWidth()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        0f to Color.Black,
                                        0.9f to Color.Black,
                                        1f to Color.Transparent
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                            .basicMarquee(
                                iterations = 1,
                                initialDelayMillis = 1_500
                            )
                    )
                }
                Text(
                    text = feed?.title.orEmpty(),
                    // Sea Glass sets the show in the sans, under the serif title.
                    style = if (tokens != null) MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                    else MaterialTheme.typography.titleMedium,
                    color = seaInk ?: onPlayerDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Sea Glass, portrait: a line of small capitals at the top, the
            // episode's date and length, with download and cast at its end.
            if (seaPortrait && fade > 0f) {
                val length = if (durationMs > 0) durationMs else episode.durationMs
                val eyebrow = listOf(formatDate(episode.pubDate), formatLength(length))
                    .filter { it.isNotBlank() }
                    .joinToString("  ·  ")
                    .uppercase()
                Row(
                    Modifier
                        .offset(colLeft, statusInset + 14.dp)
                        .width(colW)
                        .height(44.dp)
                        .graphicsLayer { alpha = fade },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = eyebrow,
                        style = MaterialTheme.typography.labelMedium,
                        color = seaInk ?: onPlayerDim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    DownloadIconButton(download, tint = onPlayer) {
                        haptics.play(Haptic.Tap)
                        if (download == null || download.state == com.glasscast.app.data.DownloadState.FAILED) onDownload()
                        else onRemoveDownload()
                    }
                    PlayerIconButton(Icons.Filled.Cast, tr("Play on another device"), tint = onPlayer) {
                        haptics.play(Haptic.Tap)
                        showDevices = true
                    }
                }
            }

            // Download and cast.
            if (fade > 0f && !seaPortrait) {
                Row(
                    Modifier
                        .offset(colLeft + colW - iconsWidth, titleTop + 8.dp)
                        .width(iconsWidth)
                        .graphicsLayer { alpha = fade },
                    horizontalArrangement = Arrangement.spacedBy(if (playerGlass != null) 8.dp else 0.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DownloadIconButton(download, tint = onPlayer, glass = playerGlass) {
                        haptics.play(Haptic.Tap)
                        if (download == null || download.state == com.glasscast.app.data.DownloadState.FAILED) onDownload()
                        else onRemoveDownload()
                    }
                    PlayerIconButton(Icons.Filled.Cast, tr("Play on another device"), tint = onPlayer, glass = playerGlass) {
                        haptics.play(Haptic.Tap)
                        showDevices = true
                    }
                }
            }

            // The wave (a rod of glass in the Glass look), thinning into the header's.
            val sliderRect = mix(
                DpRect(colLeft, sliderTop, colW, 30.dp),
                DpRect(cLeft, compactWaveTop, cW, 18.dp),
                e
            )
            WavySlider(
                progress = if (scrubbing) scrubValue else progress,
                playing = isPlaying,
                color = seaInk ?: onPlayer,
                thumbGlass = pigment,
                thumbGlassDark = tokens?.dark == true,
                // Glass: a still rod of glass that swells under the finger.
                glassLine = playerGlass != null,
                enabled = durationMs > 0,
                height = sliderRect.h,
                amplitude = mix(4.dp, 3.dp, e),
                wavelength = mix(30.dp, 26.dp, e),
                strokeWidth = mix(4.5.dp, 3.5.dp, e),
                showThumb = e < 0.5f,
                voice = { com.glasscast.app.player.VoiceLevel.current() },
                marks = adMarks,
                markColor = colors.adMark,
                // Where the bookmarks are, as small dots under the line.
                ticks = if (durationMs > 0) bookmarks.map { it.positionMs.toFloat() / durationMs } else emptyList(),
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
                        .offset(colLeft, timesTop)
                        .width(colW)
                        .graphicsLayer { alpha = fade }
                ) {
                    Text(
                        text = formatTime(shownPosition),
                        style = MaterialTheme.typography.bodySmall,
                        color = onPlayerDim
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemaining(shownPosition, durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = onPlayerDim
                    )
                }
            }

            // The transport: five buttons at rest, three in the header. Back
            // and forward 30 and play travel to their compact slots; restart
            // and next narrow away into the group's ends as they fade.
            val fullSlots = DpRect(colLeft, groupTop, colW, groupH)
                .split(listOf(0.78f, 1f, 1.7f, 1f, 0.78f), 4.dp)
            val compactSlots = DpRect(cLeft + cW - 178.dp, compactTop, 178.dp, 48.dp)
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
            // Liquid Glass: Apple's player has no tiles under its controls,
            // just the symbols on the picture's own blur.
            val liquidLook = com.glasscast.app.ui.liquid.LocalLiquid.current != null
            val quiet = if (tokens != null || liquidLook) Color.Transparent else OnPlayer.copy(alpha = 0.16f)
            val leftEdge = compactSlots[0].copy(w = 0.dp)
            val rightEdge = compactSlots[2].copy(x = compactSlots[2].x + compactSlots[2].w, w = 0.dp)
            // Sea Glass: the skips are clear pebbles, drawn once at their full
            // size and scaled into the header, so the morph never redraws them.
            val skipGlass = tokens?.let { SkipGlass(it.clearGlass(pigment), it.dark, minOf(fullSlots[1].w, fullSlots[1].h).coerceAtLeast(8.dp)) }

            if (fade > 0f) {
                MorphButton(
                    rect = mix(fullSlots[0], leftEdge, e),
                    corners = Corners(30.dp, 10.dp, 10.dp, 30.dp),
                    container = quiet, content = onPlayer,
                    icon = Icons.Filled.SkipPrevious, description = tr("Restart episode"),
                    iconSize = 24.dp, alpha = fade, glassBackdrop = playerGlass
                ) {
                    haptics.play(Haptic.SkipBack)
                    onRestartEpisode()
                }
            }
            MorphButton(
                rect = mix(fullSlots[1], compactSlots[0], e),
                corners = mix(Corners(10.dp, 10.dp, 10.dp, 10.dp), Corners(20.dp, 8.dp, 8.dp, 20.dp), e),
                container = quiet, content = onPlayer,
                icon = skipBackIcon(skipSeconds), description = tr("Back {0} seconds", skipSeconds),
                iconSize = if (liquidLook) mix(36.dp, 22.dp, e) else mix(28.dp, 20.dp, e),
                glass = skipGlass?.copy(seed = 32),
                glassBackdrop = playerGlass
            ) {
                haptics.play(Haptic.SkipBack)
                onSeekBy(-skipSeconds * 1_000L)
            }
            Box(
                Modifier
                    .offset(mix(fullSlots[2].x, compactSlots[1].x, e), mix(fullSlots[2].y, compactSlots[1].y, e))
                    .size(mix(fullSlots[2].w, compactSlots[1].w, e), mix(fullSlots[2].h, compactSlots[1].h, e))
            ) {
                val c = mix(playCorner, playCornerSmall, e)
                if (tokens != null && pigment != null) {
                    // Drawn at its full size and scaled, like the skips.
                    val fullH = fullSlots[2].h.coerceAtLeast(8.dp)
                    val fullW = minOf(fullSlots[2].w, fullH * 1.25f).coerceAtLeast(8.dp)
                    val nowH = mix(fullSlots[2].h, compactSlots[1].h, e)
                    SeaPlayPebble(
                        isPlaying = isPlaying,
                        pigment = pigment,
                        dark = tokens.dark,
                        size = fullH,
                        width = fullW,
                        iconSize = 38.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .requiredSize(fullW, fullH)
                            .graphicsLayer {
                                val k = nowH / fullH
                                scaleX = k
                                scaleY = k
                            }
                    ) {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    }
                } else if (playerGlass != null) {
                    // Liquid Glass: play is a disc of clear glass on the
                    // picture, round while paused and a softer square while
                    // playing (the Original button's change of shape), and it
                    // gives a little under the finger.
                    val disc = minOf(mix(fullSlots[2].w, compactSlots[1].w, e), mix(fullSlots[2].h, compactSlots[1].h, e))
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val squash by animateFloatAsState(
                        targetValue = if (pressed) 0.92f else 1f,
                        animationSpec = spring(dampingRatio = 0.55f),
                        label = "liquidPlayPress"
                    )
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(disc)
                            .graphicsLayer {
                                scaleX = squash
                                scaleY = squash
                            }
                            .liquidGlass(playerGlass, com.glasscast.app.ui.liquid.LiquidMaterial.disc(), corner = c, dark = true)
                            .clip(RoundedCornerShape(c))
                            .clickable(interactionSource = interaction, indication = null) {
                                haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                                onPlayPause()
                            }
                            .semantics { contentDescription = if (isPlaying) tr("Pause") else tr("Play") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = OnPlayer,
                            modifier = Modifier.size(mix(42.dp, 26.dp, e))
                        )
                    }
                } else {
                MorphButton(
                    rect = DpRect(0.dp, 0.dp, mix(fullSlots[2].w, compactSlots[1].w, e), mix(fullSlots[2].h, compactSlots[1].h, e)),
                    corners = Corners(c, c, c, c),
                    container = OnPlayer,
                    content = PlayGlyph,
                    icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    description = if (isPlaying) tr("Pause") else tr("Play"),
                    iconSize = mix(38.dp, 24.dp, e)
                ) {
                    haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                    onPlayPause()
                }
                }
                if (buffering) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = (pigment?.ink ?: if (playerGlass != null) OnPlayer else PlayGlyph).copy(alpha = 0.55f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(mix(52.dp, 32.dp, e))
                    )
                }
            }
            MorphButton(
                rect = mix(fullSlots[3], compactSlots[2], e),
                corners = mix(Corners(10.dp, 10.dp, 10.dp, 10.dp), Corners(8.dp, 20.dp, 20.dp, 8.dp), e),
                container = quiet, content = onPlayer,
                icon = skipForwardIcon(skipSeconds), description = tr("Forward {0} seconds", skipSeconds),
                iconSize = if (liquidLook) mix(36.dp, 22.dp, e) else mix(28.dp, 20.dp, e),
                glass = skipGlass?.copy(seed = 33),
                glassBackdrop = playerGlass
            ) {
                haptics.play(Haptic.SkipForward)
                onSeekBy(skipSeconds * 1_000L)
            }
            if (fade > 0f) {
                MorphButton(
                    rect = mix(fullSlots[4], rightEdge, e),
                    corners = Corners(10.dp, 30.dp, 30.dp, 10.dp),
                    container = quiet, content = onPlayer,
                    icon = Icons.Filled.SkipNext,
                    description = if (upNextCount > 0) tr("Next episode") else tr("Nothing queued"),
                    iconSize = 24.dp, alpha = fade, enabled = upNextCount > 0, glassBackdrop = playerGlass
                ) {
                    haptics.play(Haptic.SkipForward)
                    onSkipNext()
                }
            }

            if (fade > 0f && !shortLandscape) {
                Box(
                    Modifier
                        .offset(colLeft, volumeTop)
                        .width(colW)
                        .graphicsLayer { alpha = fade }
                ) {
                    VolumeRow(accent = seaInk ?: onPlayer, onGround = onPlayerDim)
                }
            }

            // ---- the panel: strip at rest, the four tabs when pulled up ----
            PlayerPanel(
                expand = e,
                tab = panelTab,
                art = art,
                upNextCount = upNextCount,
                bookmarkCount = bookmarks.size,
                speed = speed,
                timerArmed = timerArmed,
                surface = panelColor,
                accent = panelAccent,
                ink = onPlayer,
                glass = pigment,
                glassBackdrop = playerGlass,
                onTab = { openPanel(it) },
                onDrag = { dragPanel(it) },
                onDragEnd = { settlePanel(it) },
                modifier = Modifier
                    .then(
                        if (landscape) Modifier.offset(x = colLeft - 10.dp).width(colW + side + endInset + 10.dp)
                        else Modifier.fillMaxWidth()
                    )
                    .height(maxHeight - expandedTop)
                    .offset(y = mix(collapsedTop, expandedTop, e))
            ) { tab ->
                when (tab) {
                    PanelTab.UP_NEXT -> PanelQueue(
                        upNext = upNext,
                        feedFor = feedFor,
                        // On glass the rows are the glass itself.
                        surface = if (playerGlass != null) Color.Transparent else panelColor,
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
                    PanelTab.BOOKMARKS -> PanelBookmarks(
                        bookmarks = bookmarks,
                        positionMs = positionMs,
                        surface = if (playerGlass != null) Color.Transparent else panelColor,
                        accent = panelAccent,
                        onAdd = onAddBookmark,
                        onSeekTo = onSeekTo,
                        onMemo = onBookmarkMemo,
                        onRemove = onRemoveBookmark,
                        bottomInset = navInset
                    )
                    PanelTab.SPEED -> SpeedPanel(
                        speed = speed,
                        accent = panelAccent,
                        onSpeedChange = onSpeedChange,
                        bottomInset = navInset,
                        skipSilence = skipSilence,
                        voiceBoost = voiceBoost,
                        onSkipSilence = onSkipSilenceChange,
                        onVoiceBoost = onVoiceBoostChange,
                        skipAds = skipAds,
                        onSkipAds = onSkipAdsChange
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
    /** Sea Glass: a pebble instead of the filled shape. */
    glass: SkipGlass? = null,
    /** Liquid Glass: a disc of glass rises under the symbol while it's held. */
    glassBackdrop: com.glasscast.app.ui.liquid.LiquidBackdrop? = null,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "morphPress"
    )
    if (glass != null) {
        val glow by animateFloatAsState(if (pressed) 1f else 0f, tween(if (pressed) 90 else 260), label = "morphGlow")
        Box(
            Modifier
                .offset(rect.x, rect.y)
                .size(rect.w, rect.h)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                    this.alpha = alpha
                }
                .clickable(
                    enabled = enabled && alpha > 0.3f,
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .requiredSize(glass.reference)
                    .graphicsLayer {
                        val k = minOf(rect.w, rect.h) / glass.reference
                        scaleX = k
                        scaleY = k
                    }
                    .seaGlass(
                        pigment = glass.pigment,
                        recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Pebble,
                        seed = glass.seed,
                        dark = glass.dark,
                        glow = { glow }
                    )
            )
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = if (enabled) glass.pigment.ink else glass.pigment.ink.copy(alpha = 0.35f),
                modifier = Modifier.size(iconSize)
            )
        }
        return
    }
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
        if (glassBackdrop != null) {
            val lift by animateFloatAsState(
                targetValue = if (pressed) 1f else 0f,
                animationSpec = tween(if (pressed) 110 else 320),
                label = "morphLiquid"
            )
            if (lift > 0.01f) {
                Box(
                    Modifier
                        .size(minOf(rect.w, rect.h) * 0.92f)
                        .graphicsLayer {
                            this.alpha = lift
                            val k = 0.82f + 0.18f * lift
                            scaleX = k
                            scaleY = k
                        }
                        .liquidGlass(glassBackdrop, com.glasscast.app.ui.liquid.LiquidMaterial.disc(), dark = true)
                )
            }
        }
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
 * screen's height, centered), so whatever sat just below the cover's bottom edge
 * came from the *middle* of the image. On a cover whose center differs from its
 * foot, the color broke at the seam and settled into a mud of the whole.
 *
 * Cider mirrors the cover vertically underneath itself. Then the first thing
 * below the bottom edge is that same edge, reflected, and every color carries
 * straight on down — the legs in a photo continue as their own reflection, a
 * colored band at the foot of the art continues as the same band. The pair
 * (cover over its reflection) is blurred as one image, so the blur runs across
 * the seam instead of stopping at it, and the sharp cover laid on top dissolves
 * into its own blurred self before the reflection begins.
 *
 * The reflection runs to the far edge of the screen ([length]). It used to be
 * exactly as long as the cover, which covers a portrait phone, but a phone on
 * its side is more than twice as wide as it is tall: the reflection stopped
 * short and left a strip of the plain blur below as a block of another color
 * down the right side. Where the screen is longer than twice the cover, the
 * reflection is stretched to fill it.
 *
 * Underneath, a tiny full-screen blur shows before the pair is ready, and a
 * scrim deepens toward the controls so white type always reads.
 */
@Composable
private fun PlayerBackdrop(url: String, band: Dp, length: Dp, sideways: Boolean, aspect: Float, modifier: Modifier = Modifier) {
    // Rounded, so the mirror is made once per layout, not on every tiny change.
    val aspectKey = (aspect * 100f).roundToInt() / 100f
    val reach = if (band > 0.dp) ((length / band).coerceIn(2f, 4f) * 20f).roundToInt() / 20f else 2f
    val store = LocalImageStore.current
    // Both layers are made once per cover, off the main thread, as tiny
    // pre-blurred bitmaps (see SoftBitmaps.kt). They used to be live
    // Modifier.blur layers — two full-screen GPU blurs recomputed on every
    // frame, and the player redraws every frame while the wave moves.
    var field by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var mirror by remember(url, sideways, aspectKey, reach) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url, sideways, aspectKey, reach) {
        if (url.isBlank()) return@LaunchedEffect
        val source = store.peek(url, 160) ?: store.load(url, 160) ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val soft = softened(source).asImageBitmap()
            // Portrait: the reflection below. Landscape: to the right, flipped
            // sideways — stretching the portrait one sideways smeared the
            // cover's right edge across the controls.
            val pair = (if (sideways) softenedMirrorSideways(source, aspectKey, reach) else softenedMirror(source, aspectKey, reach)).asImageBitmap()
            withContext(Dispatchers.Main) {
                field = soft
                mirror = pair
            }
        }
    }

    Box(modifier) {
        field?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,   // bilinear: the stretch is the blur
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.3f
                        scaleY = 1.3f
                    }
            )
        }

        // Cover over its own reflection, softened as one image — so the blur
        // runs across the seam — and stretched to the cover band plus its
        // mirror below, to the screen's far edge.
        mirror?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = if (sideways) {
                    Modifier.width(band * reach).fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth().height(band * reach)
                }
            )
        }

        // Clear over the cover, deepening through the reflection toward the
        // controls — the "common color" the foot settles into is the blurred
        // reflection darkened, so it's always this cover's own color.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (sideways) {
                        Brush.horizontalGradient(
                            0.00f to Color.Black.copy(alpha = 0.06f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.62f to Color.Black.copy(alpha = 0.34f),
                            1.00f to Color.Black.copy(alpha = 0.62f)
                        )
                    } else {
                        Brush.verticalGradient(
                            0.00f to Color.Black.copy(alpha = 0.06f),
                            0.42f to Color.Black.copy(alpha = 0.16f),
                            0.62f to Color.Black.copy(alpha = 0.34f),
                            1.00f to Color.Black.copy(alpha = 0.62f)
                        )
                    }
                )
        )
    }
}

/** The sharp cover, erased at its foot — a mask, never a color overlay. */
@Composable
private fun PlayerArtwork(url: String, modifier: Modifier = Modifier, sideways: Boolean = false) {
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
                // The same dissolve either way: downward in portrait, toward the
                // controls in landscape.
                val stops = arrayOf(
                    0.00f to Color.Black,
                    0.56f to Color.Black,
                    0.74f to Color.Black.copy(alpha = 0.70f),
                    0.88f to Color.Black.copy(alpha = 0.28f),
                    1.00f to Color.Transparent
                )
                drawRect(
                    brush = if (sideways) Brush.horizontalGradient(*stops) else Brush.verticalGradient(*stops),
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

/** Liquid Glass: a small disc of glass under a player button, refracting the picture. */
private fun Modifier.playerDisc(glass: com.glasscast.app.ui.liquid.LiquidBackdrop?): Modifier =
    if (glass == null) this
    else this
        .padding(2.dp)
        .liquidGlass(glass, com.glasscast.app.ui.liquid.LiquidMaterial.disc(), dark = true)

@Composable
private fun DownloadIconButton(
    entry: com.glasscast.app.data.DownloadEntry?,
    tint: Color = OnPlayer,
    glass: com.glasscast.app.ui.liquid.LiquidBackdrop? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .playerDisc(glass)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (entry?.state) {
            com.glasscast.app.data.DownloadState.QUEUED, com.glasscast.app.data.DownloadState.RUNNING ->
                CircularProgressIndicator(
                    progress = { entry.progress.coerceAtLeast(0.04f) },
                    strokeWidth = 2.5.dp,
                    color = tint,
                    trackColor = tint.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            com.glasscast.app.data.DownloadState.DONE ->
                Icon(Icons.Filled.DownloadForOffline, contentDescription = tr("Remove download"), tint = tint, modifier = Modifier.size(24.dp))
            else ->
                Icon(Icons.Outlined.DownloadForOffline, contentDescription = tr("Download"), tint = tint, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PlayerIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color = OnPlayer,
    glass: com.glasscast.app.ui.liquid.LiquidBackdrop? = null,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .playerDisc(glass)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(if (glass != null) 21.dp else 24.dp))
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

/** The Sea Glass cover's corner, here and where the flight lands. */
private val SeaCoverCorner = 24.dp

/** A skip button's glass: its pigment, and the size it's drawn at before scaling. */
private data class SkipGlass(
    val pigment: com.glasscast.app.ui.seaglass.Pigment,
    val dark: Boolean,
    val reference: Dp,
    val seed: Int = 32
)

/**
 * Sea Glass's ground behind the player: the page (paper, or the tide at
 * night) with the cover's glass color washed across its upper part, as light
 * through colored glass would pool on a table.
 */
@Composable
private fun SeaPlayerGround(
    wash: com.glasscast.app.ui.seaglass.SeaWash,
    dark: Boolean,
    landscape: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.drawBehind {
            val center = if (landscape) Offset(size.width * 0.28f, size.height * 0.5f) else Offset(size.width / 2f, size.height * 0.30f)
            drawSeaWash(wash, dark, center, maxOf(size.width, size.height) * 0.62f)
        }
    )
}

/** The cover as Sea Glass sets it: inset on the page, rounded, whole. */
@Composable
private fun SeaPlayerCover(url: String, modifier: Modifier = Modifier) {
    val store = LocalImageStore.current
    var bitmap by remember(url) { mutableStateOf(store.peek(url, 1080)) }
    LaunchedEffect(url) {
        if (bitmap == null && url.isNotBlank()) bitmap = store.load(url, 1080)
    }
    Box(modifier.clip(RoundedCornerShape(SeaCoverCorner))) {
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

/**
 * An episode's length in words, for the player's line of small capitals:
 * "32 min", "1 hr 4 min". (formatCompact's "32m" reads as 32 million once
 * it's set in capitals.)
 */
private fun formatLength(ms: Long): String {
    if (ms <= 0) return ""
    val minutes = ((ms + 30_000) / 60_000).toInt().coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> tr("{0} min", m)
        m == 0 -> tr("{0} hr", h)
        else -> tr("{0} hr {1} min", h, m)
    }
}
