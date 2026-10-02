package com.glasscast.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.ThemeMode
import com.glasscast.app.ui.GlassCastRoot
import com.glasscast.app.ui.LocalImageStore
import com.glasscast.app.ui.rememberArtworkColors
import com.glasscast.app.ui.theme.AccentPurple
import com.glasscast.app.ui.theme.GlassCastTheme

class MainActivity : ComponentActivity() {

    private val app: GlassCastApp get() = application as GlassCastApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestHighRefreshRate()

        app.player.connect()
        handleIncoming(intent)

        setContent {
            val themeMode by app.settings.theme.collectAsStateWithLifecycle()
            val dynamicColor by app.settings.dynamicColor.collectAsStateWithLifecycle()
            val pendingOpml by app.pendingOpml.collectAsStateWithLifecycle()
            val pendingOpenFeed by app.pendingOpenFeed.collectAsStateWithLifecycle()
            val nowPlaying by app.player.currentEpisode.collectAsStateWithLifecycle()
            val nowPlayingFeed by app.player.currentFeed.collectAsStateWithLifecycle()

            // The store has to be available above the theme, because in show
            // colors mode the theme's primary is extracted from the artwork.
            CompositionLocalProvider(LocalImageStore provides app.imageStore) {
                val artUrl = nowPlaying?.imageUrl
                    ?.ifBlank { nowPlayingFeed?.imageUrl.orEmpty() }
                    .orEmpty()
                // Called unconditionally and gated afterwards: a composable
                // inside an if resets its remembered state every time the
                // branch flips, which here would mean re-extracting the palette
                // whenever the theme setting changed. Extraction is cached, so
                // the unused case costs a map lookup.
                val resolvedDark = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                val playingColors = rememberArtworkColors(
                    url = artUrl,
                    dark = resolvedDark,
                    brand = AccentPurple
                ).first

                val showAccent = playingColors.accent
                    .takeIf { dynamicColor && artUrl.isNotBlank() }

            GlassCastTheme(themeMode, showAccent) {
                GlassCastRoot(
                    feedStore = app.feedStore,
                    imageStore = app.imageStore,
                    settings = app.settings,
                    player = app.player,
                    pendingOpml = pendingOpml,
                    onOpmlHandled = { app.pendingOpml.value = null },
                    pendingOpenFeed = pendingOpenFeed,
                    onOpenFeedHandled = { app.pendingOpenFeed.value = null }
                )
            }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    /**
     * An OPML arriving from another app's share sheet, or from a file manager.
     * AntennaPod's OPML export shares as text/xml, so the filter is broad and
     * the parser is what decides whether the file is really OPML.
     */
    private fun handleIncoming(intent: Intent?) {
        intent?.getStringExtra(com.glasscast.app.background.NewEpisodeNotifier.EXTRA_OPEN_FEED)?.let {
            app.pendingOpenFeed.value = it
            // Consumed, so recreating the activity doesn't open the show again.
            intent?.removeExtra(com.glasscast.app.background.NewEpisodeNotifier.EXTRA_OPEN_FEED)
            return
        }
        val uri = when (intent?.action) {
            Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(
                intent, Intent.EXTRA_STREAM, Uri::class.java
            )
            Intent.ACTION_VIEW -> intent.data
            else -> null
        } ?: return
        app.pendingOpml.value = uri
    }

    override fun onResume() {
        super.onResume()
        // OEM skins park apps back at 60Hz across lifecycle transitions, so ask again.
        requestHighRefreshRate()
    }

    /**
     * Ask for the fastest refresh rate the panel offers — 60, 90, 120 and 144 all
     * work the same way, since the mode list is whatever the panel reports.
     *
     * Two details matter. Candidate modes are filtered to the resolution that is
     * already active, because some panels expose high-rate modes only at reduced
     * resolution and picking one blindly downgrades the whole display. And
     * preferredRefreshRate is set alongside preferredDisplayModeId, because a few
     * OEM compositors honour one and ignore the other.
     *
     * The system is free to refuse regardless — thermal state, battery saver and
     * per-app overrides all outrank this — so nothing may depend on it working.
     */
    private fun requestHighRefreshRate() {
        try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                display
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay
            } ?: return

            val active = display.mode ?: return
            val best = display.supportedModes
                .filter {
                    it.physicalWidth == active.physicalWidth &&
                        it.physicalHeight == active.physicalHeight
                }
                .maxByOrNull { it.refreshRate }
                ?: return

            window.attributes = window.attributes.apply {
                preferredDisplayModeId = best.modeId
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    preferredRefreshRate = best.refreshRate
                }
            }

            // Android 15+ adapts the refresh rate to content, and a skin can
            // read "no strong preference" as license to sit at 60Hz. Asking for
            // the high frame-rate category states the preference in the newer
            // API as well as the older one.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                window.decorView.requestedFrameRate = android.view.View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
            }
        } catch (_: Exception) {
            // Not worth crashing over.
        }
    }
}
