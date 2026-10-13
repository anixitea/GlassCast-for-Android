package com.glasscast.app.data

import android.net.Uri
import android.os.Build
import com.glasscast.app.GlassCastApp
import com.glasscast.app.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** An episode's place, as a backup keeps it. */
data class SavedProgress(val positionMs: Long, val durationMs: Long, val played: Boolean)

/** What a backup holds, for the line that asks before restoring it. */
data class BackupSummary(val shows: Int, val bookmarks: Int, val createdAt: Long, val device: String)

/** Where a backup or restore stands, for Settings. */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Working(val label: String, val done: Int = 0, val total: Int = 0) : BackupStatus
    data class Done(val message: String) : BackupStatus
    data class Failed(val message: String) : BackupStatus
}

/**
 * Everything worth keeping when moving to a new phone, as one JSON document:
 * the shows you follow (and when you followed and last played each), every
 * episode's played mark and place, the queue, bookmarks with their memos,
 * and the settings, per-show ones included.
 *
 * Not the downloads (the files are the publisher's, and big; they download
 * again) and not a gPodder password (it stays sealed on the phone it was
 * typed on).
 *
 * The same document goes to a file you keep (Settings > Backup), and is the
 * heart of what syncs with a Google account (GoogleAccount). Restoring
 * merges rather than replaces: shows are followed, marks and places only move
 * forward, bookmarks are added beside any already there.
 */
