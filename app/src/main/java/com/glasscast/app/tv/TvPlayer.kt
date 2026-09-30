package com.glasscast.app.tv

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.produceState
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import com.glasscast.app.player.SleepTimer
import com.glasscast.app.ui.LocalImageStore
import com.glasscast.app.ui.WavySlider
import com.glasscast.app.ui.chromeButton
import com.glasscast.app.ui.formatSpeed
import com.glasscast.app.ui.formatCompact
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.artworkGround
import com.glasscast.app.ui.formatRemaining
import com.glasscast.app.ui.formatTime
import com.glasscast.app.ui.stripHtml

/**
 * Now playing, full screen.
 *
 * The phone player's gestures all had to be replaced. What took their place:
 *
 *  - **Left and right seek by 30 seconds without moving focus.** The remote's
 *    horizontal axis is the natural scrub axis, and making people walk focus
 *    onto a seek button to move through an episode would be exhausting. The
 *    key handler consumes those presses, so focus stays on the play button.
 *  - **Back closes**, rather than a swipe down.
 *  - **The play button holds focus from the moment it opens.** On a TV the
 *    thing you most likely want is the thing under the cursor already.
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
    onClose: () -> Unit
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val playFocus = remember { FocusRequester() }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /*
     * The player sits on the blurred cover, darkened, so its type is white in
     * every case — and its accent is the phone mini player's: the pale tone of
     * the cover's colour, with a dark glyph on it. Built once per palette.
     */
    val pc = remember(colors) {
        colors.copy(
            content = Color.White,
            contentVariant = Color.White.copy(alpha = 0.68f),
            accent = colors.chromeButton
        )
    }

    var showNotes by remember { mutableStateOf(false) }
    var showUpNext by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { playFocus.requestWhenReady() }

    // Sleep timer state, shared with the phone's timer. The label ticks once a
    // second, and only while a timer is running.
    val sleepEndsAt by SleepTimer.endsAtMs.collectAsStateWithLifecycle()
    val sleepAtEnd by SleepTimer.endOfEpisode.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis(), sleepEndsAt) {
        while (sleepEndsAt != null) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val sleepLabel = when {
        sleepAtEnd -> "End of episode"
        sleepEndsAt != null -> formatTime(((sleepEndsAt ?: now) - now).coerceAtLeast(0L))
        else -> "Sleep"
    }

    Box(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    // Left and right are NOT intercepted: the D-pad moves focus
                    // along the controls. The remote's media keys still seek.
                    Key.MediaPlay, Key.MediaPause, Key.MediaPlayPause -> { onPlayPause(); true }
                    Key.MediaFastForward -> { onSeekBy(30_000); true }
                    Key.MediaRewind -> { onSeekBy(-30_000); true }
                    Key.MediaNext -> { onSkipNext(); true }
                    Key.MediaPrevious -> { onRestart(); true }
                    else -> false
                }
            }
    ) {
        TvCoverBackdrop(url = art, colors = colors)

        /*
         * Up Next, speed, sleep and notes: the phone's pull-up panel, as a row
         * of pills at the top right. Up from the transport reaches them; they
         * stay out of the row you actually use during an episode. Speed and
         * sleep cycle on each press — on a remote, one button that steps
         * through the common values beats a list to scroll.
         */
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = TvSpacing.overscanV, end = TvSpacing.overscanH),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvPillButton(
                label = if (upNext.isEmpty()) "Up Next" else "Up Next · ${upNext.size}",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                colors = pc
            ) { showUpNext = true }
            TvPillButton(
                label = formatSpeed(speed),
                icon = Icons.Filled.Speed,
                colors = pc
            ) { onSpeedChange(nextSpeed(speed)) }
            TvPillButton(
                label = sleepLabel,
                icon = Icons.Filled.Bedtime,
                colors = pc,
                filled = sleepEndsAt != null || sleepAtEnd
            ) {
                cycleSleep(
                    remainingMs = sleepEndsAt?.let { it - System.currentTimeMillis() },
                    atEnd = sleepAtEnd
                )
            }
            TvControl(Icons.Outlined.Info, "Episode notes", pc, 52.dp) { showNotes = true }
        }

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = TvSpacing.overscanH * 1.15f, vertical = TvSpacing.overscanV),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork large and left, controls right. On a landscape panel a
            // centred stack wastes two thirds of the width.
            Box(
                Modifier.shadow(
                    elevation = 36.dp,
                    shape = RoundedCornerShape(22.dp),
                    ambientColor = Color.Black,
                    spotColor = Color.Black
                )
            ) {
                Artwork(url = art, sizeDp = 380.dp, corner = 22.dp)
            }

            Spacer(Modifier.width(56.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = feed?.title.orEmpty().uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = pc.contentVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = pc.content,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(30.dp))

                // The phone's wave: travelling while it plays, flat when
                // paused. Display-only — the remote's media keys seek, and a
                // focusable bar would be one more stop in the way.
                WavySlider(
                    progress = progress,
                    playing = isPlaying,
                    color = pc.accent,
                    enabled = false,
                    height = 26.dp,
                    amplitude = 4.5.dp,
                    wavelength = 36.dp,
                    strokeWidth = 5.dp,
                    showThumb = false,
                    voice = { com.glasscast.app.player.VoiceLevel.current() }
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        text = formatTime(positionMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = pc.contentVariant
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = formatRemaining(positionMs, durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        color = pc.contentVariant
                    )
                }

                Spacer(Modifier.height(30.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // Sized to fit the column: Modifier.size is a preference,
                    // and a row that overflows squeezes its last button oval.
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TvControl(Icons.Filled.SkipPrevious, "Restart episode", pc, 52.dp, onRestart)
                    TvControl(Icons.Filled.Replay30, "Back 30 seconds", pc, 60.dp) {
                        onSeekBy(-30_000)
                    }
                    TvPlayButton(
                        isPlaying = isPlaying,
                        colors = pc,
                        modifier = Modifier.focusRequester(playFocus),
                        onClick = onPlayPause
                    )
                    TvControl(Icons.Filled.Forward30, "Forward 30 seconds", pc, 60.dp) {
                        onSeekBy(30_000)
                    }
                    TvControl(
                        icon = Icons.Filled.SkipNext,
                        description = if (upNext.isNotEmpty()) "Next episode" else "Nothing queued",
                        colors = pc,
                        size = 52.dp,
                        enabled = upNext.isNotEmpty(),
                        onClick = onSkipNext
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    text = "BACK to close",
                    style = MaterialTheme.typography.bodySmall,
                    color = pc.contentVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    if (showNotes) {
        TvNotesPanel(
            episode = episode,
            feed = feed,
            colors = colors,
            onClose = { showNotes = false }
        )
    }

    if (showUpNext) {
        TvUpNextPanel(
            upNext = upNext,
            feedFor = feedFor,
            colors = colors,
            onPlay = { index ->
                onPlayFromUpNext(index)
                showUpNext = false
            },
            onRemove = onRemoveFromUpNext,
            onClose = { showUpNext = false }
        )
    }
}

private val TvSpeeds = listOf(0.8f, 1f, 1.2f, 1.5f, 1.8f, 2f)

/** The next speed up, wrapping from the fastest back to the slowest. */
private fun nextSpeed(current: Float): Float {
    val index = TvSpeeds.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
    return if (index < 0) 1f else TvSpeeds[(index + 1) % TvSpeeds.size]
}

/**
 * Off → 15 → 30 → 45 → 60 minutes → end of episode → off. Worked out from the
 * time remaining rather than remembered, so it also steps correctly from a
 * timer set on the phone: the next preset past what's left.
 */
private fun cycleSleep(remainingMs: Long?, atEnd: Boolean) {
    when {
        atEnd -> SleepTimer.cancel()
        remainingMs == null -> SleepTimer.armMinutes(15)
        else -> {
            val remainingMinutes = remainingMs / 60_000f
            val next = listOf(15, 30, 45, 60).firstOrNull { it > remainingMinutes + 1f }
            if (next != null) SleepTimer.armMinutes(next) else SleepTimer.armEndOfEpisode()
        }
    }
}

/**
 * The cover, blurred to fill the screen — the phone's and the Mac's backdrop in
 * its landscape form, built for the Streamer.
 *
 * Not `Modifier.blur`. The wave animates while playing, so this screen redraws
 * every frame, and a RenderEffect blur over the full panel would be recomputed
 * by the GPU on each one. Instead the cover is shrunk to 32px and box-blurred
 * once, off the main thread, and that tiny bitmap is stretched across the
 * screen with bilinear filtering — which is itself a heavy blur. Per frame it
 * costs one bitmap draw.
 */
@Composable
private fun TvCoverBackdrop(url: String, colors: ArtworkColors) {
    val store = LocalImageStore.current
    var soft by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isBlank()) return@LaunchedEffect
        val source = store.peek(url, 96) ?: store.load(url, 96) ?: return@LaunchedEffect
        soft = withContext(Dispatchers.Default) { com.glasscast.app.ui.softened(source) }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.meshBase)
    ) {
        soft?.let { bmp ->
            val image = remember(bmp) { bmp.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,   // bilinear: the blur is the point
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.30f),
                        0.45f to Color.Black.copy(alpha = 0.48f),
                        1f to Color.Black.copy(alpha = 0.66f)
                    )
                )
        )
    }
}

