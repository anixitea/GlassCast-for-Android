package com.glasscast.app.ui.liquid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.FlightClip
import com.glasscast.app.ui.GlassTab
import com.glasscast.app.ui.TabStrip
import androidx.compose.ui.geometry.CornerRadius
import com.glasscast.app.ui.Haptic
import com.glasscast.app.ui.LocalPlayerScope
import com.glasscast.app.ui.MiniArtKey
import com.glasscast.app.ui.MiniArtShape
import com.glasscast.app.ui.rememberHaptics
import com.glasscast.app.ui.sharedArtwork
import com.glasscast.app.ui.skipForwardIcon
import com.glasscast.app.ui.tr
import kotlin.math.roundToInt

/** The bar (and the bubble, which matches it), the mini player above it, and the gap between them. */
internal val LiquidBarHeight = 62.dp
internal val LiquidAccessoryHeight = 56.dp
internal val LiquidGap = 10.dp
private val LiquidSide = 16.dp

/**
 * Frosted Glass's floating chrome (the code calls the look Liquid; see
 * Liquid.kt): a capsule of glass with every tab in it, Search included,
 * the mini player riding above it as the bar's accessory.
 *
 * Scrolling down a page, as in the Original look, the mini player becomes a
 * bubble of glass beside the bar (the cover inside it, the time running
 * round it as a ring) and the bar narrows to make room for it, its names
 * folding away to icons. Scrolling back up brings the mini player back
 * above. With nothing playing nothing changes. [minimize] (0 out, 1
 * bubble) is read only while placing and drawing, so the whole morph is
 * layout and paint; nothing recomposes for it.
 *
 * [dark] is the bar's own appearance, which isn't always the app's: with
 * Adaptive tab bar on it follows the page under it (see GlassCastRoot).
 */
@Composable
fun LiquidChrome(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    minimize: State<Float>,
    minimized: Boolean,
    dark: Boolean,
    hasAccessory: Boolean,
    accessory: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLiquid.current ?: return
    val backdrop = LocalLiquidBackdrop.current
    val ink = if (dark) Color.White else Color.Black
    val accent = legibleAccent(t.accent, dark)
    Layout(
        modifier = modifier.fillMaxWidth(),
        content = {
            // The strip's own touches: a tap beside or between the glass
            // shouldn't open whatever is on the page behind it.
            Box(Modifier.pointerInput(Unit) {})
            LiquidTabBar(
                tabs = tabs,
                selected = selected,
                onSelect = onSelect,
                compact = minimized && hasAccessory,
                dark = dark,
                ink = ink,
                accent = accent,
                backdrop = backdrop
            )
            Box { if (hasAccessory) accessory() }
        }
    ) { measurables, constraints ->
        val m = if (hasAccessory) minimize.value.coerceIn(0f, 1f) else 0f
        val width = constraints.maxWidth
        val side = LiquidSide.roundToPx()
        val fullW = (width - side * 2).coerceAtLeast(1)
        val barH = LiquidBarHeight.roundToPx()
        val accFull = LiquidAccessoryHeight.toPx()
        val gap = LiquidGap.toPx()
        val height = (if (hasAccessory) barH + gap + accFull else barH.toFloat()).roundToInt()
        val barY = height - barH

        // The bar gives up its right end to the bubble: a bar's height and the gap.
        val barW = (fullW - (barH + gap) * m).roundToInt().coerceAtLeast(1)
        val bar = measurables[1].measure(Constraints.fixed(barW, barH))

        var top = barY
        val accPlaced = if (hasAccessory) {
            // The Original look's move, in two steps: the capsule draws in
            // toward its right end until it's a circle there, then drops
            // into the bubble at the bar's right end (the bar's own height,
            // level with it). Moving both ways at once read as a diagonal.
            val a = liquidSlide(m)
            val b = liquidDrop(m)
            val right = width - side
            val accH = accFull + (barH - accFull) * b
            val accW = (fullW + (accFull - fullW) * a) + (barH - accFull) * b
            val accX = right - accW
            val accY = barY * b
            top = minOf(top, accY.roundToInt())
            Triple(
                measurables[2].measure(Constraints.fixed(accW.roundToInt().coerceAtLeast(1), accH.roundToInt().coerceAtLeast(1))),
                accX.roundToInt(),
                accY.roundToInt()
            )
        } else {
            null
        }
        val guard = measurables[0].measure(Constraints.fixed(width, (height - top).coerceAtLeast(0)))
        layout(width, height) {
            guard.place(0, top)
            bar.place(side, barY)
            accPlaced?.let { (p, x, y) -> p.place(x, y) }
        }
    }
}

