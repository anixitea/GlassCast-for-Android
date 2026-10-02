package com.glasscast.app.tv

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.glasscast.app.ui.requestWhenReady
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * The focus vocabulary.
 *
 * On a phone, "where am I" is answered by where the finger is. On a TV there is
 * no finger, so the interface has to answer it continuously, for every item,
 * without being asked. This is the single largest difference between the two
 * versions and the thing most likely to make the TV build feel wrong if it is
 * done weakly.
 *
 * Three signals together, because any one alone is ambiguous at three metres:
 * the item **grows**, gains a **bright ring**, and **lifts** off the page. Scale
 * reads first in peripheral vision, the ring survives on busy artwork where
 * scale doesn't, and the shadow separates it from neighbours of similar colour.
 */
@Composable
fun Modifier.tvFocusable(
    shape: Shape = RoundedCornerShape(14.dp),
    accent: Color,
    scale: Float = 1.07f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    /*
     * One animation, not two.
     *
     * Scale and ring were separate animateFloatAsState calls, which on a grid
     * of twenty tiles meant forty running animations. They always move together,
     * so they are one value now.
     */
    val focus by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "focus"
    )

    /*
     * No elevation shadow. `graphicsLayer.shadowElevation` casts its shadow from
     * the *layer's* outline, and that outline is a rectangle unless a shape is
     * set on the layer — which is exactly the grey square that appeared around
     * every focused pill. Modifier.shadow(clip = false) had the same effect from
     * the other direction.
     *
     * Scale and the ring carry focus on their own, and dropping the shadow
     * removes a per-item render pass from every frame of every focus change.
     */
    this
        .graphicsLayer {
            val grow = 1f + (scale - 1f) * focus
            scaleX = grow
            scaleY = grow
        }
        .clip(shape)
        .border(
            width = (3 * focus).dp,
            color = accent.copy(alpha = focus),
            shape = shape
        )
        .focusable(enabled = enabled, interactionSource = interaction)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            // No ripple: a ripple is a touch idiom and on a focused-but-unclicked
            // item it reads as a second, competing highlight.
            indication = null,
            onClick = onClick
        )
}

/**
 * A focusable row or list entry, where growing would disturb the layout. The
 * ring and a filled background carry it instead.
 */
@Composable
fun Modifier.tvFocusableRow(
    accent: Color,
    surface: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val level by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = spring(),
        label = "rowFocus"
    )

    this
        .clip(shape)
        .background(surface.copy(alpha = 0.10f * level))
        .border((2 * level).dp, accent.copy(alpha = level * 0.9f), shape)
        .focusable(enabled = enabled, interactionSource = interaction)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
}
