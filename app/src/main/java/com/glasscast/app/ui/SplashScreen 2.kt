package com.glasscast.app.ui

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.glasscast.app.R
import com.glasscast.app.ui.theme.LocalBrandAccent
import com.glasscast.app.ui.theme.LocalIsDark
import kotlinx.coroutines.delay

/**
 * The mark, tinted accent, on a plain field that follows the *in-app* theme
 * setting rather than the system's — otherwise someone running the app forced
 * to dark gets a white flash on every cold start.
 *
 * About a second, then a cross-fade. A launch screen you notice is too long.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val dark = LocalIsDark.current
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

    LaunchedEffect(Unit) {
        visible = true
        delay(1_000)
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
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
