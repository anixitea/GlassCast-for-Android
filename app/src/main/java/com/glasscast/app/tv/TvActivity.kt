package com.glasscast.app.tv

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.glasscast.app.GlassCastApp
import com.glasscast.app.data.GOOGLE_SIGN_IN
import com.glasscast.app.data.ThemeMode
import com.glasscast.app.ui.LocalImageStore
import com.glasscast.app.ui.theme.GlassCastTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The TV entry point.
 *
 * A separate Activity in the same module rather than a separate module. The
 * data and playback layers are identical — same `FeedStore`, same
 * `PlaybackService`, same `PlayerConnection` — and only the drawing differs, so
 * splitting the project would have bought a build-graph refactor and nothing
 * else. One APK also means one version number and one install for a person who
 * happens to own both.
 *
 * `LEANBACK_LAUNCHER` is what puts it on the TV home screen; the phone
 * Activity's plain `LAUNCHER` category is ignored there, so the two never
 * collide.
 */
class TvActivity : ComponentActivity() {

    private val app: GlassCastApp get() = application as GlassCastApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A TV has no status bar to inset around and no notch to avoid; the
        // whole panel is the canvas and overscan is handled in layout instead.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        app.player.connect()
        // Sea Glass's shader compiles once; doing it now saves the first
        // screen a hitch.
        if (app.settings.look.value == com.glasscast.app.data.Look.SEA_GLASS) {
            lifecycleScope.launch(Dispatchers.Default) { com.glasscast.app.ui.seaglass.GlassBody.warm() }
        }

        setContent {
            val look by app.settings.look.collectAsStateWithLifecycle()
            /*
             * Pinned to Dark, and the theme setting is ignored entirely.
             *
             * Every surface here draws from the artwork palette, so the scheme
             * only supplies a handful of fallbacks before a cover has loaded.
             * Lights out therefore changed nothing visible, which is worse than
             * not offering it — the setting is gone from the TV settings page
             * too.
             *
             * The look does apply: Sea Glass on the TV is its night side, the
             * tide (see TvSea.kt). It's set in the TV's Settings, and it's
             * the same setting as the phone's.
             */
            CompositionLocalProvider(LocalImageStore provides app.imageStore) {
                GlassCastTheme(ThemeMode.DARK, look = look) {
                    ProvideTv {
                        TvRoot(
                            feedStore = app.feedStore,
                            settings = app.settings,
                            player = app.player
                        )
                    }
                }
            }
        }
    }

    // Signed in with Google: pick up what the phone changed on the way in,
    // and hand over what changed here on the way out (GoogleAccount.syncSoon,
    // at most every two minutes). Off the main thread: the first use reads
    // the account's preferences.
    override fun onStart() {
        super.onStart()
        if (GOOGLE_SIGN_IN) lifecycleScope.launch(Dispatchers.IO) { app.account.syncSoon() }
    }

    override fun onStop() {
        if (GOOGLE_SIGN_IN) lifecycleScope.launch(Dispatchers.IO) { app.account.syncSoon() }
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) app.player.release()
    }
}
