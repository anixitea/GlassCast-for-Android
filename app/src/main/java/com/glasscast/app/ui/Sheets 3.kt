package com.glasscast.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.EpisodeSort
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.Settings
import com.glasscast.app.data.ShowSort
import com.glasscast.app.data.ThemeMode
import com.glasscast.app.player.SleepTimer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val SPEED_PRESETS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeedSheet(
    speed: Float,
    accent: Color,
    onSpeedChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SheetLabel("SPEED")
            Spacer(Modifier.height(10.dp))
            Text(
                text = String.format(Locale.US, "%.2f", speed).trimEnd('0').trimEnd('.') + "×",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))

            // 0.5×–3× in 0.05 steps: 50 intervals, so 49 stops between the ends.
            Slider(
                value = speed,
                onValueChange = { onSpeedChange(Math.round(it / 0.05f) * 0.05f) },
                valueRange = 0.5f..3f,
                steps = 49,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                    inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                SPEED_PRESETS.forEach { preset ->
                    val label = String.format(Locale.US, "%.2f", preset)
                        .trimEnd('0').trimEnd('.') + "×"
                    Pill(
                        label = label,
                        active = kotlin.math.abs(preset - speed) < 0.001f,
                        accent = accent,
                        contentPadding = PaddingValues(horizontal = 11.dp, vertical = 7.dp),
                        onClick = { onSpeedChange(preset) }
                    )
                }
            }
        }
    }
}


private val TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepSheet(
    accent: Color,
    armed: Boolean,
    remainingLabel: String,
    shakeToRestart: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetLabel("TIMER")
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (armed) remainingLabel else "Off",
                style = MaterialTheme.typography.displaySmall,
                color = if (armed) accent else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Volume fades over the last 15 seconds rather than cutting mid-word.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TIMER_MINUTES.forEach { minutes ->
                    Pill(
                        label = "${minutes}m",
                        accent = accent,
                        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp),
                        onClick = {
                            SleepTimer.armMinutes(minutes)
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    label = "End of episode",
                    accent = accent,
                    onClick = {
                        SleepTimer.armEndOfEpisode()
                        onDismiss()
                    }
                )
                if (armed) {
                    Pill(
                        label = "+10m",
                        accent = accent,
                        onClick = { SleepTimer.addMinutes(10) }
                    )
                    Pill(
                        label = "Cancel",
                        accent = accent,
                        onClick = {
                            SleepTimer.cancel()
                            onDismiss()
                        }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Hairline(alpha = 0.5f)
            Spacer(Modifier.height(8.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onShakeToggle(!shakeToRestart) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Shake to restart",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Still awake when it stops? Shake the phone to run " +
                            "the timer again and carry on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(
                    checked = shakeToRestart,
                    onCheckedChange = onShakeToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = accent
                    )
                )
            }
        }
    }
}

