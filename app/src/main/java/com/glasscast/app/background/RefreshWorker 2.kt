package com.glasscast.app.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.glasscast.app.GlassCastApp

/**
 * Refresh every show, and notify about episodes that are genuinely new.
 *
 * "New" is decided carefully, because a careless rule spams:
 *
 *  - the episode's guid wasn't in the library before this run, **and**
 *  - it's newer than the show's newest episode before this run.
 *
 * The second condition matters. Feeds re-list old episodes, change guids after
 * a hosting move, or come back from a failed fetch with their whole back
 * catalogue — all of which would otherwise look "new". And a show with no
 * episodes stored yet is skipped outright, so a first successful fetch never
 * fires two hundred notifications.
 *
 * Episodes that appear while the app is open are never notified: the app
 * refreshed them itself, so they're already in the library when this runs.
 */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GlassCastApp
        if (!app.settings.newEpisodeNotifications.value) return Result.success()

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

        store.flushNow()

        if (fresh.isNotEmpty()) {
            NewEpisodeNotifier.post(applicationContext, fresh, app.imageStore)
        }
        return Result.success()
    }
}
