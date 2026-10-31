package com.glasscast.app.ui.liquid

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Apple's switch, in frosted glass: a 51 × 31 track (the accent when on, a
 * faint fill when off) with light gathered along its top, and a knob of
 * glass that slides across. Held, the knob swells into a wider drop of
 * clearer glass, as Apple's does, and settles back when let go. GlassCast's
 * accent rather than Apple's green.
 */
@Composable
fun LiquidSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    tokens: LiquidTokens,
    modifier: Modifier = Modifier,
    accent: Color = tokens.accent,
    enabled: Boolean = true
) {
    val travel by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 600f),
        label = "liquidSwitch"
    )
    val track by animateColorAsState(
        targetValue = if (checked) accent else if (tokens.dark) Color(0xFF39393D) else Color(0xFFE9E9EA),
        animationSpec = tween(200),
        label = "liquidSwitchTrack"
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val held by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "liquidSwitchHeld"
    )
    Box(
        modifier
            .size(width = 51.dp, height = 31.dp)
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        interactionSource = interaction,
                        indication = null,
                        onValueChange = onCheckedChange
                    )
                } else {
                    Modifier
                }
            )
            .drawBehind {
                val r = size.height / 2f
                drawRoundRect(track, cornerRadius = CornerRadius(r))
                // Light along the top of the track, and its rim.
                drawRoundRect(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        0f to Color.White.copy(alpha = if (tokens.dark) 0.10f else 0.22f),
                        0.5f to Color.White.copy(alpha = 0f)
                    ),
                    cornerRadius = CornerRadius(r)
                )
                drawRoundRect(
                    Color.Black.copy(alpha = if (tokens.dark) 0.20f else 0.05f),
                    cornerRadius = CornerRadius(r),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx())
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .offset {
                    // Swollen while held, toward the side it's going.
                    val grow = (8.dp.toPx() * held)
                    IntOffset((2.dp.toPx() + travel * (20.dp.toPx() - grow)).roundToInt(), 0)
                }
                .size(width = 27.dp + 8.dp * held, height = 27.dp)
                .drawBehind {
                    val r = size.height / 2f
                    // A soft drawn shadow, then the knob: white, turning to
                    // clearer glass while it's held.
                    for (i in 1..3) {
                        val grow = i * 1.2.dp.toPx()
                        drawRoundRect(
                            Color.Black.copy(alpha = 0.06f / i),
                            topLeft = Offset(-grow / 2f, -grow / 2f + 1.5.dp.toPx()),
                            size = Size(size.width + grow, size.height + grow),
                            cornerRadius = CornerRadius(r + grow / 2f)
                        )
                    }
                    drawRoundRect(Color.White.copy(alpha = 1f - 0.45f * held), cornerRadius = CornerRadius(r))
                    drawRoundRect(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            0f to Color.White.copy(alpha = 0.9f * held),
                            1f to Color.White.copy(alpha = 0f),
                            center = Offset(size.width * 0.35f, size.height * 0.3f),
                            radius = size.height * 0.6f
                        ),
                        cornerRadius = CornerRadius(r)
                    )
                    drawRoundRect(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            0f to Color.White,
                            0.5f to Color.Black.copy(alpha = 0.06f),
                            1f to Color.White.copy(alpha = 0.7f),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height)
                        ),
                        cornerRadius = CornerRadius(r),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(0.75.dp.toPx())
                    )
                }
        )
    }
}