/** The bubble's two steps: drawing in to the right (0 → 1 over the first 55%), then dropping (the rest). */
internal fun liquidSlide(m: Float) = (m / 0.55f).coerceIn(0f, 1f)
internal fun liquidDrop(m: Float) = ((m - 0.55f) / 0.45f).coerceIn(0f, 1f)

/** How tall the chrome stands over the page when it's fully out. */
internal fun liquidChromeHeight(withAccessory: Boolean) =
    LiquidBarHeight + if (withAccessory) LiquidGap + LiquidAccessoryHeight else 0.dp

/** How tall it stands with the mini player as the bubble: the bar's row. */
internal val LiquidChromeMinimizedHeight = LiquidBarHeight

@Composable
private fun LiquidTabBar(
    tabs: List<GlassTab>,
    selected: Int,
    onSelect: (Int) -> Unit,
    compact: Boolean,
    dark: Boolean,
    ink: Color,
    accent: Color,
    backdrop: LiquidBackdrop?
) {
    val fill = if (dark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.075f)
    Box(
        Modifier
            .fillMaxSize()
            .liquidGlass(backdrop, LiquidMaterial.regular(dark), dark = dark)
            .clip(CircleShape)
            .padding(7.dp)
    ) {
        // The tabs, as every look's bar lays them out (TabStrip): icons, and
        // the one you're on widened to show its whole name beside its icon,
        // folded to icons alone while the bubble shares the row.
        TabStrip(
            tabs = tabs,
            selectedIndex = selected,
            onTabSelected = onSelect,
            compact = compact,
            ink = { ink },
            selectedInk = { accent },
            restAlpha = 0.88f,
            // The selection: a drop of frosted glass in the bar, light along
            // its top and round its rim.
            drawPill = { x, w ->
                val r = CornerRadius(size.height / 2f)
                // The liquid finish: a clearer drop with more light on it.
                val light = PaneFinish.light
                drawRoundRect(PaneFinish.fill(fill), Offset(x, 0f), Size(w, size.height), r)
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = ((if (dark) 0.10f else 0.45f) * light).coerceAtMost(1f)),
                        0.5f to Color.White.copy(alpha = 0f)
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = r
                )
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        0f to Color.White.copy(alpha = ((if (dark) 0.30f else 0.9f) * light).coerceAtMost(1f)),
                        0.5f to Color.White.copy(alpha = 0f),
                        1f to Color.White.copy(alpha = ((if (dark) 0.16f else 0.5f) * light).coerceAtMost(1f)),
                        start = Offset(x, 0f),
                        end = Offset(x + w, size.height)
                    ),
                    topLeft = Offset(x, 0f),
                    size = Size(w, size.height),
                    cornerRadius = r,
                    style = Stroke(PaneFinish.rim.toPx())
                )
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * The mini player, as the bar's accessory: a capsule of glass with the cover
 * at its start, the title and show, a line of how far in you are under them,
 * then play and skip forward. Tapping it opens the player, the cover flying
 * up out of it and back.
 *
 * As the bar minimizes it becomes a bubble of its own, beside the circle: the
 * capsule draws in to a drop of glass, everything but the cover fades, the
 * cover rounds into a circle in its middle with a rim of light round it,
 * and the time runs round it as a ring. One element all the way, so the
 * morph is layout and paint ([minimize]).
 */
