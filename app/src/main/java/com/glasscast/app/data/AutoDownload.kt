package com.glasscast.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * New episodes of the shows set to download on their own (the switch in a
 * show's info sheet).
 *
 * Looked for after every refresh, in the app and in the background job, and
 * only on an unmetered network (Wi-Fi or Ethernet). The address is looked up
 * at that moment, so a host's short-lived signed link is fresh when the
 * download starts; the download is then held to unmetered networks too.
 *
 * Which episodes: among a show's three newest, those published since
 * auto-download was turned on for it (less half a day, for feeds that list an
 * episode a while after its date) and not played. Each episode is tried once
 * ever (Downloads.startAutomatic), so deleting one doesn't bring it back.
 * Only the newest three are looked at so that a feed which re-dates its back
 * catalog can't fill the phone.
 *
 * Nothing is deleted automatically.
 */
class AutoDownload(
    context: Context,
    private val store: FeedStore,
    private val downloads: Downloads,
    private val settings: Settings
) {
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)

    /** Starts whatever is due; returns once it's queued, not downloaded. */
    suspend fun run() {
        val shows = settings.autoDownload.value
        if (shows.isEmpty() || !unmetered()) return
        store.awaitLoaded()
        val feeds = store.feeds.value.associateBy { it.url }
        val due = shows.flatMap { (url, since) ->
            val feed = feeds[url] ?: return@flatMap emptyList()
            store.episodesFor(url)
                .sortedByDescending { it.pubDate }
                .take(NEWEST)
                .filter { it.pubDate >= since - GRACE_MS && !it.effectivelyPlayed && it.audioUrl.isNotBlank() }
                .map { it to feed }
        }
        if (due.isNotEmpty()) downloads.startAutomatic(due)
    }

    private fun unmetered(): Boolean {
        val cm = connectivity ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private companion object {
        const val NEWEST = 3
        const val GRACE_MS = 12 * 60 * 60 * 1000L
    }
}
