package com.glasscast.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

private val BarFill = Color(0xFF232227)
/** Icons and labels on the dark bar. */
val BarContentLight = Color(0xFFF4F3F7)

/**
 * One spring for everything in the bar, so the pill, the widths and the label
 * move as one. Nearly critically damped: at 0.62 the pill ran about 8% past
 * its tab and back, and since every tab's width follows the pill, the whole
 * row of icons and the label shook side to side as it settled.
 */
private fun <T> barSpring() = spring<T>(dampingRatio = 0.86f, stiffness = 460f)

/** Inside a tab: from its edge to the icon, and from the icon to the label. */
private val TabPad = 10.dp
private val TabGap = 8.dp

/**
 * The tab bar, rebuilt on Cider's.
 *
 * What made the old one feel stiff is that nothing in it ever *moved*: each tab
 * faded its own background in or out, so switching tabs was two fades in two
 * places. Here the selection is a single pill that travels. Every tab reports
 * where it is; the pill springs to the selected one's bounds, so the motion
 * is one object arriving, not an animation bolted onto each item.
 *
 * The selected tab also widens to fit its label (exactly its label: the
 * width comes from the label's own length, so "Downloads" isn't cut short)
 * while the one it left narrows, its label fading as it goes, on the same
 * spring. Because the pill tracks the tab's *live* bounds
 * while those widths are still animating, it chases a moving target, which is
 * where the fluid, slightly liquid feel comes from.
 *
 * Dark, as Cider's is: the bar is chrome floating over content, and a dark
 * pill reads as "on top" against a light page and a dark one alike. With
 * Adaptive tab bar on (Settings > Appearance) it follows the mini player to
 * its pale version over a dark page, crossfading (GlassCastRoot, chromeMix).
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun GlassTabBar(
    tabs: List<GlassTab>,
    selectedIndex: Int,
    hazeState: HazeState,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Icons only, when the mini player's bubble is sharing the row. */
    compact: Boolean = false,
    /**
     * The bar's color: the playing cover's hue, dark or pale (see chromeBar,
     * chromeBarPale). A function, read while drawing, so a fade doesn't
     * recompose the bar.
     */
    tint: () -> Color = { BarFill },
    /** Icons and labels: light on the dark bar, dark on the pale one. */
    content: () -> Color = { BarContentLight }
) {
    val haptics = rememberHaptics()
    val shape = RoundedCornerShape(percent = 50)

    /*
     * Everything that moves here is driven by two animated numbers, read only
     * while measuring and drawing — the bar never recomposes to animate.
     *
     * It used to: each tab wrote its position into state after layout, the
     * pill read those positions during composition and chased them with
     * springs, and each tab's width was its own spring read the same way. Any
     * change of size ran layout → positions written → recompose → springs
     * restarted → layout again, frame after frame until it settled. The morph
     * narrows the bar on every frame of a collapse, which kept that loop going
     * on every scroll.
     *
     * [pill] is the selected tab as a traveling index — 1.4 is forty percent
     * of the way from tab 1 to tab 2 — so the pill glides between tabs and the
     * widths hand over between them from the same value. [labeled] is how much
     * the selected tab's label is showing (0 while compact).
     */
    val pill = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex) { pill.animateTo(selectedIndex.toFloat(), barSpring()) }
    val labeled = remember { Animatable(if (compact) 0f else 1f) }
    LaunchedEffect(compact) { labeled.animateTo(if (compact) 0f else 1f, barSpring()) }

    // The tab being left keeps its label while it narrows, fading with the
    // room it has left. It used to vanish the moment another tab was tapped,
    // a frame before anything else moved.
    val lastSelected = remember { intArrayOf(selectedIndex) }
    val leaving = remember(selectedIndex) { lastSelected[0].also { lastSelected[0] = selectedIndex } }

    // Each tab's x and width, written during placement and read while drawing
    // the pill. Plain floats, not state: nothing should recompose from them.
    val slots = remember(tabs.size) { FloatArray(tabs.size * 2) }
    // Each tab's room for its label once settled as the selection, written
    // while measuring (below) and read by the tab as it measures.
    val labelRoom = remember(tabs.size) { IntArray(tabs.size) }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = GlassGutter)
            .padding(bottom = 8.dp)
            .clip(shape)
            // Opaque, as Cider's is. The page's blur shows around and beneath
            // the bar (BottomGlassFade), not through it — the bar itself is a
            // solid object in the cover's hue.
            .drawBehind {
                drawRect(tint())
                val hairline = 0.5.dp.toPx()
                drawRoundRect(
                    color = content().copy(alpha = 0.08f),
                    topLeft = Offset(hairline / 2f, hairline / 2f),
                    size = Size(size.width - hairline, size.height - hairline),
                    cornerRadius = CornerRadius(size.height / 2f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(hairline)
                )
            }
            .padding(6.dp)
    ) {
        Layout(
            content = {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    TabItem(
                        tab = tab,
                        selected = selected,
                        // How much of the selected tab's label is showing:
                        // the same number that gives it its extra width
                        // (below), so the label and the room for it grow and
                        // shrink together. Tabs the pill only passes over
                        // widen for it but keep their labels hidden.
                        reveal = {
                            if (!selected && index != leaving) 0f
                            else (labeled.value.coerceIn(0f, 1f) *
                                (1f - kotlin.math.abs(pill.value - index)).coerceAtLeast(0f)).coerceIn(0f, 1f)
                        },
                        room = { labelRoom.getOrElse(index) { 0 } },
                        content = content,
                        modifier = Modifier,
                        onClick = {
                            if (!selected) haptics.play(Haptic.Select)
                            onTabSelected(index)
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .drawBehind {
                    val last = (tabs.size - 1).coerceAtLeast(0)
                    val p = pill.value.coerceIn(0f, last.toFloat())
                    val i = p.toInt().coerceAtMost(last)
                    val j = (i + 1).coerceAtMost(last)
                    val f = p - i
                    val x = slots[i * 2] + (slots[j * 2] - slots[i * 2]) * f
                    val w = slots[i * 2 + 1] + (slots[j * 2 + 1] - slots[i * 2 + 1]) * f
                    drawRoundRect(
                        color = content().copy(alpha = 0.16f),
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        ) { measurables, constraints ->
            val total = constraints.maxWidth
            val height = constraints.maxHeight
            val n = measurables.size
            if (n == 0) return@Layout layout(total, height) {}
            val p = pill.value
            val lab = labeled.value.coerceIn(0f, 1f)
            // What each tab needs: its icon alone (min intrinsic), and what its
            // label adds (max minus min; see TabItem). It used to be a fixed
            // share, 1.4 tabs' worth, which left "Downloads" as "Downlo…".
            val bare = IntArray(n) { measurables[it].minIntrinsicWidth(height) }
            val need = IntArray(n) { (measurables[it].maxIntrinsicWidth(height) - bare[it]).coerceAtLeast(0) }
            // Every tab keeps room for an icon; a label gets the rest, all it
            // needs if the bar has it, or the same fraction for every label on
            // a bar too narrow. The fraction comes from the widths alone, so it
            // holds still while the pill moves.
            val floor = bare.max()
            val spare = (total - floor * n).coerceAtLeast(0)
            val fit = (spare.toFloat() / need.max().coerceAtLeast(1)).coerceAtMost(1f)
            // The tab the pill sits on gets its label's room, shared between
            // two tabs while the pill travels.
            val extra = FloatArray(n) { i ->
                need[i] * fit * lab * (1f - kotlin.math.abs(p - i)).coerceAtLeast(0f)
            }
            val base = (total - extra.sum()) / n
            var used = 0
            val placeables = measurables.mapIndexed { index, measurable ->
                if (index < labelRoom.size) labelRoom[index] = (need[index] * fit).roundToInt()
                val w = if (index == measurables.lastIndex) {
                    (total - used).coerceAtLeast(0)
                } else {
                    (base + extra[index]).roundToInt().coerceAtLeast(0)
                }
                used += w
                measurable.measure(Constraints.fixed(w, height))
            }
            layout(total, height) {
                var x = 0
                placeables.forEachIndexed { index, placeable ->
                    placeable.place(x, 0)
                    if (index * 2 + 1 < slots.size) {
                        slots[index * 2] = x.toFloat()
                        slots[index * 2 + 1] = placeable.width.toFloat()
                    }
                    x += placeable.width
                }
            }
        }
    }
}

/**
 * One tab: the icon, and the label beside it as far as [reveal] says.
 *
 * Laid out by hand rather than as a row with an animated label. The label
 * used to appear and disappear on its own timing (a fade) while the tab's
 * width animated on the bar's spring, so for a few frames of every collapse
 * and expand the icon and label were wider than the tab: the row couldn't
 * center them, and the icon was pushed out past the pill's edge, then snapped
 * back when the label went. Here the label takes only the share of its width
 * that [reveal] gives it (the number that also sizes the tab), and the pair is
 * centered every frame, so the icon stays inside the pill throughout.
 *
 * The label is shortened only to [room], the room it has once the tab has
 * settled, never to the width of the moment: fitted to the moving width, it
 * was cut again every frame as the tab grew ("Dow…", "Downl…", "Downlo…").
 * Its intrinsic widths tell the bar what the tab needs: the icon alone (min)
 * and the icon with its label (max).
 */
@Composable
private fun TabItem(
    tab: GlassTab,
    selected: Boolean,
    reveal: () -> Float,
    room: () -> Int,
    content: () -> Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    // A small, firm give under the finger. It was 0.86 on a 0.5 spring, which
    // bounced back past full size.
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "tabPress"
    )
    // Read here, so a fade recomposes the tab, not the bar.
    val ink = content()
    val tint = if (selected) ink else ink.copy(alpha = 0.66f)

    val policy = remember(reveal, room) {
        object : MeasurePolicy {
            override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
                val w = constraints.maxWidth
                val h = constraints.maxHeight
                val icon = measurables[0].measure(Constraints())
                val gap = TabGap.roundToPx()
                val label = measurables[1].measure(Constraints(maxWidth = (room() - gap).coerceAtLeast(0)))
                val r = reveal()
                val shown = if (label.width > 0) (r * (gap + label.width)).roundToInt() else 0
                val x = (w - icon.width - shown) / 2
                return layout(w, h) {
                    icon.place(x, (h - icon.height) / 2)
                    if (r > 0f && label.width > 0) {
                        label.placeWithLayer(x + icon.width + gap, (h - label.height) / 2) {
                            // In only once there's mostly room for it.
                            alpha = ((r - 0.4f) / 0.6f).coerceIn(0f, 1f)
                        }
                    }
                }
            }

            override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                measurables[0].maxIntrinsicWidth(height) + 2 * TabPad.roundToPx()

            override fun IntrinsicMeasureScope.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
                measurables[0].maxIntrinsicWidth(height) + 2 * TabPad.roundToPx() +
                    TabGap.roundToPx() + measurables[1].maxIntrinsicWidth(height)
        }
    }

    Layout(
        content = {
            Icon(
                imageVector = tab.icon,
                contentDescription = tab.label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = tab.label,
                style = MaterialTheme.typography.titleSmall,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                // The icon already names the tab for TalkBack.
                modifier = Modifier.clearAndSetSemantics { }
            )
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        measurePolicy = policy
    )
}
