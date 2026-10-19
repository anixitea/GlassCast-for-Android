package com.glasscast.app.tv

import com.glasscast.app.ui.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.player.SleepTimer
import com.glasscast.app.player.VoiceLevel
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.LocalImageStore
import com.glasscast.app.ui.WavySlider
import com.glasscast.app.ui.chromeButton
import com.glasscast.app.ui.chromeSurface
import com.glasscast.app.ui.formatDate
import com.glasscast.app.ui.formatTime
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.stripHtml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import android.os.SystemClock
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt
import kotlin.random.Random
import com.glasscast.app.player.AdSkipper
import com.glasscast.app.ui.adMark
import com.glasscast.app.data.usable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import com.glasscast.app.data.Bookmark
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import com.glasscast.app.ui.rememberSteadyPlaying
import com.glasscast.app.ui.seaglass.GlassRecipe
import com.glasscast.app.ui.seaglass.LocalSea
import com.glasscast.app.ui.seaglass.seaCoverPool
import com.glasscast.app.ui.seaglass.drawSeaWash
import com.glasscast.app.ui.seaglass.seaPigment
import com.glasscast.app.ui.seaglass.seaWash

private enum class TvPanel(val label: String) {
    UP_NEXT(tr("Up Next")), INFO(tr("Info")), BOOKMARKS(tr("Bookmarks")), SOUND(tr("Speed & sound")), TIMER(tr("Timer"))
}

private val Speeds = listOf(0.8f, 1f, 1.2f, 1.5f, 1.8f, 2f)

/**
 * The TV player, in the phone's language, laid out for a 16:9 panel.
 *
 * The blurred cover fills the screen; the cover sits large on the left, and on
 * the right, top to bottom: the show, the title (scrolling once when long),
 * the voice-reactive wave as the progress bar (focus it, then left/right to
 * seek ten seconds), the phone's connected transport group, and beneath it the
 * phone's panel bar — Up Next, Info, Speed & sound, Timer — each opening a
 * side panel with the same four tabs as the phone's pull-up panel.
 *
 * Group buttons keep their shapes; focus fills one with the cover's pale
 * tone. Only play/pause changes shape — oval paused, rounded rectangle
 * playing — as on the phone. Back closes an open panel first, then the player.
 */
