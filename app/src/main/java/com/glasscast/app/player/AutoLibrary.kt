package com.glasscast.app.player

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.annotation.DrawableRes
import androidx.core.content.IntentCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.glasscast.app.GlassCastApp
import com.glasscast.app.R
import com.glasscast.app.data.DownloadState
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.formatCompact
import com.glasscast.app.ui.formatDate
import com.glasscast.app.ui.historyDay
import com.glasscast.app.ui.tr
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The library as Android Auto (and any media browser) sees it:
 *
 *     GlassCast
 *     ├── Home         Continue Listening, then Up Next, under their own headings
 *     ├── Library      History, then the shows as a grid → each show's episodes
 *     ├── Latest       the newest episodes across every show, by day
 *     └── Downloads    episodes saved on the phone
 *
 * Four tabs, because that's what Auto shows (the root hints say how many;
 * past the limit the rest are dropped). Before 2.0 there were five once
 * Continue Listening was added, and Library could go missing.
 *
 * Episodes carry Auto's played marks, a progress bar when they're part way
 * through, and a badge when they're downloaded. Long-pressing one (or its ⋮
 * on newer cars) offers Add to / Remove from Up Next and Mark as played /
 * unplayed: custom browse actions, Media3's command buttons for media items.
 * Open lists follow the app as it changes (a download finishing, the queue
 * moving on, a refresh), so the car never shows a stale Up Next.
 *
 * Search is supported, and so is "play *show* on GlassCast": the Assistant
 * sends a bare search query, resolved to that show's newest unplayed episode.
 *
 * Artwork goes through [ArtworkProvider] as content:// addresses — Auto won't
 * load images from the web. Tab and action icons are the app's own vector
 * drawables, as android.resource:// addresses.
 *
 * Playing from here behaves like the app's own play(): the episode starts
 * where it was left, and Up Next is kept behind it. The app's own player
 * connection sends complete items (with an address), which pass straight
 * through; only bare ids and queries are resolved here.
 *
 * Every answer waits for the library to load from disk first: Auto can
 * connect in a car before the app has been opened since the phone started.
 *
 * It's also the session's callback for two things outside the library: the
 * car's back and forward buttons ([CarButtons]), and headset buttons.
 */
