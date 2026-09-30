package com.glasscast.app.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

enum class DownloadState { QUEUED, RUNNING, DONE, FAILED }

data class DownloadEntry(
    val guid: String,
    val feedUrl: String,
    val title: String,
    val downloadId: Long,
    val fileName: String,
    val state: DownloadState,
    /** 0–1 while running; 1 once done. */
    val progress: Float,
    /** Bytes on disk once done; bytes so far while running. */
    val bytes: Long,
    val addedAt: Long
)

/**
 * Offline episodes.
 *
 * Built on Android's DownloadManager rather than an in-app downloader: it
 * carries on in the background and across process death, retries on its own,
 * shows the system's progress notification, and needs no permission, because
 * files go to GlassCast's own folder (Android/data/…/files/Podcasts). That
 * folder is removed with the app, and nothing else can read it.
 *
 * What GlassCast adds is the bookkeeping: which episode each download is, its
 * state and progress for the UI, and — on launch — a check that every
 * "downloaded" file still exists, so storage cleanup never leaves a row
 * claiming an episode is offline when it isn't.
 *
 * Progress is polled (every 0.7s) only while something is actually
 * downloading; with nothing in flight there's no work at all.
 */
class Downloads(private val context: Context) {

    private val manager = context.getSystemService(DownloadManager::class.java)
    private val prefs = context.getSharedPreferences("glasscast_downloads", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var polling: Job? = null

    private val folder: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS)
            ?: File(context.filesDir, "podcasts").apply { mkdirs() }

    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<Map<String, DownloadEntry>> = _entries.asStateFlow()

    init {
        // Anything marked done whose file has gone (storage cleanup, a manual
        // delete) is forgotten; anything in flight resumes being watched.
        val kept = _entries.value.filterValues { entry ->
            entry.state != DownloadState.DONE || File(folder, entry.fileName).exists()
        }
        if (kept.size != _entries.value.size) commit(kept)
        if (kept.values.any { it.active }) watch()
    }

    private val DownloadEntry.active get() = state == DownloadState.QUEUED || state == DownloadState.RUNNING

    fun start(episode: Episode, feed: Feed?) {
        val existing = _entries.value[episode.guid]
        if (existing != null && existing.state != DownloadState.FAILED) return
        val name = fileNameFor(episode)
        val request = DownloadManager.Request(Uri.parse(episode.audioUrl))
            .setTitle(episode.title)
            .setDescription(feed?.title ?: "GlassCast")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_PODCASTS, name)
            .setAllowedOverRoaming(false)
        val id = runCatching { manager?.enqueue(request) }.getOrNull() ?: return
        commit(
            _entries.value + (episode.guid to DownloadEntry(
                guid = episode.guid,
                feedUrl = episode.feedUrl,
                title = episode.title,
                downloadId = id,
                fileName = name,
                state = DownloadState.QUEUED,
                progress = 0f,
                bytes = 0L,
                addedAt = System.currentTimeMillis()
            ))
        )
        watch()
    }

    /** Cancels a download in flight, or deletes a finished one. */
    fun remove(guid: String) {
        val entry = _entries.value[guid] ?: return
        runCatching { manager?.remove(entry.downloadId) }
        runCatching { File(folder, entry.fileName).delete() }
        commit(_entries.value - guid)
    }

    /** The episode's file, if it's downloaded and still there. */
    fun fileFor(guid: String): File? {
        val entry = _entries.value[guid] ?: return null
        if (entry.state != DownloadState.DONE) return null
        return File(folder, entry.fileName).takeIf { it.exists() }
    }

    val totalBytes: Long get() = _entries.value.values.filter { it.state == DownloadState.DONE }.sumOf { it.bytes }

    private fun watch() {
        if (polling?.isActive == true) return
        polling = scope.launch {
            while (true) {
                val active = _entries.value.values.filter { it.active }
                if (active.isEmpty()) break
                val updated = withContext(Dispatchers.IO) { query(active) }
                if (updated.isNotEmpty()) {
                    val next = _entries.value.toMutableMap()
                    updated.forEach { (guid, entry) ->
                        if (entry == null) next.remove(guid) else next[guid] = entry
                    }
                    commit(next)
                }
                delay(700)
            }
        }
    }

    /** New state for each in-flight entry; null means the download is gone. */
    private fun query(active: List<DownloadEntry>): Map<String, DownloadEntry?> {
        val dm = manager ?: return emptyMap()
        val byId = active.associateBy { it.downloadId }
        val seen = mutableSetOf<Long>()
        val out = mutableMapOf<String, DownloadEntry?>()
        runCatching {
            dm.query(DownloadManager.Query().setFilterById(*byId.keys.toLongArray()))?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val entry = byId[id] ?: continue
                    seen += id
                    val soFar = c.getLong(soFarCol)
                    val total = c.getLong(totalCol)
                    val next = when (c.getInt(statusCol)) {
                        DownloadManager.STATUS_SUCCESSFUL -> entry.copy(
                            state = DownloadState.DONE,
                            progress = 1f,
                            bytes = File(folder, entry.fileName).length().takeIf { it > 0 } ?: soFar
                        )
                        DownloadManager.STATUS_FAILED -> entry.copy(state = DownloadState.FAILED)
                        DownloadManager.STATUS_RUNNING -> entry.copy(
                            state = DownloadState.RUNNING,
                            progress = if (total > 0) (soFar.toFloat() / total).coerceIn(0f, 1f) else entry.progress,
                            bytes = soFar
                        )
                        else -> entry // pending or paused (waiting for a network)
                    }
                    if (next != entry) out[entry.guid] = next
                }
            }
        }
        // Cancelled from the notification: DownloadManager forgets it.
        active.filter { it.downloadId !in seen }.forEach { out[it.guid] = null }
        return out
    }

    private fun fileNameFor(episode: Episode): String {
        val hash = MessageDigest.getInstance("SHA-1").digest(episode.guid.toByteArray())
            .take(8).joinToString("") { "%02x".format(it) }
        val path = episode.audioUrl.substringBefore('?').lowercase()
        val ext = listOf(".mp3", ".m4a", ".mp4", ".aac", ".ogg", ".opus").firstOrNull { path.endsWith(it) } ?: ".mp3"
        return hash + ext
    }

    private fun commit(next: Map<String, DownloadEntry>) {
        _entries.value = next
        val arr = JSONArray()
        next.values.forEach { e ->
            arr.put(JSONObject().apply {
                put("guid", e.guid); put("feedUrl", e.feedUrl); put("title", e.title)
                put("id", e.downloadId); put("file", e.fileName); put("state", e.state.name)
                put("progress", e.progress.toDouble()); put("bytes", e.bytes); put("added", e.addedAt)
            })
        }
        prefs.edit().putString("entries", arr.toString()).apply()
    }

    private fun load(): Map<String, DownloadEntry> = runCatching {
        val arr = JSONArray(prefs.getString("entries", "[]"))
        (0 until arr.length()).associate { i ->
            val o = arr.getJSONObject(i)
            val e = DownloadEntry(
                guid = o.getString("guid"),
                feedUrl = o.optString("feedUrl"),
                title = o.optString("title"),
                downloadId = o.getLong("id"),
                fileName = o.getString("file"),
                state = runCatching { DownloadState.valueOf(o.getString("state")) }.getOrDefault(DownloadState.FAILED),
                progress = o.optDouble("progress", 0.0).toFloat(),
                bytes = o.optLong("bytes"),
                addedAt = o.optLong("added")
            )
            e.guid to e
        }
    }.getOrDefault(emptyMap())
}