@Composable
fun TvPlayerScreen(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    speed: Float,
    colors: ArtworkColors,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipNext: () -> Unit,
    onRestart: () -> Unit,
    /** How far back and forward skip (Settings.skipSeconds). */
    skipSeconds: Int = 30,
    onSpeedChange: (Float) -> Unit,
    onPlayFromUpNext: (Int) -> Unit,
    onRemoveFromUpNext: (Episode) -> Unit,
    onClose: () -> Unit,
    skipSilence: Boolean = false,
    voiceBoost: Boolean = false,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onVoiceBoostChange: (Boolean) -> Unit = {},
    skipAds: Boolean = false,
    onSkipAdsChange: (Boolean) -> Unit = {},
    /** This episode's bookmarks, in order. */
    bookmarks: List<Bookmark> = emptyList(),
    onAddBookmark: () -> Unit = {},
    onRemoveBookmark: (Long) -> Unit = {},
    /** Moves an Up Next entry up (-1) or down (1), by guid; false if it couldn't. */
    onMoveInUpNext: (String, Int) -> Boolean = { _, _ -> false }
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val accent = colors.chromeButton
    val glyph = if (accent.luminance() > 0.5f) Color(0xFF15121A) else Color.White
    val playFocus = remember { FocusRequester() }
    // Sea Glass draws each button as its own pebble, so they stand apart.
    val groupGap = if (LocalSea.current != null) 12.dp else 4.dp
    var panel by remember { mutableStateOf<TvPanel?>(null) }
    var shownPanel by remember { mutableStateOf(TvPanel.UP_NEXT) }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
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

    // The timer, read the way the phone reads it. SleepTimer's deadline is on
    // the elapsed-realtime clock; this screen used to subtract the wall clock
    // from it, got a negative number, and showed 0:00 forever.
    val endsAt by SleepTimer.endsAtMs.collectAsStateWithLifecycle()
    val atEnd by SleepTimer.endOfEpisode.collectAsStateWithLifecycle()
    val lastDuration by SleepTimer.lastDurationMs.collectAsStateWithLifecycle()
    var remaining by remember { mutableStateOf(SleepTimer.remainingMs()) }
    LaunchedEffect(endsAt) {
        while (true) {
            remaining = SleepTimer.remainingMs()
            if (endsAt == null) break
            delay(1_000)
        }
    }
    val timerLabel = when {
        atEnd -> tr("End")
        remaining != null -> formatTime(remaining!!)
        else -> null
    }

    LaunchedEffect(panel) { if (panel == null) playFocus.requestWhenReady() }
    BackHandler { if (panel != null) panel = null else onClose() }

    fun open(p: TvPanel) {
        shownPanel = p
        panel = p
    }

    Box(Modifier.fillMaxSize()) {
        TvCoverBackdrop(url = art, colors = colors)

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TvSpacing.overscanH * 1.4f, vertical = TvSpacing.overscanV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(url = art, sizeDp = 400.dp, corner = 30.dp, modifier = Modifier.seaCoverPool(art, 30.dp))
            Spacer(Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = (feed?.title ?: "").uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                key(episode.guid) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(iterations = 1, initialDelayMillis = 1_500)
                    )
                }
                Spacer(Modifier.height(26.dp))
                TvScrubber(
                    progress = progress, playing = isPlaying, accent = accent, onSeekBy = onSeekBy,
                    marks = adMarks, markColor = colors.adMark
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(formatTime(positionMs), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.72f))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "-" + formatTime((durationMs - positionMs).coerceAtLeast(0L)),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
                Spacer(Modifier.height(28.dp))

                // Transport: the phone's connected group, in the phone's
                // proportions — five slots weighted 0.78 / 1 / 1.7 / 1 / 0.78
                // across the full column, so it fits whatever width the
                // column has. It used to be fixed widths totalling 512dp in a
                // 362dp column: Forward 30 got the leftovers and Next nothing.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(groupGap)) {
                    TvGroupButton(
                        corners = TvCorners(30.dp, 10.dp, 10.dp, 30.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(0.78f), seed = 3, bare = true, onClick = onRestart
                    ) {
                        Icon(Icons.Filled.SkipPrevious, tr("Restart"), tint = it, modifier = Modifier.size(30.dp))
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 5, onClick = { onSeekBy(-skipSeconds * 1_000L) }
                    ) {
                        Icon(com.glasscast.app.ui.skipBackIcon(skipSeconds), tr("Back {0} seconds", skipSeconds), tint = it, modifier = Modifier.size(32.dp))
                    }
                    TvPlayPauseButton(
                        isPlaying = isPlaying, accent = accent, glyph = glyph,
                        focusRequester = playFocus, modifier = Modifier.weight(1.7f), onClick = onPlayPause
                    )
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 6, onClick = { onSeekBy(skipSeconds * 1_000L) }
                    ) {
                        Icon(com.glasscast.app.ui.skipForwardIcon(skipSeconds), tr("Forward {0} seconds", skipSeconds), tint = it, modifier = Modifier.size(32.dp))
                    }
                    TvGroupButton(
                        corners = TvCorners(10.dp, 30.dp, 30.dp, 10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(0.78f), seed = 4, bare = true,
                        enabled = upNext.isNotEmpty(), onClick = onSkipNext
                    ) {
                        Icon(Icons.Filled.SkipNext, tr("Next"), tint = it, modifier = Modifier.size(30.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))

                // The panel bar: the phone's Cider pill as a connected group,
                // four equal slots under the transport. Icons, as on the phone
                // — labels didn't fit (Timer was a sliver in English and every
                // other language is longer); the side panel names each tab.
                // Values show where there is one: queue length, speed, the
                // timer's time left.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(groupGap)) {
                    TvGroupButton(
                        corners = TvCorners(30.dp, 10.dp, 10.dp, 30.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 11, onClick = { open(TvPanel.UP_NEXT) }
                    ) {
                        TvBarContent(Icons.AutoMirrored.Filled.QueueMusic, tr("Up Next"), upNext.size.takeIf { n -> n > 0 }?.toString(), it)
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 12, onClick = { open(TvPanel.INFO) }
                    ) {
                        TvBarContent(Icons.Outlined.Info, tr("Info"), null, it)
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 13, onClick = { open(TvPanel.BOOKMARKS) }
                    ) {
                        TvBarContent(Icons.Outlined.BookmarkBorder, tr("Bookmarks"), bookmarks.size.takeIf { n -> n > 0 }?.toString(), it)
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 14, onClick = { open(TvPanel.SOUND) }
                    ) {
                        TvBarContent(null, tr("Speed & sound"), speedLabel(speed), it)
                    }
                    TvGroupButton(
                        corners = TvCorners(10.dp, 30.dp, 30.dp, 10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), seed = 15, onClick = { open(TvPanel.TIMER) }
                    ) {
                        TvBarContent(Icons.Filled.Bedtime, tr("Sleep timer"), timerLabel, it)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = panel != null,
            enter = slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f)) { it } + fadeIn(),
            exit = slideOutHorizontally(tween(220)) { it } + fadeOut(tween(220)),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            TvSidePanel(
                tab = shownPanel,
                onTab = { open(it) },
                colors = colors,
                accent = accent,
                glyph = glyph,
                episode = episode,
                upNext = upNext,
                feedFor = feedFor,
                speed = speed,
                skipSilence = skipSilence,
                voiceBoost = voiceBoost,
                remaining = remaining,
                atEnd = atEnd,
                armedMinutes = if (endsAt != null) (lastDuration / 60_000L).toInt() else null,
                onPlayFromUpNext = onPlayFromUpNext,
                onRemoveFromUpNext = onRemoveFromUpNext,
                onSpeedChange = onSpeedChange,
                onSkipSilenceChange = onSkipSilenceChange,
                onVoiceBoostChange = onVoiceBoostChange,
                skipAds = skipAds,
                onSkipAdsChange = onSkipAdsChange,
                positionMs = positionMs,
                bookmarks = bookmarks,
                onAddBookmark = onAddBookmark,
                onRemoveBookmark = onRemoveBookmark,
                onSeekTo = onSeekTo,
                onMoveInUpNext = onMoveInUpNext
            )
        }
    }
}

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

