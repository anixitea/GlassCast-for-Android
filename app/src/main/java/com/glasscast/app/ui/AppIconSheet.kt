package com.glasscast.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glasscast.app.data.AppIcon
import com.glasscast.app.data.AppIcons
import com.glasscast.app.data.IconFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An icon's name, in the phone's language. */
fun appIconName(icon: AppIcon): String = when (icon) {
    AppIcon.ORIGINAL -> tr("Original")
    AppIcon.MIDNIGHT -> tr("Midnight")
    AppIcon.PAPER -> tr("Paper")
    AppIcon.SEA_GLASS -> tr("Sea Glass")
    AppIcon.PEBBLE -> tr("Pebble")
    AppIcon.SHORE -> tr("Shore")
    AppIcon.TIDE -> tr("Tide")
    AppIcon.LAGOON -> tr("Lagoon")
    AppIcon.ROSE -> tr("Rose")
    AppIcon.AMBER -> tr("Amber")
    AppIcon.MINT -> tr("Mint")
    AppIcon.LIQUID_GLASS -> tr("Glass")
    AppIcon.PRISM -> tr("Prism")
    AppIcon.CLEAR -> tr("Clear Glass")
    AppIcon.DARK -> tr("Dark")
}

private fun familyName(family: IconFamily): String = when (family) {
    IconFamily.ORIGINAL -> tr("Original")
    IconFamily.SEA_GLASS -> tr("Sea Glass")
    IconFamily.LIQUID_GLASS -> tr("Glass")
}

/**
 * The icon as a launcher shows it: the middle 72 of its 108dp, in [shape]
 * (a circle, or Apple's rounded square in Liquid Glass; the launcher's own
 * mask decides on the home screen).
 */
@Composable
fun AppIconImage(icon: AppIcon, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(shape)
    ) {
        Image(
            painter = painterResource(icon.preview),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 1.5f
                    scaleY = 1.5f
                }
        )
    }
}

/** The shape icons are previewed in, for the current look. */
@Composable
fun appIconShape(): Shape =
    if (com.glasscast.app.ui.liquid.LocalLiquid.current != null) RoundedCornerShape(23) else CircleShape

/**
 * Settings > Appearance > App icon: the row, with the icon that's on, and
 * the picker it opens. Which icon is on is read from the package manager
 * (data/AppIcons), off the main thread.
 */
@Composable
internal fun AppIconRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<AppIcon?>(null) }
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        current = withContext(Dispatchers.IO) { AppIcons.current(context.applicationContext) }
    }
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    val shown = current ?: AppIcon.ORIGINAL
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(shown, size = if (liquid != null) 30.dp else 32.dp, shape = appIconShape())
        Spacer(Modifier.width(14.dp))
        Text(
            text = tr("App icon"),
            style = if (liquid != null) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)
            else MaterialTheme.typography.titleSmall,
            fontWeight = if (liquid != null) FontWeight.Normal else FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = appIconName(shown),
            style = if (liquid != null) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)
            else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
        Spacer(Modifier.width(4.dp))
        Chevron()
    }
    if (open) {
        AppIconSheet(
            current = shown,
            onPick = { icon ->
                current = icon
                val app = context.applicationContext
                scope.launch(Dispatchers.IO) {
                    if (AppIcons.set(app, icon)) com.glasscast.app.Shortcuts.publish(app)
                }
            },
            onDismiss = { open = false }
        )
    }
}

/**
 * The icons, in the three families. A grid of icons in the Original look and
 * Sea Glass, the chosen one ringed in the accent; Apple's list in Liquid
 * Glass, a check beside the chosen one.
 */
@Composable
fun AppIconSheet(current: AppIcon, onPick: (AppIcon) -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    val sea = com.glasscast.app.ui.seaglass.LocalSea.current
    val accent = MaterialTheme.colorScheme.primary
    fun pick(icon: AppIcon) {
        if (icon != current) haptics.play(Haptic.Select)
        onPick(icon)
    }
    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Text(
                text = tr("App icon"),
                style = if (liquid != null) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .then(if (liquid != null) Modifier.align(Alignment.CenterHorizontally) else Modifier.padding(start = 4.dp))
                    .padding(top = 4.dp, bottom = 8.dp)
            )
            IconFamily.entries.forEach { family ->
                val icons = AppIcon.entries.filter { it.family == family }
                Text(
                    text = familyName(family).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = liquid?.secondary ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = if (liquid != null) 16.dp else 8.dp, top = 18.dp, bottom = 8.dp)
                )
                if (liquid != null) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(26.dp))
                            .background(liquid.card)
                    ) {
                        icons.forEachIndexed { index, icon ->
                            val on = icon == current
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { pick(icon) }
                                    .semantics {
                                        role = Role.RadioButton
                                        selected = on
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AppIconImage(icon, 52.dp, RoundedCornerShape(23))
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = appIconName(icon),
                                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp),
                                    color = liquid.ink,
                                    modifier = Modifier.weight(1f)
                                )
                                if (on) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = liquid.accent, modifier = Modifier.size(22.dp))
                                }
                            }
                            if (index < icons.lastIndex) {
                                Box(
                                    Modifier
                                        .padding(start = 80.dp)
                                        .fillMaxWidth()
                                        .height(0.5.dp)
                                        .background(liquid.separator)
                                )
                            }
                        }
                    }
                } else {
                    // Four to a row, in a card like Settings' groups.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .then(if (sea != null) Modifier.border(0.75.dp, sea.hairline, RoundedCornerShape(24.dp)) else Modifier)
                            .padding(vertical = 10.dp, horizontal = 6.dp)
                    ) {
                        icons.chunked(4).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                row.forEach { icon ->
                                    val on = icon == current
                                    Column(
                                        Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(16.dp))
                                            .clickable { pick(icon) }
                                            .semantics {
                                                role = Role.RadioButton
                                                selected = on
                                            }
                                            .padding(vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (on) {
                                                Box(
                                                    Modifier
                                                        .size(68.dp)
                                                        .border(2.5.dp, accent, CircleShape)
                                                )
                                            }
                                            AppIconImage(icon, 56.dp, CircleShape)
                                            if (on) {
                                                Box(
                                                    Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .background(accent),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.onPrimary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            text = appIconName(icon),
                                            style = if (sea != null && on) com.glasscast.app.ui.seaglass.rowTitleStyle().copy(fontSize = 14.sp)
                                            else MaterialTheme.typography.labelLarge,
                                            fontWeight = if (sea == null && on) FontWeight.SemiBold else null,
                                            color = if (on) accent else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
