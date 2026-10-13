package com.glasscast.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.graphicsLayer
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

/** One Discover shelf. Shared with the TV's Discover screen. */
@Immutable
internal data class Shelf(val title: String, val subtitle: String, val items: List<DirectoryResult>)

/**
 * Discover: recommendations built from what you already follow.
 *
 * No account, no listening data leaves the phone. Each show carries its own
 * categories in its feed; those are tallied across the library — a show you've
 * played in the last fortnight counts three times — and the strongest genres
 * become shelves of that genre's chart, minus anything already followed. The
 * head of each shelf feeds the picks carousel.
 *
 * **Refresh** deals a new hand: each round shuffles deeper into every genre's
 * chart and rotates in a genre you follow a little less. **Long-press** any
 * card for *Not interested*; it leaves at once and never comes back (Settings
 * can bring hidden shows back). Results are kept between visits, so returning
 * to the tab doesn't refetch or flash the skeleton.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DiscoverScreen(
    feeds: List<Feed>,
    store: FeedStore,
    bottomInset: Dp,
    previewingUrl: String?,
    onPreview: (DirectoryResult) -> Unit,
    dismissed: Set<String> = emptySet(),
    onNotInterested: (DirectoryResult) -> Unit = {}
) {
    var round by rememberSaveable { mutableIntStateOf(0) }
    val genreKey = feeds.joinToString { it.categories.joinToString() + it.lastPlayedAt / 86_400_000 }
    val cacheKey = "$genreKey#$round"
    var shelves by remember { mutableStateOf(DiscoverCache.shelvesFor(cacheKey)) }
    var backfilled by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    // Which card's menu is open, as "slot|feedUrl" — a show can sit in two rows.
    var menuFor by remember { mutableStateOf<String?>(null) }

    // Libraries from before category parsing: fetch those feeds once so their
    // categories exist. Conditional refreshes would never re-parse them.
    LaunchedEffect(Unit) {
        if (!backfilled && feeds.any { it.categories.isEmpty() }) {
            runCatching { store.backfillCategories() }
            backfilled = true
        }
    }

    LaunchedEffect(cacheKey) {
        val cached = DiscoverCache.shelvesFor(cacheKey)
        if (cached != null) {
            shelves = cached
            return@LaunchedEffect
        }
        shelves = null
        val built = runCatching { buildShelves(feeds, round) }.getOrDefault(emptyList())
        DiscoverCache.put(cacheKey, built)
        shelves = built
    }

    // Hidden shows are filtered at render, so Not interested takes effect at
    // once without refetching anything.
    val visible = shelves?.map { shelf -> shelf.copy(items = shelf.items.filterNot { it.feedUrl in dismissed }) }
        ?.filter { it.items.isNotEmpty() }

    fun longPress(slot: String, result: DirectoryResult) {
        haptics.play(Haptic.Select)
        menuFor = slot + "|" + result.feedUrl
    }
    val notInterested: (DirectoryResult) -> Unit = { result ->
        menuFor = null
        onNotInterested(result)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomInset + 24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(key = "header") {
            Row(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 44.dp, bottom = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Column(Modifier.weight(1f)) {
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
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = shelves != null) {
                            haptics.play(Haptic.Select)
                            round += 1
                            scope.launch { listState.animateScrollToItem(0) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (shelves == null) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "New recommendations",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        if (visible == null) {
            item(key = "skeleton") { DiscoverSkeleton() }
        } else {
            val picks = visible.flatMap { it.items.take(2) }.distinctBy { it.feedUrl }.take(6)
            // The picks aren't repeated in the shelves beneath them.
            val pickUrls = picks.map { it.feedUrl }.toSet()
            if (picks.isNotEmpty()) {
                item(key = "picks-title") { ShelfTitle("Top picks for you", "The best of your strongest genres") }
                item(key = "picks") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(picks, key = { "pick:" + it.feedUrl }) { result ->
                            PickCard(
                                result = result,
                                busy = previewingUrl == result.feedUrl,
                                menuOpen = menuFor == "pick|" + result.feedUrl,
                                onClick = { onPreview(result) },
                                onLongClick = { longPress("pick", result) },
                                onDismissMenu = { menuFor = null },
                                onNotInterested = { notInterested(result) }
                            )
                        }
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            visible.map { it.copy(items = it.items.filterNot { r -> r.feedUrl in pickUrls }) }
                .filter { it.items.isNotEmpty() }
                .forEach { shelf ->
                    item(key = "title:" + shelf.title) { ShelfTitle(shelf.title, shelf.subtitle) }
                    item(key = "row:" + shelf.title) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(shelf.items, key = { shelf.title + it.feedUrl }) { result ->
                                ShowCard(
                                    result = result,
                                    busy = previewingUrl == result.feedUrl,
                                    menuOpen = menuFor == shelf.title + "|" + result.feedUrl,
                                    onClick = { onPreview(result) },
                                    onLongClick = { longPress(shelf.title, result) },
                                    onDismissMenu = { menuFor = null },
                                    onNotInterested = { notInterested(result) }
                                )
                            }
                        }
                        Spacer(Modifier.height(26.dp))
                    }
                }
        }
    }
}

/**
 * Built shelves, kept for the life of the process. Leaving the tab disposes
 * the screen; without this, every return refetched every chart and showed the
 * skeleton again — at whatever scroll position the list had been left.
 */