/** One panel-bar slot: an icon, a value, or both. */
@Composable
private fun TvBarContent(icon: ImageVector?, description: String, value: String?, tint: Color) {
    Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(24.dp))
        }
        if (value != null) {
            if (icon != null) Spacer(Modifier.width(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/** A group button's corners, in the phone's order. */
private data class TvCorners(val topStart: Dp, val topEnd: Dp, val bottomEnd: Dp, val bottomStart: Dp) {
    companion object {
        fun all(radius: Dp) = TvCorners(radius, radius, radius, radius)
    }
}

/**
 * Play/pause — the one button whose shape means something: an oval while
 * paused, a rounded rectangle while playing (38dp ⇄ 22dp at 76dp tall), on
 * the phone's spring. Its own composable so the morph recomposes this button
 * and not the player.
 */
@Composable
private fun TvPlayPauseButton(
    isPlaying: Boolean,
    accent: Color,
    glyph: Color,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Sea Glass: a tumbled pebble that turns over when it's pressed, and only
    // then, its glyph held steady through the beat of buffering a skip causes
    // (as on the phone).
    if (LocalSea.current != null) {
        val turns = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val shown = rememberSteadyPlaying(isPlaying)
        TvGroupButton(
            corners = TvCorners.all(38.dp), height = 76.dp,
            accent = accent, glyph = glyph, primary = true,
            focusRequester = focusRequester, modifier = modifier,
            seed = 8,
            turn = { turns.value },
            onClick = {
                shown.pressed()
                scope.launch { turns.animateTo(turns.targetValue + 1f, tween(520, easing = FastOutSlowInEasing)) }
                onClick()
            }
        ) {
            Icon(
                if (shown.value) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                if (shown.value) "Pause" else tr("Play"),
                tint = it,
                modifier = Modifier.size(40.dp)
            )
        }
        return
    }
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 22.dp else 38.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "playShape"
    )
    // The spring overshoots a touch past 38 on the way to the oval; a corner
    // can't be more than half the height, so hold it there.
    val c = corner.coerceIn(0.dp, 38.dp)
    TvGroupButton(
        corners = TvCorners.all(c), height = 76.dp,
        accent = accent, glyph = glyph, primary = true,
        focusRequester = focusRequester, modifier = modifier, onClick = onClick
    ) {
        Icon(
            if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            if (isPlaying) "Pause" else tr("Play"),
            tint = it,
            modifier = Modifier.size(40.dp)
        )
    }
}

/**
 * One button of a connected group, as on the phone: its shape is fixed —
 * outer ends round, inner corners tight — and never changes with focus.
 *
 * Focus is a fill: quiet glass at rest, the cover's pale tone when focused,
 * the glyph flipping to stay legible. Only one button in the player is ever
 * filled, so there is never a question of which one the remote is on. (Play
 * used to be filled all the time, so a focused Back 30 made two pink buttons
 * side by side.) No ring and no grow — in a group with 4dp gaps, growing
 * collides with the neighbors. Select squashes it slightly, as a press does
 * on the phone.
 *
 * The fill and the squash are read in draw and in the layer, so a focus move
 * or a press never recomposes the button's content. [primary] (play) rests a
 * little brighter than the rest.
 */
@Composable
private fun TvGroupButton(
    corners: TvCorners,
    height: Dp,
    accent: Color,
    glyph: Color,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    /** Sea Glass: which pebble, and its turn (play turns over when pressed). */
    seed: Int = 1,
    turn: () -> Float = { 0f },
    /** Sea Glass: only the icon until focused (restart and next, as on the phone). */
    bare: Boolean = false,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
    // Sea Glass: each button its own piece of glass, the group's shapes left
    // behind, laid out as the phone's player lays them out: the skips are
    // round pebbles, play a wider tumbled one resting a little lit, and
    // restart and next ([bare]) are just their icons until focused. Focus
    // lights a piece in the cover's color. The panel bar's slots stay
    // capsules.
    val tokens = LocalSea.current
    if (tokens != null) {
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier.height(height),
            contentAlignment = Alignment.Center
        ) {
            val round = !primary && height >= 70.dp
            TvGlass(
                tokens = tokens,
                accent = accent,
                modifier = if (round) Modifier.size(minOf(maxWidth, height)) else Modifier.fillMaxSize(),
                rest = if (primary) 0.4f else 0f,
                bare = bare,
                recipe = when {
                    primary -> GlassRecipe.Tumbled
                    !round -> GlassRecipe.Quiet
                    else -> GlassRecipe.Pebble
                },
                seed = seed,
                grow = if (round) 1.08f else 1.05f,
                enabled = enabled,
                focusRequester = focusRequester,
                turn = turn,
                onClick = onClick
            ) { ink -> content(ink) }
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val squash = animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "groupPress"
    )
    val rest = Color.White.copy(alpha = if (primary) 0.22f else 0.13f)
    val fill = animateColorAsState(
        targetValue = if (focused) accent else rest,
        animationSpec = tween(160),
        label = "groupFill"
    )
    val shape = RoundedCornerShape(
        topStart = corners.topStart, topEnd = corners.topEnd,
        bottomEnd = corners.bottomEnd, bottomStart = corners.bottomStart
    )
    Box(
        modifier
            .height(height)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .graphicsLayer {
                val s = squash.value
                scaleX = s
                scaleY = s
                alpha = if (enabled) 1f else 0.4f
                this.shape = shape
                clip = true
            }
            .drawBehind { drawRect(fill.value) }
            .focusable(enabled = enabled, interactionSource = interaction)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content(if (focused) glyph else Color.White)
    }
}

/** The wave as a progress bar. Focused, left and right seek ten seconds (held, they repeat). */
@Composable
private fun TvScrubber(
    progress: Float,
    playing: Boolean,
    accent: Color,
    onSeekBy: (Long) -> Unit,
    marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
    markColor: Color = accent
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val lit by animateFloatAsState(if (focused) 1f else 0f, tween(180), label = "scrubLit")
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.12f * lit))
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onSeekBy(-10_000); true }
                    Key.DirectionRight -> { onSeekBy(10_000); true }
                    else -> false
                }
            }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        WavySlider(
            progress = progress,
            playing = playing,
            color = accent,
            enabled = false,
            height = 22.dp,
            amplitude = 4.5.dp,
            wavelength = 36.dp,
            strokeWidth = 5.dp,
            showThumb = focused,
            voice = { VoiceLevel.current() },
            marks = marks,
            markColor = markColor
        )
    }
}

