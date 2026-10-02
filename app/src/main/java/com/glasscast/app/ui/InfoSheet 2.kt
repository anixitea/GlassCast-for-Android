package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Chapter
import com.glasscast.app.data.Episode
import com.glasscast.app.data.EpisodeExtras
import com.glasscast.app.data.Feed
import com.glasscast.app.data.TranscriptCue

private enum class InfoTab(val label: String) { NOTES("Notes"), CHAPTERS("Chapters"), TRANSCRIPT("Transcript") }

/**
 * Notes, chapters and transcript without a container — the Info tab of the
 * player's pull-up panel.
 *
 * Chapter sourcing, in order of how good the data is and inverse order of how
 * often it's there: a <podcast:chapters> JSON file, then timestamps parsed out
 * of the show notes. Most feeds only give you the second.
 * [showHeader] is off in the panel, where the
 * compact header already names the episode; [listModifier] lets the panel's
 * lists fill it instead of stopping at a sheet-sized cap.
 */
@Composable
fun EpisodeInfoContent(
    episode: Episode,
    feed: Feed?,
    accent: Color,
    positionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    listModifier: Modifier = Modifier.heightIn(max = 460.dp)
) {
    var chapters by remember(episode.guid) {
        mutableStateOf(EpisodeExtras.chaptersFromNotes(episode.description))
    }
    var cues by remember(episode.guid) { mutableStateOf<List<TranscriptCue>>(emptyList()) }
    var loadingTranscript by remember(episode.guid) {
        mutableStateOf(episode.transcriptUrl.isNotBlank())
    }

    LaunchedEffect(episode.guid) {
        if (episode.chaptersUrl.isNotBlank()) {
            val fetched = EpisodeExtras.fetchChapters(episode.chaptersUrl)
            if (fetched.isNotEmpty()) chapters = fetched
        }
        if (episode.transcriptUrl.isNotBlank()) {
            cues = EpisodeExtras.fetchTranscript(episode.transcriptUrl)
            loadingTranscript = false
        }
    }

    val notes = remember(episode.description) { stripHtml(episode.description) }

    // Only tabs with something behind them. A Transcript tab that opens on
    // "no transcript" is a worse answer than no tab at all.
    val tabs = buildList {
        add(InfoTab.NOTES)
        if (chapters.isNotEmpty()) add(InfoTab.CHAPTERS)
        if (episode.transcriptUrl.isNotBlank()) add(InfoTab.TRANSCRIPT)
    }
    var tab by remember(episode.guid) { mutableStateOf(InfoTab.NOTES) }

        Column(
            modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                if (showHeader) {
                Text(
                    text = formatDate(episode.pubDate).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!feed?.title.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = feed.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                }

                if (tabs.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tabs.forEach { entry ->
                            Pill(
                                label = entry.label,
                                active = entry == tab,
                                accent = accent,
                                onClick = { tab = entry }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            when (tab) {
                InfoTab.NOTES -> LazyColumn(
                    listModifier
                        .padding(horizontal = 24.dp)
                ) {
                    item {
                        Text(
                            text = notes.ifBlank { "This episode has no description." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(24.dp))
                    }
                }

                InfoTab.CHAPTERS -> LazyColumn(
                    listModifier
                        .padding(horizontal = 24.dp)
                ) {
                    items(chapters, key = { it.startMs }) { chapter ->
                        ChapterRow(
                            chapter = chapter,
                            accent = accent,
                            playing = isCurrent(chapter, chapters, positionMs)
                        ) {
                            onSeekTo(chapter.startMs)
                        }
                    }
                }

                InfoTab.TRANSCRIPT -> TranscriptList(
                    listModifier = listModifier,
                    cues = cues,
                    loading = loadingTranscript,
                    positionMs = positionMs,
                    accent = accent,
                    onSeekTo = onSeekTo
                )
            }
        }
}

private fun isCurrent(chapter: Chapter, all: List<Chapter>, positionMs: Long): Boolean {
    val index = all.indexOf(chapter)
    val end = all.getOrNull(index + 1)?.startMs ?: Long.MAX_VALUE
    return positionMs >= chapter.startMs && positionMs < end
}

/**
 * The transcript, following the audio.
 *
 * Borrowed from how good lyrics views behave: the line being spoken is the only
 * one at full strength, its neighbours recede, and the list keeps itself
 * positioned so you never hunt for your place. Tapping a line seeks to it,
 * which turns a transcript into a navigation surface rather than a wall of
 * text — the thing an hour-long episode most lacks.
 *
 * The current line also fills left-to-right as it's spoken. That is honest
 * here: SRT and VTT give a start and end per cue, so the fill is real elapsed
 * time within the line rather than a guess at individual words.
 */
@Composable
private fun TranscriptList(
    listModifier: Modifier,
    cues: List<TranscriptCue>,
    loading: Boolean,
    positionMs: Long,
    accent: Color,
    onSeekTo: (Long) -> Unit
) {
    if (loading) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = accent,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = "Loading transcript…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    if (cues.isEmpty()) {
        Text(
            text = "This episode's transcript couldn't be read.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        return
    }

    val listState = rememberLazyListState()

    val currentIndex by remember(cues) {
        derivedStateOf {
            cues.indexOfLast { it.startMs <= positionMs }.coerceAtLeast(0)
        }
    }

    // Two lines of lead-in, so the spoken line sits a little below the top edge
    // rather than pinned against it.
    LaunchedEffect(currentIndex) {
        listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
    }

    LazyColumn(
        state = listState,
        modifier = listModifier
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        itemsIndexed(cues, key = { _, cue -> cue.startMs }) { index, cue ->
            val isCurrent = index == currentIndex
            val end = cues.getOrNull(index + 1)?.startMs ?: (cue.startMs + 4_000)
            val span = (end - cue.startMs).coerceAtLeast(1L)
            val progress = ((positionMs - cue.startMs).toFloat() / span).coerceIn(0f, 1f)

            val spoken = MaterialTheme.colorScheme.onSurface
            val unspoken = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f)

            Text(
                text = cue.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    brush = if (isCurrent) {
                        // Stops must strictly increase, so the fill edge is
                        // nudged rather than duplicated at the same offset.
                        Brush.horizontalGradient(
                            0f to accent,
                            progress.coerceIn(0.001f, 0.999f) to accent,
                            (progress + 0.001f).coerceIn(0.002f, 1f) to unspoken,
                            1f to unspoken
                        )
                    } else {
                        null
                    }
                ),
                color = if (isCurrent) Color.Unspecified else spoken.copy(alpha = 0.42f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSeekTo(cue.startMs) }
                    .padding(vertical = 2.dp)
            )
        }

        item { Spacer(Modifier.height(32.dp)) }
    }
}

@Composable
private fun ChapterRow(
    chapter: Chapter,
    accent: Color,
    playing: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (playing) accent.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = EpisodeExtras.formatChapterTime(chapter.startMs),
            style = MaterialTheme.typography.bodySmall,
            color = accent
        )
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
    }
}
