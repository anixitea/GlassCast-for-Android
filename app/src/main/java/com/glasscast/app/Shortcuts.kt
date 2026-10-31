package com.glasscast.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.glasscast.app.ui.tr

/**
 * The launcher shortcuts (long-press the app icon): Resume, Latest, Search and
 * Downloads, plus the widget's "open the player".
 *
 * Dynamic rather than declared in XML, so the labels come from tr() in the
 * phone's language like the rest of the app (an XML shortcut can only take a
 * string resource, and GlassCast's text doesn't live in resources). They're
 * set each time the app opens, so they appear from the first launch on, and
 * a language change is picked up on the next one.
 *
 * Each is an action on GlassCastActivity (handled in GlassCastActivity.handleIncoming,
 * then by GlassCastRoot).
 */
object Shortcuts {
    const val RESUME = "com.glasscast.app.shortcut.RESUME"
    const val LATEST = "com.glasscast.app.shortcut.LATEST"
    const val SEARCH = "com.glasscast.app.shortcut.SEARCH"
    const val DOWNLOADS = "com.glasscast.app.shortcut.DOWNLOADS"
    /** The widget's card: open the app on the full player. */
    const val OPEN_PLAYER = "com.glasscast.app.OPEN_PLAYER"

    val actions = setOf(RESUME, LATEST, SEARCH, DOWNLOADS, OPEN_PLAYER)

    /** Off the main thread: the shortcut service is a binder call and a disk write. */
    fun publish(context: Context) {
        // Pinned to whichever launcher entry is on (the app icon picked in
        // Settings), so they hang off the icon on the home screen; a shortcut
        // belonging to a disabled alias would vanish with it.
        val entry = com.glasscast.app.data.AppIcons.current(context).component(context)
        val list = listOf(
            shortcut(context, entry, "resume", RESUME, tr("Resume"), R.drawable.ic_shortcut_resume, rank = 0),
            shortcut(context, entry, "latest", LATEST, tr("Latest"), R.drawable.ic_shortcut_latest, rank = 1),
            shortcut(context, entry, "search", SEARCH, tr("Search"), R.drawable.ic_shortcut_search, rank = 2),
            shortcut(context, entry, "downloads", DOWNLOADS, tr("Downloads"), R.drawable.ic_shortcut_downloads, rank = 3)
        )
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, list) }
    }

    private fun shortcut(context: Context, entry: android.content.ComponentName, id: String, action: String, label: String, icon: Int, rank: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(label)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setRank(rank)
            .setActivity(entry)
            .setIntent(Intent(context, GlassCastActivity::class.java).setAction(action))
            .build()
}
