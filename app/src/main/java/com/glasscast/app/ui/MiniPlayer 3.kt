package com.glasscast.app.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import kotlin.math.PI
import kotlin.math.roundToInt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import dev.chrisbanes.haze.HazeState

/**
 * The now-playing chrome, as one element that changes shape — Cider's move.
 *
 * Scrolling down, the card shrinks toward its right end until only the cover
 * is left, then drops into the space beside the tab bar and becomes the
 * scalloped bubble, its progress ring fading in. Scrolling up runs it back.
 *
 * It replaces a card and a separate bubble that swapped with enter/exit
 * transitions. Two elements meant two sets of animations (and, until the keys
 * were split, a shared-element flight between them), and the card's height
 * animation re-laid-out the chrome every frame. Here one value, [collapse]
 * (0 = card, 1 = bubble), drives everything, and it is read only while
 * placing and drawing: a frame of the morph moves and repaints, and never
 * recomposes or re-measures. This box keeps the card's size throughout — the
 * page just shows through once it's collapsed — so nothing around it moves
 * except the bar, which gives up room on its right for the bubble.
 *
 * The bubble's position comes from the tab bar's measured bounds ([barBounds],
 * in root coordinates), so it sits centred on the bar at any screen size.
 */
@Composable
fun MiniPlayer(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    collapse: State<Float>,
    collapsed: Boolean,
    barBounds: () -> Rect?,
    onPlayPause: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val (colors, _) = rememberArtworkColors(art)

    // Cider's card: solid, dark in both themes, in the cover's own hue.
    val surface by animateColorAsState(colors.chromeSurface, tween(600), label = "miniSurface")
    val button by animateColorAsState(colors.chromeButton, tween(600), label = "miniButton")
    val ringPlayed by animateColorAsState(colors.ringPlayed, tween(600), label = "ringPlayed")
    val ringTrack by animateColorAsState(colors.ringTrack, tween(600), label = "ringTrack")
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val progressState = rememberUpdatedState(progress)

    val density = LocalDensity.current
    val m = remember(density) { MorphMetrics(density) }
    var selfBounds by remember { mutableStateOf<Rect?>(null) }

    // The bubble's lobes turn slowly while playing — only once fully collapsed,
    // and 30 times a second (see FrameClock).
    val spin = rememberFrameClock(running = isPlaying && collapsed, fps = 30)

    // The card's text and controls exist only while the card does: invisible
    // buttons above the bar would otherwise swallow taps meant for the page.
    val showContent by remember { derivedStateOf { collapse.value.coerceIn(0f, 1f) < 0.35f } }

    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .onGloballyPositioned { selfBounds = it.boundsInRoot() }
            .drawWithContent {
                val f = m.frame(collapse.value.coerceIn(0f, 1f), size.width, barBounds(), selfBounds)
                // The container: card → pill → circle, fading as it becomes the bubble.
                if (f.bgAlpha > 0f) {
                    drawRoundRect(
                        color = surface.copy(alpha = surface.alpha * f.bgAlpha),
                        topLeft = f.rect.topLeft,
                        size = f.rect.size,
                        cornerRadius = CornerRadius(f.radius)
                    )
                }
                drawContent()
                // The progress ring, tracing the bubble's scalloped edge.
                if (f.ringAlpha > 0f) {
                    val stroke = m.ringStroke
                    val rotation = (spin.value / 2.4f) * (2 * PI / CookieLobes).toFloat()
                    val ring = cookiePath(f.rect.width, f.rect.height, rotation = rotation, inset = stroke / 2f, steps = 72)
                    ring.close()
                    translate(f.rect.left, f.rect.top) {
                        drawPath(ring, ringTrack.copy(alpha = f.ringAlpha), style = Stroke(stroke))
                        val measure = PathMeasure().apply { setPath(ring, true) }
                        val arc = Path()
                        measure.getSegment(0f, measure.length * progressState.value, arc, true)
                        drawPath(arc, ringPlayed.copy(alpha = f.ringAlpha), style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            }
    ) {
        if (showContent) {
            Row(
                Modifier
                    .padding(horizontal = GlassGutter)
                    .fillMaxWidth()
                    .height(68.dp)
                    .graphicsLayer {
                        val p = collapse.value.coerceIn(0f, 1f)
                        alpha = (1f - p / 0.3f).coerceIn(0f, 1f)
                        translationX = p * m.contentDrift
                    }
                    .clip(RoundedCornerShape(30.dp))
                    .clickable {
                        haptics.play(Haptic.Expand)
                        onOpen()
                    }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The cover is drawn separately below — it's the part that
                // survives into the bubble — so the row keeps its place.
                Spacer(Modifier.width(48.dp + 14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = feed?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    WavySlider(
                        progress = progress,
                        playing = isPlaying,
                        color = button,
                        enabled = false,
                        height = 8.dp,
                        amplitude = 1.8.dp,
                        wavelength = 18.dp,
                        strokeWidth = 2.6.dp,
                        showThumb = false,
                        frameRate = 30,
                        voice = { com.glasscast.app.player.VoiceLevel.current() }
                    )
                }
                Spacer(Modifier.width(12.dp))
                MiniPlayButton(
                    isPlaying = isPlaying,
                    fill = button,
                    tint = Color(0xFF16141A),
                    onClick = {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    }
                )
            }
        }

        // The cover: the one part that travels. Genuinely *placed* where it
        // is each frame — measured at its current size, laid out at its
        // current position — not drawn somewhere else by a layer offset. It
        // was a layer offset, which left its real position at the box's
        // top-left corner; the player-closing flight lands on an element's
        // real position, so the cover flew to that corner, sat there for a
        // moment outside the bubble, then snapped in. Clipped while drawing:
        // rounded square, opening to a circle, then the scallops grow in.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val f = m.frame(
                        collapse.value.coerceIn(0f, 1f),
                        constraints.maxWidth.toFloat(),
                        barBounds(),
                        selfBounds
                    )
                    val side = f.art.width.roundToInt().coerceAtLeast(1)
                    val placeable = measurable.measure(Constraints.fixed(side, side))
                    layout(0, 0) {
                        placeable.place(f.art.left.roundToInt(), f.art.top.roundToInt())
                    }
                }
                .drawWithContent {
                    val b = m.bubblePhase(collapse.value.coerceIn(0f, 1f))
                    // Tell the flight what shape to land on (see FlightClip).
                    MiniArtShape.roundness = 0.25f + 0.25f * (b * 2f).coerceAtMost(1f)
                    val clip = if (b < 0.5f) {
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    0f, 0f, size.width, size.height,
                                    CornerRadius(m.artCorner + (size.width / 2f - m.artCorner) * (b * 2f))
                                )
                            )
                        }
                    } else {
                        val rotation = (spin.value / 2.4f) * (2 * PI / CookieLobes).toFloat()
                        cookiePath(size.width, size.height, rotation = rotation, depth = CookieDepth * (b - 0.5f) * 2f, steps = 72)
                            .also { it.close() }
                    }
                    clipPath(clip) { this@drawWithContent.drawContent() }
                }
        ) {
            Artwork(
                url = art,
                sizeDp = 48.dp,
                corner = 0.dp,
                modifier = Modifier.sharedArtwork(MiniArtKey, LocalPlayerScope.current, clip = FlightClip)
            )
        }
    }
}

