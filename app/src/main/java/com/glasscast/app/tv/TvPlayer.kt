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
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
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

private enum class TvPanel(val label: String) {
    UP_NEXT(tr("Up Next")), INFO(tr("Info")), SOUND(tr("Speed & sound")), TIMER(tr("Timer"))
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
    onSpeedChange: (Float) -> Unit,
    onPlayFromUpNext: (Int) -> Unit,
    onRemoveFromUpNext: (Episode) -> Unit,
    onClose: () -> Unit,
    skipSilence: Boolean = false,
    voiceBoost: Boolean = false,
    onSkipSilenceChange: (Boolean) -> Unit = {},
    onVoiceBoostChange: (Boolean) -> Unit = {},
    skipAds: Boolean = false,
    onSkipAdsChange: (Boolean) -> Unit = {}
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val accent = colors.chromeButton
    val glyph = if (accent.luminance() > 0.5f) Color(0xFF15121A) else Color.White
    val playFocus = remember { FocusRequester() }
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
            Artwork(url = art, sizeDp = 400.dp, corner = 30.dp)
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TvGroupButton(
                        corners = TvCorners(30.dp, 10.dp, 10.dp, 30.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(0.78f), onClick = onRestart
                    ) {
                        Icon(Icons.Filled.SkipPrevious, tr("Restart"), tint = it, modifier = Modifier.size(30.dp))
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { onSeekBy(-30_000) }
                    ) {
                        Icon(Icons.Filled.Replay30, tr("Back 30 seconds"), tint = it, modifier = Modifier.size(32.dp))
                    }
                    TvPlayPauseButton(
                        isPlaying = isPlaying, accent = accent, glyph = glyph,
                        focusRequester = playFocus, modifier = Modifier.weight(1.7f), onClick = onPlayPause
                    )
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { onSeekBy(30_000) }
                    ) {
                        Icon(Icons.Filled.Forward30, tr("Forward 30 seconds"), tint = it, modifier = Modifier.size(32.dp))
                    }
                    TvGroupButton(
                        corners = TvCorners(10.dp, 30.dp, 30.dp, 10.dp), height = 76.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(0.78f),
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
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TvGroupButton(
                        corners = TvCorners(30.dp, 10.dp, 10.dp, 30.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { open(TvPanel.UP_NEXT) }
                    ) {
                        TvBarContent(Icons.AutoMirrored.Filled.QueueMusic, tr("Up Next"), upNext.size.takeIf { n -> n > 0 }?.toString(), it)
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { open(TvPanel.INFO) }
                    ) {
                        TvBarContent(Icons.Outlined.Info, tr("Info"), null, it)
                    }
                    TvGroupButton(
                        corners = TvCorners.all(10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { open(TvPanel.SOUND) }
                    ) {
                        TvBarContent(null, tr("Speed & sound"), speedLabel(speed), it)
                    }
                    TvGroupButton(
                        corners = TvCorners(10.dp, 30.dp, 30.dp, 10.dp), height = 60.dp,
                        accent = accent, glyph = glyph, modifier = Modifier.weight(1f), onClick = { open(TvPanel.TIMER) }
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
                onSkipAdsChange = onSkipAdsChange
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
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit
) {
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
    onSkipAdsChange: (Boolean) -> Unit
) {
    val tabFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { tabFocus.requestWhenReady() }
    Column(
        Modifier
            .fillMaxHeight()
            .width(600.dp)
            .padding(vertical = TvSpacing.overscanV, horizontal = 28.dp)
            .tvPanel(RoundedCornerShape(34.dp), container = colors.chromeSurface.copy(alpha = 0.97f))
            .padding(horizontal = 28.dp, vertical = 26.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                        itemsIndexed(upNext, key = { _, e -> e.guid }) { index, e ->
                            val f = feedFor(e)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier
                                        .weight(1f)
                                        .tvFocusableRow(accent = accent, surface = Color.White, onClick = { onPlayFromUpNext(index) })
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
                                        .tvFocusable(shape = CircleShape, accent = accent) { onRemoveFromUpNext(e) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Close, tr("Remove"), tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
                                }
                            }
                        }
                    }
                }
            }

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
