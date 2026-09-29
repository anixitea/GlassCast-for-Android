package com.glasscast.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import kotlin.math.PI


/**
 * The mini player, collapsed to a bubble beside the tab bar while you scroll.
 *
 * A cookie-shaped cover with a progress ring around it. The scalloped edge
 * turns slowly while audio plays and stops where it is on pause — a quieter
 * version of the wave's job, telling play state at a glance without text.
 * Only the outline turns; the cover inside stays upright.
 *
 * Tapping it opens the full player, same as the card it replaced.
 */
@Composable
fun MiniBubble(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    progress: Float,
    accent: Color,
    track: Color,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp
) {
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val haptics = rememberHaptics()

    // One lobe per 2.4s. Animatable rather than an infinite transition so a
    // pause stops the edge where it is instead of snapping it back to zero.
    val spin = remember { Animatable(0f) }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            spin.animateTo(
                targetValue = spin.value + (2 * PI / CookieLobes).toFloat(),
                animationSpec = tween(2_400, easing = LinearEasing)
            )
        }
    }

    Box(
        modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptics.play(Haptic.Expand)
                onOpen()
            },
        contentAlignment = Alignment.Center
    ) {
        /*
         * Track and progress both follow the scalloped edge — one curve, drawn
         * twice. The progress is a PathMeasure segment of that curve from the
         * top, so it runs along every lobe exactly as the grey track does.
         */
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.5.dp.toPx()
            val outline = cookiePath(
                width = this.size.width,
                height = this.size.height,
                rotation = spin.value,
                inset = stroke / 2f
            )
            drawPath(outline, color = track, style = Stroke(width = stroke))

            val fraction = progress.coerceIn(0f, 1f)
            if (fraction > 0f) {
                val measure = PathMeasure()
                measure.setPath(outline, false)
                val arc = Path()
                measure.getSegment(0f, measure.length * fraction, arc, true)
                drawPath(
                    arc,
                    color = accent,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        // The cover, clipped to the same curve, inset inside the ring.
        Box(
            Modifier
                .padding(7.dp)
                .sharedArtwork(NowPlayingArtKey, LocalPlayerScope.current)
                .fillMaxSize()
                .clip(CookieShape(rotation = spin.value))
        ) {
            Artwork(url = art, sizeDp = size, corner = 0.dp, fill = true)
        }
    }
}
