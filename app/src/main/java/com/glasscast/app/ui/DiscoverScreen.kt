package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.DirectoryResult
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.ITunesDirectory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Apple's podcast genre ids, including a few the Search chips don't show.
 * Matched against a show's own <itunes:category> text by containment, so
 * "Society & Culture" and its sub-category "Documentary" both land on Society.
 */
private val DiscoverGenres = listOf(
    1303 to "Comedy", 1489 to "News", 1324 to "Society", 1318 to "Technology",
    1304 to "Education", 1320 to "Fiction", 1488 to "True Crime", 1310 to "Music",
    1545 to "Sports", 1512 to "Health", 1321 to "Business", 1533 to "Science",
    1301 to "Arts", 1314 to "Religion", 1487 to "History", 1309 to "TV & Film",
    1305 to "Kids & Family", 1502 to "Leisure", 1511 to "Government"
)

private val SubCategoryHints = mapOf(
    "documentary" to 1324, "personal journals" to 1324, "relationships" to 1324,
    "philosophy" to 1324, "places & travel" to 1324, "film" to 1309, "tv" to 1309,
    "commentary" to 1489, "politics" to 1489, "daily news" to 1489
)

@Immutable
private data class Shelf(val title: String, val subtitle: String, val items: List<DirectoryResult>)

/**
 * Discover: recommendations built from what you already follow.
 *
 * No account, no listening data leaves the phone. Each show carries its own
 * categories in its feed; those are tallied across the library — a show you've
 * played in the last fortnight counts three times — and the strongest genres
 * become shelves of that genre's current chart, minus anything already
 * subscribed. The top of each shelf feeds the picks carousel.
 *
 * It's deliberately simple. Charts within your genres are a better starting
 * point than a clever model with nothing to learn from, and every shelf says
 * plainly why it's there.
 */
