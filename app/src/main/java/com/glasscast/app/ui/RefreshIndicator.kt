package com.glasscast.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The pull-to-refresh indicator: the bubble's cookie, again.
 *
 * It rides down with the pull, grows from small to full size, and turns as it
 * comes — so the distance you've pulled reads as the shape winding up. An arc
 * inside fills toward the threshold; past it, the arc is complete and the tick
 * has fired. While the refresh runs the whole cookie spins and the arc chases
 * itself, then it rides back up as the list settles.
 *
 * Reusing the shape is deliberate: the app now has one expressive form — the
 * bubble, this, the scallop around the progress ring — rather than a new
 * flourish per feature.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CookieRefreshIndicator(
    state: PullToRefreshState,
    refreshing: Boolean,
    modifier: Modifier = Modifier
) {
    val fraction = state.distanceFraction
    if (fraction <= 0f && !refreshing) return

    val spin by rememberInfiniteTransition(label = "refreshSpin").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "refreshSpinValue"
    )
    val surface = MaterialTheme.colorScheme.surfaceContainerHighest
    val accent = MaterialTheme.colorScheme.primary

    Box(
        modifier
            .graphicsLayer {
                val f = fraction.coerceIn(0f, 1.4f)
                translationY = 72.dp.toPx() * f - 28.dp.toPx()
                val grow = if (refreshing) 1f else 0.45f + 0.55f * f.coerceAtMost(1f)
                scaleX = grow
                scaleY = grow
                alpha = if (refreshing) 1f else f.coerceIn(0f, 1f)
                rotationZ = if (refreshing) spin else f * 220f
            }
            .size(48.dp)
    ) {
        /*
         * The shadow is cast by a plain circle tucked just inside the
         * scallops' dips, not by the cookie itself. A shadow from a concave
         * outline has to be cut into triangles by the system's renderer, and
         * this one turns and grows on every frame of the pull and spins
         * through the refresh, so that work ran every frame. On an Android 17
         * pad it took the render thread seconds a frame, and the app stopped
         * answering mid-pull (two reports, both "phone library", both waiting
         * on a drag). A circle's shadow is drawn straight from its radius.
         * The cookie covers the circle, so it looks the same.
         */
        Box(
            Modifier
                .matchParentSize()
                .padding(CookieShadowInset)
                .shadow(10.dp, CircleShape)
        )
        Canvas(Modifier.size(48.dp)) {
            drawPath(cookiePath(size.width, size.height), color = surface)
            val stroke = 3.dp.toPx()
            val inset = 14.dp.toPx()
            val sweep = if (refreshing) 270f else 330f * fraction.coerceIn(0f, 1f)
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * How far in from the 48dp indicator's edge the scallops dip: the cookie's
 * crests touch the edge, its dips sit at (1 - depth) / (1 + depth) of the
 * radius (cookiePath), about 2.7dp in.
 */
private val CookieShadowInset: Dp = 24.dp * (1f - (1f - CookieDepth) / (1f + CookieDepth)) + 0.5.dp
