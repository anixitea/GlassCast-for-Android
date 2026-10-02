package com.glasscast.app.ui

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
    /** The bar's colour — the playing cover's hue, darkened. See chromeBar. */
    tint: Color = BarFill
) {
    val haptics = rememberHaptics()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(percent = 50)
    val bounds = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }

    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = GlassGutter)
            .padding(bottom = 8.dp)
            .clip(shape)
            .hazeEffect(state = hazeState, style = HazeMaterials.thick(tint))
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), shape)
            .padding(6.dp)
    ) {
        // The travelling pill. Only composed once the selected tab has been
        // measured, so its springs start from the right place instead of
        // sliding in from the left edge on first launch.
        bounds[selectedIndex]?.let { target ->
            val x by animateFloatAsState(target.first, barSpring(), label = "pillX")
            val w by animateFloatAsState(target.second, barSpring(), label = "pillW")
            Box(
                Modifier
                    .offset { IntOffset(x.roundToInt(), 0) }
                    .width(with(density) { w.toDp() })
                    .height(48.dp)
                    .clip(shape)
                    .background(BarContent.copy(alpha = 0.16f))
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val weight by animateFloatAsState(
                    targetValue = if (selected && !compact) 2.2f else 1f,
                    animationSpec = barSpring(),
                    label = "tabWeight"
                )
                TabItem(
                    tab = tab,
                    selected = selected,
                    showLabel = selected && !compact,
                    modifier = Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .onGloballyPositioned {
                            bounds[index] = it.positionInParent().x to it.size.width.toFloat()
                        },
                    onClick = {
                        if (!selected) haptics.play(Haptic.Select)
                        onTabSelected(index)
                    }
                )
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
        AnimatedVisibility(
            visible = showLabel,
            enter = expandHorizontally(barSpring()) + fadeIn(),
            exit = shrinkHorizontally(barSpring()) + fadeOut()
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
