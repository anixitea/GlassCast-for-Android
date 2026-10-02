package com.glasscast.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.DirectoryResult
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.ITunesDirectory
import com.glasscast.app.data.PodcastGenres
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/**
 * Directory search, on its own tab. Debounced rather than fired per keystroke —
 * a search API call for every letter of a show name is rude to Apple and slow
 * on a phone connection.
 */
@Composable
fun SearchScreen(
    store: FeedStore,
    subscribed: List<Feed>,
    bottomInset: Dp,
    /** Opens the show without subscribing to it. */
    onPreviewFeed: (DirectoryResult) -> Unit,
    onAdd: (DirectoryResult) -> Unit,
    addingUrl: String?,
    /** Set when the pull gesture brought us here, so the keyboard is already up. */
    autoFocus: Boolean = false,
    onAutoFocusHandled: () -> Unit = {},
    hazeState: HazeState
) {
    var query by remember { mutableStateOf("") }

    // Only the row you tapped carries the cover's flight key. Results can
    // include shows already in the library, and two elements with one key on
    // screen at once — a tile and a row during a tab slide — make covers fly
    // between them. Saveable so the flight home lands on the same row.
    var tappedUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val openPreview: (DirectoryResult) -> Unit = {
        tappedUrl = it.feedUrl
        onPreviewFeed(it)
    }
    var results by remember { mutableStateOf<List<DirectoryResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var genre by remember { mutableStateOf(PodcastGenres.first()) }
    var charts by remember { mutableStateOf<List<DirectoryResult>>(emptyList()) }
    var loadingCharts by remember { mutableStateOf(true) }

    // Charts load per genre and are what fills the screen before anyone types —
    // an empty search page is a dead end, and most people arrive without a show
    // in mind.
    LaunchedEffect(genre.id) {
        loadingCharts = true
        charts = runCatching { ITunesDirectory.top(genre.id) }.getOrDefault(emptyList())
        loadingCharts = false
    }

    LaunchedEffect(query) {
        if (query.trim().length < 2) {
            results = emptyList()
            searching = false
            return@LaunchedEffect
        }
        delay(400)
        searching = true
        results = runCatching { ITunesDirectory.search(query) }.getOrDefault(emptyList())
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.statusBarsPadding().height(20.dp))
            // Search has no scrolling content under its header, so it takes the
            // plain title rather than a glass ramp with nothing behind it.
            Text(
                text = "Search",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(14.dp))

            SearchField(
                value = query,
                onValueChange = { query = it },
                onClear = { query = "" },
                autoFocus = autoFocus,
                onAutoFocusHandled = onAutoFocusHandled
            )
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.weight(1f)) {
            val idle = query.trim().length < 2

            when {
                searching -> LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
                ) {
                    items(6) { index -> ResultRowSkeleton(index) }
                }

                !idle && results.isNotEmpty() -> LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
                ) {
                    items(results, key = { it.feedUrl }) { result ->
                        ResultRow(result, subscribed, addingUrl, openPreview, onAdd, tappedUrl = tappedUrl)
                    }
                }

                !idle -> CenterNote("Nothing found. A feed URL always works.")

                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = bottomInset + 24.dp)
                ) {
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PodcastGenres.forEach { g ->
                                Pill(
                                    label = g.label,
                                    active = g.id == genre.id,
                                    onClick = { genre = g }
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = if (genre.id == 0) "TOP SHOWS" else "TOP IN ${genre.label.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                    }

                    if (loadingCharts) {
                        items(8) { index -> ResultRowSkeleton(index) }
                    } else if (charts.isEmpty()) {
                        item { CenterNote("Charts didn't load. Search or paste a feed URL.") }
                    } else {
                        itemsIndexed(charts, key = { _, r -> r.feedUrl }) { index, result ->
                            ResultRow(result, subscribed, addingUrl, openPreview, onAdd, rank = index + 1, tappedUrl = tappedUrl)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: DirectoryResult,
    subscribed: List<Feed>,
    addingUrl: String?,
    onPreviewFeed: (DirectoryResult) -> Unit,
    onAdd: (DirectoryResult) -> Unit,
    rank: Int = 0,
    tappedUrl: String? = null
) {
    val already = subscribed.any { it.url.equals(result.feedUrl, true) }
    DirectoryRow(
        sharedKey = if (tappedUrl == result.feedUrl) coverKey(result.feedUrl) else null,
        result = result,
        subscribed = already,
        busy = addingUrl == result.feedUrl,
        rank = rank,
        // Tapping the row browses; only the Add pill commits.
        onClick = { onPreviewFeed(result) },
        onAdd = { onAdd(result) }
    )
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    autoFocus: Boolean,
    onAutoFocusHandled: () -> Unit
) {
    val focused = remember { MutableInteractionSource() }
    val isFocused by focused.collectIsFocusedAsState()
    val requester = remember { FocusRequester() }

    LaunchedEffect(autoFocus) {
        if (autoFocus) {
            requester.requestWhenReady()
            // One-shot: coming back to this tab by tapping shouldn't raise the
            // keyboard again.
            onAutoFocusHandled()
        }
    }

    val border by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else Color.Transparent,
        label = "searchBorder"
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        interactionSource = focused,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.5.dp, border, RoundedCornerShape(27.dp))
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = if (isFocused) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp)
                )
                Spacer(Modifier.size(14.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            text = "Shows, hosts, topics",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun CenterNote(text: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DirectoryRow(
    sharedKey: String? = null,
    result: DirectoryResult,
    subscribed: Boolean,
    busy: Boolean,
    rank: Int,
    onClick: () -> Unit,
    onAdd: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank > 0) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(22.dp)
            )
        }
        Artwork(
            url = result.artworkUrl,
            sizeDp = 58.dp,
            corner = 10.dp,
            modifier = sharedKey?.let { Modifier.sharedArtwork(it, LocalNavScope.current) } ?: Modifier
        )
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (result.episodeCount > 0) {
                    "${result.author} · ${result.episodeCount} episodes"
                } else {
                    result.author
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.size(10.dp))
        when {
            busy -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            subscribed -> Icon(
                Icons.Filled.Check,
                contentDescription = "Subscribed",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            else -> Pill(label = "Add", active = true, onClick = onAdd)
        }
    }
}
