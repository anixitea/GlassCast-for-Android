package com.glasscast.app.data

import org.json.JSONObject

data class Feed(
    val url: String,
    val title: String,
    val author: String = "",
    val description: String = "",
    val imageUrl: String = "",
    val addedAt: Long = System.currentTimeMillis(),
    /** Last time playback started from this show, for the recently-played sort. */
    val lastPlayedAt: Long = 0L,
    /** From the last fetch, so refreshes can be conditional. */
    val etag: String = "",
    val lastModified: String = "",
    /** From <itunes:category text="…">, top level and nested. Drives Discover. */
    val categories: List<String> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("title", title)
        put("author", author)
        put("description", description)
        put("imageUrl", imageUrl)
        put("addedAt", addedAt)
        put("lastPlayedAt", lastPlayedAt)
        put("etag", etag)
        put("lastModified", lastModified)
        put("categories", org.json.JSONArray(categories))
    }

    companion object {
        fun fromJson(o: JSONObject) = Feed(
            url = o.optString("url"),
            title = o.optString("title"),
            author = o.optString("author"),
            description = o.optString("description"),
            imageUrl = o.optString("imageUrl"),
            addedAt = o.optLong("addedAt"),
            lastPlayedAt = o.optLong("lastPlayedAt"),
            etag = o.optString("etag"),
            lastModified = o.optString("lastModified"),
            categories = o.optJSONArray("categories")?.let { arr ->
                List(arr.length()) { arr.optString(it) }.filter { it.isNotBlank() }
            }.orEmpty()
        )
    }
}
