package com.glasscast.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A hairline scrubber that thickens under the finger, rather than a Material
 * slider with a permanent thumb.
 *
 * Material's Slider is right for a setting you adjust and leave. A scrubber is
 * looked at constantly and touched rarely, so at rest it should be a progress
 * line and nothing else — the thumb is visual noise for the 99% of the time
 * you're only reading position off it.
 */
@Composable
fun ThinSlider(
    progress: Float,
    accent: Color,
    trackColor: Color,
    enabled: Boolean,
    /**
     * Weight is hierarchy. The scrubber and the volume line were identical, so
     * the two read as a repeated element rather than as a primary control and a
     * secondary one. The scrubber is the thicker of the two now.
     */
    restHeight: Dp = 4.dp,
    activeHeight: Dp = 9.dp,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var dragging by remember { mutableStateOf(false) }
    var localProgress by remember { mutableStateOf(0f) }

    val shown = if (dragging) localProgress else progress.coerceIn(0f, 1f)

    val trackHeight by animateDpAsState(
        targetValue = if (dragging) activeHeight else restHeight,
        animationSpec = spring(),
        label = "trackHeight"
    )
    val trackAlpha by animateFloatAsState(
        targetValue = if (dragging) 1f else 0.85f,
        label = "trackAlpha"
    )

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            // A tall touch area over a short track: the line is 4dp, the target
            // is 40. Nobody should have to aim at a hairline.
            .height(40.dp)
    ) {
        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }

        fun fractionAt(x: Float) = (x / widthPx).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .align(Alignment.Center)
                .clip(RoundedCornerShape(percent = 50))
                .background(trackColor)
                /*
                 * One gesture loop for taps and drags together.
                 *
                 * This used to be two pointerInput blocks — detectTapGestures
                 * and detectHorizontalDragGestures. That doesn't work: the drag
                 * detector claims the pointer on the way down, and a tap has no
                 * drag to report, so tapping the bar to seek silently did
                 * nothing. Dragging worked, which is why it went unnoticed.
                 *
                 * Taking the position from the initial down also means the bar
                 * jumps to your finger immediately instead of waiting for the
                 * first movement.
                 */
                .pointerInput(enabled, widthPx) {
                    if (!enabled) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        localProgress = fractionAt(down.position.x)
                        onScrubStart()
                        onScrub(localProgress)

                        while (true) {
                            val event = awaitPointerEvent()
                            val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!pointer.pressed) {
                                pointer.consume()
                                break
                            }
                            if (pointer.positionChanged()) {
                                localProgress = fractionAt(pointer.position.x)
                                onScrub(localProgress)
                                pointer.consume()
                            }
                        }

                        dragging = false
                        onScrubEnd(localProgress)
                    }
                }
        ) {
            Box(
                Modifier
                    .fillMaxWidth(shown)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(accent.copy(alpha = trackAlpha))
            )
        }
    }
}
