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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
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
import androidx.compose.ui.graphics.lerp
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
import kotlinx.coroutines.launch
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
 * in root coordinates), so it sits centered on the bar at any screen size.
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
    /** 0 = the dark card (over a light page), 1 = the pale one (over a dark page). */
    pale: State<Float>,
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

    // The card's own pair. Over a light page, Cider's: the dark surface, with
    // the cover's pale tone on the play button and the progress line. Over a
    // dark page that dark card sank into it, so the card swaps them: a pale
    // card, the dark tone on the button. [pale] says how far it has swapped
    // (it crossfades when the page changes; see GlassCastRoot, chromeMix).
    // The bubble's ring keeps the original pair (surface, button) either way.
    val mix = pale.value.coerceIn(0f, 1f)
    // Sea Glass: a paper card lying on the page in a pool of the cover's
    // color, ink type, and play as a pebble of the cover's glass. The bubble
    // is the cover set in a ring of that glass.
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    val pigment = if (tokens != null) {
        com.glasscast.app.ui.seaglass.animatePigment(
            androidx.compose.runtime.remember(colors, tokens.dark) { com.glasscast.app.ui.seaglass.seaPigment(colors.meshAccent, tokens.dark) }
        )
    } else {
        null
    }
    // Sea Glass's bubble: the cover filled into a worn pebble, its progress
    // traced round a line of the same shape just outside it. The outlines
    // are rebuilt as the pebble breathes, into these.
    val seaRing = remember { Path() }
    val seaArc = remember { Path() }
    val seaClip = remember { Path() }
    val seaMeasure = remember { PathMeasure() }
    // Sea Glass: paper with a little of the playing cover's color in it.
    val cardBg = if (tokens != null && pigment != null) lerp(tokens.card, pigment.body, if (tokens.dark) 0.18f else 0.24f) else lerp(surface, button, mix)
    val cardAccent = if (tokens != null) tokens.ink else lerp(button, surface, mix)
    val cardInk = if (tokens != null) tokens.ink else lerp(Color.White, ChromeInk, mix)
    val cardGlyph = if (pigment != null) pigment.ink else lerp(ChromeInk, button, mix)
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
    val cardClip = remember { Path() }

    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .onGloballyPositioned { selfBounds = it.boundsInRoot() }
            .drawWithContent {
                val p = collapse.value.coerceIn(0f, 1f)
                val f = m.frame(p, size.width, barBounds(), selfBounds)
                if (tokens != null && pigment != null) {
                    // The card's ground: drawn at the card's resting size and
                    // gone a third of the way into the collapse, so the
                    // shrinking card never needs a new one.
                    val rest = (1f - p * 6f).coerceIn(0f, 1f)
                    if (rest > 0f) {
                        translate(m.gutter, 0f) {
                            with(com.glasscast.app.ui.seaglass.GlassGround) {
                                drawCoverGround(
                                    width = (size.width - m.gutter * 2f) / density.density,
                                    height = m.cardH / density.density,
                                    radius = m.cardCorner / density.density,
                                    pigment = pigment,
                                    dark = tokens.dark,
                                    strength = rest * 0.55f
                                )
                            }
                        }
                    }
                    // The bubble's shadow on the page, at its final size,
                    // arriving with the ring.
                    if (f.ringAlpha > 0f) {
                        translate(f.rect.left, f.rect.top) {
                            with(com.glasscast.app.ui.seaglass.GlassGround) {
                                drawGround(
                                    width = m.bubble / density.density,
                                    height = m.bubble / density.density,
                                    radius = m.bubble / 2f / density.density,
                                    recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Pebble,
                                    pigment = pigment,
                                    dark = tokens.dark,
                                    strength = f.ringAlpha * 0.7f
                                )
                            }
                        }
                    }
                }
                // The container: card → pill → circle, fading as it becomes the bubble.
                if (f.bgAlpha > 0f) {
                    val bg = cardBg
                    drawRoundRect(
                        color = bg.copy(alpha = bg.alpha * f.bgAlpha),
                        topLeft = f.rect.topLeft,
                        size = f.rect.size,
                        cornerRadius = CornerRadius(f.radius)
                    )
                    if (tokens != null) {
                        val hairline = 0.75.dp.toPx()
                        drawRoundRect(
                            color = tokens.hairline.copy(alpha = f.bgAlpha),
                            topLeft = f.rect.topLeft + Offset(hairline / 2f, hairline / 2f),
                            size = Size(f.rect.width - hairline, f.rect.height - hairline),
                            cornerRadius = CornerRadius(f.radius),
                            style = Stroke(hairline)
                        )
                    }
                }
                // Everything inside stays inside the shape as it changes. The
                // title and subtitle fade on their own timing, and growing back
                // out of the bubble they showed past the card's left edge, over
                // the covers behind it. In the bubble phase the rect is the
                // bubble, and the cover sits inside it.
                cardClip.reset()
                cardClip.addRoundRect(RoundRect(f.rect, CornerRadius(f.radius)))
                clipPath(cardClip) { this@drawWithContent.drawContent() }
                // Sea Glass: the progress line round the pebble. It draws
                // itself in round the shape as the cover settles, then the
                // played part runs in the cover's ink (the color says where
                // you are; no dot at its head, as in the Original look).
                if (f.ringAlpha > 0f && tokens != null && pigment != null) {
                    val stroke = m.ringStroke * 0.86f
                    seaBubbleOutline(
                        seaRing, f.rect.width - stroke, f.rect.height - stroke, density.density,
                        e = 1f, cornerDp = 0f, turn = spin.value / SeaBubbleBreath
                    )
                    translate(f.rect.left + stroke / 2f, f.rect.top + stroke / 2f) {
                        seaMeasure.setPath(seaRing, true)
                        val length = seaMeasure.length
                        val shown = f.ringAlpha
                        seaArc.reset()
                        seaMeasure.getSegment(0f, length * shown, seaArc, true)
                        drawPath(seaArc, tokens.ink.copy(alpha = 0.16f), style = Stroke(stroke, cap = StrokeCap.Round))
                        val lit = if (tokens.dark) pigment.edge else pigment.ink
                        val played = length * progressState.value * shown
                        if (played > 0.5f) {
                            seaArc.reset()
                            seaMeasure.getSegment(0f, played, seaArc, true)
                            drawPath(seaArc, lit, style = Stroke(stroke, cap = StrokeCap.Round))
                        }
                    }
                } else if (f.ringAlpha > 0f) {
                // The progress ring, tracing the bubble's scalloped edge.
                    val stroke = m.ringStroke
                    val rotation = (spin.value / 2.4f) * (2 * PI / CookieLobes).toFloat()
                    val ring = cookiePath(f.rect.width, f.rect.height, rotation = rotation, inset = stroke / 2f, steps = 72)
                    ring.close()
                    translate(f.rect.left, f.rect.top) {
                        val track = if (tokens != null) tokens.ink.copy(alpha = 0.14f) else surface
                        val lit = pigment?.ink ?: button
                        drawPath(ring, track.copy(alpha = track.alpha * f.ringAlpha), style = Stroke(stroke))
                        val measure = PathMeasure().apply { setPath(ring, true) }
                        val arc = Path()
                        measure.getSegment(0f, measure.length * progressState.value, arc, true)
                        drawPath(arc, lit.copy(alpha = f.ringAlpha), style = Stroke(stroke, cap = StrokeCap.Round))
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
                        style = if (tokens != null) MaterialTheme.typography.titleSmall.copy(
                            fontFamily = com.glasscast.app.ui.theme.SeaSerif,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal,
                            fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                        ) else MaterialTheme.typography.titleSmall,
                        color = cardInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = feed?.title.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tokens != null) tokens.muted else cardInk.copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(5.dp))
                    WavySlider(
                        progress = progress,
                        playing = isPlaying,
                        color = cardAccent,
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
                if (tokens != null && pigment != null) {
                    SeaPlayPebble(
                        isPlaying = isPlaying,
                        pigment = pigment,
                        dark = tokens.dark,
                        size = 48.dp,
                        iconSize = 24.dp,
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onPlayPause()
                        }
                    )
                } else {
                    MiniPlayButton(
                        isPlaying = isPlaying,
                        fill = cardAccent,
                        tint = cardGlyph,
                        onClick = {
                            haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                            onPlayPause()
                        }
                    )
                }
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
                    MiniArtShape.sea = tokens != null
                    if (tokens != null) {
                        // Sea Glass: one outline all the way, a rounded
                        // square on the card becoming the pebble as it
                        // drops into the bubble, then breathing while it plays.
                        val turn = spin.value / SeaBubbleBreath
                        MiniArtShape.roundness = 0.25f
                        MiniArtShape.bubble = b
                        MiniArtShape.seaTurn = turn
                        seaBubbleOutline(
                            seaClip, size.width, size.height, density.density,
                            e = b * b * (3f - 2f * b), cornerDp = 12f, turn = turn
                        )
                        clipPath(seaClip) { this@drawWithContent.drawContent() }
                        return@drawWithContent
                    }
                    // Tell the flight what shape to land on (see FlightClip).
                    MiniArtShape.roundness = 0.25f + 0.25f * (b * 2f).coerceAtMost(1f)
                    MiniArtShape.bubble = b
                    MiniArtShape.rotation = (spin.value / 2.4f) * (2 * PI / CookieLobes).toFloat()
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
    /** The bubble's center below this box's top: the 76dp box, then half of the 60dp bar. */
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
            contentDescription = if (isPlaying) "Pause" else tr("Play"),
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}


