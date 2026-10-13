package com.glasscast.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** The hue track: the spectrum, red to red. */
private val HueStops = List(7) { i -> Color(android.graphics.Color.HSVToColor(floatArrayOf(i * 60f, 1f, 1f))) }

private fun hsv(h: Float, s: Float, v: Float) =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/**
 * Custom color: three sliders (hue, saturation, brightness) over a preview,
 * no wheel. Each track is drawn in the colors it moves through, so it shows
 * where it goes before you drag. The app takes the color only on Use, and
 * the theme clamps it for legibility on light and dark pages alike, so a
 * pale pick still reads.
 */
@Composable
internal fun ColorPickerSheet(
    initial: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val start = remember(initial) { FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = hsv(hue, sat, value)
    val haptics = rememberHaptics()

    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(color)
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(
                        text = tr("Custom color"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "#%06X".format(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)) and 0xFFFFFF),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            GradientSlider(
                fraction = hue / 360f,
                brush = Brush.horizontalGradient(HueStops),
                thumb = hsv(hue, 1f, 1f),
                onChange = { hue = it * 360f }
            )
            Spacer(Modifier.height(18.dp))
            GradientSlider(
                fraction = sat,
                brush = Brush.horizontalGradient(listOf(hsv(hue, 0f, value), hsv(hue, 1f, value))),
                thumb = color,
                onChange = { sat = it }
            )
            Spacer(Modifier.height(18.dp))
            GradientSlider(
                fraction = value,
                brush = Brush.horizontalGradient(listOf(Color.Black, hsv(hue, sat, 1f))),
                thumb = color,
                onChange = { value = it }
            )

            Spacer(Modifier.height(28.dp))
            SheetPillButton(
                label = tr("Use this color"),
                icon = Icons.Filled.Check,
                fill = color,
                ink = if (color.luminance() > 0.6f) ChromeInk else Color.White,
                onClick = {
                    haptics.play(Haptic.ToggleOn)
                    onPick(android.graphics.Color.HSVToColor(floatArrayOf(hue, sat, value)))
                    onDismiss()
                }
            )
            Spacer(Modifier.height(10.dp))
            SheetPillButton(
                label = tr("Cancel"),
                icon = null,
                fill = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ink = MaterialTheme.colorScheme.onSurface,
                onClick = onDismiss
            )
        }
    }
}

/**
 * A 30dp capsule track painted with [brush], and a ringed thumb filled with
 * [thumb]. Tap to jump, drag to move; [fraction] is 0 to 1.
 */
@Composable
private fun GradientSlider(
    fraction: Float,
    brush: Brush,
    thumb: Color,
    onChange: (Float) -> Unit
) {
    // The thumb's center runs from one rounded end to the other, so a touch
    // is measured along that same span.
    fun along(x: Float, w: Int, h: Int): Float {
        val r = h / 2f
        return ((x - r) / (w - 2 * r).coerceAtLeast(1f)).coerceIn(0f, 1f)
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectTapGestures { onChange(along(it.x, size.width, size.height)) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange(along(change.position.x, size.width, size.height))
                }
            }
    ) {
        val r = size.height / 2f
        drawRoundRect(brush = brush, cornerRadius = CornerRadius(r))
        // The thumb travels inside the track's rounded ends.
        val x = r + (size.width - 2 * r) * fraction.coerceIn(0f, 1f)
        val center = Offset(x, r)
        drawCircle(Color.Black.copy(alpha = 0.18f), radius = r - 1.dp.toPx(), center = center + Offset(0f, 1.5.dp.toPx()))
        drawCircle(Color.White, radius = r - 2.dp.toPx(), center = center)
        drawCircle(thumb, radius = r - 6.dp.toPx(), center = center)
        drawCircle(Color.Black.copy(alpha = 0.08f), radius = r - 2.dp.toPx(), center = center, style = Stroke(1.dp.toPx()))
    }
}
