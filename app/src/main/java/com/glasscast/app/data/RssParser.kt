package com.glasscast.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

data class FeedFetch(
    val feed: Feed,
    val episodes: List<Episode>,
    /** 304 Not Modified — nothing changed, keep what's stored. */
    val notModified: Boolean = false
)

/**
 * XmlPullParser feed reader. Handles the itunes: namespace by matching on the
 * raw tag name, because plenty of feeds in the wild declare the namespace
 * loosely or not at all and namespace-aware matching then silently drops fields.
 */
object RssParser {

    private const val UA = "GlassCast/0.1 (Android)"

    fun fetch(url: String, etag: String = "", lastModified: String = ""): FeedFetch? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml, */*")
                if (etag.isNotBlank()) setRequestProperty("If-None-Match", etag)
                if (lastModified.isNotBlank()) setRequestProperty("If-Modified-Since", lastModified)
            }
            if (conn.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                return FeedFetch(Feed(url = url, title = ""), emptyList(), notModified = true)
            }
            if (conn.responseCode !in 200..299) return null

            val newEtag = conn.getHeaderField("ETag").orEmpty()
            val newLastMod = conn.getHeaderField("Last-Modified").orEmpty()
            val parsed = conn.inputStream.use { parse(it, url) } ?: return null
            parsed.copy(feed = parsed.feed.copy(etag = newEtag, lastModified = newLastMod))
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parse(input: InputStream, feedUrl: String): FeedFetch? {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var channelTitle = ""
        var channelAuthor = ""
        var channelDescription = ""
        var channelImage = ""
        val channelCategories = mutableListOf<String>()

        var inItem = false
        var inChannelImageTag = false

        // Per-item accumulators
        var title = ""
        var guid = ""
        var link = ""
        var enclosureUrl = ""
        var itemImage = ""
        var itemDescription = ""
        var itemSummary = ""
        var itemContent = ""
        var pubDate = 0L
        var duration = 0L
        var episodeNumber = 0
        var season = 0
        var chaptersUrl = ""
        var transcriptUrl = ""
        var transcriptRank = 0

        val episodes = mutableListOf<Episode>()

        fun resetItem() {
            title = ""; guid = ""; link = ""; enclosureUrl = ""; itemImage = ""
            itemDescription = ""; itemSummary = ""; itemContent = ""
            pubDate = 0L; duration = 0L; episodeNumber = 0; season = 0
            chaptersUrl = ""; transcriptUrl = ""; transcriptRank = 0
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT).orEmpty()
            when (event) {
                XmlPullParser.START_TAG -> when (name) {
                    "item" -> { inItem = true; resetItem() }
                    "image" -> if (!inItem) inChannelImageTag = true
                    "title" -> {
                        val t = readText(parser)
                        if (inItem) title = t
                        else if (!inChannelImageTag && channelTitle.isEmpty()) channelTitle = t
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href").orEmpty()
                        if (href.isNotBlank()) {
                            if (inItem) itemImage = href
                            else if (channelImage.isEmpty()) channelImage = href
                        }
                    }
                    "url" -> if (inChannelImageTag && channelImage.isEmpty()) channelImage = readText(parser)
                    // Categories are attributes, not text, and can nest
                    // (Society & Culture › Documentary). Both levels are kept;
                    // Discover matches on either.
                    "itunes:category" -> if (!inItem) {
                        val text = parser.getAttributeValue(null, "text").orEmpty().trim()
                        if (text.isNotBlank() && text !in channelCategories) channelCategories += text
                    }
                    "itunes:author", "managingeditor" ->
                        if (!inItem && channelAuthor.isEmpty()) channelAuthor = readText(parser)
                    "description" -> {
                        val t = readText(parser)
                        if (inItem) itemDescription = t else if (channelDescription.isEmpty()) channelDescription = t
                    }
                    "itunes:summary" -> {
                        val t = readText(parser)
                        if (inItem) itemSummary = t else if (channelDescription.isEmpty()) channelDescription = t
                    }
                    "content:encoded" -> if (inItem) itemContent = readText(parser)
                    "guid" -> if (inItem) guid = readText(parser)
                    "link" -> if (inItem) link = readText(parser)
                    "pubdate" -> if (inItem) pubDate = parseRfc822(readText(parser))
                    "itunes:duration" -> if (inItem) duration = parseDuration(readText(parser))
                    "itunes:episode" -> if (inItem) episodeNumber = readText(parser).toIntOrNull() ?: 0
                    "itunes:season" -> if (inItem) season = readText(parser).toIntOrNull() ?: 0
                    // Podcasting 2.0. Namespaced tags are matched by raw name for
                    // the same reason as itunes: feeds declare them loosely.
                    "podcast:chapters" -> if (inItem) {
                        chaptersUrl = parser.getAttributeValue(null, "url").orEmpty()
                    }
                    "podcast:transcript" -> if (inItem) {
                        val type = parser.getAttributeValue(null, "type").orEmpty().lowercase(Locale.ROOT)
                        val u = parser.getAttributeValue(null, "url").orEmpty()
                        // Feeds often list several formats. Timed ones only — a
                        // transcript is worth having because it follows along.
                        // SRT and VTT rank above the Podcasting 2.0 JSON format
                        // (Buzzsprout, Captivate and others publish only that),
                        // which ranks above nothing; the first of the best wins.
                        val rank = when {
                            type.contains("srt") || type.contains("vtt") -> 2
                            type.contains("json") -> 1
                            else -> 0
                        }
                        if (u.isNotBlank() && rank > transcriptRank) {
                            transcriptUrl = u
                            transcriptRank = rank
                        }
                    }
                    "enclosure" -> if (inItem) {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val u = parser.getAttributeValue(null, "url").orEmpty()
                        // Audio only — plenty of feeds attach images as enclosures too.
                        if (u.isNotBlank() && (type.startsWith("audio") || type.isBlank())) enclosureUrl = u
                    }
                }

                XmlPullParser.END_TAG -> when (name) {
                    "image" -> inChannelImageTag = false
                    "item" -> {
                        inItem = false
                        if (enclosureUrl.isNotBlank()) {
                            val id = guid.ifBlank { link.ifBlank { enclosureUrl } }
                            episodes += Episode(
                                guid = id,
                                feedUrl = feedUrl,
                                title = title.ifBlank { "Untitled episode" },
                                audioUrl = enclosureUrl,
                                description = itemContent.ifBlank { itemDescription.ifBlank { itemSummary } },
                                imageUrl = itemImage,
                                pubDate = pubDate,
                                durationMs = duration,
                                episodeNumber = episodeNumber,
                                season = season,
                                chaptersUrl = chaptersUrl,
                                transcriptUrl = transcriptUrl
                            )
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (channelTitle.isBlank() && episodes.isEmpty()) return null

        // Dedupe by guid, keeping the first (feeds are newest-first).
        val seen = HashSet<String>()
        val deduped = episodes.filter { seen.add(it.guid) }

        return FeedFetch(
            feed = Feed(
                url = feedUrl,
                title = channelTitle.ifBlank { "Untitled show" },
                author = channelAuthor,
                description = channelDescription,
                imageUrl = channelImage,
                categories = channelCategories.toList()
            ),
            episodes = deduped
        )
    }

    /**
     * Defensive: mixed content inside an element makes nextTag() throw, and one
     * malformed <description> should not take the whole feed down with it.
     */
    private fun readText(parser: XmlPullParser): String = try {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text.orEmpty().trim()
            parser.nextTag()
        }
        result
    } catch (_: Exception) {
        ""
    }

    /** itunes:duration is "3600", "1:02:03" or "12:34". */
    private fun parseDuration(raw: String): Long {
        if (raw.isBlank()) return 0L
        val parts = raw.split(":").mapNotNull { it.trim().toDoubleOrNull() }
        return when (parts.size) {
            1 -> (parts[0] * 1000).toLong()
            2 -> ((parts[0] * 60 + parts[1]) * 1000).toLong()
            3 -> ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000).toLong()
            else -> 0L
        }
    }

    private val rfc822Formats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm zzz",
        "dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ"
    )

    private fun parseRfc822(raw: String): Long {
        if (raw.isBlank()) return 0L
        for (f in rfc822Formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                if (f.endsWith("'Z'")) sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(raw)?.time ?: continue
            } catch (_: Exception) { /* try the next shape */ }
        }
        return 0L
    }
}
