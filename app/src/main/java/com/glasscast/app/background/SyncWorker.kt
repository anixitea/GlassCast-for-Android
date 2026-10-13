package com.glasscast.app.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.glasscast.app.GlassCastApp

/**
 * One sync with the Google account (data/GoogleAccount), when the app opens
 * or closes. A worker rather than a coroutine in the app, so a sync started
 * as the app is swiped away still finishes; it waits for a network.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as GlassCastApp
        if (app.account.sync(app)) return Result.success()
        val s = app.account.state.value
        // Retried a couple of times for a flaky network; not when the person
        // has to sign in again, or once it's clearly not going to work.
        return if (!s.signedIn || s.needsSignIn || runAttemptCount >= 2) Result.success() else Result.retry()
    }

    companion object {
        private const val NAME = "glasscast-sync"

        fun enqueue(context: Context) {
            val work = runCatching { WorkManager.getInstance(context) }.getOrNull() ?: return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniqueWork(NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