/** The round check under a choice (Apple's Display & Brightness): filled when chosen. */
@Composable
fun LiquidRadio(selected: Boolean, tokens: LiquidTokens, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(24.dp)
            .then(
                if (selected) Modifier.background(tokens.accent, CircleShape)
                else Modifier.border(1.5.dp, tokens.tertiary, CircleShape)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (tokens.accent.luminance() > 0.62f) Color(0xFF101014) else Color.White,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/** A setting's badge as Apple draws it: a small rounded square of color, a white glyph. */
fun Modifier.liquidBadge(color: Color, corner: Float = 0.23f): Modifier = this.drawBehind {
    drawRoundRect(color, cornerRadius = CornerRadius(size.minDimension * corner))
}

/** The faint capsule behind an inactive control. */
fun Modifier.liquidFill(tokens: LiquidTokens, corner: Float = 0.5f): Modifier = this.drawBehind {
    drawRoundRect(tokens.fill, size = Size(size.width, size.height), cornerRadius = CornerRadius(size.minDimension * corner))
}

/**
 * The pane Liquid Glass uses for buttons inside a page (a page's own bar
 * buttons can't refract it; see LiquidLens): Apple's toolbar glass, a light
 * pane lifted off the page by a soft shadow, with the rim's light. On a white
 * page the first build's white pane vanished; this one is a breath of gray
 * and stands up off it.
 */
object Panes {
    fun Modifier.pane(t: LiquidTokens): Modifier = this
        .drawBehind {
            val radius = size.minDimension / 2f
            for (i in 1..3) {
                val grow = i * 1.6.dp.toPx()
                drawRoundRect(
                    Color.Black.copy(alpha = (if (t.dark) 0.22f else 0.045f) / i),
                    topLeft = Offset(-grow / 2f, -grow / 2f + i * 0.8.dp.toPx()),
                    size = Size(size.width + grow, size.height + grow),
                    cornerRadius = CornerRadius(radius + grow / 2f)
                )
            }
        }
        .liquidPane(
            color = if (t.dark) Color(0xFF2C2C2E).copy(alpha = 0.90f) else Color(0xFFF7F7F9).copy(alpha = 0.94f),
            dark = t.dark
        )
}

/**
 * An episode's length as Apple's lists show it: a play symbol and the time,
 * in the accent, on a faint capsule of the accent.
 */
@androidx.compose.runtime.Composable
fun LiquidLengthCapsule(label: String, t: LiquidTokens, modifier: Modifier = Modifier) {
    val accent = legibleAccent(t.accent, t.dark)
    androidx.compose.foundation.layout.Row(
        modifier
            // A little capsule of the accent's glass.
            .frostedGlass(accent.copy(alpha = if (t.dark) 0.24f else 0.13f), t.dark)
            .padding(start = 8.dp, end = 11.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            androidx.compose.material.icons.Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(15.dp)
        )
        androidx.compose.foundation.layout.Spacer(Modifier.size(3.dp))
        androidx.compose.material3.Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            ),
            color = accent,
            maxLines = 1
        )
    }
}


/**
 * A round button of real glass floating over a page, refracting the page's
 * own backdrop (rememberPageBackdrop): Apple's toolbar buttons. It gives a
 * little under the finger.
 */
@androidx.compose.runtime.Composable
fun LiquidGlassButton(
    backdrop: LiquidBackdrop?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    dark: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "glassButton"
    )
    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .liquidGlass(backdrop, LiquidMaterial.button(dark), dark = dark)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = if (dark) Color.White else Color.Black, modifier = Modifier.size(22.dp))
    }
}

/**
 * Frosted glass drawn on a page, for controls in it (a show's buttons, the
 * pills): [fill] as the glass's body, a soft shadow lifting it off the page,
 * light gathered along its top, and the rim's two lines of light. A capsule
 * at [corner] or rounder.
 */
object FrostedGlass {
    fun Modifier.frosted(fill: Color, dark: Boolean, corner: androidx.compose.ui.unit.Dp = 999.dp): Modifier = frostedGlass(fill, dark, corner)
}

fun Modifier.frostedGlass(
    fill: Color,
    dark: Boolean,
    corner: androidx.compose.ui.unit.Dp = 999.dp,
    /** False for panes that float over content (menus): they keep their body in either finish. */
    finish: Boolean = true
): Modifier = this.drawBehind {
    val radius = minOf(corner.toPx(), size.minDimension / 2f)
    // The finish (PaneFinish): a little clearer in Liquid, frosted otherwise.
    val liquidFinish = finish && GlassFinish.liquid
    val light = if (finish) PaneFinish.light else 1f
    for (i in 1..3) {
        val grow = i * 1.6.dp.toPx()
        drawRoundRect(
            Color.Black.copy(alpha = (if (dark) 0.20f else 0.05f) / i * (if (liquidFinish) 0.7f else 1f)),
            topLeft = Offset(-grow / 2f, -grow / 2f + i * 0.9.dp.toPx()),
            size = Size(size.width + grow, size.height + grow),
            cornerRadius = CornerRadius(radius + grow / 2f)
        )
    }
    drawRoundRect(if (finish) PaneFinish.fill(fill) else fill, cornerRadius = CornerRadius(radius))
    if (liquidFinish) {
        drawPaneSheen(radius, dark)
    } else {
        drawRoundRect(
            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.White.copy(alpha = if (dark) 0.14f else 0.30f),
                0.55f to Color.White.copy(alpha = 0f)
            ),
            cornerRadius = CornerRadius(radius)
        )
    }
    drawRoundRect(
        brush = androidx.compose.ui.graphics.Brush.linearGradient(
            0f to Color.White.copy(alpha = ((if (dark) 0.34f else 0.9f) * light).coerceAtMost(1f)),
            0.45f to Color.White.copy(alpha = 0f),
            0.55f to Color.White.copy(alpha = 0f),
            1f to Color.White.copy(alpha = ((if (dark) 0.18f else 0.55f) * light).coerceAtMost(1f)),
            start = Offset.Zero,
            end = Offset(size.width, size.height)
        ),
        cornerRadius = CornerRadius(radius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(if (finish) PaneFinish.rim.toPx() else 1.dp.toPx())
    )
}
