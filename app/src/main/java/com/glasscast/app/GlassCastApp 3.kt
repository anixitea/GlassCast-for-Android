package com.glasscast.app

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
        initCast()

        // The channel exists from the start so it appears in system settings
        // even before the first notification; the job is (re)scheduled on
        // every launch, which is idempotent and survives app updates.
        com.glasscast.app.background.NewEpisodeNotifier.ensureChannel(this)
        com.glasscast.app.background.BackgroundRefresh.apply(this, settings.newEpisodeNotifications.value)
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