@Composable
private fun TvSidePanel(
    tab: TvPanel,
    onTab: (TvPanel) -> Unit,
    colors: ArtworkColors,
    accent: Color,
    glyph: Color,
    episode: Episode,
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    speed: Float,
    skipSilence: Boolean,
    voiceBoost: Boolean,
    remaining: Long?,
    atEnd: Boolean,
    armedMinutes: Int?,
    onPlayFromUpNext: (Int) -> Unit,
    onRemoveFromUpNext: (Episode) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSkipSilenceChange: (Boolean) -> Unit,
    onVoiceBoostChange: (Boolean) -> Unit,
    skipAds: Boolean,
    onSkipAdsChange: (Boolean) -> Unit,
    positionMs: Long,
    bookmarks: List<Bookmark>,
    onAddBookmark: () -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onMoveInUpNext: (String, Int) -> Boolean
) {
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { tabFocus.requestWhenReady() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(600.dp)
            .padding(vertical = TvSpacing.overscanV, horizontal = 28.dp)
            .tvPanel(RoundedCornerShape(34.dp), container = tvPanelColor(colors).copy(alpha = 0.97f))
            .padding(horizontal = 28.dp, vertical = 26.dp)
    ) {
        // Five tabs can outgrow the panel in a longer language; the row
        // scrolls, and focus brings the chosen one into view.
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TvPanel.entries.forEach { t ->
                TvChip(
                    label = t.label,
                    selected = t == tab,
                    accent = accent,
                    glyph = glyph,
                    modifier = if (t == tab) Modifier.focusRequester(tabFocus) else Modifier,
                    onClick = { onTab(t) }
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        when (tab) {
            TvPanel.UP_NEXT -> {
                if (upNext.isEmpty()) {
                    Text(tr("Nothing queued"), style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.7f))
                } else {
                    TvUpNextList(
                        upNext = upNext,
                        feedFor = feedFor,
                        accent = accent,
                        glyph = glyph,
                        onPlay = onPlayFromUpNext,
                        onRemove = onRemoveFromUpNext,
                        onMove = onMoveInUpNext
                    )
                }
            }

            TvPanel.BOOKMARKS -> TvBookmarkList(
                positionMs = positionMs,
                bookmarks = bookmarks,
                accent = accent,
                onAdd = onAddBookmark,
                onRemove = onRemoveBookmark,
                onSeekTo = onSeekTo
            )

            TvPanel.INFO -> {
                val notes = remember(episode.description) {
                    stripHtml(episode.description).split("\n").map { it.trim() }.filter { it.isNotBlank() }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                    item {
                        Text(episode.title, style = MaterialTheme.typography.titleLarge, color = Color.White)
                        if (episode.pubDate > 0) {
                            Text(formatDate(episode.pubDate), style = MaterialTheme.typography.labelLarge, color = accent)
                        }
                    }
                    // Each paragraph takes focus, so the remote can scroll the notes.
                    items(notes.size) { i ->
                        val interaction = remember { MutableInteractionSource() }
                        val focused by interaction.collectIsFocusedAsState()
                        Text(
                            text = notes[i],
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = if (focused) 1f else 0.82f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = if (focused) 0.08f else 0f))
                                .focusable(interactionSource = interaction)
                                .padding(8.dp)
                        )
                    }
                }
            }

            // Scrolls, in case speed, sound and ads outgrow the panel.
            TvPanel.SOUND -> Column(Modifier.verticalScroll(rememberScrollState())) {
                TvPanelLabel(tr("SPEED"))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Speeds.forEach { s ->
                        TvChip(
                            label = speedLabel(s),
                            selected = kotlin.math.abs(speed - s) < 0.01f,
                            accent = accent,
                            glyph = glyph,
                            onClick = { onSpeedChange(s) }
                        )
                    }
                }
                Spacer(Modifier.height(26.dp))
                TvPanelLabel(tr("SOUND"))
                TvToggleRow(tr("Skip silence"), skipSilence, accent) { onSkipSilenceChange(!skipSilence) }
                TvToggleRow(tr("Boost voices"), voiceBoost, accent) { onVoiceBoostChange(!voiceBoost) }
            }

            TvPanel.TIMER -> {
                if (remaining != null || atEnd) {
                    Text(
                        text = if (atEnd) tr("End of episode") else formatTime(remaining ?: 0L),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(18.dp))
                }
                val options = listOf(15, 30, 45, 60)
                TvOptionRow(tr("Off"), selected = remaining == null && !atEnd, accent = accent) { SleepTimer.cancel() }
                options.forEach { m ->
                    TvOptionRow(
                        label = if (m == 60) tr("1 hour") else tr("{0} minutes", m),
                        selected = !atEnd && armedMinutes == m,
                        accent = accent
                    ) { SleepTimer.armMinutes(m) }
                }
                TvOptionRow(tr("End of episode"), selected = atEnd, accent = accent) { SleepTimer.armEndOfEpisode() }
            }
        }
    }
}

