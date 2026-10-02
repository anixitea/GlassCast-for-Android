package com.glasscast.app.ui

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
import com.glasscast.app.player.AdSkipper
import com.glasscast.app.data.usable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.animation.core.FastOutLinearInEasing

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
        endOfEpisode -> tr("End of episode")
        timerRemaining != null -> formatTime(timerRemaining ?: 0L)
        else -> tr("Timer")
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

    // Landscape closes as one sheet sliding away (see settle).
    val landscapeScreen = LocalConfiguration.current.let {
        it.screenWidthDp > it.screenHeightDp && it.screenHeightDp >= 480
    }
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
            // Zoomed a little past square, as Cider's is — but never so tall
            // that the title lands on the cover's sharp part. The title sits
            // 292dp above the panel strip (see the morph below); the cover is
            // cut so that's 86% of the way down it, where the dissolve is past
            // 70%. Phones never reach the cap; tall tablet screens did, and
            // their titles sat over the sharp middle of the cover.
            val titleTopPortrait = maxHeight - PanelHeaderHeight -
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() - 292.dp
            // (Floored, for a phone turned sideways: too short for the
            // landscape layout, it keeps this one, and the cap alone would
            // leave it a sliver of cover.)
            val artHeight = minOf(maxWidth * 1.2f, titleTopPortrait / 0.86f)
                .coerceAtLeast(minOf(maxWidth, maxHeight) * 0.6f)
            // Landscape on a tablet: the portrait player turned on its side.
            // The cover fills the height on the left and dissolves rightward
            // into its blur; the controls keep their order in a column beside
            // it, and the panel rises inside that column. (The portrait cover,
            // maxWidth × 1.2 tall, was taller than a landscape screen: it hid
            // every control, so they seemed to load late while it flew in.)
            val landscape = maxWidth > maxHeight && maxHeight >= 480.dp
            val artWidth = if (landscape) minOf(maxHeight, maxWidth * 0.55f) else maxWidth

            PlayerBackdrop(
                url = art,
                band = if (landscape) artWidth else artHeight,
                sideways = landscape,
                aspect = if (landscape) maxHeight / artWidth else artHeight / maxWidth,
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

            // The handle is the dismiss affordance now, in place of a chevron:
            // the gesture is drag-down, so the hint should look draggable.
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .aboveFlyingArtwork(LocalPlayerScope.current)
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
            // Not in landscape: there the panel covers only the column and
            // the compact controls sit over the blur, never the cover — a
            // dimmed column read as a dark box cut out of the screen.
            if (e > 0f && !landscape) {
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
            // The controls' column: the full width in portrait (the same
            // numbers as ever); beside the cover in landscape, starting just
            // inside its dissolve. The compact header above an open panel
            // follows the column too.
            val colLeft = if (landscape) artWidth - 24.dp else side
            val colW = if (landscape) w - colLeft - side else w - side * 2
            val cLeft = if (landscape) colLeft else 24.dp
            val cW = if (landscape) colW else w - 48.dp
            // Landscape: the stack (title to volume, 274dp) centers in the
            // height above the strip, beside a cover that fills it, instead
            // of sitting on the strip as it does under a portrait cover.
            val lift = if (landscape) {
                ((collapsedTop - 18.dp - (statusInset + 32.dp) - 274.dp) / 2).coerceAtLeast(0.dp)
            } else {
                0.dp
            }
            val volumeTop = collapsedTop - 18.dp - 32.dp - lift
            val groupTop = volumeTop - 22.dp - 76.dp
            val timesTop = groupTop - 16.dp - 18.dp
            val sliderTop = timesTop - 6.dp - 30.dp
            val titleTop = sliderTop - 14.dp - 60.dp

            // Title and show name, scaled down into the header.
            // The icons beside the title: download and cast. (A moon joined
            // them while a timer ran; it took room from the title and repeated
            // the one in the panel bar, which now shows a running timer.)
            val iconsWidth = 96.dp
            val titleFull = DpRect(colLeft, titleTop, colW - iconsWidth - 4.dp, 60.dp)
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
                        color = OnPlayer,
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
                    style = MaterialTheme.typography.titleMedium,
                    color = OnPlayer.copy(alpha = 0.66f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Download and cast.
            if (fade > 0f) {
                Row(
                    Modifier
                        .offset(colLeft + colW - iconsWidth, titleTop + 8.dp)
                        .width(iconsWidth)
                        .graphicsLayer { alpha = fade },
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DownloadIconButton(download) {
                        haptics.play(Haptic.Tap)
                        if (download == null || download.state == com.glasscast.app.data.DownloadState.FAILED) onDownload()
                        else onRemoveDownload()
                    }
                    PlayerIconButton(Icons.Filled.Cast, tr("Play on another device")) {
                        haptics.play(Haptic.Tap)
                        showDevices = true
                    }
                }
            }

            // The wave, thinning into the header's.
            val sliderRect = mix(
                DpRect(colLeft, sliderTop, colW, 30.dp),
                DpRect(cLeft, compactTop + 60.dp, cW, 18.dp),
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
                voice = { com.glasscast.app.player.VoiceLevel.current() },
                marks = adMarks,
                markColor = colors.adMark,
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
            val fullSlots = DpRect(colLeft, groupTop, colW, 76.dp)
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
            val quiet = OnPlayer.copy(alpha = 0.16f)
            val leftEdge = compactSlots[0].copy(w = 0.dp)
            val rightEdge = compactSlots[2].copy(x = compactSlots[2].x + compactSlots[2].w, w = 0.dp)

            if (fade > 0f) {
                MorphButton(
                    rect = mix(fullSlots[0], leftEdge, e),
                    corners = Corners(30.dp, 10.dp, 10.dp, 30.dp),
                    container = quiet, content = OnPlayer,
                    icon = Icons.Filled.SkipPrevious, description = tr("Restart episode"),
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
                icon = Icons.Filled.Replay30, description = tr("Back 30 seconds"),
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
                    description = if (isPlaying) "Pause" else tr("Play"),
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
                icon = Icons.Filled.Forward30, description = tr("Forward 30 seconds"),
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
                    description = if (upNextCount > 0) "Next episode" else tr("Nothing queued"),
                    iconSize = 24.dp, alpha = fade, enabled = upNextCount > 0
                ) {
                    haptics.play(Haptic.SkipForward)
                    onSkipNext()
                }
            }

            if (fade > 0f) {
                Box(
                    Modifier
                        .offset(colLeft, volumeTop)
                        .width(colW)
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
                    .then(
                        if (landscape) Modifier.offset(x = colLeft - 10.dp).width(colW + side + 10.dp)
                        else Modifier.fillMaxWidth()
                    )
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
 * Underneath, a tiny full-screen blur fills anything below the reflection on
 * very tall screens, and a scrim deepens toward the controls so white type
 * always reads.
 */
@Composable
private fun PlayerBackdrop(url: String, band: Dp, sideways: Boolean, aspect: Float, modifier: Modifier = Modifier) {
    // Rounded, so the mirror is made once per layout, not on every tiny change.
    val aspectKey = (aspect * 100f).roundToInt() / 100f
    val store = LocalImageStore.current
    // Both layers are made once per cover, off the main thread, as tiny
    // pre-blurred bitmaps (see SoftBitmaps.kt). They used to be live
    // Modifier.blur layers — two full-screen GPU blurs recomputed on every
    // frame, and the player redraws every frame while the wave moves.
    var field by remember(url) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var mirror by remember(url, sideways, aspectKey) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(url, sideways, aspectKey) {
        if (url.isBlank()) return@LaunchedEffect
        val source = store.peek(url, 160) ?: store.load(url, 160) ?: return@LaunchedEffect
        withContext(Dispatchers.Default) {
            val soft = softened(source).asImageBitmap()
            // Portrait: the reflection below. Landscape: to the right, flipped
            // sideways — stretching the portrait one sideways smeared the
            // cover's right edge across the controls.
            val pair = (if (sideways) softenedMirrorSideways(source, aspectKey) else softenedMirror(source, aspectKey)).asImageBitmap()
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
        // mirror below.
        mirror?.let { image ->
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.Low,
                modifier = if (sideways) {
                    Modifier.width(band * 2).fillMaxHeight()
                } else {
                    Modifier.fillMaxWidth().height(band * 2)
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

@Composable
private fun DownloadIconButton(
    entry: com.glasscast.app.data.DownloadEntry?,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (entry?.state) {
            com.glasscast.app.data.DownloadState.QUEUED, com.glasscast.app.data.DownloadState.RUNNING ->
                CircularProgressIndicator(
                    progress = { entry.progress.coerceAtLeast(0.04f) },
                    strokeWidth = 2.5.dp,
                    color = OnPlayer,
                    trackColor = OnPlayer.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp)
                )
            com.glasscast.app.data.DownloadState.DONE ->
                Icon(Icons.Filled.DownloadForOffline, contentDescription = tr("Remove download"), tint = OnPlayer, modifier = Modifier.size(24.dp))
            else ->
                Icon(Icons.Outlined.DownloadForOffline, contentDescription = tr("Download"), tint = OnPlayer, modifier = Modifier.size(24.dp))
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
