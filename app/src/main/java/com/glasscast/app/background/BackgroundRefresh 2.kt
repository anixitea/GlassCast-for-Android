package com.glasscast.app.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules the background check for new episodes.
 *
 * Every three hours, give or take — podcasts publish daily at most, and a
 * tighter interval only spends battery to notify you a little sooner about
 * something you'd listen to later anyway. Only on a network, and not when the
 * battery is low: a missed check just means the next one finds the episode.
 *
 * Unique and UPDATE-on-reschedule, so calling this on every launch never
 * stacks duplicate jobs, and a change to the schedule actually takes effect.
 */
object BackgroundRefresh {
    private const val NAME = "glasscast-new-episodes"

    fun apply(context: Context, enabled: Boolean) {
        val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
        if (!enabled) {
            work.cancelUniqueWork(NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(3, TimeUnit.HOURS, 45, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
