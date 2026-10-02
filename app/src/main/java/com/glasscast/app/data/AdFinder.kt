package com.glasscast.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * An ad break, in episode time.
 *
 * [onlyIfLengthMs]: the times belong to a file of that length — SponsorBlock
 * marks a YouTube video — and are used only when the file playing matches it
 * within [LENGTH_TOLERANCE_MS]. A different cut, or the RSS audio with ads
 * inserted, would skip the wrong minutes. Null: the times come from the
 * feed's own chapters or transcript and apply as they are.
 */
data class AdBreak(val startMs: Long, val endMs: Long, val onlyIfLengthMs: Long? = null) {
    fun fits(lengthMs: Long): Boolean =
        onlyIfLengthMs == null || (lengthMs > 0 && kotlin.math.abs(lengthMs - onlyIfLengthMs) <= LENGTH_TOLERANCE_MS)

    companion object {
        const val LENGTH_TOLERANCE_MS = 3_000L
    }
}

/** The breaks that apply to a file this long, joined where they overlap. */
fun List<AdBreak>.usable(lengthMs: Long): List<AdBreak> = AdFinder.merge(filter { it.fits(lengthMs) })

/**
 * Finds ad breaks from what a feed publishes about an episode — its chapters
 * and its transcript. Nothing is listened to or uploaded.
 *
 * **Chapters** named like an ad ("Sponsor", "Ad break", "Mid-roll", "A word
 * from…") are breaks, start to the next chapter.
 *
 * **Transcripts**: a break opens on a sponsor line ("brought to you by",
 * "support for this podcast comes from"…) and runs to a line that returns to
 * the show ("back to the show"), or else to the last ad-sounding line after it
 * (promo codes, "dot com", free trials). An opener with nothing ad-like after
 * it is a mention, not a read, and is left alone. Breaks shorter than 10 s or
 * longer than 3 minutes are dropped as misreads.
 *
 * (SponsorBlock was tried in 1.4 and parked — docs/parked/. [AdBreak.onlyIfLengthMs]
 * is its length check, kept for when it returns.)
 *
 * The honest limit: many hosts insert ads per download. Their times are
 * whatever the published transcript and chapters say, which is right for ads
 * the hosts read themselves and can be off for inserted ones. Most feeds
 * publish neither, and then nothing is found.
 */
object AdFinder {

    suspend fun find(episode: Episode): List<AdBreak> = coroutineScope {
        val chapters = async {
            if (episode.chaptersUrl.isBlank()) emptyList()
            else runCatching { EpisodeExtras.fetchChapters(episode.chaptersUrl) }.getOrDefault(emptyList())
        }
        val cues = async {
            if (episode.transcriptUrl.isBlank()) emptyList()
            else runCatching { EpisodeExtras.fetchTranscript(episode.transcriptUrl) }.getOrDefault(emptyList())
        }
        merge(fromChapters(chapters.await(), episode.durationMs) + fromTranscript(cues.await()))
    }

    private val chapterWords = Regex(
        """\b(ads?|adverts?|advertisements?|sponsors?|sponsored|sponsorship|promos?|commercials?|ad ?breaks?|mid-?rolls?|pre-?rolls?|post-?rolls?|a word from)\b""",
        RegexOption.IGNORE_CASE
    )

    fun fromChapters(chapters: List<Chapter>, durationMs: Long): List<AdBreak> {
        val sorted = chapters.sortedBy { it.startMs }
        return sorted.mapIndexedNotNull { i, chapter ->
            if (!chapterWords.containsMatchIn(chapter.title)) return@mapIndexedNotNull null
            val end = sorted.getOrNull(i + 1)?.startMs ?: durationMs.takeIf { it > 0 } ?: return@mapIndexedNotNull null
            if (end - chapter.startMs in 5_000L..300_000L) AdBreak(chapter.startMs, end) else null
        }
    }

    private val openers = listOf(
        "brought to you by", "sponsored by", "today's sponsor", "todays sponsor", "our sponsor",
        "support for this podcast", "support for the podcast", "support for this show", "support for the show",
        "support for today's", "this episode is supported by", "this podcast is supported by",
        "this show is supported by", "a word from our sponsor", "message from our sponsor",
        "thanks to our sponsor", "is presented by"
    )
    private val adTalk = Regex(
        """(\.com\b|dot com|promo code|use code|offer code|at checkout|percent off|% off|free trial|sign up|first month|terms apply|download the .* app)""",
        RegexOption.IGNORE_CASE
    )
    private val closers = listOf(
        "back to the show", "back to the episode", "back to our show", "back to the interview",
        "back to the conversation", "and now back", "let's get back", "lets get back",
        "now back to", "anyway, back", "okay, back to", "all right, back to", "alright, back to"
    )

    fun fromTranscript(cues: List<TranscriptCue>): List<AdBreak> {
        val breaks = mutableListOf<AdBreak>()
        var i = 0
        while (i < cues.size) {
            val text = cues[i].text.lowercase()
            if (openers.none { it in text }) {
                i++
                continue
            }
            val start = cues[i].startMs
            var closer = -1
            var lastAdTalk = i
            var j = i + 1
            while (j < cues.size && cues[j].startMs - start <= 180_000L) {
                val t = cues[j].text.lowercase()
                if (closers.any { it in t }) {
                    closer = j
                    break
                }
                if (adTalk.containsMatchIn(t) || openers.any { it in t }) lastAdTalk = j
                j++
            }
            val end = when {
                closer >= 0 -> cues[closer].startMs
                lastAdTalk > i -> cues.getOrNull(lastAdTalk + 1)?.startMs ?: (cues[lastAdTalk].startMs + 5_000L)
                else -> {
                    i++
                    continue
                }
            }
            if (end - start in 10_000L..180_000L) breaks += AdBreak(start, end)
            i = (if (closer >= 0) closer else lastAdTalk) + 1
        }
        return breaks
    }

    /** Sorted, with overlapping or touching breaks joined. */
    fun merge(breaks: List<AdBreak>): List<AdBreak> {
        val out = mutableListOf<AdBreak>()
        breaks.sortedBy { it.startMs }.forEach { b ->
            val last = out.lastOrNull()
            if (last != null && b.startMs <= last.endMs + 1_000L) {
                out[out.lastIndex] = AdBreak(last.startMs, maxOf(last.endMs, b.endMs))
            } else {
                out += b
            }
        }
        return out
    }
}