/**
 * Play/pause that morphs like the phone's: a circle at rest, a squarer tile
 * while playing. The focus ring follows the same shape.
 */
@Composable
private fun TvPlayButton(
    isPlaying: Boolean,
    colors: ArtworkColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 26.dp else 41.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "tvPlayShape"
    )
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(82.dp)
            .tvFocusable(shape = shape, accent = colors.accent, scale = 1.12f, onClick = onClick)
            .background(colors.accent, shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = colors.onAccent,
            modifier = Modifier.size(38.dp)
        )
    }
}

/**
 * Up Next as a panel over the player, like the notes: pick an episode to play
 * it now, or remove it. Playing one closes the panel — you picked what's next,
 * so the player is what you want to see.
 */
@Composable
private fun TvUpNextPanel(
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    colors: ArtworkColors,
    onPlay: (Int) -> Unit,
    onRemove: (Episode) -> Unit,
    onClose: () -> Unit
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestWhenReady() }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f)
                .tvPanel(RoundedCornerShape(24.dp), colors.elevated)
                .padding(36.dp)
        ) {
            Text(
                text = "Up Next",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.content
            )
            Text(
                text = when (upNext.size) {
                    0 -> "Nothing queued"
                    1 -> "1 episode"
                    else -> "${upNext.size} episodes"
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.contentVariant
            )
            Spacer(Modifier.height(20.dp))

            if (upNext.isEmpty()) {
                Text(
                    text = "Queue episodes from any show's page and they'll line up here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.contentVariant,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp)
                ) {
                    itemsIndexed(upNext, key = { _, ep -> ep.guid }) { index, ep ->
                        val epFeed = feedFor(ep)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                                    .tvFocusable(accent = colors.accent) { onPlay(index) }
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(
                                    url = ep.imageUrl.ifBlank { epFeed?.imageUrl.orEmpty() },
                                    sizeDp = 64.dp,
                                    corner = 12.dp
                                )
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = ep.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.content,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = epFeed?.title.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.contentVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = formatCompact(ep.durationMs),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.contentVariant
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            TvControl(Icons.Filled.Close, "Remove from Up Next", colors, 44.dp) { onRemove(ep) }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Box(if (upNext.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier) {
                TvControl(Icons.Filled.Close, "Close Up Next", colors, 52.dp, onClose)
            }
        }
    }
}

/**
 * Episode notes as a panel over the player rather than a separate screen: the
 * player is still playing behind it and nothing about the session changed, so
 * leaving the screen would overstate what just happened.
 */
@Composable
private fun TvNotesPanel(
    episode: Episode,
    feed: Feed?,
    colors: ArtworkColors,
    onClose: () -> Unit
) {
    val notes = remember(episode.description) { stripHtml(episode.description) }
    val scroll = rememberScrollState()
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { closeFocus.requestWhenReady() }
    BackHandler { onClose() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.78f)
                .tvPanel(RoundedCornerShape(24.dp), colors.elevated)
                .padding(36.dp)
        ) {
            Text(
                text = feed?.title.orEmpty().uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = colors.contentVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = episode.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.content,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = notes.ifBlank { "This episode has no description." },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.contentVariant,
                modifier = Modifier
                    .weight(1f)
                    // Focusable so the D-pad can scroll it; without this, long
                    // notes are simply unreachable past the first screenful.
                    .focusable()
                    .verticalScroll(scroll)
            )
            Spacer(Modifier.height(20.dp))
            Box(Modifier.focusRequester(closeFocus)) {
                TvControl(Icons.Filled.Close, "Close notes", colors, 52.dp, onClose)
            }
        }
    }
}

@Composable
private fun TvControl(
    icon: ImageVector,
    description: String,
    colors: ArtworkColors,
    size: Dp,
    onClick: () -> Unit
) = TvControl(icon, description, colors, size, false, true, Modifier, onClick)

@Composable
private fun TvControl(
    icon: ImageVector,
    description: String,
    colors: ArtworkColors,
    size: Dp,
    filled: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .size(size)
            .tvFocusable(
                shape = CircleShape,
                accent = colors.accent,
                scale = 1.14f,
                enabled = enabled,
                onClick = onClick
            )
            .background(
                if (filled) colors.accent else colors.content.copy(alpha = 0.12f),
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                filled -> colors.onAccent
                enabled -> colors.content
                else -> colors.content.copy(alpha = 0.30f)
            },
            modifier = Modifier.size(size * 0.46f)
        )
    }
}
