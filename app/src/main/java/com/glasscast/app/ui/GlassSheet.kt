package com.glasscast.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.ui.theme.ErrorRed

/**
 * Every bottom sheet in the app goes through here.
 *
 * The one thing it adds: a sheet is never taller than the screen below the
 * status bar, less some air. A tall ModalBottomSheet otherwise slides all the
 * way to the top, and its drag handle lands under the camera cutout, which
 * looks broken (GlassBook 0.6). With the cap in one place, no sheet can do it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    dragHandle: (@Composable () -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable ColumnScope.() -> Unit
) {
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tallest = (LocalConfiguration.current.screenHeightDp.dp - statusTop - 96.dp).coerceAtLeast(240.dp)
    // Liquid Glass: Apple's sheet. Larger corners, a lifted light (or
    // charcoal) pane with the glass's rim of light along its top edge, and
    // Apple's slim grabber.
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    // Frosted Glass: the sheet is frosted glass over the app. Its window
    // blurs what's behind it (Android 12+, where the phone allows blurring
    // across windows), and the sheet itself is translucent over that blur.
    // Without the blur it stays solid: translucent over a sharp page reads
    // as a mistake, not glass.
    val context = androidx.compose.ui.platform.LocalContext.current
    val frosted = liquid != null && androidx.compose.runtime.remember(context) { crossWindowBlur(context) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = if (liquid != null) RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp) else BottomSheetDefaults.ExpandedShape,
        containerColor = if (liquid != null && containerColor == MaterialTheme.colorScheme.surfaceContainer) {
            // The liquid finish: clearer glass over a lighter blur.
            val clear = com.glasscast.app.ui.liquid.GlassFinish.liquid
            if (liquid.dark) Color(0xFF1C1C1E).copy(alpha = if (frosted) (if (clear) 0.66f else 0.78f) else 1f)
            else Color.White.copy(alpha = if (frosted) (if (clear) 0.70f else 0.80f) else 1f)
        } else {
            containerColor
        },
        contentColor = contentColor,
        // The rim of light rides on the grabber's row, the top of the sheet
        // itself. It used to be drawn by a modifier on the sheet, which sits
        // outside the sheet's slide: the rim stayed at the top of the screen
        // as a white line while the sheet came up under it (2.0).
        dragHandle = if (liquid != null) {
            {
                androidx.compose.foundation.layout.Box(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val r = 34.dp.toPx()
                            drawRoundRect(
                                brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                    0f to Color.White.copy(
                                        alpha = ((if (liquid.dark) 0.22f else 0.9f) * com.glasscast.app.ui.liquid.PaneFinish.light).coerceAtMost(1f)
                                    ),
                                    1f to Color.White.copy(alpha = 0f),
                                    startY = 0f,
                                    endY = 30.dp.toPx()
                                ),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height + r),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx())
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (dragHandle != null) {
                        androidx.compose.foundation.layout.Box(
                            Modifier
                                .padding(top = 7.dp, bottom = 10.dp)
                                .size(width = 36.dp, height = 5.dp)
                                .clip(RoundedCornerShape(50))
                                .background(liquid.tertiary)
                        )
                    }
                }
            }
        } else {
            dragHandle
        }
    ) {
        if (frosted) BlurBehindWindow(radius = if (com.glasscast.app.ui.liquid.GlassFinish.liquid) 18.dp else 28.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = tallest),
            content = content
        )
    }
}

/** Whether this phone blurs across windows right now (Android 12+; battery saver and some phones turn it off). */
private fun crossWindowBlur(context: android.content.Context): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
        runCatching {
            context.getSystemService(android.view.WindowManager::class.java)?.isCrossWindowBlurEnabled == true
        }.getOrDefault(false)

/**
 * Blurs everything behind the window this is composed in: the sheet's own
 * dialog window, found by walking up from the composition's view.
 */
@Composable
private fun BlurBehindWindow(radius: androidx.compose.ui.unit.Dp) {
    val view = androidx.compose.ui.platform.LocalView.current
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { radius.roundToPx() }
    androidx.compose.runtime.DisposableEffect(view, px) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            var v: android.view.ViewParent? = view as? android.view.ViewParent
            var window: android.view.Window? = null
            while (v != null && window == null) {
                window = (v as? androidx.compose.ui.window.DialogWindowProvider)?.window
                v = v.parent
            }
            window?.let { w ->
                runCatching {
                    w.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    val attributes = w.attributes
                    attributes.blurBehindRadius = px
                    w.attributes = attributes
                }
            }
        }
        onDispose { }
    }
}

/**
 * "Are you sure?" as a sheet, in the shape of the other menus, instead of a
 * stock dialog: the thing itself at the top (its cover, its name, whose it
 * is), so it's clear which one, then the action in red and Cancel.
 */
@Composable
fun ConfirmSheet(
    artUrl: String,
    title: String,
    subtitle: String?,
    meta: String?,
    action: String,
    icon: ImageVector,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    /** Red for something that removes; the accent for one that adds (restoring a backup). */
    destructive: Boolean = true
) {
    val haptics = rememberHaptics()
    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artUrl.isNotBlank()) {
                    Artwork(url = artUrl, sizeDp = 76.dp, corner = 14.dp)
                    Spacer(Modifier.width(16.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!meta.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            val ink = if (destructive) ErrorRed else MaterialTheme.colorScheme.primary
            SheetPillButton(
                label = action,
                icon = icon,
                fill = ink.copy(alpha = 0.16f),
                ink = ink,
                onClick = {
                    haptics.play(if (destructive) Haptic.ToggleOff else Haptic.ToggleOn)
                    onConfirm()
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

/** A full-width 54dp pill, squashing a little when pressed. */
@Composable
fun SheetPillButton(
    label: String,
    icon: ImageVector?,
    fill: Color,
    ink: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    // Sea Glass: a long capsule of glass, colored for an action (red to
    // remove, the accent to add), clear for Cancel.
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    if (tokens != null) {
        val pigment = remember(fill, tokens) { com.glasscast.app.ui.seaglass.pigmentFor(fill, tokens) }
        com.glasscast.app.ui.seaglass.GlassButton(
            onClick = onClick,
            pigment = pigment,
            dark = tokens.dark,
            recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Quiet,
            seed = label.hashCode(),
            modifier = modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = pigment.ink, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = pigment.ink
                )
            }
        }
        return
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(if (pressed) 0.97f else 1f, label = "pillSquash")
    Row(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = squash
                scaleY = squash
            }
            .clip(RoundedCornerShape(percent = 50))
            .background(fill)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = ink
        )
    }
}
