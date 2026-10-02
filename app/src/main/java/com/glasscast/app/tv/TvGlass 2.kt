package com.glasscast.app.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Panels on TV are solid. The blur is gone, and that is the single biggest
 * performance decision in this build.
 *
 * Haze works by copying the content behind a panel into a layer and blurring
 * it. On a phone that layer is a few hundred thousand pixels. On a 4K
 * television it is eight million, recopied every frame the content moves — and
 * `hazeSource` was wrapped around the *entire* scrolling page, so every scroll
 * of every list paid for a full-screen readback whether a panel was visible or
 * not.
 *
 * What the glass bought on a phone was depth against content sliding
 * underneath. On a TV the content behind these panels barely moves, so a solid
 * surface with a hairline edge reads nearly the same and costs nothing.
 */
@Composable
fun Modifier.tvPanel(
    shape: Shape,
    container: Color,
    border: Boolean = true
): Modifier = this
    .clip(shape)
    .background(container)
    .then(
        if (border) Modifier.border(1.dp, Color.White.copy(alpha = 0.10f), shape) else Modifier
    )
