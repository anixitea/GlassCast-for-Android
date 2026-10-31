package com.glasscast.app.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One episode in the history, and when it was last listened to. */
data class HistoryEntry(val guid: String, val feedUrl: String, val at: Long)

/**
 * What you've listened to, most recent first (2.0; asked for by a listener).
 * One entry per episode, moved to the top whenever it plays again, the last
 * [LIMIT] kept. Stamped by the playback service as it saves the place, at
 * most once a minute per episode, so a long listen costs a handful of writes.
 *
 * Kept by guid, as bookmarks are: an episode refreshed under it keeps its
 * entry. In one small JSON file, read at start-up, written behind.
 */
class ListenHistory(context: Context) {

    private val file = File(context.filesDir, "history.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writing = Mutex()
    private val loaded = CompletableDeferred<Boolean>()

    private val _entries = MutableStateFlow<List<HistoryEntry>>(emptyList())
    val entries: StateFlow<List<HistoryEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            val existed = file.exists()
            val read = runCatching { read() }.getOrDefault(emptyList())
            // Anything stamped before the file was read goes on top of it.
            _entries.update { stamped -> (stamped + read).distinctBy { it.guid }.take(LIMIT) }
            loaded.complete(existed)
        }
    }

    /** [guid] is playing now. Its show is looked up ([feedUrl]) only when there's something to write. */
    fun stamp(guid: String, feedUrl: () -> String) {
        val now = System.currentTimeMillis()
        val top = _entries.value.firstOrNull()
        if (top != null && top.guid == guid && now - top.at < 60_000L) return
        val show = feedUrl()
        _entries.update { list ->
            (listOf(HistoryEntry(guid, show, now)) + list.filterNot { it.guid == guid }).take(LIMIT)
        }
        save()
    }

    fun clear() {
        _entries.value = emptyList()
        save()
    }

    /**
     * The first run with history: start it from the episodes already part
     * way through, dated by when their show was last played, rather than
     * empty. Only when there was no history file before.
     */
    fun seedIfNew(started: () -> List<HistoryEntry>) {
        scope.launch {
            val existed = loaded.await()
            if (existed) return@launch
            val seed = started()
            if (seed.isEmpty()) return@launch
            _entries.update { list -> (list + seed.sortedByDescending { it.at }).distinctBy { it.guid }.take(LIMIT) }
            save()
        }
    }

    private fun save() {
        scope.launch {
            writing.withLock {
                // Always the latest list: a burst of changes writes the end state.
                runCatching { write(_entries.value) }
            }
        }
    }

    private fun read(): List<HistoryEntry> {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val guid = o.optString("guid")
            if (guid.isBlank()) null else HistoryEntry(guid, o.optString("feed"), o.optLong("at"))
        }
    }

    private fun write(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("guid", e.guid)
                put("feed", e.feedUrl)
                put("at", e.at)
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }

    private companion object {
        const val LIMIT = 300
    }
}
