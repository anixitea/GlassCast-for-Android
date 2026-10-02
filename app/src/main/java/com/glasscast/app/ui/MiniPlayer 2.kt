package com.glasscast.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import dev.chrisbanes.haze.HazeState

/**
 * Floating glass card above the tab bar, sharing its corner radius and gutter
 * so the two read as one stacked object rather than two bars.
 *
 * The artwork's palette comes through as a low-alpha horizontal wash over the
 * frost. It has to stay low: the point of the glass is that you can see the
 * page moving underneath, and a heavy tint turns the panel back into a solid.
 */
@Composable
fun MiniPlayer(
    episode: Episode,
    feed: Feed?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    hazeState: HazeState,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberHaptics()
    val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
    val (colors, _) = rememberArtworkColors(art)
    val shape = RoundedCornerShape(30.dp)

    // Cider's card: solid, dark in both themes, in the cover's own hue. The
    // colours animate so a new episode re-tints the card instead of cutting.
    val surface by animateColorAsState(colors.chromeSurface, tween(600), label = "miniSurface")
    val button by animateColorAsState(colors.chromeButton, tween(600), label = "miniButton")
    val progress = if (durationMs > 0) {
        (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = GlassGutter)
            .padding(bottom = 8.dp)
            .shadow(18.dp, shape, clip = false, ambientColor = surface, spotColor = surface)
            .clip(shape)
            .background(surface)
            .clickable {
                haptics.play(Haptic.Expand)
                onOpen()
            }
    ) {
        /*
         * One row, about 68dp tall — Cider's proportions.
         *
         * It had grown to ~93dp because the wave sat on its own line *under*
         * the row. Cider tucks its progress into the text column beneath the
         * show name, where it costs almost no height; so does this now. The
         * +30 button is gone: play/pause is the one control a mini player
         * needs, and a second glyph beside it read as clutter.
         */
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The cover the full player grows out of — same key as its
            // artwork, so opening it is this square flying into place.
            Artwork(
                url = art,
                sizeDp = 48.dp,
                corner = 12.dp,
                modifier = Modifier.sharedArtwork(NowPlayingArtKey, LocalPlayerScope.current)
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = feed?.title.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.70f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(5.dp))
                // The same wave as the full player, small enough to live under
                // the text. Moving while audio plays, flat when it doesn't.
                WavySlider(
                    progress = progress,
                    playing = isPlaying,
                    color = button,
                    enabled = false,
                    height = 8.dp,
                    amplitude = 1.8.dp,
                    wavelength = 18.dp,
                    strokeWidth = 2.6.dp,
                    showThumb = false
                )
            }
            Spacer(Modifier.width(12.dp))
            MiniPlayButton(
                isPlaying = isPlaying,
                fill = button,
                tint = Color(0xFF16141A),
                onClick = {
                    haptics.play(if (isPlaying) Haptic.Pause else Haptic.Resume)
                    onPlayPause()
                }
            )
        }
    }
}

/**
 * The mini player's play button, on a tonal tile that changes shape with state
 * — a circle at rest, a rounded square while playing — the same morph as the
 * full player's, so the two read as one control at two sizes.
 */
@Composable
private fun MiniPlayButton(
    isPlaying: Boolean,
    fill: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val corner by animateDpAsState(
        targetValue = if (isPlaying) 15.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 380f),
        label = "miniPlayShape"
    )
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(corner))
            .background(fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
    }
}

