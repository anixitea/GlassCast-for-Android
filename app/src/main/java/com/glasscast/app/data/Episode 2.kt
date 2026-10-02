package com.glasscast.app.data

import org.json.JSONObject

data class Episode(
    /** RSS <guid>, falling back to the enclosure URL. Dedupe key. */
    val guid: String,
    val feedUrl: String,
    val title: String,
    val audioUrl: String,
    val description: String = "",
    val imageUrl: String = "",
    val pubDate: Long = 0L,
    /** From itunes:duration; often wrong or absent. Lesson 4: reconcile with the player. */
    val durationMs: Long = 0L,
    val positionMs: Long = 0L,
    val played: Boolean = false,
    val episodeNumber: Int = 0,
    val season: Int = 0,
    /** Podcasting 2.0 <podcast:chapters>, JSON. Absent from most feeds. */
    val chaptersUrl: String = "",
    /** Podcasting 2.0 <podcast:transcript>, SRT or VTT. Rarer still. */
    val transcriptUrl: String = ""
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Anything past 95% counts as finished. */
    val effectivelyPlayed: Boolean
        get() = played || (durationMs > 0 && progress >= 0.95f)

    fun toJson(): JSONObject = JSONObject().apply {
        put("guid", guid)
        put("feedUrl", feedUrl)
        put("title", title)
        put("audioUrl", audioUrl)
        put("description", description)
        put("imageUrl", imageUrl)
        put("pubDate", pubDate)
        put("durationMs", durationMs)
        put("positionMs", positionMs)
        put("played", played)
        put("episodeNumber", episodeNumber)
        put("season", season)
        put("chaptersUrl", chaptersUrl)
        put("transcriptUrl", transcriptUrl)
    }

    companion object {
        fun fromJson(o: JSONObject) = Episode(
            guid = o.optString("guid"),
            feedUrl = o.optString("feedUrl"),
            title = o.optString("title"),
            audioUrl = o.optString("audioUrl"),
            description = o.optString("description"),
            imageUrl = o.optString("imageUrl"),
            pubDate = o.optLong("pubDate"),
            durationMs = o.optLong("durationMs"),
            positionMs = o.optLong("positionMs"),
            played = o.optBoolean("played"),
            episodeNumber = o.optInt("episodeNumber"),
            season = o.optInt("season"),
            chaptersUrl = o.optString("chaptersUrl"),
            transcriptUrl = o.optString("transcriptUrl")
        )
    }
}
