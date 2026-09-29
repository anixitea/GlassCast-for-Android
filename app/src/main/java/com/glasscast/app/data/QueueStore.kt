package com.glasscast.app.data

import android.content.Context
import org.json.JSONArray

/**
 * The queue's durable half.
 *
 * ExoPlayer's playlist is the source of truth while the process is alive — the
 * spec is right that managing a parallel list would only create two things to
 * disagree. But the playlist dies with the process, so the guids and the
 * current index are mirrored here on every change and replayed on next launch.
 * Positions are not stored: they already live per-episode in [FeedStore].
 */
class QueueStore(context: Context) {

    private val prefs = context.getSharedPreferences("glasscast_queue", Context.MODE_PRIVATE)

    fun save(guids: List<String>, currentIndex: Int) {
        prefs.edit()
            .putString(KEY_GUIDS, JSONArray().apply { guids.forEach { put(it) } }.toString())
            .putInt(KEY_INDEX, currentIndex.coerceAtLeast(0))
            .apply()
    }

    fun load(): Pair<List<String>, Int> {
        val guids = runCatching {
            val arr = JSONArray(prefs.getString(KEY_GUIDS, "[]"))
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
        return guids to prefs.getInt(KEY_INDEX, 0)
    }

    private companion object {
        const val KEY_GUIDS = "guids"
        const val KEY_INDEX = "index"
    }
}
