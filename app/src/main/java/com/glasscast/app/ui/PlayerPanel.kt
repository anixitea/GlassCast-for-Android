package com.glasscast.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.player.SleepTimer
import kotlin.math.roundToInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

enum class PanelTab { UP_NEXT, INFO, SPEED, TIMER }

/** Height of the panel's header — also the collapsed strip, since they're one surface. */
val PanelHeaderHeight: Dp = 80.dp

private val Ink = Color.White
private val InkDim = Color.White.copy(alpha = 0.62f)

/**
 * The pull-up panel: Up Next, episode info, speed and the sleep timer, inside
 * the player rather than in sheets laid over it.
 *
 * One surface in two states. Collapsed, its header is the strip along the
 * player's foot; pulled up, that same header rides to the top and the body
 * comes into view below it.
 *
 * **Opaque, in the cover's hue.** The first version was a translucent dark
 * layer over the artwork, and the artwork showed straight through it — faces
 * and cover type behind the queue, which is what made it look broken. It's a
 * solid surface now, the cover's own hue at a fixed dark lightness, so it
 * belongs to the episode and still reads cleanly. And it renders under a full
 * dark color scheme rather than a patched copy of the app's, so nothing in it
 * can pick up a light-theme color — which is why light mode looked worse.
 */
@Composable
fun PlayerPanel(
    expand: Float,
    tab: PanelTab,
    art: String,
    upNextCount: Int,
    speed: Float,
    timerArmed: Boolean,
    surface: Color,
    accent: Color,
    onTab: (PanelTab) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable (PanelTab) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val open = expand > 0.5f

    Column(
        modifier
            .clip(shape)
            .background(surface)
            // The panel owns every touch that lands on it.
            .pointerInput(Unit) {}
    ) {
        // ---- header: the strip when collapsed, the title bar when open ----
        Column(
            Modifier
                .fillMaxWidth()
                .height(PanelHeaderHeight)
                .pointerInput(Unit) {
                    var velocity = 0f
                    detectVerticalDragGestures(
                        onDragStart = { velocity = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            velocity = amount
                            onDrag(amount)
                        },
                        onDragEnd = { onDragEnd(velocity * 60f) },
                        onDragCancel = { onDragEnd(0f) }
                    )
                }
                .padding(top = 8.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.34f))
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The cover takes up no room once the panel is open: its width
                // shrinks with it, so the title slides left into its place
                // instead of being left indented beside a gap.
                val k = (1f - expand * 1.4f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .width(52.dp * k)
                        .graphicsLayer {
                            alpha = k
                            scaleX = 0.6f + 0.4f * k
                            scaleY = 0.6f + 0.4f * k
                        }
                ) {
                    Artwork(url = art, sizeDp = 40.dp, corner = 10.dp)
                }

                AnimatedContent(
                    targetState = if (open) tab else null,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    modifier = Modifier.weight(1f),
                    label = "panelTitle"
                ) { shown ->
                    Column {
                        Text(
                            text = when (shown) {
                                PanelTab.INFO -> tr("Episode info")
                                PanelTab.SPEED -> tr("Speed & sound")
                                PanelTab.TIMER -> tr("Sleep timer")
                                else -> tr("Playing Next")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 1
                        )
                        Text(
                            text = when {
                                shown == PanelTab.INFO -> tr("Notes, chapters and transcript")
                                shown == PanelTab.SPEED -> formatSpeed(speed)
                                shown == PanelTab.TIMER -> if (timerArmed) tr("Running") else tr("Off")
                                upNextCount == 0 -> tr("Nothing queued")
                                upNextCount == 1 -> tr("1 episode")
                                else -> tr("{0} episodes", upNextCount)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Cider's button pill, with speed and timer joining Up Next and
                // info. Each opens the panel on its own tab; the lit one is the
                // tab showing, and only while the panel is actually open.
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink.copy(alpha = 0.10f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    PanelTabButton(open && tab == PanelTab.UP_NEXT, { onTab(PanelTab.UP_NEXT) }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, tr("Up Next"), Modifier.size(21.dp), tint = Ink)
                    }
                    PanelTabButton(open && tab == PanelTab.INFO, { onTab(PanelTab.INFO) }) {
                        Icon(Icons.Outlined.Info, tr("Episode info"), Modifier.size(21.dp), tint = Ink)
                    }
                    PanelTabButton(open && tab == PanelTab.SPEED, { onTab(PanelTab.SPEED) }) {
                        Text(formatSpeed(speed), style = MaterialTheme.typography.titleSmall, color = Ink)
                    }
                    // Lit while a timer runs, not only while its tab is open —
                    // with the accent moon, the timer's only status in the player.
                    PanelTabButton((open && tab == PanelTab.TIMER) || timerArmed, { onTab(PanelTab.TIMER) }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            tr("Sleep timer"),
                            Modifier.size(20.dp),
                            tint = if (timerArmed) accent else Ink
                        )
                    }
                }
            }
        }

        // ---- body ----
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = ((expand - 0.2f) / 0.5f).coerceIn(0f, 1f) }
        ) {
            PanelColors(accent) { body(tab) }
        }
    }
}

