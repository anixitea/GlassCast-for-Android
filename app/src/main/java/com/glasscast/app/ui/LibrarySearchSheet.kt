package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed

/**
 * Searching the library, not the internet.
 *
 * These are different questions with the same verb. "Where is that episode I
 * half-listened to" is a filter over things you already have and should answer
 * instantly and offline; "what podcasts exist about X" is a directory lookup.
 * Putting both behind one field meant the local answer waited on a network
 * round trip it never needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySearchSheet(
    feeds: List<Feed>,
    episodeMap: Map<String, List<Episode>>,
    onOpenFeed: (Feed) -> Unit,
    onPlayEpisode: (Episode) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { requester.requestWhenReady() }

    val trimmed = query.trim()
    val feedsByUrl = remember(feeds) { feeds.associateBy { it.url } }

    val matchingShows = remember(trimmed, feeds) {
        if (trimmed.length < 2) emptyList()
        else feeds.filter {
            it.title.contains(trimmed, true) || it.author.contains(trimmed, true)
        }
    }

    val matchingEpisodes = remember(trimmed, episodeMap) {
        if (trimmed.length < 2) {
            emptyList()
        } else {
            episodeMap.values.flatten()
                .filter { it.title.contains(trimmed, true) }
                .sortedByDescending { it.pubDate }
                .take(40)
        }
    }

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
                .padding(bottom = 12.dp)
        ) {
            Row(
                Modifier
                    .padding(horizontal = 20.dp)
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(25.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.size(14.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = tr("Your shows and episodes"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        interactionSource = remember { MutableInteractionSource() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(requester)
                    )
                }
                if (query.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable { query = "" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = tr("Clear"),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            when {
                trimmed.length < 2 -> Hint(tr("Type to filter the shows and episodes you already have."))

                matchingShows.isEmpty() && matchingEpisodes.isEmpty() ->
                    Hint(tr("Nothing in your library matches. The Search tab looks further afield."))

                else -> LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    if (matchingShows.isNotEmpty()) {
                        item { SectionHeader(tr("SHOWS")) }
                        items(matchingShows, key = { "show-${it.url}" }) { feed ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFeed(feed); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Artwork(url = feed.imageUrl, sizeDp = 46.dp, corner = 9.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = feed.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (feed.author.isNotBlank()) {
                                        Text(
                                            text = feed.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (matchingEpisodes.isNotEmpty()) {
                        item { SectionHeader(tr("EPISODES")) }
                        items(matchingEpisodes, key = { "ep-${it.guid}" }) { episode ->
                            val feed = feedsByUrl[episode.feedUrl]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlayEpisode(episode); onDismiss() }
                                    .padding(horizontal = 20.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Artwork(
                                    url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                                    sizeDp = 46.dp,
                                    corner = 9.dp
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
                                            formatDate(episode.pubDate).takeIf { it.isNotBlank() }
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
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
    )
}