/** Pixel geometry for the morph, and the frame at any point along it. */
private class MorphMetrics(density: androidx.compose.ui.unit.Density) {
    val gutter = with(density) { GlassGutter.toPx() }
    val cardH = with(density) { 68.dp.toPx() }
    val cardCorner = with(density) { 30.dp.toPx() }
    val bubble = with(density) { 58.dp.toPx() }
    val bubbleInset = with(density) { 7.dp.toPx() }
    val artPx = with(density) { 48.dp.toPx() }
    val artCorner = with(density) { 12.dp.toPx() }
    val artLead = with(density) { 10.dp.toPx() }
    val barPad = with(density) { 8.dp.toPx() }
    /** The bubble's centre below this box's top: the 76dp box, then half of the 60dp bar. */
    val fallbackDrop = with(density) { 106.dp.toPx() }
    val ringStroke = with(density) { 3.5.dp.toPx() }
    val contentDrift = with(density) { 40.dp.toPx() }

    class Frame(val rect: Rect, val radius: Float, val bgAlpha: Float, val ringAlpha: Float, val art: Rect)

    /** 0 until the pill is a square at the card's right end, then 0 → 1 into the bubble. */
    fun bubblePhase(p: Float) = ((p - 0.55f) / 0.45f).coerceIn(0f, 1f)

    fun frame(p: Float, width: Float, bar: Rect?, self: Rect?): Frame {
        val cardLeft = gutter
        val cardRight = width - gutter
        val a = (p / 0.55f).coerceIn(0f, 1f)
        val b = bubblePhase(p)

        // Phase one: the card shrinks toward its right end, the cover gliding
        // along inside it, until it's a square the height of the card.
        val left = cardLeft + (cardRight - cardH - cardLeft) * a
        var rect = Rect(left, 0f, cardRight, cardH)
        var radius = cardCorner + (cardH / 2f - cardCorner) * a
        val artInset = (cardH - artPx) / 2f
        val artStartX = cardLeft + artLead
        val artEndX = cardRight - cardH + artInset
        var art = Rect(Offset(artStartX + (artEndX - artStartX) * a, artInset), Size(artPx, artPx))

        // Phase two: that square drops into the bubble beside the bar.
        if (b > 0f) {
            val centerY = if (bar != null && self != null) {
                bar.top + (bar.height - barPad) / 2f - self.top
            } else {
                fallbackDrop
            }
            val target = Offset(width - gutter - bubble / 2f, centerY)
            val from = rect.center
            val center = Offset(from.x + (target.x - from.x) * b, from.y + (target.y - from.y) * b)
            val side = cardH + (bubble - cardH) * b
            rect = Rect(center.x - side / 2f, center.y - side / 2f, center.x + side / 2f, center.y + side / 2f)
            radius = side / 2f
            val inner = artPx + ((bubble - bubbleInset * 2f) - artPx) * b
            art = Rect(center.x - inner / 2f, center.y - inner / 2f, center.x + inner / 2f, center.y + inner / 2f)
        }
        return Frame(
            rect = rect,
            radius = radius,
            bgAlpha = 1f - b,
            ringAlpha = ((b - 0.7f) / 0.3f).coerceIn(0f, 1f),
            art = art
        )
    }
}

/**
 * The mini player's play button, on a tonal tile that changes shape with state
 * — a circle at rest, a rounded square while playing — the same morph as the
 * full player's, so the two read as one control at two sizes.
 */
@Composable
private fun MiniPlayButton(
    isPlaying: Boolean,
    fill: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 15.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "miniPlayShape"
    )
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

