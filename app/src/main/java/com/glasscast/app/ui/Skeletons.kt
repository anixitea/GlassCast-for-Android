package com.glasscast.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Gray stand-ins for content still on the wire, laid out to the same metrics as
 * the real rows so nothing jumps when the data lands.
 *
 * A spinner says "something is happening". A skeleton says what is about to be
 * there and how much of it, which on a list is most of what you wanted to know
 * — and it removes the layout shift that a spinner guarantees, because the
 * spinner occupies nothing like the space the content will.
 */

private const val SHIMMER_PERIOD_MS = 1400

private val BlockShape = RoundedCornerShape(8.dp)
private val LineShape = RoundedCornerShape(4.dp)

// Ragged widths, so a run of rows reads as text rather than as a barcode.
private val TitleWidths = listOf(0.68f, 0.46f, 0.58f, 0.74f, 0.52f)
private val SubtitleWidths = listOf(0.34f, 0.44f, 0.27f, 0.38f, 0.31f)

/**
 * One placeholder block with a highlight sweeping across it.
 *
 * The sweep is read inside the draw block rather than in the composable body: a
 * screenful of these would otherwise recompose on every animation frame, and
 * all any of them needs per frame is a fresh gradient.
 */
@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = BlockShape) {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = MaterialTheme.colorScheme.onSurfaceVariant
        .copy(alpha = 0.16f)
        .compositeOver(base)

    val sweep by rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(SHIMMER_PERIOD_MS, easing = LinearEasing)),
        label = "sweep"
    )

    androidx.compose.foundation.layout.Box(
        modifier
            .clip(shape)
            .drawWithCache {
                // The band travels from fully off one edge to fully off the
                // other, leaving a beat of flat gray between passes rather than
                // a highlight parked permanently somewhere on the block.
                val band = size.width * 0.5f
                val startX = -band + sweep * (size.width + band * 2)
                val brush = Brush.horizontalGradient(
                    colors = listOf(base, highlight, base),
                    startX = startX,
                    endX = startX + band
                )
                onDrawBehind { drawRect(brush) }
            }
    )
}

@Composable
private fun SkeletonLine(fraction: Float, height: Dp, modifier: Modifier = Modifier) {
    ShimmerBox(
        modifier = modifier.fillMaxWidth(fraction).height(height),
        shape = LineShape
    )
}

/** Stands in for a directory search result or a chart row, artwork and all. */
@Composable
fun ResultRowSkeleton(index: Int = 0) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShimmerBox(Modifier.size(58.dp), RoundedCornerShape(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            SkeletonLine(TitleWidths[index % TitleWidths.size], 15.dp)
            SkeletonLine(SubtitleWidths[index % SubtitleWidths.size], 12.dp)
        }
    }
}

/** Stands in for an episode row on a show page or in Latest. */
@Composable
fun EpisodeRowSkeleton(index: Int = 0) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        SkeletonLine(0.22f, 11.dp)
        SkeletonLine(TitleWidths[index % TitleWidths.size], 16.dp)
        SkeletonLine(SubtitleWidths[(index + 2) % SubtitleWidths.size] + 0.4f, 12.dp)
        Spacer(Modifier.height(2.dp))
        ShimmerBox(Modifier.size(width = 74.dp, height = 26.dp), RoundedCornerShape(13.dp))
    }
}

/** Stands in for one library tile in the two-column grid. */
@Composable
fun ShowTileSkeleton(index: Int = 0) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ShimmerBox(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            RoundedCornerShape(12.dp)
        )
        SkeletonLine(TitleWidths[index % TitleWidths.size], 14.dp)
        SkeletonLine(SubtitleWidths[index % SubtitleWidths.size], 11.dp)
    }
}