@Composable
fun LiquidAccessory(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    skipSeconds: Int,
    minimize: State<Float>,
    dark: Boolean,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val t = LocalLiquid.current ?: return
    val backdrop = LocalLiquidBackdrop.current
    val haptics = rememberHaptics()
    val ink = if (dark) Color.White else Color.Black
    val accent = legibleAccent(t.accent, dark)
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val progress = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    // Once it has drawn in to the circle, it is the bubble: the cover becomes glass, and the buttons stop taking taps.
    val bubble by remember { derivedStateOf { minimize.value > 0.6f } }
    SideEffect { MiniArtShape.bubble = 0f; MiniArtShape.sea = false }

    Box(
        modifier
            .fillMaxSize()
            .liquidGlass(backdrop, LiquidMaterial.regular(dark), dark = dark)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.play(Haptic.Expand)
                onOpen()
            }
            // The bubble's ring: the time, round the cover.
            .drawWithContent {
                drawContent()
                val k = ((liquidDrop(minimize.value) - 0.6f) / 0.4f).coerceIn(0f, 1f)
                if (k > 0f) {
                    val stroke = 2.5.dp.toPx()
                    val r = size.minDimension / 2f - 3.5.dp.toPx()
                    drawCircle(ink.copy(alpha = 0.14f * k), radius = r, style = Stroke(stroke))
                    if (progress > 0.001f) {
                        val sweep = 360f * progress
                        drawArc(
                            color = accent.copy(alpha = k),
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(center.x - r, center.y - r),
                            size = Size(r * 2f, r * 2f),
                            style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                }
            }
    ) {
        // The capsule's contents, beside where the cover sits. They go first.
        Row(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (1f - liquidSlide(minimize.value) * 2.5f).coerceIn(0f, 1f) }
                .padding(start = LiquidArtInset + LiquidAccessoryArt + 11.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feed?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp, lineHeight = 15.sp),
                    color = ink.copy(alpha = 0.58f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                // How far in: the whole width of the text, so it visibly moves.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .drawBehind {
                            val r = size.height / 2f
                            drawRoundRect(ink.copy(alpha = 0.13f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
                            if (progress > 0f) {
                                drawRoundRect(
                                    accent,
                                    size = Size((size.width * progress).coerceAtLeast(size.height), size.height),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r)
                                )
                            }
                        }
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !bubble) {
                        haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                        onPlayPause()
                    }
                    .semantics { contentDescription = if (isPlaying) tr("Pause") else tr("Play") },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(28.dp)
                )
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !bubble) {
                        haptics.play(Haptic.SkipForward)
                        onSkipForward()
                    }
                    .semantics { contentDescription = tr("Forward {0} seconds", skipSeconds) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    skipForwardIcon(skipSeconds),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(25.dp)
                )
            }
        }

        // The cover: at the capsule's start, then the middle of the bubble.
        // Rounded square to circle over the first half, with the glass's rim
        // of light coming up round it as it drops in. It was a lens of glass
        // once in the bubble, but the lens bends the picture's edges, so the
        // cover seemed to change size inside the bubble the moment it landed
        // (from the full player especially). Now it's the same picture all
        // the way, and the bubble around it is the glass.
        Box(
            Modifier
                .layout { measurable, constraints ->
                    val m = minimize.value.coerceIn(0f, 1f)
                    // Glides along to the middle as the capsule draws in, and
                    // grows a little as it drops into the bubble (the bar's height).
                    val side = (LiquidAccessoryArt.toPx() + (LiquidBubbleArt - LiquidAccessoryArt).toPx() * liquidDrop(m)).roundToInt()
                    val p = measurable.measure(Constraints.fixed(side, side))
                    val w = constraints.maxWidth
                    val h = constraints.maxHeight
                    val fromX = LiquidArtInset.toPx()
                    val toX = (w - side) / 2f
                    val a = liquidSlide(m)
                    layout(w, h) {
                        p.place((fromX + (toX - fromX) * a).roundToInt(), (h - side) / 2)
                    }
                }
                .graphicsLayer {
                    val round = liquidSlide(minimize.value)
                    val fraction = LiquidArtCorner + (0.5f - LiquidArtCorner) * round
                    // The flight back from the player lands on this shape.
                    MiniArtShape.roundness = fraction
                    shape = RoundedCornerShape(percent = (fraction * 100f).roundToInt())
                    clip = true
                }
                .drawWithContent {
                    drawContent()
                    val k = liquidDrop(minimize.value)
                    if (k > 0f) {
                        drawCircle(
                            brush = Brush.linearGradient(
                                0f to Color.White.copy(alpha = 0.55f * k),
                                0.5f to Color.White.copy(alpha = 0f),
                                1f to Color.White.copy(alpha = 0.22f * k),
                                start = Offset.Zero,
                                end = Offset(size.width, size.height)
                            ),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
        ) {
            if (bubble) SideEffect { MiniArtShape.roundness = 0.5f }
            Artwork(
                url = art,
                sizeDp = LiquidBubbleArt,
                corner = 0.dp,
                fill = true,
                modifier = Modifier.sharedArtwork(MiniArtKey, LocalPlayerScope.current, clip = FlightClip)
            )
        }
    }
}

/**
 * The mini player's cover: its side in the capsule and in the bubble, its
 * margin from the capsule's end (clear of the rounded end; it sat almost on
 * it at 7dp), and its corner as a share of the side in the capsule.
 */
private val LiquidAccessoryArt = 38.dp
private val LiquidBubbleArt = 46.dp
private val LiquidArtInset = 11.dp
private const val LiquidArtCorner = 9f / 38f

/**
 * The accent, moved just far enough toward white (on dark glass) or black
 * (on light glass) to read as a label there. A dark red on a dark bar sank;
 * a pale yellow on a light one vanished.
 */
fun legibleAccent(accent: Color, dark: Boolean): Color {
    val target = if (dark) Color.White else Color.Black
    var k = 0f
    var c = accent
    while (k <= 0.8f) {
        c = lerp(accent, target, k)
        val l = c.luminance()
        if ((dark && l >= 0.30f) || (!dark && l <= 0.20f)) return c
        k += 0.05f
    }
    return c
}
