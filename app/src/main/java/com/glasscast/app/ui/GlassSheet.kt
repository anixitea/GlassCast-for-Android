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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = containerColor,
        contentColor = contentColor,
        dragHandle = dragHandle
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = tallest),
            content = content
        )
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
