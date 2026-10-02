package com.glasscast.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlin.math.roundToInt

private val BarFill = Color(0xFF232227)
private val BarContent = Color(0xFFF4F3F7)

/** One spring for everything in the bar, so the pill, the widths and the label move as one. */
private fun <T> barSpring() = spring<T>(dampingRatio = 0.62f, stiffness = 420f)

/**
 * The tab bar, rebuilt on Cider's.
 *
 * What made the old one feel stiff is that nothing in it ever *moved*: each tab
 * faded its own background in or out, so switching tabs was two fades in two
 * places. Here the selection is a single pill that travels. Every tab reports
 * where it is; the pill springs to the selected one's bounds on an underdamped
 * spring, so it overshoots a touch and settles — the bounce is the physics of
 * one object arriving, not an animation bolted onto each item.
 *
 * The selected tab also widens to fit its label while the one it left
 * narrows, on the same spring. Because the pill tracks the tab's *live* bounds
 * while those widths are still animating, it chases a moving target, which is
 * where the fluid, slightly liquid feel comes from.
 *
 * Dark in both themes, as Cider's is: the bar is chrome floating over content,
 * and a dark pill reads as "on top" against a light page and a dark one alike.
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
    /** The bar's color — the playing cover's hue, darkened. See chromeBar. */
    tint: Color = BarFill
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

    // Each tab's x and width, written during placement and read while drawing
    // the pill. Plain floats, not state: nothing should recompose from them.
    val slots = remember(tabs.size) { FloatArray(tabs.size * 2) }
    val pillColor = BarContent.copy(alpha = 0.16f)

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
            .background(tint)
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(6.dp)
    ) {
        Layout(
            content = {
                tabs.forEachIndexed { index, tab ->
                    val selected = index == selectedIndex
                    TabItem(
                        tab = tab,
                        selected = selected,
                        showLabel = selected && !compact,
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
                        color = pillColor,
                        topLeft = Offset(x, 0f),
                        size = Size(w, size.height),
                        cornerRadius = CornerRadius(size.height / 2f)
                    )
                }
        ) { measurables, constraints ->
            val total = constraints.maxWidth
            val height = constraints.maxHeight
            val p = pill.value
            val lab = labeled.value.coerceIn(0f, 1f)
            // Every tab gets 1; the tab the pill sits on gets up to 1.2 more,
            // shared between two tabs while the pill travels.
            val weights = FloatArray(measurables.size) { index ->
                1f + 1.2f * lab * (1f - kotlin.math.abs(p - index)).coerceAtLeast(0f)
            }
            val sum = weights.sum().coerceAtLeast(0.0001f)
            var used = 0
            val placeables = measurables.mapIndexed { index, measurable ->
                val w = if (index == measurables.lastIndex) {
                    (total - used).coerceAtLeast(0)
                } else {
                    (total * weights[index] / sum).roundToInt().coerceAtLeast(0)
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

@Composable
private fun TabItem(
    tab: GlassTab,
    selected: Boolean,
    showLabel: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.86f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "tabPress"
    )
    val tint = if (selected) BarContent else BarContent.copy(alpha = 0.66f)

    Row(
        modifier
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(RoundedCornerShape(percent = 50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        // Fade only: the tab's width is animated by the bar's layout, so the
        // label doesn't need to animate its own size as well.
        AnimatedVisibility(
            visible = showLabel,
            enter = fadeIn(tween(160, delayMillis = 60)),
            exit = fadeOut(tween(90))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tab.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                    maxLines = 1
                )
            }
        }
    }
}
