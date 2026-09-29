package com.glasscast.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A connected button group — Material 3 Expressive's shape, as Cider uses it
 * for transport and for album actions.
 *
 * The buttons share a row with only a hairline between them, and only the
 * group's outer ends get the large radius; the inner corners are tight. That
 * is what makes three buttons read as one control with three parts rather than
 * three separate buttons that happen to be adjacent — the grouping is carried
 * by the shape, so no container or divider is needed.
 */
enum class GroupPosition { Start, Middle, End, Only }

private fun groupShape(position: GroupPosition, outer: Dp, inner: Dp) = when (position) {
    GroupPosition.Start -> RoundedCornerShape(outer, inner, inner, outer)
    GroupPosition.Middle -> RoundedCornerShape(inner)
    GroupPosition.End -> RoundedCornerShape(inner, outer, outer, inner)
    GroupPosition.Only -> RoundedCornerShape(outer)
}

@Composable
fun ButtonGroup(
    modifier: Modifier = Modifier,
    height: Dp = 64.dp,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/**
 * One segment of a group. Pressing squashes it slightly toward its neighbours
 * rather than showing a ripple — the Expressive press response, and one that
 * survives on a glass or artwork background where a ripple would be invisible.
 */
@Composable
fun RowScope.GroupButton(
    position: GroupPosition,
    container: Color,
    content: Color,
    icon: ImageVector? = null,
    label: String? = null,
    description: String = label.orEmpty(),
    weight: Float = 1f,
    iconSize: Dp = 26.dp,
    outerRadius: Dp = 30.dp,
    innerRadius: Dp = 10.dp,
    /** Overrides every corner — for a button whose shape carries meaning, like play. */
    cornerOverride: Dp? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.55f),
        label = "groupPress"
    )
    val shape = cornerOverride?.let { RoundedCornerShape(it) }
        ?: groupShape(position, outerRadius, innerRadius)

    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(shape)
            .background(if (enabled) container else container.copy(alpha = container.alpha * 0.5f))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        val tint = if (enabled) content else content.copy(alpha = 0.35f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = description,
                    tint = tint,
                    modifier = Modifier.size(iconSize)
                )
            }
            if (icon != null && label != null) Spacer(Modifier.width(8.dp))
            if (label != null) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = tint
                )
            }
        }
    }
}