/** Remembers, between key events, that a press's release is already spoken for. */
private class MoveKeys {
    var swallowUp = false
}

/**
 * Up Next, with reordering for a remote. Each entry plays on select and has
 * two buttons: the handle, which picks it up, and remove. Holding select on
 * the entry picks it up too. While it's up, up and down carry it through the
 * list and select or Back sets it down; left and right do nothing, so focus
 * can't wander off with it.
 */
@Composable
private fun TvUpNextList(
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    accent: Color,
    glyph: Color,
    onPlay: (Int) -> Unit,
    onRemove: (Episode) -> Unit,
    /** Moves the entry with this guid up (-1) or down (1); false if it couldn't. */
    onMove: (String, Int) -> Boolean
) {
    var moving by remember { mutableStateOf<String?>(null) }
    // It left the list (played, removed elsewhere): nothing is up any more.
    LaunchedEffect(upNext) {
        if (moving != null && upNext.none { it.guid == moving }) moving = null
    }
    BackHandler(enabled = moving != null) { moving = null }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keys = remember { MoveKeys() }

    // By guid, not position: a held key repeats faster than the list
    // recomposes, and a position from the last frame could be another entry.
    fun step(guid: String, by: Int) {
        if (!onMove(guid, by)) return
        // Keep the carried entry on screen: scroll a row's height when it
        // reaches the edge of what's visible.
        val info = listState.layoutInfo
        val row = info.visibleItemsInfo.firstOrNull { it.key == guid } ?: return
        val step = row.size + info.mainAxisItemSpacing
        val nextTop = row.offset + if (by > 0) step else -step
        if (nextTop < info.viewportStartOffset || nextTop + row.size > info.viewportEndOffset) {
            scope.launch { listState.animateScrollBy((if (by > 0) step else -step).toFloat()) }
        }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 12.dp)
    ) {
        itemsIndexed(upNext, key = { _, e -> e.guid }) { index, e ->
            val f = feedFor(e)
            val lifted = moving == e.guid
            val liftFill by animateColorAsState(
                if (lifted) accent.copy(alpha = 0.24f) else Color.Transparent,
                tween(160),
                label = "lifted"
            )
            Row(
                Modifier
                    .animateItem()
                    .onPreviewKeyEvent { ev ->
                        if (!lifted && !keys.swallowUp) return@onPreviewKeyEvent false
                        when {
                            ev.isSelectKey() -> {
                                if (ev.type == KeyEventType.KeyDown) {
                                    // A fresh press sets it down. The repeats
                                    // of the hold that picked it up don't.
                                    if (lifted && ev.nativeKeyEvent.repeatCount == 0) {
                                        moving = null
                                        keys.swallowUp = true
                                    }
                                } else if (ev.type == KeyEventType.KeyUp) {
                                    keys.swallowUp = false
                                }
                                true
                            }
                            !lifted -> false
                            ev.key == Key.DirectionUp -> {
                                if (ev.type == KeyEventType.KeyDown) step(e.guid, -1)
                                true
                            }
                            ev.key == Key.DirectionDown -> {
                                if (ev.type == KeyEventType.KeyDown) step(e.guid, 1)
                                true
                            }
                            ev.key == Key.DirectionLeft || ev.key == Key.DirectionRight -> true
                            else -> false
                        }
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .tvFocusableRow(
                            accent = accent,
                            surface = Color.White,
                            onLongClick = {
                                moving = e.guid
                                keys.swallowUp = true
                            },
                            onClick = { onPlay(index) }
                        )
                        .drawBehind { drawRect(liftFill) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Artwork(url = e.imageUrl.ifBlank { f?.imageUrl.orEmpty() }, sizeDp = 60.dp, corner = 12.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(e.title, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(f?.title.orEmpty(), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .tvFocusable(shape = CircleShape, accent = accent) {
                            moving = if (lifted) null else e.guid
                        }
                        .background(if (lifted) accent else Color.Transparent),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (lifted) Icons.Filled.SwapVert else Icons.Filled.DragHandle,
                        tr("Move"),
                        tint = if (lifted) glyph else Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .tvFocusable(shape = CircleShape, accent = accent, enabled = !lifted) { onRemove(e) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, tr("Remove"), tint = Color.White.copy(alpha = if (lifted) 0.3f else 0.8f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

/**
 * Bookmarks, as on the phone: one at the current time, then this episode's
 * bookmarks in order. Select jumps to one; the button beside it removes it.
 * Memos written on the phone show here; they're edited there.
 */
@Composable
private fun TvBookmarkList(
    positionMs: Long,
    bookmarks: List<Bookmark>,
    accent: Color,
    onAdd: () -> Unit,
    onRemove: (Long) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(key = "add") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .tvFocusableRow(accent = accent, surface = Color.White, onClick = onAdd)
                    .background(accent.copy(alpha = 0.16f))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    tr("Bookmark {0}", formatTime(positionMs)),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
        }
        if (bookmarks.isEmpty()) {
            item(key = "none") {
                Text(
                    tr("No bookmarks"),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 18.dp)
                )
            }
        }
        items(bookmarks, key = { it.id }) { b ->
            Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier
                        .weight(1f)
                        .tvFocusableRow(accent = accent, surface = Color.White, onClick = { onSeekTo(b.positionMs) })
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.2f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(formatTime(b.positionMs), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    }
                    if (b.memo.isNotBlank()) {
                        Spacer(Modifier.width(14.dp))
                        Text(
                            b.memo,
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White.copy(alpha = 0.88f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .tvFocusable(shape = CircleShape, accent = accent) { onRemove(b.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Close, tr("Remove"), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun TvPanelLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.6f))
    Spacer(Modifier.height(10.dp))
}

/** A small pill: selected fills with the accent; focus rings it. */
@Composable
private fun TvChip(
    label: String,
    selected: Boolean,
    accent: Color,
    glyph: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tokens = LocalSea.current
    if (tokens != null) {
        TvGlass(
            tokens = tokens,
            accent = accent,
            modifier = modifier.height(48.dp),
            filled = selected,
            focusLights = false,
            recipe = GlassRecipe.Quiet,
            seed = glassSeed(label),
            grow = 1.05f,
            onClick = onClick
        ) { ink ->
            Text(label, style = MaterialTheme.typography.titleSmall, color = ink, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp))
        }
        return
    }
    Box(
        modifier
            .height(48.dp)
            .tvFocusable(shape = RoundedCornerShape(24.dp), accent = Color.White, onClick = onClick)
            .background(if (selected) accent else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = if (selected) glyph else Color.White, maxLines = 1)
    }
}

@Composable
private fun TvToggleRow(label: String, checked: Boolean, accent: Color, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusableRow(accent = accent, surface = Color.White, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        val knob by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.7f), label = "toggle")
        Box(
            Modifier
                .width(56.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (checked) accent else Color.White.copy(alpha = 0.22f))
                .padding(4.dp)
        ) {
            Box(
                Modifier
                    .graphicsLayer { translationX = knob * 24.dp.toPx() }
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color(0xFF15121A) else Color.White)
            )
        }
    }
}

@Composable
private fun TvOptionRow(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .tvFocusableRow(accent = accent, surface = Color.White, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
    }
}

/**
 * False where a backdrop is covered — a page under the open player — so it
 * holds still instead of animating unseen.
 */
val LocalTvBackdropMoving = compositionLocalOf { true }

/**
 * The blurred cover behind the TV's pages and player — alive, as Apple Music's
 * is: two copies of the softened cover, drawn larger than the screen, turning
 * slowly in opposite directions around centers that drift, the upper one
 * half-transparent. The overlap keeps changing, so the colors wander across
 * the screen, and nothing large sits still on an OLED panel.
 *
 * Kept light for the Streamer:
 * - the cover is the same 32px pre-blurred bitmap as before; the GPU scales
 *   it, and the drawing is two image draws — no live blur, no layers;
 * - the clock is read only in the Canvas's draw, so a tick redraws this one
 *   node and never recomposes or re-lays-out the page on top;
 * - it ticks about 25 times a second, not 60 — the motion is a quarter-degree
 *   per tick on a blurred image, below what the eye can step through;
 * - it stops while the app isn't visible, and wherever [LocalTvBackdropMoving]
 *   says the backdrop is covered.
 *
 * A new cover crossfades in over the old one rather than flashing the base
 * color between them. [even] darkens evenly — for the browse pages — instead
 * of the player's left-to-right fall-off.
 */
@Composable
internal fun TvCoverBackdrop(url: String, colors: ArtworkColors, darken: Float = 1f, even: Boolean = false) {
    val tokens = LocalSea.current
    if (tokens != null) {
        TvSeaBackdrop(colors, tokens, even)
        return
    }
    val store = LocalImageStore.current
    var soft by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank()) {
            soft = null
            return@LaunchedEffect
        }
        val source = store.peek(url, 96) ?: store.load(url, 96) ?: return@LaunchedEffect
        soft = withContext(Dispatchers.Default) { com.glasscast.app.ui.softened(source).asImageBitmap() }
    }
    val clock = rememberDriftClock(LocalTvBackdropMoving.current)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.meshBase)
    ) {
        Crossfade(targetState = soft, animationSpec = tween(700), label = "backdropCover") { image ->
            if (image != null) DriftingCover(image, clock)
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    if (even) {
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = (0.46f * darken).coerceAtMost(0.9f)),
                            1f to Color.Black.copy(alpha = (0.58f * darken).coerceAtMost(0.9f))
                        )
                    } else {
                        Brush.horizontalGradient(
                            0f to Color.Black.copy(alpha = 0.30f * darken),
                            0.45f to Color.Black.copy(alpha = 0.48f * darken),
                            1f to Color.Black.copy(alpha = 0.66f * darken)
                        )
                    }
                )
        )
    }
}

/**
 * Sea Glass's backdrop: the night tide, tinted by the cover's glass, with the
 * cover's colors pooled on it as light through colored glass falls on a
 * floor. The pools drift slowly on the same clock as the Original look's
 * cover, and hold still under the same conditions; drawing them is three
 * gradient fills, lighter than the cover's two image draws.
 *
 * The main pool sits toward the top right, above the player's title and
 * clear of the cover, which has its own pool under it.
 */
@Composable
private fun TvSeaBackdrop(colors: ArtworkColors, tokens: com.glasscast.app.ui.seaglass.SeaTokens, even: Boolean) {
    val wash = remember(colors, tokens) { seaWash(colors, seaPigment(colors.meshAccent, dark = true), tokens) }
    val clock = rememberDriftClock(LocalTvBackdropMoving.current)
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.floatValue
        val drift = Offset(
            cos(t / 31f) * 0.05f * size.width,
            sin(t / 43f) * 0.06f * size.height
        )
        val home = if (even) Offset(size.width * 0.70f, size.height * 0.16f) else Offset(size.width * 0.66f, size.height * 0.18f)
        drawSeaWash(wash, dark = true, center = home + drift, reach = size.maxDimension * 0.58f)
    }
}