@Composable
private fun PanelTabButton(active: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(width = 46.dp, height = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Ink.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * A complete dark scheme, not a patched copy of the app's. Patching left every
 * field I didn't name at its light-theme value — which is what broke light mode.
 */
@Composable
private fun PanelColors(accent: Color, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White,
            background = Color.Transparent,
            onBackground = Ink,
            surface = Color.Transparent,
            onSurface = Ink,
            onSurfaceVariant = InkDim,
            surfaceVariant = Ink.copy(alpha = 0.10f),
            surfaceContainer = Ink.copy(alpha = 0.08f),
            surfaceContainerHigh = Ink.copy(alpha = 0.12f),
            surfaceContainerHighest = Ink.copy(alpha = 0.16f),
            outline = Ink.copy(alpha = 0.20f),
            outlineVariant = Ink.copy(alpha = 0.12f)
        ),
        typography = MaterialTheme.typography,
        content = content
    )
}

// ------------------------------------------------------------------ queue

/**
 * Up Next as Cider lays it out: cover, title, show, duration.
 *
 * Built here rather than borrowed from the old sheet, whose rows had the same
 * flaw the episode rows once did — a "Remove" reveal drawn permanently behind
 * a transparent row. Here each row is opaque in the panel's own color and the
 * reveal draws only mid-swipe. Unlike queueing, removing *is* a dismissal, so
 * the swipe is allowed to complete and the row leaves the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PanelQueue(
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    surface: Color,
    onPlayAt: (Int) -> Unit,
    onRemove: (Episode) -> Unit,
    onClear: () -> Unit,
    bottomInset: Dp
) {
    if (upNext.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = InkDim,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(tr("Nothing queued"), style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = tr("Swipe any episode left to add it here."),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim
            )
            Spacer(Modifier.height(bottomInset + 60.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Ink.copy(alpha = 0.10f))
                    .clickable(onClick = onClear)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(tr("Clear"), style = MaterialTheme.typography.titleSmall, color = Ink)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(upNext, key = { _, ep -> ep.guid }) { index, episode ->
                val latestRemove by rememberUpdatedState { onRemove(episode) }
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            latestRemove()
                            true
                        } else {
                            false
                        }
                    },
                    positionalThreshold = { it * 0.35f }
                )
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFB3261E).copy(alpha = 0.55f))
                                    .padding(end = 28.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Filled.DeleteOutline, tr("Remove"), tint = Ink)
                            }
                        }
                    }
                ) {
                    val feed = feedFor(episode)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .clickable { onPlayAt(index) }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                            sizeDp = 54.dp,
                            corner = 12.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = feed?.title.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatCompact(episode.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ speed

private val SpeedPresets = listOf(0.8f, 1f, 1.2f, 1.5f, 1.8f, 2f)

/**
 * Speed as a panel rather than a sheet: the number large, a slider for fine
 * control, and the common speeds as one-tap chips. The chips are what people
 * actually use; the slider is for the 1.35× crowd.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedPanel(
    speed: Float,
    accent: Color,
    onSpeedChange: (Float) -> Unit,
    bottomInset: Dp,
    skipSilence: Boolean = false,
    voiceBoost: Boolean = false,
    onSkipSilence: (Boolean) -> Unit = {},
    onVoiceBoost: (Boolean) -> Unit = {},
    skipAds: Boolean = false,
    onSkipAds: (Boolean) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(top = 12.dp, bottom = bottomInset + 16.dp)
    ) {
        Text(
            text = formatSpeed(speed),
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )
        Spacer(Modifier.height(10.dp))
        Slider(
            value = speed,
            onValueChange = { onSpeedChange(((it * 20).roundToInt() / 20f).coerceIn(0.5f, 3f)) },
            valueRange = 0.5f..3f,
            steps = 49,
            colors = SliderDefaults.colors(
                thumbColor = Ink,
                activeTrackColor = accent,
                inactiveTrackColor = Ink.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(14.dp))
        // One row, six equal chips — a lone 2× wrapping onto a second line
        // looked like an afterthought. Equal widths fill the row exactly.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpeedPresets.forEach { preset ->
                val active = kotlin.math.abs(preset - speed) < 0.001f
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (active) accent else Ink.copy(alpha = 0.10f))
                        .clickable { onSpeedChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatSpeed(preset),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active && accent.luminance() > 0.62f) Color(0xFF101014) else Ink,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = tr("SOUND"),
            style = MaterialTheme.typography.labelMedium,
            color = InkDim
        )
        Spacer(Modifier.height(4.dp))
        PanelSwitchRow(
            title = tr("Skip silence"),
            checked = skipSilence,
            accent = accent,
            onChange = onSkipSilence
        )
        PanelSwitchRow(
            title = tr("Boost voices"),
            checked = voiceBoost,
            accent = accent,
            onChange = onVoiceBoost
        )
    }
}

@Composable
private fun PanelSwitchRow(
    title: String,
    detail: String? = null,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Color(0xFF16141A),
                uncheckedTrackColor = Ink.copy(alpha = 0.14f),
                uncheckedThumbColor = InkDim,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ------------------------------------------------------------------ timer

private val TimerPresets = listOf(5, 10, 15, 30, 45, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerPanel(
    armed: Boolean,
    remainingLabel: String,
    accent: Color,
    shakeToRestart: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    bottomInset: Dp
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp)
            .padding(top = 12.dp, bottom = bottomInset + 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (armed) remainingLabel else tr("Off"),
                style = MaterialTheme.typography.displaySmall,
                color = if (armed) accent else Ink
            )
            Spacer(Modifier.weight(1f))
            if (armed) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Ink.copy(alpha = 0.12f))
                        .clickable { SleepTimer.cancel() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(tr("Cancel"), style = MaterialTheme.typography.titleSmall, color = Ink)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerPresets.forEach { minutes ->
                PanelChip(label = tr("{0} min", minutes), active = false, accent = accent) {
                    SleepTimer.armMinutes(minutes)
                }
            }
            PanelChip(label = tr("End of episode"), active = false, accent = accent) {
                SleepTimer.armEndOfEpisode()
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onShakeToggle(!shakeToRestart) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Shake to restart"), style = MaterialTheme.typography.titleSmall, color = Ink)
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = shakeToRestart,
                onCheckedChange = onShakeToggle,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Ink.copy(alpha = 0.14f),
                    uncheckedThumbColor = InkDim,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun PanelChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(if (active) accent else Ink.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (active && accent.luminance() > 0.62f) Color(0xFF101014) else Ink
        )
    }
}

fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString().trimEnd('0')
    return "${text}×"
}

/**
 * The system's own output picker — speakers, Bluetooth, and Cast devices for
 * apps that register them.
 */
fun openOutputSwitcher(context: Context) {
    runCatching {
        androidx.mediarouter.app.SystemOutputSwitcherDialogController.showDialog(context)
    }
}
