package com.glasscast.app.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.QueueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The UI's view of the player. Wraps a MediaController talking to
 * [PlaybackService], and exposes plain StateFlows so composables never touch
 * Media3 directly.
 *
 * The queue is ExoPlayer's own playlist rather than a list kept alongside it —
 * a parallel list only gives you two things that can disagree about what is
 * playing. Every queue operation here is a playlist operation, and the guids
 * are mirrored to [QueueStore] so the queue survives the process.
 */
class PlayerConnection(
    private val context: Context,
    private val store: FeedStore,
    private val queueStore: QueueStore,
    private val downloads: com.glasscast.app.data.Downloads? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null

    private val _currentMediaId = MutableStateFlow<String?>(null)
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    /** Guids in playlist order, mirrored from the controller's timeline. */
    private val _queueIds = MutableStateFlow<List<String>>(emptyList())
    private val _queueIndex = MutableStateFlow(0)

    /**
     * Resolved against the store, so a title edited by a feed refresh shows the
     * new one and an episode whose show was unsubscribed simply drops out.
     */
    val queue: StateFlow<List<Episode>> =
        combine(_queueIds, store.episodes) { ids, _ ->
            ids.mapNotNull { store.episodeByGuid(it) }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** What is queued *after* the playing item — Apple's "Up Next". */
    val upNext: StateFlow<List<Episode>> =
        queue.map { it.drop(1) }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val currentEpisode: StateFlow<Episode?> =
        combine(_currentMediaId, store.episodes) { id, _ ->
            id?.let { store.episodeByGuid(it) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    val currentFeed: StateFlow<Feed?> =
        combine(currentEpisode, store.feeds) { ep, _ ->
            ep?.let { store.feedFor(it) }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    /*
     * What was playing last time, shown straight away. The saved queue is read
     * once, here, before anything can write to it, and becomes the current
     * episode and Up Next immediately: the mini player is back as soon as the
     * library has loaded, without waiting for the player service to start.
     * The service gets the queue itself when it connects (restorePersistedQueue).
     */
    private val savedQueue: List<String> = queueStore.load().first

    /** True from connecting to an empty player until the saved queue is back in it. */
    private var restoring = false

    /** Asked to play before the player had anything (see [resume]). */
    private var playWhenRestored = false

    init {
        if (savedQueue.isNotEmpty()) {
            _currentMediaId.value = savedQueue.first()
            _queueIds.value = savedQueue
            scope.launch {
                store.awaitLoaded()
                // Until the player has it, the progress shown is the saved one.
                if ((controller?.mediaItemCount ?: 0) > 0) return@launch
                val ep = store.episodeByGuid(savedQueue.first()) ?: return@launch
                _positionMs.value = if (ep.effectivelyPlayed) 0L else ep.positionMs
                _durationMs.value = ep.durationMs.coerceAtLeast(0L)
            }
        }
    }

    /** Where the current listening session began, for gPodder's "started". */
    private var sessionStartMs = 0L

    /** Told when playback pauses: the episode, where this session started, where it stopped, its length. */
    var onPaused: ((Episode, Long, Long, Long) -> Unit)? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            val c = controller ?: return
            if (isPlaying) {
                sessionStartMs = c.currentPosition.coerceAtLeast(0L)
            } else {
                val ep = currentEpisode.value ?: return
                val d = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: ep.durationMs
                onPaused?.invoke(ep, sessionStartMs, c.currentPosition.coerceAtLeast(0L), d)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaId.value = mediaItem?.mediaId
            // Auto-advance leaves the finished episode behind the playhead;
            // drop it so index 0 stays the playing item.
            consumePlayed()
            syncQueue()
            pullState()
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) = syncQueue()

        override fun onPlaybackStateChanged(state: Int) {
            _buffering.value = state == Player.STATE_BUFFERING
            pullState()
        }
    }

    fun connect() {
        // A controller whose service has gone (the app was swiped away while
        // paused, and the process lived on) is dead: start again.
        controller?.let { if (it.isConnected || connecting) return else release() }
        if (connecting) return
        connecting = true
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    if (this@PlayerConnection.controller !== controller) return
                    release()
                    _isPlaying.value = false
                }
            })
            .buildAsync()
        future.addListener({
            connecting = false
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            /*
             * An empty player means the service is new: bring the saved queue
             * back. This used to sync first, which saved the empty playlist
             * over the saved queue and then restored nothing, so GlassCast
             * never remembered what was playing once the app was closed.
             */
            if (c.mediaItemCount == 0) {
                restorePersistedQueue(c)
            } else {
                _currentMediaId.value = c.currentMediaItem?.mediaId
                syncQueue()
                pullState()
                if (playWhenRestored) {
                    playWhenRestored = false
                    c.play()
                }
            }
            startTicker()
        }, ContextCompat.getMainExecutor(context))
    }

    private var connecting = false

    fun release() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }

    // ---------------------------------------------------------------- queue
    //
    // One invariant holds everything together: the playing item is ALWAYS
    // index 0, and everything after it is Up Next. There is no history in the
    // queue.
    //
    // Without that rule, playing episode B while A is queued leaves A sitting
    // behind the playhead, so finishing B walks backwards into something you
    // already listened to. Anything you move away from is consumed and dropped.

    private fun syncQueue() {
        val c = controller ?: return
        // The player is still empty while the saved queue is on its way back.
        if (restoring && c.mediaItemCount == 0) return
        val ids = (0 until c.mediaItemCount).map { c.getMediaItemAt(it).mediaId }
        _queueIds.value = ids
        _queueIndex.value = 0
        queueStore.save(ids, 0)
    }

    /** Drops anything the playhead has moved past, restoring index 0. */
    private fun consumePlayed() {
        val c = controller ?: return
        val index = c.currentMediaItemIndex
        if (index > 0) c.removeMediaItems(0, index)
    }

    private fun upNextItems(): List<MediaItem> {
        val c = controller ?: return emptyList()
        return (1 until c.mediaItemCount).map { c.getMediaItemAt(it) }
    }

    /**
     * Puts the saved queue back into a new, empty player. Waits for the store
     * to finish loading, because the controller can connect well before the
     * episode JSON has been read off disk. The queue is the one read at
     * start-up, or what's been queued since, whichever is current.
     */
    private fun restorePersistedQueue(c: MediaController) {
        val guids = _queueIds.value.ifEmpty { savedQueue }
        if (guids.isEmpty()) {
            playWhenRestored = false
            return
        }
        restoring = true
        scope.launch {
            try {
                store.awaitLoaded()
                // Something was played meanwhile, or this controller is gone.
                if (controller !== c || c.mediaItemCount > 0) return@launch
                val episodes = guids.mapNotNull { store.episodeByGuid(it) }
                if (episodes.isEmpty()) {
                    _currentMediaId.value = null
                    return@launch
                }
                val startAt = episodes[0].let { if (it.effectivelyPlayed) 0L else it.positionMs }
                c.setMediaItems(episodes.map { mediaItemFor(it, store.feedFor(it)) }, 0, startAt)
                c.prepare()
                // Restored, not resumed: nobody wants audio when they open the
                // app. Unless they asked for it (the Resume shortcut).
                if (playWhenRestored) c.play()
                _currentMediaId.value = c.currentMediaItem?.mediaId
            } finally {
                restoring = false
                playWhenRestored = false
                if (controller === c) {
                    syncQueue()
                    pullState()
                }
            }
        }
    }

    /**
     * Plays an episode now. Whatever was playing is dropped from the queue;
     * Up Next is untouched. If the episode was already in Up Next it is lifted
     * out of the list rather than duplicated.
     */
    fun play(episode: Episode, feed: Feed?) {
        val c = controller ?: return
        val rest = upNextItems().filterNot { it.mediaId == episode.guid }
        val start = if (episode.effectivelyPlayed) 0L else episode.positionMs

        c.setMediaItems(listOf(mediaItemFor(episode, feed)) + rest, 0, start)
        c.prepare()
        c.play()
        _currentMediaId.value = episode.guid
        store.markShowPlayed(episode.feedUrl)
        syncQueue()
    }

    /** Front of Up Next — the next thing after whatever is playing. */
    fun playNext(episode: Episode, feed: Feed?) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(episode, feed)
            return
        }
        removeFromQueue(episode.guid)
        c.addMediaItem(1.coerceAtMost(c.mediaItemCount), mediaItemFor(episode, feed))
        c.prepare()
        syncQueue()
    }

    /** Back of Up Next. */
    fun addToQueue(episode: Episode, feed: Feed?) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            play(episode, feed)
            return
        }
        if ((0 until c.mediaItemCount).any { c.getMediaItemAt(it).mediaId == episode.guid }) return
        c.addMediaItem(mediaItemFor(episode, feed))
        c.prepare()
        syncQueue()
    }

    fun isQueued(guid: String): Boolean {
        val c = controller ?: return false
        return (1 until c.mediaItemCount).any { c.getMediaItemAt(it).mediaId == guid }
    }

    /** Only removes from Up Next; the playing item is never silently dropped. */
    fun removeFromQueue(guid: String) {
        val c = controller ?: return
        val index = (1 until c.mediaItemCount)
            .firstOrNull { c.getMediaItemAt(it).mediaId == guid } ?: return
        c.removeMediaItem(index)
        syncQueue()
    }

    /** [upNextIndex] is an index into Up Next, not into the whole queue. */
    fun playFromUpNext(upNextIndex: Int) {
        val c = controller ?: return
        val real = upNextIndex + 1
        if (real !in 1 until c.mediaItemCount) return
        val episode = store.episodeByGuid(c.getMediaItemAt(real).mediaId)
        val start = episode?.let { if (it.effectivelyPlayed) 0L else it.positionMs } ?: 0L
        // Everything skipped over is consumed, keeping the playing item at 0.
        c.removeMediaItems(0, real)
        c.seekTo(0, start)
        c.prepare()
        c.play()
        _currentMediaId.value = c.currentMediaItem?.mediaId
        syncQueue()
    }

    /**
     * Restart the current episode.
     *
     * This is what "previous" means here: the queue keeps no history, so there
     * is nothing behind the playhead to go back to. Jumping to the start is the
     * honest interpretation and matches what the notification's previous button
     * does on a one-item queue.
     */
    fun restartEpisode() {
        val c = controller ?: return
        c.seekTo(0, 0L)
        _positionMs.value = 0L
    }

    /** Advance to the next queued episode, consuming the one being left. */
    fun skipToNext() {
        val c = controller ?: return
        if (c.mediaItemCount <= 1) return
        playFromUpNext(0)
    }

    fun clearUpNext() {
        val c = controller ?: return
        if (c.mediaItemCount <= 1) return
        c.removeMediaItems(1, c.mediaItemCount)
        syncQueue()
    }

    // ------------------------------------------------------------- playback

    private fun pullState() {
        val c = controller ?: return
        _isPlaying.value = c.isPlaying
        _speed.value = c.playbackParameters.speed
        // An empty player has no progress to report; keep the remembered one.
        if (c.mediaItemCount == 0) return
        val d = c.duration
        _durationMs.value = if (d > 0 && d != C.TIME_UNSET) d else 0L
        _positionMs.value = c.currentPosition.coerceAtLeast(0L)
    }

    private var ticking = false
    private fun startTicker() {
        if (ticking) return
        ticking = true
        scope.launch {
            while (true) {
                val c = controller
                if (c != null && c.mediaItemCount > 0) {
                    _positionMs.value = c.currentPosition.coerceAtLeast(0L)
                    val d = c.duration
                    if (d > 0 && d != C.TIME_UNSET) _durationMs.value = d
                }
                delay(if (_isPlaying.value) 400L else 1_000L)
            }
        }
    }

    /**
     * Lesson 5: Media3 does not inherit artwork from your data layer. With no
     * artwork set it falls back to whatever picture is embedded in the file, so
     * the lock screen shows something stale or nothing at all.
     */
    private fun mediaItemFor(episode: Episode, feed: Feed?): MediaItem =
        EpisodeItems.playable(episode, feed, downloads)

    /**
     * Replace the session's item in place after metadata changes — capture the
     * position, swap, seek back — so the lock screen updates without a gap.
     */
    fun refreshMetadata(episode: Episode, feed: Feed?) {
        val c = controller ?: return
        val index = (0 until c.mediaItemCount)
            .firstOrNull { c.getMediaItemAt(it).mediaId == episode.guid } ?: return
        val pos = c.currentPosition
        val wasCurrent = index == c.currentMediaItemIndex
        val wasPlaying = c.isPlaying
        c.replaceMediaItem(index, mediaItemFor(episode, feed))
        if (wasCurrent) {
            c.seekTo(index, pos)
            if (wasPlaying) c.play()
        }
    }

    /**
     * Puts a restored queue (from a backup) in place, paused, when nothing is
     * loaded. Anything already playing or queued is left alone.
     */
    fun loadQueue(episodes: List<Episode>) {
        if (episodes.isEmpty() || _currentMediaId.value != null) return
        val c = controller
        val ids = episodes.map { it.guid }
        if (c != null && c.isConnected && !restoring) {
            if (c.mediaItemCount > 0) return
            val first = episodes[0]
            c.setMediaItems(episodes.map { mediaItemFor(it, store.feedFor(it)) }, 0, if (first.effectivelyPlayed) 0L else first.positionMs)
            c.prepare()
            _currentMediaId.value = first.guid
            syncQueue()
            pullState()
        } else {
            // Not connected yet: it becomes the saved queue, restored on connect.
            _queueIds.value = ids
            _currentMediaId.value = ids.first()
            queueStore.save(ids, 0)
        }
    }

    /**
     * Plays what's loaded: the Resume shortcut. At once if the player has it;
     * otherwise as soon as the saved queue is back in it, since the app has
     * usually only just started.
     */
    fun resume() {
        val c = controller
        if (c != null && c.isConnected && !restoring) {
            // Connected and settled: play what's there, if anything. Nothing
            // is left pending, or a later connection would start by itself.
            if (c.mediaItemCount > 0) c.play()
            return
        }
        playWhenRestored = true
        connect()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(ms: Long) {
        controller?.seekTo(ms.coerceAtLeast(0L))
        _positionMs.value = ms.coerceAtLeast(0L)
    }

    fun seekBy(deltaMs: Long) {
        val c = controller ?: return
        val target = (c.currentPosition + deltaMs)
            .coerceIn(0L, if (c.duration > 0) c.duration else Long.MAX_VALUE)
        c.seekTo(target)
        _positionMs.value = target
    }

    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 3f)
        controller?.setPlaybackSpeed(clamped)
        _speed.value = clamped
    }

    companion object {
        /** MediaMetadata extra: the episode's online address. */
        const val REMOTE_URL = "glasscast.remoteUrl"
    }
}
