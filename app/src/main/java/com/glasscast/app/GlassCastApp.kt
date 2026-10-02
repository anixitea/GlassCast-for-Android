package com.glasscast.app

import kotlinx.coroutines.launch

import android.app.Application
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.ImageStore
import com.glasscast.app.data.QueueStore
import com.glasscast.app.data.Settings
import com.glasscast.app.player.PlayerConnection
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Owns the stores. No Hilt — with four singletons a constructed graph costs
 * more than it saves.
 */
class GlassCastApp : Application() {

    lateinit var feedStore: FeedStore
        private set
    lateinit var imageStore: ImageStore
        private set
    lateinit var settings: Settings
        private set
    lateinit var queueStore: QueueStore
        private set
    lateinit var player: PlayerConnection
        private set
    lateinit var updates: com.glasscast.app.update.AppUpdater
        private set
    lateinit var downloads: com.glasscast.app.data.Downloads
        private set
    lateinit var gpodder: com.glasscast.app.data.GPodderSync
        private set

    /** Set when the app is opened by a shared or opened OPML file. */
    val pendingOpml = MutableStateFlow<android.net.Uri?>(null)

    /** A show to open, from tapping a new-episode notification. */
    val pendingOpenFeed = MutableStateFlow<String?>(null)

    override fun onCreate() {
        super.onCreate()
        feedStore = FeedStore(this)
        imageStore = ImageStore(this)
        settings = Settings(this)
        queueStore = QueueStore(this)
        downloads = com.glasscast.app.data.Downloads(this)
        player = PlayerConnection(this, feedStore, queueStore, downloads)
        updates = com.glasscast.app.update.AppUpdater(this)
        gpodder = com.glasscast.app.data.GPodderSync(this, feedStore)
        wireSync()
        initCast()

        // The channel exists from the start so it appears in system settings
        // even before the first notification; the job is (re)scheduled on
        // every launch, which is idempotent and survives app updates.
        com.glasscast.app.background.NewEpisodeNotifier.ensureChannel(this)
        com.glasscast.app.background.BackgroundRefresh.apply(this, settings.newEpisodeNotifications.value)
    }

    /**
     * gPodder sync's hooks: follows and unfollows, episodes marked played, and
     * pauses go into its outbox; it syncs once at launch (and again from the
     * background refresh). With no account set up, every hook is a no-op.
     */
    private fun wireSync() {
        val sync = gpodder
        feedStore.onSubscriptionChanged = { url, added -> sync.recordSubscription(url, added) }
        feedStore.onMarkedPlayed = { ep, played ->
            val total = ep.durationMs.coerceAtLeast(1_000L)
            sync.recordPlay(ep, 0L, if (played) total else 0L, total)
        }
        player.onPaused = { ep, started, position, duration -> sync.recordPlay(ep, started, position, duration) }
        sync.playingGuid = { player.currentEpisode.value?.guid }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            sync.sync()
        }
    }

    /**
     * Start the Cast SDK early, so its device discovery is running before the
     * device list is ever opened — Cast routes only appear once the SDK has
     * registered its provider with the media router.
     *
     * Guarded: phones without Google Play Services (or with it disabled) simply
     * never show Cast devices, instead of crashing on launch.
     */
    private fun initCast() {
        runCatching {
            com.google.android.gms.cast.framework.CastContext.getSharedInstance(this)
            androidx.mediarouter.media.MediaRouter.getInstance(this).setRouterParams(
                androidx.mediarouter.media.MediaRouterParams.Builder()
                    // Let the system's own output picker show Cast devices too,
                    // and hand playback back to the phone when casting ends.
                    .setOutputSwitcherEnabled(true)
                    .setTransferToLocalEnabled(true)
                    .build()
            )
        }
    }
}
