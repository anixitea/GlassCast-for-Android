package com.glasscast.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.data.HistoryEntry
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Library > History: every episode you've listened to, most recent first,
 * under the day you last played it. Each row says how far you got, played
 * or the time that's left, and tapping it plays it from there. Clear empties
 * it. Episodes from shows you've since unfollowed drop out (there's nothing
 * left to play).
 */
@Composable
fun HistorySheet(
    entries: List<HistoryEntry>,
    episodeMap: Map<String, List<Episode>>,
    feeds: List<Feed>,
    onPlay: (Episode) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val byGuid = remember(episodeMap) { episodeMap.values.asSequence().flatten().associateBy { it.guid } }
    val feedsByUrl = remember(feeds) { feeds.associateBy { it.url } }
    val rows = remember(entries, byGuid) {
        entries.mapNotNull { e -> byGuid[e.guid]?.let { e to it } }
    }
    val days = remember(rows) { rows.groupBy { historyDay(it.first.at) }.toList() }

    GlassSheet(
        onDismiss = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 12.dp)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr("History"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (rows.isNotEmpty()) {
                    Text(
                        text = tr("Clear history"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onClear() }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            if (rows.isEmpty()) {
                Text(
                    text = tr("Nothing here yet"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 620.dp)) {
                    days.forEach { (day, list) ->
                        item(key = "day:$day") {
                            Text(
                                text = day.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(list, key = { "h:" + it.first.guid }) { (_, episode) ->
                            val feed = feedsByUrl[episode.feedUrl]
                            val status = when {
                                episode.effectivelyPlayed -> tr("Played")
                                episode.durationMs > 0 && episode.positionMs > 0 ->
                                    tr("{0} left", formatCompact((episode.durationMs - episode.positionMs).coerceAtLeast(0L)))
                                else -> ""
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlay(episode)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 20.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Artwork(
                                    url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                                    sizeDp = 52.dp,
                                    corner = 10.dp
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = episode.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(
                                            feed?.title?.takeIf { it.isNotBlank() },
                                            status.takeIf { it.isNotBlank() }
                                        ).joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.padding(bottom = 8.dp)) }
                }
            }
        }
    }
}

private val historyDayFormat = SimpleDateFormat("EEEE, d MMM", Locale.getDefault())

/** The calendar day [at] falls on, as a heading: Today, Yesterday, or the date. Also the car's History and Latest headings. */
internal fun historyDay(at: Long): String {
    if (at <= 0) return tr("Earlier")
    val day = Calendar.getInstance().apply {
        timeInMillis = at
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val today = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val diff = ((today.timeInMillis - day.timeInMillis) / 86_400_000L).toInt()
    return when (diff) {
        0 -> tr("Today")
        1 -> tr("Yesterday")
        else -> historyDayFormat.format(Date(at))
    }
}