private object DiscoverCache {
    private var key: String? = null
    private var shelves: List<Shelf>? = null
    fun shelvesFor(k: String): List<Shelf>? = if (k == key) shelves else null
    fun put(k: String, value: List<Shelf>) {
        key = k
        shelves = value
    }
}

/** Shelves from the library's genres. Shared by the phone and TV Discover screens. */
internal suspend fun buildShelves(feeds: List<Feed>, round: Int = 0): List<Shelf> = coroutineScope {
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

    val ranked = scores.entries.sortedByDescending { it.value }.map { it.key }
    // Round 0 is your three strongest genres. Each refresh rotates in one more
    // from further down the list, so a refresh brings a genuinely new shelf.
    val wildcard = if (round > 0 && ranked.size > 3) ranked[3 + (round - 1) % (ranked.size - 3)] else null
    val topGenres = (ranked.take(3) + listOfNotNull(wildcard))
        .ifEmpty { listOf(1303, 1489) } // nothing known yet: two broad starting points

    // Round 0 is the charts' own order. Later rounds dig deeper — the top 50,
    // shuffled with the round as the seed — so a refresh isn't the same list.
    fun List<DirectoryResult>.dealt(seed: Int): List<DirectoryResult> =
        if (round == 0) take(15) else shuffled(kotlin.random.Random(round * 7919 + seed)).take(15)

    val genreShelves = topGenres.map { id ->
        async {
            val label = DiscoverGenres.firstOrNull { it.first == id }?.second ?: "your genres"
            val because = feeds.firstOrNull { f -> f.categories.any { genreFor(it) == id } }?.title
            Shelf(
                title = "More in $label",
                subtitle = because?.let { "Because you follow $it" } ?: "Popular right now",
                items = runCatching { ITunesDirectory.top(id, if (round == 0) 30 else 50) }
                    .getOrDefault(emptyList()).fresh().dealt(id)
            )
        }
    }
    val top = async {
        Shelf(
            title = "Top podcasts",
            subtitle = "What everyone's listening to",
            items = runCatching { ITunesDirectory.top(0, if (round == 0) 30 else 50) }
                .getOrDefault(emptyList()).fresh().dealt(0)
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
/** The large carousel card: cover full-bleed, name set over a darkening foot. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PickCard(
    result: DirectoryResult,
    busy: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onNotInterested: () -> Unit
) {
    Box {
        Box(
            Modifier
                .width(260.dp)
                .height(330.dp)
                .clip(RoundedCornerShape(26.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        ) {
            Artwork(
                url = result.artworkUrl,
                sizeDp = 330.dp,
                corner = 0.dp,
                modifier = Modifier.fillMaxSize()
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
        NotInterestedMenu(menuOpen, onDismissMenu, onNotInterested)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ShowCard(
    result: DirectoryResult,
    busy: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDismissMenu: () -> Unit,
    onNotInterested: () -> Unit
) {
    // No rounded clip on the card. It was clipped to a 16dp rounded rectangle
    // for the ripple, and the bottom-left curve sliced the first letter off
    // whatever line sat in that corner — "NPR", "New York Times". The cover
    // rounds its own corners; the card answers a press by squashing slightly.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val squash by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "cardPress"
    )
    Box {
        Column(
            Modifier
                .width(148.dp)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                }
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            Artwork(
                url = result.artworkUrl,
                sizeDp = 148.dp,
                corner = 16.dp
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
        NotInterestedMenu(menuOpen, onDismissMenu, onNotInterested)
    }
}

@Composable
private fun NotInterestedMenu(open: Boolean, onDismiss: () -> Unit, onNotInterested: () -> Unit) {
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text("Not interested") },
            leadingIcon = { Icon(Icons.Outlined.ThumbDown, contentDescription = null) },
            onClick = onNotInterested
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