@Composable
private fun DriftingCover(image: ImageBitmap, clock: FloatState) {
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.floatValue
        // A square this big still covers the screen at any angle, with its
        // center anywhere inside the drift below (8% of the screen).
        val side = sqrt(size.width * size.width + size.height * size.height) * 1.2f
        val dst = IntSize(side.roundToInt(), side.roundToInt())
        fun layer(periodS: Float, phase: Float, turn: Float, alpha: Float) {
            val cycle = (t / periodS + phase) * 2f * PI.toFloat()
            val cx = center.x + cos(cycle * 0.5f) * 0.08f * size.width
            val cy = center.y + sin(cycle * 0.35f) * 0.08f * size.height
            rotate(degrees = turn * (t / periodS + phase) * 360f, pivot = Offset(cx, cy)) {
                drawImage(
                    image = image,
                    dstOffset = IntOffset((cx - side / 2f).roundToInt(), (cy - side / 2f).roundToInt()),
                    dstSize = dst,
                    alpha = alpha,
                    filterQuality = FilterQuality.Low
                )
            }
        }
        layer(periodS = 110f, phase = 0f, turn = 1f, alpha = 1f)
        layer(periodS = 150f, phase = 0.37f, turn = -1f, alpha = 0.55f)
    }
}

/**
 * Seconds of drift, advanced ~25 times a second while [moving] and the app is
 * started. Each step is capped, so time spent paused never turns into a jump
 * when it resumes. Starts at a random point, so it isn't the same picture
 * every time a page opens.
 */
@Composable
private fun rememberDriftClock(moving: Boolean): FloatState {
    val seconds = remember { mutableFloatStateOf(Random.nextFloat() * 900f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(moving, lifecycle) {
        if (!moving) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            var last = SystemClock.uptimeMillis()
            while (true) {
                delay(40)
                val now = SystemClock.uptimeMillis()
                seconds.floatValue += (now - last).coerceAtMost(100L) / 1000f
                last = now
            }
        }
    }
    return seconds
}
