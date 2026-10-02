package com.glasscast.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class DirectoryResult(
    val title: String,
    val author: String,
    val feedUrl: String,
    val artworkUrl: String,
    val episodeCount: Int
)

/**
 * A directory is anything that turns a search term into feed URLs.
 *
 * Podcast Index has better coverage but needs credentials, so iTunes goes
 * first — free, no key, and good enough that most people never notice. Swapping
 * or stacking implementations later touches nothing outside this file.
 */
interface PodcastDirectory {
    val name: String
    suspend fun search(term: String): List<DirectoryResult>

    /** What to show before anyone types. [genreId] of 0 means all podcasts. */
    suspend fun top(genreId: Int = 0, limit: Int = 20): List<DirectoryResult>
}

data class Genre(val id: Int, val label: String)

/**
 * iTunes' podcast genre ids. Hard-coded because they are stable and the
 * endpoint that lists them costs a request to tell you what hasn't changed
 * since 2008.
 */
val PodcastGenres = listOf(
    Genre(0, "Top"),
    Genre(1303, "Comedy"),
    Genre(1489, "News"),
    Genre(1324, "Society"),
    Genre(1318, "Technology"),
    Genre(1304, "Education"),
    Genre(1320, "Fiction"),
    Genre(1488, "True Crime"),
    Genre(1310, "Music"),
    Genre(1545, "Sports"),
    Genre(1512, "Health"),
    Genre(1321, "Business"),
    Genre(1533, "Science"),
    Genre(1301, "Arts"),
    Genre(1314, "Religion"),
    Genre(1487, "History")
)

object ITunesDirectory : PodcastDirectory {

    override val name = "iTunes"

    override suspend fun search(term: String): List<DirectoryResult> = withContext(Dispatchers.IO) {
        if (term.isBlank()) return@withContext emptyList()

        // Results are region-scoped, so ask in the user's own store rather than
        // defaulting to the US — otherwise local shows are missing entirely.
        val country = Locale.getDefault().country.ifBlank { "US" }
        val encoded = URLEncoder.encode(term.trim(), "UTF-8")
        val url = "https://itunes.apple.com/search" +
            "?media=podcast&entity=podcast&limit=25&country=$country&term=$encoded"

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "GlassCast/0.1 (Android)")
            }
            if (conn.responseCode !in 200..299) return@withContext emptyList()

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()

            // iTunes returns the same show more than once often enough that a
            // keyed LazyColumn will throw on the duplicate. Dedupe at the source.
            val seen = HashSet<String>()
            buildList {
                for (i in 0 until results.length()) {
                    val o = results.optJSONObject(i) ?: continue
                    val feed = o.optString("feedUrl")
                    if (feed.isBlank() || !seen.add(feed)) continue
                    add(
                        DirectoryResult(
                            title = o.optString("collectionName").ifBlank { o.optString("trackName") },
                            author = o.optString("artistName"),
                            feedUrl = feed,
                            artworkUrl = o.optString("artworkUrl600")
                                .ifBlank { o.optString("artworkUrl100") },
                            episodeCount = o.optInt("trackCount")
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * The charts feed ranks shows but doesn't carry feed URLs, so the ids it
     * returns are resolved through the lookup endpoint — one extra request for
     * the whole page rather than one per show.
     */
    override suspend fun top(genreId: Int, limit: Int): List<DirectoryResult> =
        withContext(Dispatchers.IO) {
            val country = Locale.getDefault().country.ifBlank { "US" }.lowercase(Locale.ROOT)
            val genrePath = if (genreId > 0) "genre=$genreId/" else ""
            val chartUrl =
                "https://itunes.apple.com/$country/rss/toppodcasts/limit=$limit/${genrePath}json"

            // Built explicitly rather than with buildList. An early
            // return@withContext inside a buildList block leaves the compiler
            // with nothing to infer the element type from, and it fails on the
            // lambda rather than on the return.
            val ids = mutableListOf<String>()
            try {
                val body = get(chartUrl)
                val entries = if (body == null) null
                else JSONObject(body).optJSONObject("feed")?.optJSONArray("entry")

                if (entries != null) {
                    for (i in 0 until entries.length()) {
                        val id = entries.optJSONObject(i)
                            ?.optJSONObject("id")
                            ?.optJSONObject("attributes")
                            ?.optString("im:id")
                            .orEmpty()
                        if (id.isNotBlank()) ids.add(id)
                    }
                }
            } catch (_: Exception) {
                ids.clear()
            }

            if (ids.isEmpty()) emptyList() else lookup(ids)
        }

    private fun lookup(ids: List<String>): List<DirectoryResult> = try {
        val body = get("https://itunes.apple.com/lookup?id=" + ids.joinToString(","))
        val results = if (body == null) null else JSONObject(body).optJSONArray("results")
        if (results == null) {
            emptyList()
        } else {
            val byId = HashMap<String, DirectoryResult>()
            for (i in 0 until results.length()) {
                val o = results.optJSONObject(i) ?: continue
                val feed = o.optString("feedUrl")
                if (feed.isBlank()) continue
                byId[o.optLong("collectionId").toString()] = DirectoryResult(
                    title = o.optString("collectionName").ifBlank { o.optString("trackName") },
                    author = o.optString("artistName"),
                    feedUrl = feed,
                    artworkUrl = o.optString("artworkUrl600")
                        .ifBlank { o.optString("artworkUrl100") },
                    episodeCount = o.optInt("trackCount")
                )
            }
            // Chart order is the entire point, and lookup does not preserve it.
            ids.mapNotNull { byId[it] }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "GlassCast/0.1 (Android)")
            }
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
