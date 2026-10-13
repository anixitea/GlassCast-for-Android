package com.glasscast.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glasscast.app.GlassCastApp

/**
 * Refresh every show, start any auto-downloads that are due (on Wi-Fi; see
 * AutoDownload), notify about episodes that are genuinely new, and sync with the
 * Google account once a day when signed in.
 *
 * "New" is decided carefully, because a careless rule spams:
 *
 *  - the episode's guid wasn't in the library before this run, **and**
 *  - it's newer than the show's newest episode before this run.
 *
 * The second condition matters. Feeds re-list old episodes, change guids after
 * a hosting move, or come back from a failed fetch with their whole back
 * catalog — all of which would otherwise look "new". And a show with no
 * episodes stored yet is skipped outright, so a first successful fetch never
 * fires two hundred notifications.
 *
 * Episodes that appear while the app is open are never notified: the app
 * refreshed them itself, so they're already in the library when this runs.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GlassCastApp
        val notify = app.settings.newEpisodeNotifications.value
        val refresh = notify || app.settings.autoDownload.value.isNotEmpty()
        // The day's sync with the Google account rides along
        // (GoogleAccount.syncIfDue): after the refresh when there is one.
        if (!refresh) {
            runCatching { app.account.syncIfDue() }
            return Result.success()
        }

        val store = app.feedStore
        store.awaitLoaded()

        val newestBefore = store.feeds.value.associate { feed ->
            feed.url to (store.episodesFor(feed.url).maxOfOrNull { it.pubDate } ?: 0L)
        }
        val knownGuids = store.episodes.value.values.flatten().mapTo(HashSet()) { it.guid }

        runCatching { store.refreshAll() }.onFailure { return Result.retry() }

        val fresh = store.feeds.value.flatMap { feed ->
            val newest = newestBefore[feed.url] ?: 0L
            if (newest == 0L) return@flatMap emptyList()
            store.episodesFor(feed.url)
                .filter { it.guid !in knownGuids && it.pubDate > newest }
                .map { feed to it }
        }.sortedByDescending { it.second.pubDate }

        // Queued before this returns: the process can go once it does.
        runCatching { app.autoDownload.run() }

        // Pick up progress and follows from other devices while we're here.
        runCatching { app.gpodder.sync() }

        store.flushNow()

        if (notify && fresh.isNotEmpty()) {
            NewEpisodeNotifier.post(applicationContext, fresh, app.imageStore)
        }
        runCatching { app.account.syncIfDue() }
        return Result.success()
    }
}
