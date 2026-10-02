package com.glasscast.app.player

import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import com.glasscast.app.GlassCastApp
import com.glasscast.app.data.DownloadState
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.tr
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The library as Android Auto (and any media browser) sees it:
 *
 *     GlassCast
 *     ├── Up Next      what's queued in the player right now
 *     ├── Latest       the newest episodes across every show
 *     ├── Downloads    episodes saved on the phone
 *     └── Library      the shows, as a grid → each show's episodes
 *
 * Episodes carry Auto's played / in-progress marks. Search is supported, and
 * so is "play *show* on GlassCast": the Assistant sends a bare search query,
 * resolved to that show's newest unplayed episode.
 *
 * Artwork goes through [ArtworkProvider] as content:// addresses — Auto won't
 * load images from the web.
 *
 * Playing from here behaves like the app's own play(): the episode starts
 * where it was left, and Up Next is kept behind it. The app's own player
 * connection sends complete items (with an address), which pass straight
 * through; only bare ids and queries are resolved here.
 *
 * Every answer waits for the library to load from disk first: Auto can
 * connect in a car before the app has been opened since the phone started.
 */
internal class AutoLibrary(
    private val context: Context,
    private val scope: CoroutineScope
) : MediaLibrarySession.Callback {

    private val app get() = context.applicationContext as GlassCastApp
    private val store get() = app.feedStore

    private fun <T> later(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            runCatching {
                store.awaitLoaded()
                block()
            }.onSuccess { future.set(it) }.onFailure { future.setException(it) }
        }
        return future
    }

    // ---------------------------------------------------------------- browse

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val hints = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putBoolean(SEARCH_SUPPORTED, true)
            putInt(CONTENT_STYLE_BROWSABLE, STYLE_LIST)
            putInt(CONTENT_STYLE_PLAYABLE, STYLE_LIST)
        }
        val root = folder(ROOT, "GlassCast", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
        return Futures.immediateFuture(LibraryResult.ofItem(root, LibraryParams.Builder().setExtras(hints).build()))
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        LibraryResult.ofItemList(pageOf(childrenOf(parentId, session), page, pageSize), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = later {
        val item = when {
            mediaId == ROOT -> folder(ROOT, "GlassCast", MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            mediaId in FOLDERS -> topFolders().firstOrNull { it.mediaId == mediaId }
            mediaId.startsWith(SHOW_PREFIX) ->
                store.feeds.value.firstOrNull { it.url == mediaId.removePrefix(SHOW_PREFIX) }?.let(::showItem)
            else -> store.episodeByGuid(mediaId)?.let { episodeItem(it, store.feedFor(it)) }
        }
        if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = later {
        session.notifySearchResultChanged(browser, query, matches(query).size, params)
        LibraryResult.ofVoid()
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = later {
        val items = matches(query).map { episodeItem(it, store.feedFor(it)) }
        LibraryResult.ofItemList(pageOf(items, page, pageSize), params)
    }

    private fun childrenOf(parentId: String, session: MediaLibrarySession): List<MediaItem> = when {
        parentId == ROOT -> topFolders()
        parentId == UP_NEXT -> {
            val player = session.player
            val current = player.currentMediaItemIndex
            (0 until player.mediaItemCount)
                .filter { it > current }
                .mapNotNull { store.episodeByGuid(player.getMediaItemAt(it).mediaId) }
                .map { episodeItem(it, store.feedFor(it)) }
        }
        parentId == LATEST -> store.episodes.value.values.asSequence().flatten()
            .sortedByDescending { it.pubDate }
            .take(40)
            .map { episodeItem(it, store.feedFor(it)) }
            .toList()
        parentId == DOWNLOADS -> app.downloads.entries.value.values
            .filter { it.state == DownloadState.DONE }
            .sortedByDescending { it.addedAt }
            .mapNotNull { store.episodeByGuid(it.guid) }
            .map { episodeItem(it, store.feedFor(it)) }
        parentId == SHOWS -> store.feeds.value
            .sortedByDescending { store.latestEpisodeAt(it) }
            .map(::showItem)
        parentId.startsWith(SHOW_PREFIX) -> {
            val feed = store.feeds.value.firstOrNull { it.url == parentId.removePrefix(SHOW_PREFIX) }
            store.episodesFor(parentId.removePrefix(SHOW_PREFIX))
                .sortedByDescending { it.pubDate }
                .take(100)
                .map { episodeItem(it, feed) }
        }
        else -> emptyList()
    }

    private fun topFolders(): List<MediaItem> = listOf(
        folder(UP_NEXT, tr("Up Next"), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
        folder(LATEST, tr("Latest"), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
        folder(DOWNLOADS, tr("Downloads"), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS),
        folder(
            SHOWS, tr("Library"), MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS,
            Bundle().apply { putInt(CONTENT_STYLE_BROWSABLE, STYLE_GRID) }
        )
    )

    // ------------------------------------------------------------------ play

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>
    ): ListenableFuture<MutableList<MediaItem>> {
        if (mediaItems.all { it.localConfiguration != null }) return Futures.immediateFuture(mediaItems)
        return later { mediaItems.mapNotNull(::resolve).toMutableList() }
    }

    override fun onSetMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: MutableList<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        // The app's player connection sends complete items: untouched.
        if (mediaItems.all { it.localConfiguration != null }) {
            return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, startPositionMs))
        }
        return later {
            val requested = mediaItems.getOrNull(startIndex.coerceAtLeast(0)) ?: mediaItems.firstOrNull()
            val episode = when {
                requested == null -> resumeCandidate()
                requested.mediaId.isNotEmpty() -> store.episodeByGuid(requested.mediaId)
                else -> bestFor(requested.requestMetadata.searchQuery)
            } ?: throw IllegalArgumentException("Nothing matched")
            val player = mediaSession.player
            val current = player.currentMediaItemIndex
            val rest = (0 until player.mediaItemCount)
                .filter { it > current }
                .map { player.getMediaItemAt(it) }
                .filterNot { it.mediaId == episode.guid }
            store.markShowPlayed(episode.feedUrl)
            MediaSession.MediaItemsWithStartPosition(
                listOf(EpisodeItems.playable(episode, store.feedFor(episode), app.downloads)) + rest,
                0,
                if (episode.effectivelyPlayed) 0L else episode.positionMs
            )
        }
    }

    /** "Resume" from the car, the lock screen or a headset: the saved queue. */
    override fun onPlaybackResumption(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = later {
        val (guids, index) = app.queueStore.load()
        val saved = guids.mapNotNull { store.episodeByGuid(it) }
        val current = guids.getOrNull(index)?.let { store.episodeByGuid(it) }
            ?: resumeCandidate()
            ?: throw IllegalStateException("Nothing to resume")
        val after = saved.dropWhile { it.guid != current.guid }.drop(1)
        MediaSession.MediaItemsWithStartPosition(
            (listOf(current) + after).map { EpisodeItems.playable(it, store.feedFor(it), app.downloads) },
            0,
            if (current.effectivelyPlayed) 0L else current.positionMs
        )
    }

    private fun resolve(item: MediaItem): MediaItem? {
        if (item.localConfiguration != null) return item
        val episode = if (item.mediaId.isNotEmpty()) {
            store.episodeByGuid(item.mediaId)
        } else {
            bestFor(item.requestMetadata.searchQuery)
        }
        return episode?.let { EpisodeItems.playable(it, store.feedFor(it), app.downloads) }
    }

    /** What a spoken request means: a show's newest unplayed episode, else an episode by title. */
    private fun bestFor(query: CharSequence?): Episode? {
        val q = query?.toString()?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) return resumeCandidate()
        val feeds = store.feeds.value.filter { it.title.isNotBlank() }
        val show = feeds.firstOrNull { it.title.lowercase() == q }
            ?: feeds.firstOrNull { it.title.lowercase().contains(q) || q.contains(it.title.lowercase()) }
        if (show != null) {
            val episodes = store.episodesFor(show.url).sortedByDescending { it.pubDate }
            return episodes.firstOrNull { !it.effectivelyPlayed } ?: episodes.firstOrNull()
        }
        return matches(q).firstOrNull()
    }

    /** Whatever was last playing, or failing that the newest unplayed episode. */
    private fun resumeCandidate(): Episode? {
        val (guids, index) = app.queueStore.load()
        return guids.getOrNull(index)?.let { store.episodeByGuid(it) }
            ?: store.episodes.value.values.asSequence().flatten()
                .filter { !it.effectivelyPlayed }
                .maxByOrNull { it.pubDate }
    }

    private fun matches(query: String): List<Episode> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val shows = store.feeds.value.filter { it.title.isNotBlank() && it.title.lowercase().contains(q) }
        val fromShows = shows.flatMap { feed -> store.episodesFor(feed.url).sortedByDescending { it.pubDate }.take(5) }
        val byTitle = store.episodes.value.values.asSequence().flatten()
            .filter { it.title.lowercase().contains(q) }
            .sortedByDescending { it.pubDate }
            .take(25)
            .toList()
        return (fromShows + byTitle).distinctBy { it.guid }.take(40)
    }

    // ----------------------------------------------------------------- items

    private fun folder(id: String, title: String, type: Int, extras: Bundle? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(type)
                    .apply { if (extras != null) setExtras(extras) }
                    .build()
            )
            .build()

    private fun showItem(feed: Feed): MediaItem =
        MediaItem.Builder()
            .setMediaId(SHOW_PREFIX + feed.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(feed.title)
                    .setArtist(feed.author)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                    .setArtworkUri(ArtworkProvider.uriFor(context, feed.imageUrl))
                    .build()
            )
            .build()

    private fun episodeItem(episode: Episode, feed: Feed?): MediaItem {
        val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
        val status = when {
            episode.effectivelyPlayed -> STATUS_PLAYED
            episode.positionMs > 1_000 -> STATUS_PARTIAL
            else -> STATUS_NOT_PLAYED
        }
        return MediaItem.Builder()
            .setMediaId(episode.guid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(feed?.title.orEmpty())
                    .setSubtitle(feed?.title.orEmpty())
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setArtworkUri(ArtworkProvider.uriFor(context, art))
                    .setExtras(Bundle().apply { putInt(PLAYBACK_STATUS, status) })
                    .build()
            )
            .build()
    }

    private fun pageOf(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        val from = (page.toLong() * pageSize).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return items.drop(from).take(pageSize.coerceAtLeast(1))
    }

    private companion object {
        const val ROOT = "glasscast:root"
        const val UP_NEXT = "glasscast:upnext"
        const val LATEST = "glasscast:latest"
        const val DOWNLOADS = "glasscast:downloads"
        const val SHOWS = "glasscast:shows"
        const val SHOW_PREFIX = "glasscast:show:"
        val FOLDERS = setOf(UP_NEXT, LATEST, DOWNLOADS, SHOWS)

        // Android Auto's browse hints and completion marks. Plain keys rather
        // than library constants, which have moved between Media3 versions.
        const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        const val CONTENT_STYLE_BROWSABLE = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        const val CONTENT_STYLE_PLAYABLE = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        const val STYLE_LIST = 1
        const val STYLE_GRID = 2
        const val PLAYBACK_STATUS = "android.media.extra.PLAYBACK_STATUS"
        const val STATUS_NOT_PLAYED = 0
        const val STATUS_PARTIAL = 1
        const val STATUS_PLAYED = 2
    }
}