@Composable
fun DiscoverScreen(
    feeds: List<Feed>,
    store: FeedStore,
    bottomInset: Dp,
    previewingUrl: String?,
    onPreview: (DirectoryResult) -> Unit
) {
    var shelves by remember { mutableStateOf<List<Shelf>?>(null) }

    /*
     * Only the card you tapped carries its cover's flight key.
     *
     * Every card used to, and the picks carousel is built from the first items
     * of the shelves below it — so the same show sat on screen twice under one
     * key. Shared-element keys must be unique among what's visible; with two,
     * covers were drawn at the other card's size and place as rows scrolled in
     * and out. That was the large stray artwork floating over the page.
     *
     * "slot|feedUrl", so a show that appears in two shelves still flies from
     * the one you actually touched. Saveable, so the flight home lands there.
     */
    var tapped by rememberSaveable { mutableStateOf<String?>(null) }
    fun keyFor(slot: String, result: DirectoryResult) =
        if (tapped == slot + "|" + result.feedUrl) coverKey(result.feedUrl) else null
    var backfilled by rememberSaveable { mutableStateOf(false) }

    // Libraries from before category parsing: fetch those feeds once so their
    // categories exist. Conditional refreshes would never re-parse them.
    LaunchedEffect(Unit) {
        if (!backfilled && feeds.any { it.categories.isEmpty() }) {
            runCatching { store.backfillCategories() }
            backfilled = true
        }
    }

    val genreKey = feeds.joinToString { it.categories.joinToString() + it.lastPlayedAt / 86_400_000 }
    LaunchedEffect(genreKey) {
        shelves = runCatching { buildShelves(feeds) }.getOrDefault(emptyList())
    }

    val loaded = shelves
    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 44.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Discover",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (feeds.isEmpty()) "What people are listening to"
                    else "Picked from the shows you follow",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (loaded == null) {
            item { DiscoverSkeleton() }
        } else {
            val picks = loaded.flatMap { it.items.take(2) }.distinctBy { it.feedUrl }.take(6)
            // The picks aren't repeated in the shelves beneath them.
            val pickUrls = picks.map { it.feedUrl }.toSet()
            if (picks.isNotEmpty()) {
                item { ShelfTitle("Top picks for you", "The best of your strongest genres") }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(picks, key = { "pick:" + it.feedUrl }) { result ->
                            PickCard(result, busy = previewingUrl == result.feedUrl, sharedKey = keyFor("pick", result)) {
                                tapped = "pick|" + result.feedUrl
                                onPreview(result)
                            }
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            loaded.map { it.copy(items = it.items.filterNot { r -> r.feedUrl in pickUrls }) }
                .filter { it.items.isNotEmpty() }
                .forEach { shelf ->
                item(key = "title:" + shelf.title) { ShelfTitle(shelf.title, shelf.subtitle) }
                item(key = "row:" + shelf.title) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(shelf.items, key = { shelf.title + it.feedUrl }) { result ->
                            ShowCard(result, busy = previewingUrl == result.feedUrl, sharedKey = keyFor(shelf.title, result)) {
                                tapped = shelf.title + "|" + result.feedUrl
                                onPreview(result)
                            }
                        }
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
        }
    }
}

private suspend fun buildShelves(feeds: List<Feed>): List<Shelf> = coroutineScope {
    val now = System.currentTimeMillis()
    val scores = mutableMapOf<Int, Double>()
    feeds.forEach { feed ->
        val weight = if (now - feed.lastPlayedAt < 14L * 86_400_000) 3.0 else 1.0
        feed.categories
            .mapNotNull { genreFor(it) }
            .distinct()
            .forEach { id -> scores[id] = (scores[id] ?: 0.0) + weight }
    }

    val followed = feeds.map { it.url.lowercase() }.toSet()
    val followedTitles = feeds.map { it.title.lowercase() }.toSet()
    fun List<DirectoryResult>.fresh() = filter {
        it.feedUrl.lowercase() !in followed && it.title.lowercase() !in followedTitles
    }

    val topGenres = scores.entries.sortedByDescending { it.value }.take(3).map { it.key }
        .ifEmpty { listOf(1303, 1489) } // nothing known yet: two broad starting points

    val genreShelves = topGenres.map { id ->
        async {
            val label = DiscoverGenres.firstOrNull { it.first == id }?.second ?: "your genres"
            val because = feeds.firstOrNull { f -> f.categories.any { genreFor(it) == id } }?.title
            Shelf(
                title = "More in $label",
                subtitle = because?.let { "Because you follow $it" } ?: "Popular right now",
                items = runCatching { ITunesDirectory.top(id, 30) }.getOrDefault(emptyList()).fresh().take(15)
            )
        }
    }
    val top = async {
        Shelf(
            title = "Top podcasts",
            subtitle = "What everyone's listening to",
            items = runCatching { ITunesDirectory.top(0, 30) }.getOrDefault(emptyList()).fresh().take(15)
        )
    }
    (genreShelves.awaitAll() + top.await()).filter { it.items.isNotEmpty() }
}

private fun genreFor(category: String): Int? {
    val c = category.lowercase()
    DiscoverGenres.firstOrNull { (_, label) -> c.contains(label.lowercase()) }?.let { return it.first }
    return SubCategoryHints.entries.firstOrNull { c.contains(it.key) }?.value
}

@Composable
private fun ShelfTitle(title: String, subtitle: String) {
    Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The large carousel card: cover full-bleed, name set over a darkening foot. */
@Composable
private fun PickCard(result: DirectoryResult, busy: Boolean, sharedKey: String?, onClick: () -> Unit) {
    Box(
        Modifier
            .width(260.dp)
            .height(330.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(onClick = onClick)
    ) {
        Artwork(
            url = result.artworkUrl,
            sizeDp = 330.dp,
            corner = 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .then(sharedKey?.let { Modifier.sharedArtwork(it, LocalNavScope.current) } ?: Modifier)
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.78f)
                    )
                )
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Text(
                text = result.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (busy) "Opening…" else result.author,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShowCard(result: DirectoryResult, busy: Boolean, sharedKey: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Artwork(
            url = result.artworkUrl,
            sizeDp = 148.dp,
            corner = 16.dp,
            modifier = sharedKey?.let { Modifier.sharedArtwork(it, LocalNavScope.current) } ?: Modifier
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = result.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (busy) "Opening…" else result.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DiscoverSkeleton() {
    Column {
        ShelfTitle("Top picks for you", "Finding shows…")
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            userScrollEnabled = false
        ) {
            items(3) {
                ShimmerBox(
                    Modifier.size(width = 260.dp, height = 330.dp),
                    shape = RoundedCornerShape(26.dp)
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        repeat(2) {
            Box(Modifier.padding(horizontal = 24.dp).padding(bottom = 12.dp)) {
                ShimmerBox(Modifier.size(width = 180.dp, height = 22.dp), shape = RoundedCornerShape(8.dp))
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false
            ) {
                items(4) {
                    ShimmerBox(Modifier.size(148.dp), shape = RoundedCornerShape(16.dp))
                }
            }
            Spacer(Modifier.height(26.dp))
        }
    }
}
