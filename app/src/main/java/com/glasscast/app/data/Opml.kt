package com.glasscast.app.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OpmlEntry(val title: String, val url: String)

/**
 * OPML in and out — the only interchange format podcast apps actually agree on.
 *
 * This is the AntennaPod migration path, and it's their supported one:
 * Settings → Import/Export → OPML export produces a file GlassCast can read
 * directly. Pocket Casts, Overcast and Apple Podcasts all export the same
 * shape.
 *
 * What crosses over is subscriptions. Play positions and played flags don't —
 * OPML has no field for them, and nobody has agreed on an extension. The other
 * option is reading AntennaPod's SQLite database out of its backup, which does
 * carry positions but means depending on the private schema of an app that is
 * free to change it in any release. Not worth the breakage for a one-time
 * migration.
 */
object Opml {

    /**
     * Every <outline> carrying an xmlUrl, at any depth. Categories nest
     * outlines inside outlines and the nesting varies by exporter, so this
     * ignores structure and takes the leaves.
     */
    fun parse(input: InputStream): List<OpmlEntry> {
        val entries = mutableListOf<OpmlEntry>()
        val seen = HashSet<String>()
        try {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(input, null)

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG &&
                    parser.name.equals("outline", ignoreCase = true)
                ) {
                    val url = parser.getAttributeValue(null, "xmlUrl")
                        ?: parser.getAttributeValue(null, "xmlurl")
                    if (!url.isNullOrBlank() && seen.add(url)) {
                        val title = parser.getAttributeValue(null, "text")
                            ?: parser.getAttributeValue(null, "title")
                            ?: ""
                        entries += OpmlEntry(title.trim(), url.trim())
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            // A partial read is better than none: whatever parsed before the
            // malformed element is still a valid list of subscriptions.
        }
        return entries
    }

    fun build(feeds: List<Feed>): String {
        val stamp = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US).format(Date())
        return buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
            append("<opml version=\"2.0\">\n")
            append("  <head>\n")
            append("    <title>GlassCast subscriptions</title>\n")
            append("    <dateCreated>$stamp</dateCreated>\n")
            append("  </head>\n")
            append("  <body>\n")
            feeds.forEach { feed ->
                append("    <outline type=\"rss\" text=\"")
                append(feed.title.escaped())
                append("\" title=\"")
                append(feed.title.escaped())
                append("\" xmlUrl=\"")
                append(feed.url.escaped())
                append("\" />\n")
            }
            append("  </body>\n")
            append("</opml>\n")
        }
    }

    private fun String.escaped(): String = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}

data class ImportProgress(
    val total: Int = 0,
    val done: Int = 0,
    val added: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val running: Boolean = false
)