class BackupManager(private val app: GlassCastApp) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _status = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val status: StateFlow<BackupStatus> = _status.asStateFlow()

    fun clearStatus() {
        if (_status.value !is BackupStatus.Working) _status.value = BackupStatus.Idle
    }

    // ------------------------------------------------------------- writing

    /** The backup document. Waits for the library to load; JSON is built off the main thread. */
    suspend fun build(): String {
        val store = app.feedStore
        store.awaitLoaded()
        val settings = app.settings
        val feeds = store.feeds.value
        val episodes = store.episodes.value
        val queue = app.queueStore.load().first
        val bookmarks = app.bookmarks.all.value
        return withContext(Dispatchers.Default) {
            val root = JSONObject()
            root.put("app", "GlassCast")
            root.put("format", FORMAT)
            root.put("version", appVersion())
            root.put("createdAt", System.currentTimeMillis())
            root.put("device", deviceName())

            root.put("shows", JSONArray().apply {
                feeds.forEach { f ->
                    put(JSONObject().apply {
                        put("url", f.url)
                        put("title", f.title)
                        put("image", f.imageUrl)
                        put("addedAt", f.addedAt)
                        put("lastPlayedAt", f.lastPlayedAt)
                    })
                }
            })
            // Only episodes with something to remember: a place or a mark.
            root.put("episodes", JSONArray().apply {
                episodes.values.forEach { list ->
                    list.forEach { e ->
                        if (e.played || e.positionMs > 0) {
                            put(JSONObject().apply {
                                put("guid", e.guid)
                                put("feed", e.feedUrl)
                                put("position", e.positionMs)
                                put("duration", e.durationMs)
                                put("played", e.played)
                            })
                        }
                    }
                }
            })
            root.put("queue", JSONArray(queue))
            root.put("bookmarks", JSONArray().apply {
                bookmarks.forEach { b ->
                    put(JSONObject().apply {
                        put("id", b.id)
                        put("guid", b.guid)
                        put("position", b.positionMs)
                        put("memo", b.memo)
                        put("created", b.createdAt)
                        put("edited", b.editedAt)
                    })
                }
            })
            root.put("settings", JSONObject().apply {
                put("theme", settings.theme.value.name)
                put("dynamicColor", settings.dynamicColor.value)
                put("sort", settings.sort.value.name)
                put("showSort", settings.showSort.value.name)
                put("hidePlayedLatest", settings.hidePlayedInLatest.value)
                put("hidePlayedShows", settings.hidePlayedInShows.value)
                put("shake", settings.shakeToRestart.value)
                put("skipSeconds", settings.skipSeconds.value)
                put("headphoneSkip", settings.headphoneSkip.value)
                put("newEpisodes", settings.newEpisodeNotifications.value)
                put("skipSilence", settings.skipSilence.value)
                put("voiceBoost", settings.voiceBoost.value)
                put("episodeOrder", JSONObject().apply {
                    settings.episodeOrder.value.forEach { (url, order) -> put(url, order.name) }
                })
                put("autoDownload", JSONArray(settings.autoDownload.value.keys.toList()))
                put("discoverHidden", JSONArray().apply {
                    val info = settings.discoverHiddenInfo.value
                    settings.discoverHidden.value.forEach { url ->
                        put(JSONObject().apply {
                            put("url", url)
                            put("title", info[url]?.title.orEmpty())
                            put("art", info[url]?.artworkUrl.orEmpty())
                        })
                    }
                })
            })
            root.toString()
        }
    }

    /** Saves a backup to a file the person picked (the system's Save dialog). */
    fun saveTo(uri: Uri) {
        if (_status.value is BackupStatus.Working) return
        scope.launch {
            _status.value = BackupStatus.Working(tr("Saving backup…"))
            val result = runCatching {
                val json = build()
                withContext(Dispatchers.IO) {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) }
                        ?: error("no stream")
                }
            }
            _status.value = if (result.isSuccess) BackupStatus.Done(tr("Backup saved"))
            else BackupStatus.Failed(tr("Couldn't save the backup"))
        }
    }

    /** Reads a backup file the person picked; null if it isn't one. */
    suspend fun readFile(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            app.contentResolver.openInputStream(uri)?.use { input ->
                // A backup of a big library is a few MB; anything far past that isn't one.
                val bytes = input.readBytes()
                if (bytes.size > MAX_BYTES) null else String(bytes, Charsets.UTF_8)
            }
        }.getOrNull()?.takeIf { summarize(it) != null }
    }

    // ------------------------------------------------------------- reading

    fun summarize(json: String): BackupSummary? = runCatching {
        val root = JSONObject(json)
        if (root.optString("app") != "GlassCast") return null
        BackupSummary(
            shows = root.optJSONArray("shows")?.length() ?: 0,
            bookmarks = root.optJSONArray("bookmarks")?.length() ?: 0,
            createdAt = root.optLong("createdAt"),
            device = root.optString("device")
        )
    }.getOrNull()

    /**
     * Restores a backup into this phone's library. Shows not followed yet are
     * fetched one at a time (as an OPML import is), so it takes a while on a
     * big library; Settings shows how far along it is. Keeps running if
     * Settings is closed.
     */
    fun restore(json: String) {
        if (_status.value is BackupStatus.Working) return
        scope.launch {
            _status.value = BackupStatus.Working(tr("Restoring…"))
            val result = runCatching { restoreNow(json) }
            _status.value = result.fold(
                onSuccess = { failed ->
                    if (failed == 0) BackupStatus.Done(tr("Backup restored"))
                    else BackupStatus.Done(tr("Backup restored · {0} shows couldn't be reached", failed))
                },
                onFailure = { BackupStatus.Failed(tr("Couldn't restore that backup")) }
            )
        }
    }

    /** Returns how many shows couldn't be fetched. */
    private suspend fun restoreNow(json: String): Int {
        val root = withContext(Dispatchers.Default) { JSONObject(json) }
        if (root.optString("app") != "GlassCast") error("not a GlassCast backup")
        val store = app.feedStore
        store.awaitLoaded()

        root.optJSONObject("settings")?.let { applySettings(it) }

        // Shows, fetched in order, with progress.
        val shows = root.optJSONArray("shows") ?: JSONArray()
        val entries = (0 until shows.length()).mapNotNull { i ->
            val o = shows.optJSONObject(i) ?: return@mapNotNull null
            val url = o.optString("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            OpmlEntry(title = o.optString("title"), url = url)
        }
        val missing = entries.filter { e -> store.feeds.value.none { it.url.equals(e.url, ignoreCase = true) } }
        var failed = 0
        if (missing.isNotEmpty()) {
            _status.value = BackupStatus.Working(tr("Restoring…"), 0, missing.size)
            val watch = scope.launch {
                store.importProgress.collect { p ->
                    if (p.running) _status.value = BackupStatus.Working(tr("Restoring…"), p.done, p.total)
                }
            }
            store.importOpml(missing)
            failed = store.importProgress.value.failed
            watch.cancel()
            store.clearImportProgress()
        }
        val dates = (0 until shows.length()).mapNotNull { i ->
            val o = shows.optJSONObject(i) ?: return@mapNotNull null
            o.optString("url") to (o.optLong("addedAt") to o.optLong("lastPlayedAt"))
        }.toMap()
        store.restoreShowDates(dates)

        // Marks and places.
        val eps = root.optJSONArray("episodes") ?: JSONArray()
        val progress = HashMap<String, SavedProgress>(eps.length())
        for (i in 0 until eps.length()) {
            val o = eps.optJSONObject(i) ?: continue
            val guid = o.optString("guid").takeIf { it.isNotBlank() } ?: continue
            progress[guid] = SavedProgress(o.optLong("position"), o.optLong("duration"), o.optBoolean("played"))
        }
        store.applyProgress(progress)

        // Bookmarks.
        val marks = root.optJSONArray("bookmarks") ?: JSONArray()
        app.bookmarks.restore((0 until marks.length()).mapNotNull { i ->
            val o = marks.optJSONObject(i) ?: return@mapNotNull null
            Bookmark(
                id = o.optLong("id"),
                guid = o.optString("guid").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
                positionMs = o.optLong("position"),
                memo = o.optString("memo"),
                createdAt = o.optLong("created"),
                editedAt = o.optLong("edited", o.optLong("created"))
            )
        })

        // The queue, if nothing is loaded here.
        val queue = root.optJSONArray("queue") ?: JSONArray()
        val queued = (0 until queue.length()).mapNotNull { store.episodeByGuid(queue.optString(it)) }
        app.player.loadQueue(queued)

        store.flushNow()
        return failed
    }

    internal fun applySettings(o: JSONObject) {
        val s = app.settings
        fun <T : Enum<T>> enumOf(values: Array<T>, name: String?): T? = values.firstOrNull { it.name == name }
        enumOf(ThemeMode.entries.toTypedArray(), o.optString("theme"))?.let(s::setTheme)
        if (o.has("dynamicColor")) s.setDynamicColor(o.optBoolean("dynamicColor"))
        enumOf(EpisodeSort.entries.toTypedArray(), o.optString("sort"))?.let(s::setSort)
        enumOf(ShowSort.entries.toTypedArray(), o.optString("showSort"))?.let(s::setShowSort)
        if (o.has("hidePlayedLatest")) s.setHidePlayedInLatest(o.optBoolean("hidePlayedLatest"))
        if (o.has("hidePlayedShows")) s.setHidePlayedInShows(o.optBoolean("hidePlayedShows"))
        if (o.has("shake")) s.setShakeToRestart(o.optBoolean("shake"))
        if (o.has("skipSeconds")) s.setSkipSeconds(o.optInt("skipSeconds", 30))
        if (o.has("headphoneSkip")) s.setHeadphoneSkip(o.optBoolean("headphoneSkip"))
        if (o.has("newEpisodes")) s.setNewEpisodeNotifications(o.optBoolean("newEpisodes"))
        if (o.has("skipSilence")) s.setSkipSilence(o.optBoolean("skipSilence"))
        if (o.has("voiceBoost")) s.setVoiceBoost(o.optBoolean("voiceBoost"))
        o.optJSONObject("episodeOrder")?.let { orders ->
            orders.keys().forEach { url ->
                enumOf(EpisodeSort.entries.toTypedArray(), orders.optString(url))?.let { s.setEpisodeOrder(url, it) }
            }
        }
        // Auto-download starts from now on this phone, so a restore never
        // pulls in episodes published while it wasn't there.
        o.optJSONArray("autoDownload")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let { s.setAutoDownload(it, true) }
        }
        o.optJSONArray("discoverHidden")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.optJSONObject(i) ?: continue
                val url = h.optString("url").takeIf { it.isNotBlank() } ?: continue
                s.hideFromDiscover(url, h.optString("title"), h.optString("art"))
            }
        }
    }

    // --------------------------------------------------------------- misc

    /** "GlassCast backup 2026-10-12.json", for the Save dialog. */
    fun fileName(): String {
        val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())
        return "GlassCast backup $day.json"
    }

    private fun appVersion(): String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
    }

    internal fun setStatus(status: BackupStatus) {
        _status.value = status
    }

    private companion object {
        const val FORMAT = 1
        const val MAX_BYTES = 64 * 1024 * 1024
    }
}
