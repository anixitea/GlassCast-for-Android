package com.glasscast.app.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.glasscast.app.MainActivity
import com.glasscast.app.R
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.data.ImageStore

/**
 * New-episode notifications.
 *
 * One per episode, carrying the cover, grouped under a summary once there's
 * more than one — so a morning with four new episodes is one expandable entry,
 * not four buzzes. Capped at six individual notifications per run; the summary
 * still counts them all.
 *
 * Tapping one opens that show's page.
 */
object NewEpisodeNotifier {
    const val CHANNEL = "new_episodes"
    const val EXTRA_OPEN_FEED = "com.glasscast.OPEN_FEED"
    private const val GROUP = "com.glasscast.NEW_EPISODES"
    private const val SUMMARY_ID = 0x6C0A57

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL,
            "New episodes",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "When a show you follow publishes a new episode"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun post(context: Context, items: List<Pair<Feed, Episode>>, images: ImageStore) {
        if (!canPost(context)) return
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)

        items.take(6).forEach { (feed, episode) ->
            val art = runCatching {
                images.load(episode.imageUrl.ifBlank { feed.imageUrl }, 256)
            }.getOrNull()

            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_mark)
                .setContentTitle(feed.title)
                .setContentText(episode.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(episode.title))
                .setLargeIcon(art)
                .setWhen(episode.pubDate.takeIf { it > 0 } ?: System.currentTimeMillis())
                .setShowWhen(true)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setContentIntent(openShow(context, feed.url))
                .build()
            runCatching { manager.notify(episode.guid.hashCode(), notification) }
        }

        if (items.size > 1) {
            val inbox = NotificationCompat.InboxStyle()
                .setSummaryText("${items.size} new episodes")
            items.take(6).forEach { (feed, episode) -> inbox.addLine("${feed.title} — ${episode.title}") }

            val summary = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_mark)
                .setContentTitle("${items.size} new episodes")
                .setContentText(items.map { it.first.title }.distinct().joinToString(", "))
                .setStyle(inbox)
                .setGroup(GROUP)
                .setGroupSummary(true)
                .setAutoCancel(true)
                .setContentIntent(openShow(context, null))
                .build()
            runCatching { manager.notify(SUMMARY_ID, summary) }
        }
    }

    private fun openShow(context: Context, feedUrl: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (feedUrl != null) putExtra(EXTRA_OPEN_FEED, feedUrl)
        }
        return PendingIntent.getActivity(
            context,
            feedUrl?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }
}
