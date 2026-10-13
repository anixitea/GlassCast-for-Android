package com.glasscast.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.palette.graphics.Palette
import com.glasscast.app.GlassCastApp
import com.glasscast.app.MainActivity
import com.glasscast.app.R
import com.glasscast.app.Shortcuts
import com.glasscast.app.player.PlaybackService
import com.glasscast.app.ui.tr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The home-screen widget: the episode that's playing (or was last), its show,
 * and back, play/pause and forward, on a card in the cover's color.
 *
 * The playback service tells it whenever the episode or play state changes
 * ([onPlayer]); with the service gone it shows the saved queue's first
 * episode, the one Resume would play. Tapping the card opens the player.
 *
 * The buttons are media-button intents to the playback service, the same ones
 * Media3's notification sends. Play is a foreground-service start, because
 * from a stopped app it starts playback (Media3 resumes the saved queue
 * through AutoLibrary.onPlaybackResumption) and playback is what makes the
 * service foreground. Back and forward never start the service: with nothing
 * playing there's nothing to skip.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        refresh(context) { pending.finish() }
    }

    companion object {
        /** The service's last word: the episode and whether it's meant to be playing. */
        @Volatile
        private var fromPlayer: Pair<String?, Boolean>? = null

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private var job: Job? = null

        /** From the playback service when the episode or play/pause changes. */
        fun onPlayer(context: Context, guid: String?, playing: Boolean) {
            if (fromPlayer == (guid to playing)) return
            fromPlayer = guid to playing
            refresh(context)
        }

        /** Redraws every placed widget; cheap and silent when there are none. Main thread. */
        fun refresh(context: Context, done: (() -> Unit)? = null) {
            val app = context.applicationContext as GlassCastApp
            val manager = AppWidgetManager.getInstance(app)
            val ids = runCatching {
                manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
            }.getOrNull()
            if (ids == null || ids.isEmpty()) {
                done?.invoke()
                return
            }
            // The newest state wins over one still being drawn.
            job?.cancel()
            job = scope.launch {
                try {
                    val views = build(app)
                    runCatching { manager.updateAppWidget(ids, views) }
                } finally {
                    done?.invoke()
                }
            }
        }

        private suspend fun build(app: GlassCastApp): RemoteViews {
            val store = app.feedStore
            store.awaitLoaded()
            val (guid, playing) = fromPlayer ?: (app.queueStore.load().first.firstOrNull() to false)
            val episode = guid?.let { store.episodeByGuid(it) }
            val feed = episode?.let { store.feedFor(it) }
            val views = RemoteViews(app.packageName, R.layout.widget_now_playing)

            views.setOnClickPendingIntent(R.id.widget_body, openPlayer(app))

            if (episode == null) {
                views.setTextViewText(R.id.widget_title, app.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_show, "")
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
                listOf(R.id.widget_back, R.id.widget_play, R.id.widget_forward).forEach {
                    views.setViewVisibility(it, android.view.View.GONE)
                }
                return views
            }

            views.setTextViewText(R.id.widget_title, episode.title)
            views.setTextViewText(R.id.widget_show, feed?.title.orEmpty())

            val density = app.resources.displayMetrics.density
            val coverPx = (52 * density).toInt().coerceIn(64, 256)
            val art = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }
            val cover = runCatching { app.imageStore.load(art, coverPx) }.getOrNull()
            if (cover != null) {
                views.setImageViewBitmap(R.id.widget_cover, rounded(cover, coverPx, 12 * density))
                views.setInt(R.id.widget_bg, "setColorFilter", cardColor(cover))
            } else {
                views.setImageViewResource(R.id.widget_cover, R.mipmap.ic_launcher)
            }

            val seconds = app.settings.skipSeconds.value
            val short = seconds <= 15
            views.setImageViewResource(R.id.widget_back, if (short) R.drawable.ic_replay_15 else R.drawable.ic_replay_30)
            views.setImageViewResource(R.id.widget_forward, if (short) R.drawable.ic_forward_15 else R.drawable.ic_forward_30)
            views.setContentDescription(R.id.widget_back, tr("Back {0} seconds", seconds))
            views.setContentDescription(R.id.widget_forward, tr("Forward {0} seconds", seconds))
            views.setImageViewResource(R.id.widget_play, if (playing) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            views.setContentDescription(R.id.widget_play, if (playing) tr("Pause") else tr("Play"))

            views.setOnClickPendingIntent(R.id.widget_play, mediaButton(app, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, foreground = !playing))
            views.setOnClickPendingIntent(R.id.widget_back, mediaButton(app, KeyEvent.KEYCODE_MEDIA_REWIND, foreground = false))
            views.setOnClickPendingIntent(R.id.widget_forward, mediaButton(app, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, foreground = false))
            return views
        }

        private fun openPlayer(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setAction(Shortcuts.OPEN_PLAYER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        private fun mediaButton(context: Context, keyCode: Int, foreground: Boolean): PendingIntent {
            val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
                .setComponent(ComponentName(context, PlaybackService::class.java))
                .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            // Distinct request codes, or the three buttons would share one intent.
            val request = WIDGET_REQUEST + keyCode
            return if (foreground) {
                PendingIntent.getForegroundService(context, request, intent, flags)
            } else {
                PendingIntent.getService(context, request, intent, flags)
            }
        }

        /** The cover's deep color, dark enough that white text reads on it. */
        private fun cardColor(cover: Bitmap): Int {
            val palette = runCatching { Palette.from(cover).maximumColorCount(16).generate() }.getOrNull()
            val base = palette?.darkMutedSwatch?.rgb
                ?: palette?.darkVibrantSwatch?.rgb
                ?: palette?.dominantSwatch?.rgb
                ?: Color.rgb(46, 34, 56)
            val hsv = FloatArray(3)
            Color.colorToHSV(base, hsv)
            hsv[2] = hsv[2].coerceAtMost(0.34f)
            return Color.HSVToColor(hsv)
        }

        private fun rounded(source: Bitmap, size: Int, radius: Float): Bitmap {
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val m = android.graphics.Matrix()
                m.setScale(scale, scale)
                m.postTranslate((size - source.width * scale) / 2f, (size - source.height * scale) / 2f)
                setLocalMatrix(m)
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            Canvas(out).drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), radius, radius, paint)
            return out
        }

        private const val WIDGET_REQUEST = 0x5700
    }
}
