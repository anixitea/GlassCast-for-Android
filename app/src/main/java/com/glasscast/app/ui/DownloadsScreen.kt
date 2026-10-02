package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.DownloadEntry
import com.glasscast.app.data.DownloadState
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.ui.graphics.Color

/**
 * Downloads: what's saved on the phone, and what's on its way.
 *
 * In-flight downloads sit on top with their progress; finished ones below,
 * newest first, each with its size and a downloaded mark. Tap to play (it
 * plays from the file, no connection needed); swipe left to delete, as a
 * show page's swipe left queues. The select button at the top turns the marks
 * into circles: pick episodes (or all of them) and delete them together. An
 * in-flight download keeps its cancel button. The header says how much space
 * it's all using, since that's the question a downloads page exists to answer.
 */
@Composable
fun DownloadsScreen(
    downloads: Map<String, DownloadEntry>,
    episodeMap: Map<String, List<Episode>>,
    feeds: List<Feed>,
    bottomInset: Dp,
    playingGuid: String?,
    onPlay: (Episode) -> Unit,
    onRemove: (String) -> Unit
) {
    val feedsByUrl = remember(feeds) { feeds.associateBy { it.url } }
    val episodesByGuid = remember(episodeMap) {
        episodeMap.values.flatten().associateBy { it.guid }
    }
    val active = downloads.values
        .filter { it.state == DownloadState.QUEUED || it.state == DownloadState.RUNNING || it.state == DownloadState.FAILED }
        .sortedBy { it.addedAt }
    val done = downloads.values.filter { it.state == DownloadState.DONE }.sortedByDescending { it.addedAt }
    val bytes = done.sumOf { it.bytes }

    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirming by remember { mutableStateOf(false) }
    // Selection only covers what's still on the phone.
    val doneGuids = done.map { it.guid }.toSet()
    LaunchedEffect(doneGuids) {
        selected = selected intersect doneGuids
        if (doneGuids.isEmpty()) selecting = false
    }
    fun exitSelecting() {
        selecting = false
        selected = emptySet()
    }
    BackHandler(enabled = selecting) { exitSelecting() }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = {
                Text(if (selected.size == 1) tr("Delete this download?") else tr("Delete {0} downloads?", selected.size))
            },
            confirmButton = {
                TextButton(onClick = {
                    selected.forEach(onRemove)
                    confirming = false
                    exitSelecting()
                }) { Text(tr("Delete"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) {
                    Text(tr("Cancel"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset + 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxSize()
            .readableWidth()
    ) {
        item(key = "header") {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp)
                    .padding(top = 44.dp, bottom = 10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tr("Downloads"),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f)
                    )
                    if (selecting) {
                        HeaderIcon(
                            icon = Icons.Outlined.SelectAll,
                            description = tr("Select all"),
                            active = selected.size == doneGuids.size
                        ) { selected = if (selected.size == doneGuids.size) emptySet() else doneGuids }
                        Spacer(Modifier.width(8.dp))
                        HeaderIcon(
                            icon = Icons.Outlined.DeleteOutline,
                            description = tr("Delete"),
                            enabled = selected.isNotEmpty(),
                            tint = MaterialTheme.colorScheme.error
                        ) { confirming = true }
                        Spacer(Modifier.width(8.dp))
                        HeaderIcon(icon = Icons.Outlined.Close, description = tr("Cancel")) { exitSelecting() }
                    } else if (done.isNotEmpty()) {
                        HeaderIcon(icon = Icons.Outlined.Checklist, description = tr("Select")) { selecting = true }
                    }
                }
                Text(
                    text = when {
                        selecting -> tr("{0} selected", selected.size)
                        done.isEmpty() && active.isEmpty() -> tr("Episodes you save to listen offline")
                        else -> tr(if (done.size == 1) tr("{0} episode · {1} on this phone") else tr("{0} episodes · {1} on this phone"), done.size, formatBytes(bytes))
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (done.isEmpty() && active.isEmpty()) {
            item(key = "empty") {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 60.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Outlined.DownloadForOffline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = tr("Nothing downloaded yet"),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr("Long-press any episode and choose Download to keep it for when you're offline."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(active, key = { "active:" + it.guid }) { entry ->
            val episode = episodesByGuid[entry.guid]
            DownloadRow(
                entry = entry,
                episode = episode,
                feed = feedsByUrl[entry.feedUrl],
                playing = false,
                onClick = {},
                onRemove = { onRemove(entry.guid) }
            )
        }
        items(done, key = { "done:" + it.guid }) { entry ->
            val episode = episodesByGuid[entry.guid]
            SwipeToDelete(onDelete = { onRemove(entry.guid) }, enabled = !selecting) {
                DownloadRow(
                    entry = entry,
                    episode = episode,
                    feed = feedsByUrl[entry.feedUrl],
                    playing = entry.guid == playingGuid,
                    selecting = selecting,
                    selected = entry.guid in selected,
                    onClick = {
                        if (selecting) {
                            selected = if (entry.guid in selected) selected - entry.guid else selected + entry.guid
                        } else {
                            episode?.let(onPlay)
                        }
                    },
                    onRemove = { onRemove(entry.guid) }
                )
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadEntry,
    episode: Episode?,
    feed: Feed?,
    playing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    selecting: Boolean = false,
    selected: Boolean = false
) {
    // A light wash of the show's own color, so shows tell apart at a glance.
    val (showColors, _) = rememberArtworkColors(feed?.imageUrl.orEmpty().ifBlank { episode?.imageUrl.orEmpty() })
    val wash by androidx.compose.animation.animateColorAsState(showColors.elevated, androidx.compose.animation.core.tween(400), label = "rowWash")
    val art = episode?.imageUrl?.ifBlank { null } ?: feed?.imageUrl.orEmpty()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(wash)
            .clickable(enabled = entry.state == DownloadState.DONE && (selecting || episode != null), onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(url = art, sizeDp = 56.dp, corner = 11.dp)
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = (feed?.title ?: "").uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = episode?.title ?: entry.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            when (entry.state) {
                DownloadState.DONE -> Text(
                    text = formatBytes(entry.bytes) + if (episode == null) " · no longer in your library" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DownloadState.FAILED -> Text(
                    text = tr("Download failed — remove it and try again"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                else -> LinearProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (entry.state == DownloadState.DONE) {
            // Downloaded: a mark, not a button — swipe or select to delete.
            // While selecting, a circle that fills when picked.
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = when {
                        !selecting -> Icons.Filled.DownloadForOffline
                        selected -> Icons.Filled.CheckCircle
                        else -> Icons.Outlined.RadioButtonUnchecked
                    },
                    contentDescription = when {
                        !selecting -> tr("Downloaded")
                        selected -> tr("{0} selected", 1)
                        else -> null
                    },
                    tint = if (selecting && !selected) MaterialTheme.colorScheme.onSurfaceVariant else showColors.accent,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = tr("Cancel download"),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * The download state, sized to sit in an episode row's detail line: a small
 * filling ring while downloading, a check once it's on the phone, a warning if
 * it failed, and nothing at all otherwise.
 */
@Composable
fun DownloadBadge(entry: DownloadEntry?, modifier: Modifier = Modifier) {
    when (entry?.state) {
        DownloadState.DONE -> Icon(
            Icons.Filled.DownloadDone,
            contentDescription = tr("Downloaded"),
            tint = MaterialTheme.colorScheme.primary,
            modifier = modifier.size(16.dp)
        )
        DownloadState.QUEUED, DownloadState.RUNNING -> CircularProgressIndicator(
            progress = { entry.progress.coerceAtLeast(0.04f) },
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            modifier = modifier.size(14.dp)
        )
        DownloadState.FAILED -> Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = tr("Download failed"),
            tint = MaterialTheme.colorScheme.error,
            modifier = modifier.size(16.dp)
        )
        null -> Unit
    }
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format("%.0f MB", bytes / 1_000_000.0)
    bytes > 0 -> "${bytes / 1000} KB"
    else -> "0 MB"
}

/** A round header button, as on the other pages' top bars. */
@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean = true,
    active: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(
                if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                active -> MaterialTheme.colorScheme.primary
                else -> tint
            },
            modifier = Modifier.size(22.dp)
        )
    }
}
