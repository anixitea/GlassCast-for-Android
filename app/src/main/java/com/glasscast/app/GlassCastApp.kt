package com.glasscast.app

import kotlinx.coroutines.launch

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.StrictMode
import android.util.Log
import com.glasscast.app.data.CrashLog
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.GOOGLE_SIGN_IN
import com.glasscast.app.data.ImageStore
import com.glasscast.app.data.QueueStore
import com.glasscast.app.data.Settings
import com.glasscast.app.player.PlayerConnection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
    lateinit var bookmarks: com.glasscast.app.data.BookmarkStore
        private set
    lateinit var autoDownload: com.glasscast.app.data.AutoDownload
        private set

    /** Backups: the document, files, restoring (data/Backup.kt). */
    val backup by lazy { com.glasscast.app.data.BackupManager(this) }

    /** Sign in with Google, and the library sync that comes with it (data/GoogleAccount.kt). */
    val account by lazy { com.glasscast.app.data.GoogleAccount(this) }

    /** Set when the app is opened by a shared or opened OPML file. */
    val pendingOpml = MutableStateFlow<android.net.Uri?>(null)

    /** A show to open, from tapping a new-episode notification. */
    val pendingOpenFeed = MutableStateFlow<String?>(null)

    /** A launcher shortcut or the widget's card, to act on (see Shortcuts). */
    val pendingShortcut = MutableStateFlow<String?>(null)

    /**
     * The launch animation plays once per process. Without this it lived in
     * the activity's composition, so every recreation (a split-screen resize,
     * a display-size change) played it again from the start.
     */
    var splashPlayed = false

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /*
     * The crash log goes in here rather than in onCreate: the app's content
     * providers (WorkManager's start-up, the profile installer, the artwork
     * provider) are created between the two, and a failure there would
     * otherwise go unrecorded.
     */
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashLog.install(this)
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) watchMainThread()
    }

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
        bookmarks = com.glasscast.app.data.BookmarkStore(this)
        autoDownload = com.glasscast.app.data.AutoDownload(this, feedStore, downloads, settings)
        settings.alsoWantsBackground = { GOOGLE_SIGN_IN && account.state.value.signedIn }
        feedStore.onRefreshed = { background.launch { autoDownload.run() } }
        wireSync()

        // The channel exists from the start so it appears in system settings
        // even before the first notification.
        com.glasscast.app.background.NewEpisodeNotifier.ensureChannel(this)

        background.launch {
            CrashLog.load(this@GlassCastApp)
            // (Re)scheduled on every launch, which is idempotent and survives
            // app updates. Off the main thread: WorkManager opens its database.
            com.glasscast.app.background.BackgroundRefresh.apply(
                this@GlassCastApp, settings.backgroundRefreshWanted
            )
        }
        // Cast starts with the playback service, off the main thread
        // (player/CastSupport).
    }

    /**
     * gPodder sync's hooks: follows and unfollows, episodes marked played, and
     * pauses go into its outbox; it syncs once at launch (and again from the
     * background refresh). With no account set up, every hook is a no-op.
     */
    private fun wireSync() {
        val sync = gpodder
        feedStore.onSubscriptionChanged = { url, added ->
            sync.recordSubscription(url, added)
            // An unfollowed show stops downloading; following it again starts fresh.
            if (!added) settings.setAutoDownload(url, false)
            // When, for the Google account's sync: the latest of following and
            // unfollowing wins across phones.
            account.recordFollow(url, added)
        }
        bookmarks.onRemoved = { id -> account.recordBookmarkDeleted(id) }
        feedStore.onMarkedPlayed = { ep, played ->
            val total = ep.durationMs.coerceAtLeast(1_000L)
            sync.recordPlay(ep, 0L, if (played) total else 0L, total)
        }
        player.onPaused = { ep, started, position, duration -> sync.recordPlay(ep, started, position, duration) }
        sync.playingGuid = { player.currentEpisode.value?.guid }
        background.launch {
            sync.sync()
        }
    }

    /**
     * Debug builds only: log every disk read, disk write, network call and
     * custom slow call made on the main thread, under "GlassCastStrict".
     * `adb logcat -s GlassCastStrict` during a cold start lists exactly what
     * the main thread did before the first frame.
     */
    private fun watchMainThread() {
        val policy = StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .detectCustomSlowCalls()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            policy.penaltyListener(java.util.concurrent.Executors.newSingleThreadExecutor()) { violation ->
                Log.w(STRICT_TAG, violation.javaClass.simpleName, violation)
            }
        } else {
            policy.penaltyLog()
        }
        StrictMode.setThreadPolicy(policy.build())
    }

    private companion object {
        const val STRICT_TAG = "GlassCastStrict"
    }
}
