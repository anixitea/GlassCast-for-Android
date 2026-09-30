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
    // Was an infinite transition that kept running while paused — it only
    // *displayed* a still pose — and fed its values through composition, so
    // any list showing the current episode recomposed every frame, forever.
    // Now: a 30fps clock that stops when paused, read only in draw.
    val clock = rememberFrameClock(running = playing, fps = 30)

    Canvas(modifier.size(size)) {
        val t = clock.value
        fun level(periodSeconds: Float): Float {
            if (!playing) return 0.45f
            // Triangle wave 0.25 → 1 → 0.25, matching the old reversing tween.
            val x = (t % (periodSeconds * 2f)) / periodSeconds
            val tri = if (x <= 1f) x else 2f - x
            return 0.25f + 0.75f * tri
        }
        val levels = floatArrayOf(level(0.43f), level(0.61f), level(0.52f))
        val gap = this.size.width * 0.14f
        val barW = (this.size.width - gap * 2) / 3f
        levels.forEachIndexed { i, value ->
            val h = this.size.height * value
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barW + gap), this.size.height - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f)
            )
        }
    }
}
