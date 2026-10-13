package com.glasscast.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class Chapter(
    val startMs: Long,
    val title: String,
    val imageUrl: String = ""
)

data class TranscriptCue(
    val startMs: Long,
    val text: String
)

/**
 * Chapters and transcripts, where the feed publishes them.
 *
 * This is Podcasting 2.0 only, and worth being blunt about: the iTunes Search
 * API exposes neither, and nothing generates them for shows that don't ship
 * them. In practice a minority of feeds carry <podcast:chapters> and fewer
 * carry <podcast:transcript>, so the UI has to treat both as absent by default
 * rather than as a section that's merely empty.
 *
 * ID3 chapters embedded in the audio file are the other source, but reading
 * them means fetching enough of the file to parse its tags — that belongs with
 * downloads, not here.
 */
object EpisodeExtras {

    suspend fun fetchChapters(url: String): List<Chapter> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext emptyList()
        val body = get(url) ?: return@withContext emptyList()
        try {
            val arr = JSONObject(body).optJSONArray("chapters") ?: return@withContext emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("title")
                    if (title.isBlank()) continue
                    // startTime is seconds, and often fractional.
                    add(
                        Chapter(
                            startMs = (o.optDouble("startTime", 0.0) * 1000).toLong(),
                            title = title,
                            imageUrl = o.optString("img")
                        )
                    )
                }
            }.sortedBy { it.startMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * SRT, WebVTT, or Podcasting 2.0 JSON — told apart by content, since
     * feeds' declared types are unreliable.
     */
    suspend fun fetchTranscript(url: String): List<TranscriptCue> = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext emptyList()
        val body = get(url) ?: return@withContext emptyList()
        if (body.trimStart().startsWith("{")) return@withContext parseJsonTranscript(body)
        try {
            val cues = mutableListOf<TranscriptCue>()
            var pendingStart = -1L
            val text = StringBuilder()

            fun flush() {
                if (pendingStart >= 0 && text.isNotBlank()) {
                    cues += TranscriptCue(pendingStart, text.toString().trim())
                }
                pendingStart = -1L
                text.setLength(0)
            }

            body.lineSequence().forEach { raw ->
                val line = raw.trim()
                when {
                    line.isEmpty() -> flush()
                    line.contains("-->") -> {
                        flush()
                        pendingStart = parseCueTime(line.substringBefore("-->").trim())
                    }
                    // Cue numbers in SRT, and the WEBVTT header.
                    pendingStart < 0 && (line.toIntOrNull() != null || line.startsWith("WEBVTT")) -> Unit
                    pendingStart >= 0 -> {
                        if (text.isNotEmpty()) text.append(' ')
                        text.append(stripTags(line))
                    }
                }
            }
            flush()
            cues
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * The Podcasting 2.0 JSON transcript: `{"segments": [{"startTime", "body",
     * "speaker"?}, …]}`, times in seconds. Hosts often emit one segment per
     * word or phrase, which would make a list you can't read, so segments are
     * joined into lines that end at a full stop, a change of speaker, or about
     * twelve seconds — close to how SRT files break.
     */
    private fun parseJsonTranscript(body: String): List<TranscriptCue> = try {
        val segments = org.json.JSONObject(body).optJSONArray("segments")
        val cues = mutableListOf<TranscriptCue>()
        if (segments != null) {
            var start = -1L
            var speaker = ""
            val text = StringBuilder()
            fun flush() {
                if (start >= 0 && text.isNotBlank()) cues += TranscriptCue(start, text.toString().trim())
                start = -1L
                text.setLength(0)
            }
            for (i in 0 until segments.length()) {
                val seg = segments.optJSONObject(i) ?: continue
                val words = seg.optString("body").trim()
                if (words.isEmpty()) continue
                val at = (seg.optDouble("startTime", -1.0) * 1000).toLong()
                val who = seg.optString("speaker")
                if (who.isNotEmpty() && who != speaker && text.isNotEmpty()) flush()
                if (who.isNotEmpty()) speaker = who
                if (start < 0) start = at.coerceAtLeast(0L)
                if (text.isNotEmpty()) text.append(' ')
                text.append(words)
                val sentenceEnds = words.endsWith('.') || words.endsWith('?') || words.endsWith('!')
                if (sentenceEnds || (at >= 0 && at - start > 12_000)) flush()
            }
            flush()
        }
        cues.sortedBy { it.startMs }
    } catch (_: Exception) {
        emptyList()
    }

    /** "00:01:23,456" or "01:23.456". */
    private fun parseCueTime(raw: String): Long {
        val normalised = raw.replace(',', '.')
        val parts = normalised.split(":").mapNotNull { it.trim().toDoubleOrNull() }
        return when (parts.size) {
            3 -> ((parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000).toLong()
            2 -> ((parts[0] * 60 + parts[1]) * 1000).toLong()
            1 -> (parts[0] * 1000).toLong()
            else -> -1L
        }
    }

    private fun stripTags(s: String) = s.replace(Regex("<[^>]*>"), "").trim()

    private fun get(url: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 12_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "GlassCast/0.1 (Android)")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Timestamps written into show notes — "01:23:45 Topic" — are the fallback
     * when a feed publishes no chapters at all, which is most of them. Not as
     * good as real chapter data, but it's what hosts actually write.
     */
    private val NOTE_TIMESTAMP = Regex("""(?m)^\s*\(?(\d{1,2}:\d{2}(?::\d{2})?)\)?\s*[-–—:]?\s*(.{2,100})$""")

    fun chaptersFromNotes(description: String): List<Chapter> {
        val plain = description
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</p>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]*>"), "")
        return NOTE_TIMESTAMP.findAll(plain)
            .mapNotNull { match ->
                val time = parseCueTime(match.groupValues[1])
                val title = match.groupValues[2].trim().trim('-', '–', '—', ':', ' ')
                if (time < 0 || title.isBlank()) null else Chapter(time, title)
            }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
            .toList()
    }

    fun formatChapterTime(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }
}
