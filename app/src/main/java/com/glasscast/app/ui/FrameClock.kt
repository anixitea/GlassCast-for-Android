package com.glasscast.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay

/**
 * A clock for decorative motion: seconds of *running* time, advanced at most
 * [fps] times a second, and frozen — not reset — while [running] is false.
 *
 * Why it exists: anything animating forces the whole screen to redraw, and a
 * redraw pays for everything on screen, the frosted bars included. On a
 * 120–144Hz panel a frame has about 7ms, so an always-on flourish at full rate
 * — the mini player's wave, the bubble's turn, the equaliser — keeps the phone
 * redrawing 144 times a second while you're only reading a list. These move
 * slowly; at 30 updates a second they look the same and cost a fifth as much.
 * The full player's wave, which is the point of that screen, stays at full
 * rate (fps = 0).
 *
 * Read `.value` inside a draw lambda where possible, so each tick redraws
 * without recomposing.
 */
@Composable
fun rememberFrameClock(running: Boolean, fps: Int = 30): State<Float> {
    val seconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running, fps) {
        if (!running) return@LaunchedEffect
        val gapMs = if (fps > 0) 1_000L / fps else 0L
        var last = withFrameNanos { it }
        while (true) {
            if (gapMs > 0) delay(gapMs)
            val now = withFrameNanos { it }
            seconds.floatValue += (now - last) / 1_000_000_000f
            last = now
        }
    }
    return seconds
}
