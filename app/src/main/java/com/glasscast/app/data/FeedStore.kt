package com.glasscast.app.data

import com.glasscast.app.ui.tr
import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Subscriptions and episodes.
 *
 * **Storage: one file per show, written only when that show changes.**
 *
 * This used to be a single JSON blob in SharedPreferences, and it crashed the
 * app out of memory. Three things compounded:
 *
 *  - every save serialized the *whole library* — every episode's full HTML
 *    show notes — into one string of tens of megabytes;
 *  - SharedPreferences keeps its entire contents in memory for the life of the
 *    process, so a second full copy sat on the heap permanently;
 *  - position saves fire every few seconds during playback, each launched
 *    separately, so two or three of those giant strings could be building at
 *    once. A full refresh saved the whole library once per show.
 *
 * Now each show's episodes live in their own file, plus one small file listing
 * the shows. A change marks only what it touched as dirty; a burst of changes
 * is coalesced into one write about a second later, under a lock, so writes
 * never overlap. Files are streamed out an episode at a time — no giant string
 * is ever built — and written through AtomicFile, so a process killed mid-save
 * leaves the previous copy intact rather than a truncated one.
 *
 * The interface is unchanged: everything the UI needs is a StateFlow or a
 * suspend function, and nothing leaks the storage shape. Room remains the
 * eventual home; this fixes the crash without that rewrite.
 */
class FeedStore(context: Context) {

