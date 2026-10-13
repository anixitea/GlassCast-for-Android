package com.glasscast.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.glasscast.app.R
import com.glasscast.app.ui.theme.LocalBrandAccent
import kotlinx.coroutines.delay

/**
 * The mark, tinted accent, on a plain field that follows the *in-app* theme
 * setting rather than the system's — otherwise someone running the app forced
 * to dark gets a white flash on every cold start.
 *
 * Drawn over the app, which is already composed underneath (GlassCastRoot),
 * so the first real screen's work happens behind the mark instead of after
 * it. About a second, then the whole thing fades away. It takes every touch
 * until it's gone. [onOpening] is when it starts to fade: the app is usable
 * from then on.
 */
@Composable
fun SplashScreen(onOpening: () -> Unit, onFinished: () -> Unit) {
    val accent = LocalBrandAccent.current

    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(320),
        label = "splashAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.94f,
        animationSpec = tween(560),
        label = "splashScale"
    )
    val cover = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        visible = true
        delay(1_000)
        onOpening()
        cover.animateTo(0f, tween(420))
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { this.alpha = cover.value }
            .background(MaterialTheme.colorScheme.background)
            // Nothing underneath takes a touch until it's gone.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_mark),
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .size(76.dp)
                .alpha(alpha)
                .scale(scale)
        )
    }
}