internal class AutoLibrary(
    private val context: Context,
    private val scope: CoroutineScope,
    private val carButtons: CarButtons
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

    // --------------------------------------------------------------- connect

    override fun onConnect(
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): MediaSession.ConnectionResult {
        if (session.isAutoCompanionController(controller) || session.isAutomotiveController(controller)) {
            carButtons.carConnected()
            (session as? MediaLibrarySession)?.let { library -> scope.launch { follow(library) } }
        }
        // Every controller may use the skip buttons, the media notification's
        // included: the system's copy of the session only lists buttons that
        // controller is allowed. The episode actions are offered to all too;
        // only a browser that shows them (the car) ever sends them.
        val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
            .add(CarButtons.SKIP_BACK)
            .add(CarButtons.SKIP_FORWARD)
            .add(CarButtons.SPEED)
            .apply { ITEM_ACTIONS.forEach { add(SessionCommand(it, Bundle.EMPTY)) } }
            .build()
        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(commands)
            .build()
    }

    override fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customCommand: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        val itemId = args.getString(MediaConstants.EXTRA_KEY_MEDIA_ID)
        if (itemId != null && customCommand.customAction in ITEM_ACTIONS) {
            return later { onItemAction(session, customCommand.customAction, itemId) }
        }
        when (customCommand.customAction) {
            CarButtons.SKIP_BACK.customAction -> session.player.seekBack()
            CarButtons.SKIP_FORWARD.customAction -> session.player.seekForward()
            CarButtons.SPEED.customAction -> {
                val p = session.player
                p.setPlaybackSpeed(CarButtons.nextSpeed(p.playbackParameters.speed))
            }
            else -> return Futures.immediateFuture(SessionResult(SessionError.ERROR_NOT_SUPPORTED))
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
    }

    /**
     * Headset buttons. Most headphones send next and previous (a double and
     * triple tap, or a long press), which in a podcast jump to another
     * episode. With the setting on (the default) they skip back and forward
     * instead, by the skip setting (see SkipPlayer). A one-button wired
     * headset's double click is still next: Media3 turns that into next
     * after this callback.
     *
     * The notification's own buttons arrive here too on older Android, as the
     * media notification controller; those keep their meaning.
     */
    override fun onMediaButtonEvent(
        session: MediaSession,
        controllerInfo: MediaSession.ControllerInfo,
        intent: Intent
    ): Boolean {
        if (!app.settings.headphoneSkip.value) return false
        if (session.isMediaNotificationController(controllerInfo)) return false
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            ?: return false
        if (event.action != KeyEvent.ACTION_DOWN) return false
        return when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                session.player.seekForward()
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                session.player.seekBack()
                true
            }
            else -> false
        }
    }

    // ---------------------------------------------------------------- browse

    /** How many tabs each browser can show, from its root hints. */
    private val rootLimits = HashMap<MediaSession.ControllerInfo, Int>()

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        val limit = params?.extras?.getInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, TABS) ?: TABS
        rootLimits[browser] = if (limit > 0) limit else TABS
        val hints = Bundle().apply {
            putBoolean(CONTENT_STYLE_SUPPORTED, true)
            putBoolean(SEARCH_SUPPORTED, true)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        val root = folder(ROOT, "GlassCast")
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
        LibraryResult.ofItemList(pageOf(childrenOf(parentId, session, browser), page, pageSize), params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> = later {
        val item = when {
            mediaId == ROOT -> folder(ROOT, "GlassCast")
            mediaId in FOLDERS -> folderFor(mediaId)
            mediaId.startsWith(SHOW_PREFIX) ->
                store.feeds.value.firstOrNull { it.url == mediaId.removePrefix(SHOW_PREFIX) }?.let { showItem(it) }
            else -> store.episodeByGuid(mediaId)?.let { episodeItem(it, store.feedFor(it), Queue.of(session.player)) }
        }
        if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> = later {
        session.notifySearchResultChanged(browser, query, searchResults(query, Queue.of(session.player)).size, params)
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
        LibraryResult.ofItemList(pageOf(searchResults(query, Queue.of(session.player)), page, pageSize), params)
    }

    /** Shows whose name matches first, then episodes, each under its heading. */
    private fun searchResults(query: String, queue: Queue): List<MediaItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        val shows = store.feeds.value
            .filter { it.title.isNotBlank() && it.title.lowercase().contains(q) }
            .take(10)
            .map { showItem(it, group = tr("Podcasts")) }
        val episodes = matches(query).map {
            episodeItem(it, store.feedFor(it), queue, group = tr("Episodes"))
        }
        return shows + episodes
    }

    private fun childrenOf(
        parentId: String,
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo
    ): List<MediaItem> {
        val queue = Queue.of(session.player)
        return when {
            parentId == ROOT -> tabs().take(rootLimits[browser] ?: TABS)
            parentId == HOME -> {
                val continuing = tr("Continue Listening")
                val next = tr("Up Next")
                continueListening(queue).take(HOME_CONTINUE).map { episodeItem(it, store.feedFor(it), queue, group = continuing) } +
                    upNext(session.player).map { episodeItem(it, store.feedFor(it), queue, group = next) }
            }
            parentId == CONTINUE -> continueListening(queue).map { episodeItem(it, store.feedFor(it), queue) }
            parentId == UP_NEXT -> upNext(session.player).map { episodeItem(it, store.feedFor(it), queue) }
            parentId == LATEST -> latest().map { episodeItem(it, store.feedFor(it), queue, group = historyDay(it.pubDate)) }
            parentId == DOWNLOADS -> downloaded().map { episodeItem(it, store.feedFor(it), queue) }
            parentId == HISTORY -> history().map { (episode, at) ->
                episodeItem(episode, store.feedFor(episode), queue, group = historyDay(at))
            }
            parentId == SHOWS -> listOfNotNull(
                folderFor(HISTORY).takeIf { app.history.entries.value.isNotEmpty() }
            ) + shows().map { showItem(it) }
            parentId.startsWith(SHOW_PREFIX) -> {
                val feed = store.feeds.value.firstOrNull { it.url == parentId.removePrefix(SHOW_PREFIX) }
                showEpisodes(parentId.removePrefix(SHOW_PREFIX)).map { episodeItem(it, feed, queue, inShow = true) }
            }
            else -> emptyList()
        }
    }

    private fun tabs(): List<MediaItem> = listOf(HOME, SHOWS, LATEST, DOWNLOADS).map(::folderFor)

    private fun folderFor(id: String): MediaItem = when (id) {
        HOME -> folder(HOME, tr("Home"), icon = R.drawable.car_tab_home)
        SHOWS -> folder(
            SHOWS, tr("Library"), icon = R.drawable.car_tab_library,
            extras = Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM)
            }
        )
        LATEST -> folder(LATEST, tr("Latest"), icon = R.drawable.car_tab_latest)
        DOWNLOADS -> folder(DOWNLOADS, tr("Downloads"), icon = R.drawable.car_tab_downloads)
        // A row above the Library's grid, as the History button sits above it on the phone.
        HISTORY -> folder(
            HISTORY, tr("History"), icon = R.drawable.car_history,
            extras = Bundle().apply {
                putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
            }
        )
        CONTINUE -> folder(CONTINUE, tr("Continue Listening"))
        else -> folder(UP_NEXT, tr("Up Next"))
    }

    // What each list holds. Shared with [follow], which watches them change.

    /**
     * Episodes started and not finished, the most recently played first:
     * what you were in the middle of. The one loaded in the player isn't
     * repeated here; it's on the playing bar already, as on the phone.
     */
    private fun continueListening(queue: Queue): List<Episode> =
        store.inProgress(CONTINUE_LIMIT + 1).filter { it.guid != queue.loaded }.take(CONTINUE_LIMIT)

    private fun upNext(player: Player): List<Episode> {
        val current = player.currentMediaItemIndex
        return (0 until player.mediaItemCount)
            .filter { it > current }
            .mapNotNull { store.episodeByGuid(player.getMediaItemAt(it).mediaId) }
            .take(UP_NEXT_LIMIT)
    }

    private fun latest(): List<Episode> = store.episodes.value.values.asSequence().flatten()
        .sortedByDescending { it.pubDate }
        .take(LATEST_LIMIT)
        .toList()

    private fun downloaded(): List<Episode> = app.downloads.entries.value.values
        .filter { it.state == DownloadState.DONE }
        .sortedByDescending { it.addedAt }
        .mapNotNull { store.episodeByGuid(it.guid) }

    private fun history(): List<Pair<Episode, Long>> = app.history.entries.value
        .asSequence()
        .mapNotNull { entry -> store.episodeByGuid(entry.guid)?.let { it to entry.at } }
        .take(HISTORY_LIMIT)
        .toList()

    private fun shows(): List<Feed> = store.feeds.value.sortedByDescending { store.latestEpisodeAt(it) }

    private fun showEpisodes(feedUrl: String): List<Episode> =
        store.episodesFor(feedUrl).sortedByDescending { it.pubDate }.take(SHOW_LIMIT)

    // ----------------------------------------------------------- live lists

    private var following: Job? = null

    /**
     * While the car is connected, the lists it has open follow the app: when
     * what a list would show changes, the car is told to load it again.
     * Each list is boiled down to what the car draws (which episodes, in what
     * order, played or not, roughly how far in, downloaded or not), so a
     * position saved every few seconds doesn't reload anything until the
     * progress bar would move.
     *
     * The queue isn't a flow; the service calls [queueChanged] for it.
     */
    private suspend fun follow(session: MediaLibrarySession) {
        if (following?.isActive == true) return
        store.awaitLoaded()
        if (following?.isActive == true) return
        loaded = session.player.currentMediaItem?.mediaId
        following = scope.launch {
            watch(store.episodes, listOf(HOME, CONTINUE), session) {
                store.inProgress(CONTINUE_LIMIT + 1).filter { it.guid != loaded }.take(CONTINUE_LIMIT).map(::sketch)
            }
            watch(store.episodes, listOf(LATEST), session) { latest().map(::sketch) }
            watch(app.downloads.entries, listOf(DOWNLOADS), session) { downloaded().map(::sketch) }
            watch(app.history.entries, listOf(HISTORY), session) {
                app.history.entries.value.take(HISTORY_LIMIT).map { it.guid }
            }
            watch(store.feeds, listOf(SHOWS), session) {
                listOf(app.history.entries.value.isEmpty()) + shows().map { it.url }
            }
        }
    }

    private fun CoroutineScope.watch(
        source: Flow<*>,
        ids: List<String>,
        session: MediaLibrarySession,
        sketchOf: () -> List<Any>
    ) = launch {
        source
            .map { sketchOf() }
            .flowOn(Dispatchers.Default)
            .distinctUntilChanged()
            .drop(1)
            .conflate()
            .collect { sketch ->
                ids.forEach { session.notifyChildrenChanged(it, sketch.size, null) }
                delay(1_000)
            }
    }

    /** What the car draws of an episode: changes when the row would. */
    private fun sketch(episode: Episode): Any = listOf(
        episode.guid,
        episode.effectivelyPlayed,
        (episode.progress * 20).toInt(),
        app.downloads.entries.value[episode.guid]?.state == DownloadState.DONE
    )

    /** The car has gone (the last media browser unbound). */
    fun carGone() {
        following?.cancel()
        following = null
        rootLimits.clear()
    }

    /** The episode loaded in the player, for the watcher off the main thread. */
    @Volatile
    private var loaded: String? = null
    private var queueNotice: Job? = null

    /**
     * The player's queue changed: Home and Up Next show it. Several changes
     * at once (an episode ending and the next starting) make one reload.
     */
    fun queueChanged(session: MediaLibrarySession) {
        loaded = session.player.currentMediaItem?.mediaId
        if (following?.isActive != true) return
        queueNotice?.cancel()
        queueNotice = scope.launch {
            delay(400)
            listOf(HOME, UP_NEXT, CONTINUE).forEach { session.notifyChildrenChanged(it, 0, null) }
        }
    }

    // --------------------------------------------------------- item actions

    private fun onItemAction(session: MediaSession, action: String, mediaId: String): SessionResult {
        val episode = store.episodeByGuid(mediaId) ?: return SessionResult(SessionError.ERROR_BAD_VALUE)
        val player = session.player
        val message = when (action) {
            QUEUE_ADD -> {
                addToUpNext(player, episode)
                tr("Added to Up Next")
            }
            QUEUE_REMOVE -> {
                val current = player.currentMediaItemIndex
                (0 until player.mediaItemCount)
                    .firstOrNull { it > current && player.getMediaItemAt(it).mediaId == mediaId }
                    ?.let { player.removeMediaItem(it) }
                tr("Removed from Up Next")
            }
            MARK_PLAYED -> {
                store.setPlayed(episode, true)
                tr("Marked as played")
            }
            MARK_UNPLAYED -> {
                store.setPlayed(episode, false)
                tr("Marked as unplayed")
            }
            else -> return SessionResult(SessionError.ERROR_NOT_SUPPORTED)
        }
        // The row's actions flip (add becomes remove, played becomes
        // unplayed), so every list it might be in is loaded again.
        (session as? MediaLibrarySession)?.let { library ->
            (FOLDERS + (SHOW_PREFIX + episode.feedUrl)).forEach { library.notifyChildrenChanged(it, 0, null) }
        }
        return SessionResult(
            SessionResult.RESULT_SUCCESS,
            Bundle().apply { putString(RESULT_MESSAGE, message) }
        )
    }

    /** As the app's own Add to Up Next: at the end, or playing if nothing is loaded. */
    private fun addToUpNext(player: Player, episode: Episode) {
        val item = EpisodeItems.playable(episode, store.feedFor(episode), app.downloads)
        if (player.mediaItemCount == 0) {
            store.markShowPlayed(episode.feedUrl)
            player.setMediaItems(listOf(item), 0, if (episode.effectivelyPlayed) 0L else episode.positionMs)
            player.prepare()
            player.play()
            return
        }
        if ((0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == episode.guid }) return
        player.addMediaItem(item)
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
    }

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

    /** Which episode is loaded and which are queued after it, read once per list. */
    private class Queue(val loaded: String?, val queued: Set<String>) {
        companion object {
            fun of(player: Player): Queue {
                val current = player.currentMediaItemIndex
                val queued = (0 until player.mediaItemCount)
                    .filter { it > current }
                    .map { player.getMediaItemAt(it).mediaId }
                    .toSet()
                return Queue(player.currentMediaItem?.mediaId, queued)
            }
        }
    }

    private fun folder(
        id: String,
        title: String,
        @DrawableRes icon: Int = 0,
        extras: Bundle? = null
    ): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
                    .apply {
                        if (icon != 0) setArtworkUri(resourceUri(context, icon))
                        if (extras != null) setExtras(extras)
                    }
                    .build()
            )
            .build()

    private fun showItem(feed: Feed, group: String? = null): MediaItem =
        MediaItem.Builder()
            .setMediaId(SHOW_PREFIX + feed.url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(feed.title)
                    .setArtist(feed.author)
                    .setSubtitle(feed.author)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST)
                    .setArtworkUri(ArtworkProvider.uriFor(context, feed.imageUrl))
                    .apply {
                        if (group != null) {
                            setExtras(Bundle().apply { putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group) })
                        }
                    }
                    .build()
            )
            .build()

    /**
     * An episode row. The second line is the show and, part way through, the
     * time left ("The Daily · 23m left"); inside a show, where the show goes
     * without saying, it's the date and the length instead.
     */
    private fun episodeItem(
        episode: Episode,
        feed: Feed?,
        queue: Queue,
        group: String? = null,
        inShow: Boolean = false
    ): MediaItem {
        val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
        val partial = !episode.effectivelyPlayed && episode.positionMs > 1_000 && episode.durationMs > 0
        val status = when {
            episode.effectivelyPlayed -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_FULLY_PLAYED
            partial -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED
            else -> MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED
        }
        val left = if (partial) tr("{0} left", formatCompact((episode.durationMs - episode.positionMs).coerceAtLeast(0L))) else ""
        val subtitle = if (inShow) {
            line(formatDate(episode.pubDate), left.ifEmpty { formatCompact(episode.durationMs) })
        } else {
            line(feed?.title.orEmpty(), left)
        }
        val downloaded = app.downloads.entries.value[episode.guid]?.state == DownloadState.DONE
        val extras = Bundle().apply {
            putInt(MediaConstants.EXTRAS_KEY_COMPLETION_STATUS, status)
            if (partial) putDouble(MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE, episode.progress.toDouble())
            if (downloaded) putLong(MediaConstants.EXTRAS_KEY_DOWNLOAD_STATUS, DOWNLOADED)
            if (group != null) putString(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_GROUP_TITLE, group)
        }
        val actions = buildList {
            // The loaded episode can't be queued behind itself.
            if (episode.guid != queue.loaded) add(if (episode.guid in queue.queued) QUEUE_REMOVE else QUEUE_ADD)
            add(if (episode.effectivelyPlayed) MARK_UNPLAYED else MARK_PLAYED)
        }
        return MediaItem.Builder()
            .setMediaId(episode.guid)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setArtist(feed?.title.orEmpty())
                    .setSubtitle(subtitle)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setArtworkUri(ArtworkProvider.uriFor(context, art))
                    .setSupportedCommands(actions)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun line(vararg parts: String): String = parts.filter { it.isNotBlank() }.joinToString(" · ")

    private fun pageOf(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        val from = (page.toLong() * pageSize).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        return items.drop(from).take(pageSize.coerceAtLeast(1))
    }

    companion object {
        private const val ROOT = "glasscast:root"
        private const val HOME = "glasscast:home"
        private const val UP_NEXT = "glasscast:upnext"
        private const val CONTINUE = "glasscast:continue"
        private const val LATEST = "glasscast:latest"
        private const val DOWNLOADS = "glasscast:downloads"
        private const val SHOWS = "glasscast:shows"
        private const val HISTORY = "glasscast:history"
        private const val SHOW_PREFIX = "glasscast:show:"

        /** Every list besides a show's. Up Next and Continue Listening stay reachable on their own for older cars that remembered them. */
        private val FOLDERS = setOf(HOME, CONTINUE, UP_NEXT, LATEST, DOWNLOADS, SHOWS, HISTORY)

        /** Tabs Auto shows when its hints don't say. */
        private const val TABS = 4

        private const val HOME_CONTINUE = 10
        private const val CONTINUE_LIMIT = 30
        private const val UP_NEXT_LIMIT = 50
        private const val LATEST_LIMIT = 40
        private const val HISTORY_LIMIT = 100
        private const val SHOW_LIMIT = 100

        // The episode actions (custom browse actions).
        private const val QUEUE_ADD = "com.glasscast.app.QUEUE_ADD"
        private const val QUEUE_REMOVE = "com.glasscast.app.QUEUE_REMOVE"
        private const val MARK_PLAYED = "com.glasscast.app.MARK_PLAYED"
        private const val MARK_UNPLAYED = "com.glasscast.app.MARK_UNPLAYED"
        private val ITEM_ACTIONS = setOf(QUEUE_ADD, QUEUE_REMOVE, MARK_PLAYED, MARK_UNPLAYED)

        /**
         * The episode actions as the session declares them, built once with
         * the service. Each row names the ones that apply to it.
         */
        fun itemButtons(context: Context): List<CommandButton> = listOf(
            button(QUEUE_ADD, CommandButton.ICON_PLAYLIST_ADD, R.drawable.car_queue_add, tr("Add to Up Next"), context),
            button(QUEUE_REMOVE, CommandButton.ICON_PLAYLIST_REMOVE, R.drawable.car_queue_remove, tr("Remove from Up Next"), context),
            button(MARK_PLAYED, CommandButton.ICON_CHECK_CIRCLE_FILLED, R.drawable.car_played, tr("Mark as played"), context),
            button(MARK_UNPLAYED, CommandButton.ICON_CHECK_CIRCLE_UNFILLED, R.drawable.car_unplayed, tr("Mark as unplayed"), context)
        )

        private fun button(action: String, icon: Int, @DrawableRes drawable: Int, name: String, context: Context): CommandButton =
            CommandButton.Builder(icon)
                .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
                .setIconUri(resourceUri(context, drawable))
                .setDisplayName(name)
                .build()

        /** android.resource://package/type/name — the form Android Auto's own test app uses. */
        private fun resourceUri(context: Context, @DrawableRes id: Int): Uri {
            val res = context.resources
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(res.getResourcePackageName(id))
                .appendPath(res.getResourceTypeName(id))
                .appendPath(res.getResourceEntryName(id))
                .build()
        }

        // Browse hints and result keys as plain strings: some aren't in
        // Media3's constants, and the others have moved between versions.
        private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
        private const val SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"
        private const val RESULT_MESSAGE = "androidx.media.utils.extras.KEY_CUSTOM_BROWSER_ACTION_RESULT_MESSAGE"

        /** MediaDescriptionCompat.STATUS_DOWNLOADED. */
        private const val DOWNLOADED = 2L
    }
}