    private val prefs = context.getSharedPreferences("glasscast_feeds", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _feeds = MutableStateFlow<List<Feed>>(emptyList())
    val feeds: StateFlow<List<Feed>> = _feeds.asStateFlow()

    /** Keyed by feed URL. */
    private val _episodes = MutableStateFlow<Map<String, List<Episode>>>(emptyMap())
    val episodes: StateFlow<Map<String, List<Episode>>> = _episodes.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val loaded = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val libraryDir = File(context.filesDir, "library").apply { mkdirs() }
    private val episodeDir = File(libraryDir, "episodes").apply { mkdirs() }
    private val feedsFile = AtomicFile(File(libraryDir, "feeds.json"))

    private val writeLock = Mutex()
    private val dirtyEpisodes: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val removedFeeds: MutableSet<String> = ConcurrentHashMap.newKeySet()
    @Volatile private var feedsDirty = false
    /** Conflated: any number of changes before the writer wakes become one write. */
    private val writeRequests = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            load()
            loaded.complete(Unit)
        }
        scope.launch {
            for (request in writeRequests) {
                delay(900)
                runCatching { writeDirty() }
            }
        }
    }

    private fun markFeedsDirty() {
        feedsDirty = true
        writeRequests.trySend(Unit)
    }

    private fun markEpisodesDirty(feedUrl: String) {
        dirtyEpisodes += feedUrl
        writeRequests.trySend(Unit)
    }

    private fun markRemoved(feedUrl: String) {
        dirtyEpisodes -= feedUrl
        removedFeeds += feedUrl
        markFeedsDirty()
    }

    /**
     * Suspends until the library has been read from disk.
     *
     * The load is asynchronous, and a background job can start the process
     * cold: without waiting, it would refresh an empty library, find nothing,
     * and notify about nothing.
     */
    suspend fun awaitLoaded() = loaded.await()

    /**
     * Write anything pending now, rather than after the coalescing delay.
     *
     * For the background job: its process can be killed the moment it reports
     * success, so it can't rely on a write scheduled for a second later.
     */
    suspend fun flushNow() = writeDirty()

    private suspend fun writeDirty() = writeLock.withLock {
        if (feedsDirty) {
            feedsDirty = false
            writeArray(feedsFile, _feeds.value) { it.toJson() }
        }
        val urls = dirtyEpisodes.toList()
        dirtyEpisodes.removeAll(urls.toSet())
        urls.forEach { url ->
            _episodes.value[url]?.let { list -> writeArray(episodesFile(url), list) { it.toJson() } }
        }
        val gone = removedFeeds.toList()
        removedFeeds.removeAll(gone.toSet())
        gone.forEach { url -> runCatching { episodesFile(url).delete() } }
    }

    /**
     * Streamed: one item's JSON at a time into a buffered writer. The largest
     * string ever built is a single episode, not the whole show.
     */
    private fun <T> writeArray(file: AtomicFile, items: List<T>, toJson: (T) -> JSONObject) {
        val out = runCatching { file.startWrite() }.getOrNull() ?: return
        try {
            val writer = OutputStreamWriter(out, Charsets.UTF_8).buffered()
            writer.write("[")
            items.forEachIndexed { i, item ->
                if (i > 0) writer.write(",")
                writer.write(toJson(item).toString())
            }
            writer.write("]")
            writer.flush()
            file.finishWrite(out)
        } catch (t: Throwable) {
            file.failWrite(out)
        }
    }

    private fun readArray(file: AtomicFile): JSONArray? =
        runCatching { JSONArray(String(file.readFully(), Charsets.UTF_8)) }.getOrNull()

    private fun episodesFile(feedUrl: String): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-1")
            .digest(feedUrl.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return AtomicFile(File(episodeDir, "$hash.json"))
    }

    private fun load() {
        migrateFromPreferences()

        val feedList = readArray(feedsFile)?.let { arr ->
            List(arr.length()) { Feed.fromJson(arr.getJSONObject(it)) }
        }.orEmpty()

        val map = HashMap<String, List<Episode>>(feedList.size)
        feedList.forEach { feed ->
            readArray(episodesFile(feed.url))?.let { arr ->
                map[feed.url] = List(arr.length()) { Episode.fromJson(arr.getJSONObject(it)) }
            }
        }
        _feeds.value = feedList
        _episodes.value = map
    }

    /**
     * One-time move from the old SharedPreferences blob to per-show files.
     *
     * Reads the blob one last time, writes it out show by show, then removes it
     * with commit() so the file shrinks and the in-memory copy can be collected.
     * If anything fails before the new files are written, the blob is left
     * alone and the move is tried again next launch.
     */
    private fun migrateFromPreferences() {
        if (!prefs.contains(KEY_FEEDS) && !prefs.contains(KEY_EPISODES)) return
        val feeds = runCatching { JSONArray(prefs.getString(KEY_FEEDS, "[]")) }.getOrNull() ?: return
        val episodes = runCatching { JSONObject(prefs.getString(KEY_EPISODES, "{}")) }.getOrNull() ?: return

        writeArray(feedsFile, List(feeds.length()) { feeds.getJSONObject(it) }) { it }
        for (url in episodes.keys()) {
            val arr = episodes.optJSONArray(url) ?: continue
            writeArray(episodesFile(url), List(arr.length()) { arr.getJSONObject(it) }) { it }
        }
        prefs.edit().remove(KEY_FEEDS).remove(KEY_EPISODES).commit()
    }

    fun episodesFor(feedUrl: String): List<Episode> = _episodes.value[feedUrl].orEmpty()

    fun episodeByGuid(guid: String): Episode? =
        _episodes.value.values.firstOrNull { list -> list.any { it.guid == guid } }
            ?.firstOrNull { it.guid == guid }

    fun feedFor(episode: Episode): Feed? = _feeds.value.firstOrNull { it.url == episode.feedUrl }

    /** Returns null on success, or a human-readable reason on failure. */
    /** Told of every follow (true) and unfollow (false) — gPodder sync's outbox. */
    var onSubscriptionChanged: ((feedUrl: String, added: Boolean) -> Unit)? = null
    /** Told when an episode is marked played or unplayed by hand. */
    var onMarkedPlayed: ((Episode, Boolean) -> Unit)? = null

    suspend fun subscribe(rawUrl: String): String? = withContext(Dispatchers.IO) {
        val url = normalize(rawUrl)
        if (_feeds.value.any { it.url.equals(url, ignoreCase = true) }) return@withContext tr("Already subscribed")
        _refreshing.value = true
        try {
            val result = RssParser.fetch(url) ?: return@withContext tr("Couldn't read that feed")
            if (result.episodes.isEmpty() && result.feed.title.isBlank()) return@withContext tr("No episodes found")
            _feeds.value = _feeds.value + result.feed
            _episodes.value = _episodes.value + (url to result.episodes)
            markFeedsDirty()
            markEpisodesDirty(url)
            onSubscriptionChanged?.invoke(url, true)
            null
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun unsubscribe(feed: Feed) = withContext(Dispatchers.IO) {
        _feeds.value = _feeds.value.filterNot { it.url == feed.url }
        _episodes.value = _episodes.value - feed.url
        markRemoved(feed.url)
        onSubscriptionChanged?.invoke(feed.url, false)
    }

    suspend fun refresh(feed: Feed) = withContext(Dispatchers.IO) {
        _refreshing.value = true
        try {
            val result = RssParser.fetch(feed.url, feed.etag, feed.lastModified) ?: return@withContext
            if (result.notModified) return@withContext
            merge(feed.url, result)
        } finally {
            _refreshing.value = false
        }
    }

    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        _refreshing.value = true
        try {
            _feeds.value.forEach { feed ->
                val result = RssParser.fetch(feed.url, feed.etag, feed.lastModified) ?: return@forEach
                if (!result.notModified) merge(feed.url, result)
            }
        } finally {
            _refreshing.value = false
        }
    }

    /**
     * Fetch, unconditionally, the feeds that have no categories yet.
     *
     * Needed once for libraries that predate category parsing. Refreshes are
     * conditional — an unchanged feed answers 304 and is never re-parsed — so
     * without this every existing show would stay category-less until it next
     * published, and Discover would have nothing to recommend from.
     */
    suspend fun backfillCategories() = withContext(Dispatchers.IO) {
        _feeds.value.filter { it.categories.isEmpty() }.forEach { feed ->
            val result = RssParser.fetch(feed.url) ?: return@forEach
            if (!result.notModified) merge(feed.url, result)
        }
    }

    /** New episodes get added; existing ones keep their position and played state. */
    private fun merge(feedUrl: String, result: FeedFetch) {
        val existing = _episodes.value[feedUrl].orEmpty().associateBy { it.guid }
        val merged = result.episodes.map { fresh ->
            val old = existing[fresh.guid]
            if (old == null) fresh
            else fresh.copy(
                positionMs = old.positionMs,
                played = old.played,
                // Trust a reconciled duration over the feed's claim (lesson 4).
                durationMs = if (old.durationMs > 0) old.durationMs else fresh.durationMs
            )
        }
        _episodes.value = _episodes.value + (feedUrl to merged)
        _feeds.value = _feeds.value.map {
            if (it.url == feedUrl) {
                result.feed.copy(addedAt = it.addedAt, lastPlayedAt = it.lastPlayedAt)
            } else {
                it
            }
        }
        markFeedsDirty()
        markEpisodesDirty(feedUrl)
    }

    fun updateEpisode(updated: Episode) {
        val list = _episodes.value[updated.feedUrl] ?: return
        _episodes.value = _episodes.value + (updated.feedUrl to list.map {
            if (it.guid == updated.guid) updated else it
        })
        // Only this show's file — a position save used to rewrite the library.
        markEpisodesDirty(updated.feedUrl)
    }

    fun savePosition(guid: String, positionMs: Long, durationMs: Long) {
        val ep = episodeByGuid(guid) ?: return
        val markPlayed = durationMs > 0 && positionMs.toFloat() / durationMs >= 0.95f
        updateEpisode(
            ep.copy(
                positionMs = positionMs,
                durationMs = if (durationMs > 0) durationMs else ep.durationMs,
                played = ep.played || markPlayed
            )
        )
    }

    /** Stamps the show so the recently-played sort has something to sort on. */
    fun markShowPlayed(feedUrl: String) {
        val now = System.currentTimeMillis()
        val feed = _feeds.value.firstOrNull { it.url == feedUrl } ?: return
        // A minute of granularity is plenty and keeps this off the hot path —
        // position saves fire every few seconds during playback.
        if (now - feed.lastPlayedAt < 60_000L) return
        _feeds.value = _feeds.value.map {
            if (it.url == feedUrl) it.copy(lastPlayedAt = now) else it
        }
        markFeedsDirty()
    }

    /** Newest episode date, for the recently-updated sort. Falls back to when it was added. */
    fun latestEpisodeAt(feed: Feed): Long =
        _episodes.value[feed.url]?.maxOfOrNull { it.pubDate }?.takeIf { it > 0 } ?: feed.addedAt

    /**
     * Fetches a feed without subscribing to it, so a show can be browsed from
     * search before committing to it.
     */
    suspend fun preview(rawUrl: String): FeedFetch? = withContext(Dispatchers.IO) {
        RssParser.fetch(normalize(rawUrl))
    }

    private val _importProgress = MutableStateFlow(ImportProgress())
    val importProgress: StateFlow<ImportProgress> = _importProgress.asStateFlow()

    /**
     * Subscribes to every feed in an OPML file, one at a time.
     *
     * Sequential on purpose. Firing a few hundred feed fetches in parallel from
     * a phone gets you rate-limited by the bigger hosts and starves the rest of
     * the app's network; a migration is a thing you do once and can wait thirty
     * seconds for. Progress is reported so the wait doesn't look like a hang.
     */
    suspend fun importOpml(entries: List<OpmlEntry>) = withContext(Dispatchers.IO) {
        _importProgress.value = ImportProgress(total = entries.size, running = true)
        entries.forEach { entry ->
            val already = _feeds.value.any { it.url.equals(entry.url, ignoreCase = true) }
            val reason = if (already) tr("Already subscribed") else subscribe(entry.url)
            _importProgress.value = _importProgress.value.let {
                it.copy(
                    done = it.done + 1,
                    added = it.added + if (reason == null) 1 else 0,
                    skipped = it.skipped + if (already) 1 else 0,
                    failed = it.failed + if (reason != null && !already) 1 else 0
                )
            }
        }
        _importProgress.value = _importProgress.value.copy(running = false)
    }

    fun clearImportProgress() {
        _importProgress.value = ImportProgress()
    }

    fun exportOpml(): String = Opml.build(_feeds.value)

    fun setPlayed(episode: Episode, played: Boolean) {
        updateEpisode(episode.copy(played = played, positionMs = if (played) 0L else episode.positionMs))
        onMarkedPlayed?.invoke(episode, played)
    }

    private fun normalize(raw: String): String {
        var u = raw.trim()
        if (u.startsWith("feed://", true)) u = "https://" + u.substring(7)
        if (u.startsWith("pcast://", true)) u = "https://" + u.substring(8)
        if (!u.startsWith("http://", true) && !u.startsWith("https://", true)) u = "https://$u"
        return u
    }

    private companion object {
        const val KEY_FEEDS = "feeds"
        const val KEY_EPISODES = "episodes"
    }
}
