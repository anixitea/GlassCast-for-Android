package com.glasscast.app.player

import com.glasscast.app.data.AdBreak
import com.glasscast.app.data.AdFinder
import com.glasscast.app.data.Episode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Skip ads — shared between the playback service, which finds the breaks and
 * skips them, and the players, which color them on the progress line and
 * show a toast when one is skipped. Same process, so a plain object, like
 * SleepTimer.
 *
 * Only runs while Skip ads is on: nothing is fetched otherwise.
 */
object AdSkipper {

    private val _breaks = MutableStateFlow<Pair<String, List<AdBreak>>>("" to emptyList())
    /** The episode's guid and the breaks found in it. */
    val breaks: StateFlow<Pair<String, List<AdBreak>>> = _breaks.asStateFlow()

    private val _skipped = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** The guid of an episode whose ad was just skipped. No replay: a toast is for now or never. */
    val skipped: SharedFlow<String> = _skipped.asSharedFlow()

    // Main thread only (the service's effects scope).
    private val cache = object : LinkedHashMap<String, List<AdBreak>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<AdBreak>>?) = size > 24
    }
    private var requested = ""

    internal suspend fun load(episode: Episode) {
        requested = episode.guid
        val key = episode.guid
        cache[key]?.let {
            _breaks.value = episode.guid to it
            return
        }
        _breaks.value = episode.guid to emptyList()
        val found = withContext(Dispatchers.IO) { AdFinder.find(episode) }
        cache[key] = found
        // Another episode may have started while this one was being read.
        if (requested == episode.guid) _breaks.value = episode.guid to found
    }

    internal fun announceSkip(guid: String) {
        _skipped.tryEmit(guid)
    }
}
