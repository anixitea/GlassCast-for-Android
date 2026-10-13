package com.glasscast.app.data

import android.content.Context
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

/** A place in an episode worth coming back to, with an optional memo. */
data class Bookmark(
    val id: Long,
    val guid: String,
    val positionMs: Long,
    val memo: String,
    val createdAt: Long,
    /** When the memo last changed, so syncing keeps the newest edit. */
    val editedAt: Long = createdAt
)

/**
 * Bookmarks for every episode, in one small JSON file. Read off the main
 * thread at start-up; every change is written behind, in order.
 *
 * Kept by episode guid, so they survive a show being refreshed. They're the
 * person's own notes, so unfollowing a show doesn't delete them.
 */
class BookmarkStore(context: Context) {

    private val file = File(context.filesDir, "bookmarks.json")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writing = Mutex()

    private val _all = MutableStateFlow<List<Bookmark>>(emptyList())
    val all: StateFlow<List<Bookmark>> = _all.asStateFlow()

    init {
        // Anything added before the file has been read is kept alongside it.
        scope.launch {
            val loaded = runCatching { read() }.getOrDefault(emptyList())
            _all.update { added -> loaded + added.filter { a -> loaded.none { it.id == a.id } } }
        }
    }

    /** Adds one at [positionMs] and returns it. */
    fun add(guid: String, positionMs: Long): Bookmark {
        val now = System.currentTimeMillis()
        // Ids are creation times, nudged past any taken in the same millisecond.
        val id = maxOf(now, (_all.value.maxOfOrNull { it.id } ?: 0L) + 1)
        val bookmark = Bookmark(id, guid, positionMs.coerceAtLeast(0L), "", now)
        _all.update { it + bookmark }
        save()
        return bookmark
    }

    fun setMemo(id: Long, memo: String) {
        val now = System.currentTimeMillis()
        _all.update { list -> list.map { if (it.id == id) it.copy(memo = memo.trim(), editedAt = now) else it } }
        save()
    }

    /** Told when one is deleted, so syncing can delete it on other phones (GoogleAccount). */
    var onRemoved: ((Long) -> Unit)? = null

    /**
     * Another phone's bookmarks, from syncing: by id, the newer memo wins,
     * and anything deleted on either phone ([deleted]) goes. Ids stay as they
     * are, so the same bookmark is the same on every phone.
     */
    fun mergeSynced(remote: List<Bookmark>, deleted: Set<Long>) {
        _all.update { list ->
            val byId = LinkedHashMap<Long, Bookmark>()
            list.forEach { byId[it.id] = it }
            remote.forEach { r ->
                val l = byId[r.id]
                if (l == null || r.editedAt > l.editedAt) byId[r.id] = r
            }
            byId.values.filter { it.id !in deleted }
        }
        save()
    }

    /**
     * Bookmarks from a backup, added beside the ones already here. One that's
     * already here (the same episode, place and memo) isn't added twice, so
     * restoring the same backup again changes nothing.
     */
    fun restore(incoming: List<Bookmark>) {
        if (incoming.isEmpty()) return
        _all.update { list ->
            val have = list.mapTo(HashSet()) { Triple(it.guid, it.positionMs, it.memo) }
            var nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1
            val added = incoming
                .filter { Triple(it.guid, it.positionMs, it.memo) !in have }
                .map { b ->
                    // Ids are only unique on one phone; renumber past ours.
                    val id = if (list.none { it.id == b.id }) b.id else nextId++
                    b.copy(id = id)
                }
            list + added
        }
        save()
    }

    fun remove(id: Long) {
        _all.update { list -> list.filterNot { it.id == id } }
        save()
        onRemoved?.invoke(id)
    }

    private fun save() {
        scope.launch {
            writing.withLock {
                // Always the latest list: a burst of changes writes the end state.
                runCatching { write(_all.value) }
            }
        }
    }

    private fun read(): List<Bookmark> {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Bookmark(
                id = o.getLong("id"),
                guid = o.getString("guid"),
                positionMs = o.getLong("position"),
                memo = o.optString("memo"),
                createdAt = o.optLong("created"),
                editedAt = o.optLong("edited", o.optLong("created"))
            )
        }
    }

    private fun write(list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("id", b.id)
                put("guid", b.guid)
                put("position", b.positionMs)
                put("memo", b.memo)
                put("created", b.createdAt)
                put("edited", b.editedAt)
            })
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(arr.toString())
            tmp.delete()
        }
    }
}