/**
 * Sea Glass's play button: a well-worn pebble of the cover's glass. Each
 * press turns it over, so its outline shifts between play and pause the way
 * the Original look's button changes shape.
 */
@Composable
internal fun SeaPlayPebble(
    isPlaying: Boolean,
    pigment: com.glasscast.app.ui.seaglass.Pigment,
    dark: Boolean,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    width: androidx.compose.ui.unit.Dp = size,
    onClick: () -> Unit
) {
    val turns = remember { androidx.compose.animation.core.Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shown = rememberSteadyPlaying(isPlaying)
    com.glasscast.app.ui.seaglass.GlassButton(
        onClick = {
            // The pebble turns over when it's pressed, and only then: a skip
            // makes the player stop for a moment while it buffers, and that
            // used to turn it too.
            shown.pressed()
            scope.launch {
                turns.animateTo(turns.targetValue + 1f, tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing))
            }
            onClick()
        },
        pigment = pigment,
        dark = dark,
        recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Tumbled,
        seed = 8,
        turn = { turns.value },
        modifier = modifier.size(width = width, height = size)
    ) {
        val playing = shown.value
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause" else tr("Play"),
            tint = pigment.ink,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Whether play should show as playing, held steady through the moment of
 * buffering a seek causes (the player reports "not playing" for a beat, then
 * playing again). A press shows its result at once; a stop from anywhere else
 * shows after half a second, unless playing has resumed by then.
 */
internal class SteadyPlaying(initial: Boolean) {
    var value by mutableStateOf(initial)
        internal set
    internal var pressedAt = 0L

    /** The button was pressed: show the other state now. */
    fun pressed() {
        pressedAt = android.os.SystemClock.uptimeMillis()
        value = !value
    }
}

@Composable
internal fun rememberSteadyPlaying(isPlaying: Boolean): SteadyPlaying {
    val steady = remember { SteadyPlaying(isPlaying) }
    androidx.compose.runtime.LaunchedEffect(isPlaying) {
        val justPressed = android.os.SystemClock.uptimeMillis() - steady.pressedAt < 1_500
        if (isPlaying || justPressed) {
            steady.value = isPlaying
        } else {
            kotlinx.coroutines.delay(450)
            steady.value = false
        }
    }
    return steady
}

/** How wide the Sea Glass bubble's edge wanders, as a share of its size. */
internal const val SeaBubbleTumble = 0.075f
private const val SeaBubbleSeed = 31
/** Seconds of playing per turn of the bubble's pebble: slow enough to read as breathing. */
internal const val SeaBubbleBreath = 7f

/**
 * The Sea Glass bubble's pebble, [wPx] × [hPx], into [path]: [e] of the way
 * from a square with [cornerDp] corners (0) to the full pebble (1). The cover's
 * clip, the progress line and the player's flight all use it, with the same
 * seed and turn, so the line runs parallel to the cover and the flight lands
 * on exactly the shape it left.
 */
internal fun seaBubbleOutline(path: Path, wPx: Float, hPx: Float, density: Float, e: Float, cornerDp: Float, turn: Float) {
    val w = wPx / density
    val h = hPx / density
    if (w < 1f || h < 1f) {
        path.reset()
        return
    }
    val half = (minOf(w, h) / 2f).coerceAtLeast(1f)
    val from = (cornerDp / half).coerceIn(0f, 1f)
    val k = e.coerceIn(0f, 1f)
    com.glasscast.app.ui.seaglass.GlassContour(w, h, from + (1f - from) * k, SeaBubbleTumble * k, SeaBubbleSeed, turn)
        .outline(density, path)
}