/**
 * RSS only. Directory search lives on its own tab now — one field trying to be
 * both a search box and a URL box meant every pasted URL fired a search request
 * on the way in, and neither job read as the primary one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeedSheet(store: FeedStore, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            SheetLabel("ADD BY URL")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Paste an RSS feed",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it; error = null },
                singleLine = true,
                placeholder = {
                    Text(
                        "https://example.com/feed.xml",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Pill(
                    label = if (busy) "Adding…" else "Add",
                    active = true,
                    onClick = {
                        if (!busy && url.isNotBlank()) {
                            busy = true
                            error = null
                            scope.launch {
                                val reason = store.subscribe(url)
                                busy = false
                                if (reason == null) onDismiss() else error = reason
                            }
                        }
                    }
                )
                if (busy) {
                    Spacer(Modifier.size(12.dp))
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = "Looking for a show by name? Use the Search tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Long-press an episode anywhere and you get these. Apple puts them behind a
 * context menu; a sheet is the Material equivalent and is easier to hit
 * one-handed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeActionsSheet(
    episode: Episode,
    queued: Boolean,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onRemoveFromQueue: () -> Unit,
    onTogglePlayed: () -> Unit,
    onDismiss: () -> Unit,
    download: com.glasscast.app.data.DownloadEntry? = null,
    onDownload: () -> Unit = {},
    onRemoveDownload: () -> Unit = {}
) {
    val played = episode.effectivelyPlayed

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))
            }

            ActionRow(Icons.Filled.PlayArrow, "Play now") { onPlay(); onDismiss() }
            ActionRow(Icons.Filled.QueuePlayNext, "Play next") { onPlayNext(); onDismiss() }
            if (queued) {
                ActionRow(Icons.Filled.RemoveCircleOutline, "Remove from Up Next") {
                    onRemoveFromQueue(); onDismiss()
                }
            } else {
                ActionRow(Icons.Filled.QueueMusic, "Add to Up Next") { onAddToQueue(); onDismiss() }
            }
            ActionRow(
                if (played) Icons.Filled.UnfoldMore else Icons.Filled.CheckCircle,
                if (played) "Mark as unplayed" else "Mark as played"
            ) { onTogglePlayed(); onDismiss() }
            when (download?.state) {
                com.glasscast.app.data.DownloadState.DONE ->
                    ActionRow(Icons.Outlined.DeleteOutline, "Remove download") { onRemoveDownload(); onDismiss() }
                com.glasscast.app.data.DownloadState.QUEUED, com.glasscast.app.data.DownloadState.RUNNING ->
                    ActionRow(Icons.Outlined.Close, "Cancel download") { onRemoveDownload(); onDismiss() }
                else ->
                    ActionRow(Icons.Outlined.DownloadForOffline, "Download") { onDownload(); onDismiss() }
            }
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.size(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    theme: ThemeMode,
    sort: EpisodeSort,
    showSort: ShowSort,
    appVersion: String = "",
    onCheckUpdates: () -> Unit = {},
    onOpenOpml: () -> Unit,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            SheetLabel("APPEARANCE")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ThemeMode.entries.forEach { mode ->
                    Pill(
                        label = when (mode) {
                            ThemeMode.SYSTEM -> "System"
                            ThemeMode.LIGHT -> "Light"
                            ThemeMode.DARK -> "Dark"
                        },
                        active = mode == theme,
                        onClick = { settings.setTheme(mode) }
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Separate from brightness, so dynamic colour works in light and
            // dark alike — which the old five-way list made impossible.
            val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.setDynamicColor(!dynamicColor) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Dynamic colour",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Tint the app with the colours of whatever is playing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = dynamicColor,
                    onCheckedChange = { settings.setDynamicColor(it) }
                )
            }

            Spacer(Modifier.height(24.dp))
            SheetLabel("SUBSCRIPTIONS")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(label = "Import / export OPML", onClick = onOpenOpml)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Moving from AntennaPod or Pocket Casts? Export OPML there and open it here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))
            SheetLabel("NOTIFICATIONS")
            Spacer(Modifier.height(6.dp))
            val newEpisodes by settings.newEpisodeNotifications.collectAsStateWithLifecycle()
            val notifContext = androidx.compose.ui.platform.LocalContext.current
            var allowed by remember {
                mutableStateOf(com.glasscast.app.background.NewEpisodeNotifier.canPost(notifContext))
            }
            val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted -> allowed = granted }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { settings.setNewEpisodeNotifications(!newEpisodes) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "New episodes",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            newEpisodes && !allowed ->
                                "Notifications are blocked for GlassCast — tap to allow."
                            else ->
                                "Checks your shows every few hours and tells you what's new."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (newEpisodes && !allowed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (newEpisodes && !allowed) Modifier.clickable {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else Modifier
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = newEpisodes,
                    onCheckedChange = { on ->
                        settings.setNewEpisodeNotifications(on)
                        if (on && !allowed &&
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU
                        ) {
                            askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
            SheetLabel("SHOW ORDER")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    label = "Recently updated",
                    active = showSort == ShowSort.RECENTLY_UPDATED,
                    onClick = { settings.setShowSort(ShowSort.RECENTLY_UPDATED) }
                )
                Pill(
                    label = "Recently played",
                    active = showSort == ShowSort.RECENTLY_PLAYED,
                    onClick = { settings.setShowSort(ShowSort.RECENTLY_PLAYED) }
                )
            }

            Spacer(Modifier.height(24.dp))
            SheetLabel("EPISODE ORDER")
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(
                    label = "Newest first",
                    active = sort == EpisodeSort.NEWEST_FIRST,
                    onClick = { settings.setSort(EpisodeSort.NEWEST_FIRST) }
                )
                Pill(
                    label = "Oldest first",
                    active = sort == EpisodeSort.OLDEST_FIRST,
                    onClick = { settings.setSort(EpisodeSort.OLDEST_FIRST) }
                )
            }

            val hiddenFromDiscover by settings.discoverHidden.collectAsStateWithLifecycle()
            if (hiddenFromDiscover.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SheetLabel("DISCOVER")
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { settings.clearDiscoverHidden() }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${hiddenFromDiscover.size} show${if (hiddenFromDiscover.size == 1) "" else "s"} marked not interested",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Show again",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SheetLabel("ABOUT")
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCheckUpdates)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "GlassCast $appVersion",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Updates come straight from GitHub, verified before they install.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Check for updates",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
