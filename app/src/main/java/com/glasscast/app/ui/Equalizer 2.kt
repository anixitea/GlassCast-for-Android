package com.glasscast.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Three bars marking the row that is playing now, as Cider does in its track
 * lists. Still when paused, so the row still reads as "this one" without
 * claiming it is moving.
 */
@Composable
fun Equalizer(color: Color, playing: Boolean, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    val transition = rememberInfiniteTransition(label = "eq")

    @Composable
    fun bar(period: Int, label: String): Float {
        val v by transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(period, easing = LinearEasing), RepeatMode.Reverse),
            label = label
        )
        return if (playing) v else 0.45f
    }

    val a = bar(430, "b1")
    val b = bar(610, "b2")
    val c = bar(520, "b3")

    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.14f
        val barW = (this.size.width - gap * 2) / 3f
        listOf(a, b, c).forEachIndexed { i, level ->
            val h = this.size.height * level
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), this.size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
