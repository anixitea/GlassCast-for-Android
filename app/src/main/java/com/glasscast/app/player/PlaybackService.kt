package com.glasscast.app.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.DefaultRenderersFactory
import android.content.Context
import com.google.android.gms.cast.framework.CastContext
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.glasscast.app.GlassCastApp
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import com.glasscast.app.MainActivity
import androidx.media3.session.MediaLibraryService
import androidx.media3.common.MediaItem
import com.glasscast.app.data.usable

/**
 * Single ExoPlayer behind a MediaSession, so playback survives the Activity and
 * the lock screen controls come for free.
 *
 * Milestone 1 plays one episode at a time. The queue in milestone 3 becomes an
 * ExoPlayer playlist on this same player instance rather than anything new.
 *
 * The sleep timer will live here too (lesson 6) — a timer in the Activity dies
 * when the screen locks, which is exactly when someone using one has fallen
 * asleep. It arrives in milestone 4 with the shake-to-restart detector.
 */
class PlaybackService : MediaLibraryService() {

    private val effectsScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )
    private var voiceBoost: VoiceBoost? = null

    /** Whichever player the session is driving right now: local or Cast. */
    private var player: Player? = null
    private var localPlayer: ExoPlayer? = null
    private var castPlayer: CastPlayer? = null
    private var session: MediaLibraryService.MediaLibrarySession? = null

    /** Guards against writing a position back before the item is actually prepared. */
    private var lastSavedPositionMs = 0L

    private var shakeDetector: ShakeDetector? = null
    private var shakeWindowOpen = false

    private companion object {
        const val FADE_MS = 15_000L
        /**
         * Skip silence tuned for speech, between ExoPlayer's music-oriented
         * defaults (0.1s / 20% kept / threshold 1024 — choppy on quiet
         * speakers) and 1.2's (0.3s / 40% / 512 — too gentle):
         *  - pauses over 0.2s are trimmed;
         *  - 30% of each is kept, never more than 1s;
         *  - threshold 768, so soft words still aren't counted as silence.
         */
        fun speechSilenceSkipper() = SilenceSkippingAudioProcessor(
            /* minimumSilenceDurationUs = */ 200_000L,
            /* silenceRetentionRatio = */ 0.3f,
            /* maxSilenceToKeepDurationUs = */ 1_000_000L,
            /* minVolumeToKeepPercentageWhenMuting = */ 10,
            /* silenceThresholdLevel = */ 768.toShort()
        )

        /** +7dB with limiting: noticeable on quiet voices, never harsh. */
        const val VOICE_BOOST_MB = 700
        const val SHAKE_GRACE_MS = 120_000L
    }

    override fun onCreate() {
        super.onCreate()

        // The audio pipeline with one addition: a pass-through tap that
        // measures the voice's loudness for the wave (see VoiceLevel). It sits
        // ahead of skip-silence and speed, which ExoPlayer's chain still adds.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(
                        arrayOf<AudioProcessor>(TeeAudioProcessor(VoiceLevel)),
                        speechSilenceSkipper(),
                        SonicAudioProcessor()
                    )
                )
                .build()
        }

        val exo = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(30_000)
            .setSeekForwardIncrementMs(30_000)
            .build()

        /*
         * Skip silence and voice boost — the two Pocket Casts features people
         * miss. Both follow the settings live, so a toggle in the player takes
         * effect mid-sentence.
         *
         * Skip silence is ExoPlayer's own: it drops the gaps between
         * sentences, and never the speech itself.
         *
         * Voice boost is a speech chain on the player's audio session — low
         * cut, presence lift, three-band compression, limiter; see VoiceBoost.
         * The session id is fixed up front so the effect can attach before
         * the first sound plays.
         */
        val audioSession = (getSystemService(AUDIO_SERVICE) as android.media.AudioManager)
            .generateAudioSessionId()
        exo.setAudioSessionId(audioSession)
        voiceBoost = VoiceBoost(audioSession)
        val settings = (application as GlassCastApp).settings
        effectsScope.launch {
            settings.skipSilence.collect { on -> exo.skipSilenceEnabled = on }
        }
        effectsScope.launch {
            settings.voiceBoost.collect { on ->
                voiceBoost?.setEnabled(on)
            }
        }
        // Skip ads: breaks are looked for only while it's on — turning it on
        // mid-episode reads the current one at once.
        effectsScope.launch {
            settings.skipAds.collect { on -> if (on) loadAdBreaks(player?.currentMediaItem?.mediaId) }
        }

        val listener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                lastAdCheckMs = -1L
                if ((application as GlassCastApp).settings.skipAds.value) loadAdBreaks(mediaItem?.mediaId)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) reconcileDuration()
                if (state == Player.STATE_ENDED) {
                    savePosition(force = true)
                    // End-of-episode timer resolves here, so playback speed is
                    // accounted for without any wall-clock arithmetic.
                    if (SleepTimer.endOfEpisode.value) disarmAndPause()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) savePosition(force = true)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                savePosition(force = true)
            }
        }
        exo.addListener(listener)

        val activityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        // A library session: the same session, plus a browsable library for
        // Android Auto and other media browsers (see AutoLibrary).
        session = MediaLibraryService.MediaLibrarySession.Builder(this, exo, AutoLibrary(this, effectsScope))
            .setSessionActivity(activityIntent)
            .build()

        player = exo
        localPlayer = exo
        setUpCast(listener)
        shakeDetector = ShakeDetector(this) { onShake() }
        startPositionTicker()
        startSleepTicker()
        startAdTicker()
    }

    /**
     * Cast.
     *
     * A CastPlayer is a Player like any other, so casting is a swap rather than
     * a second code path: when a Cast session starts, the queue, the current
     * episode and its exact position move to the CastPlayer and the session is
     * pointed at it. Every control surface talks to the session — the app, the
     * notification, the lock screen, a watch — so they all keep working without
     * knowing the audio left the phone. When casting ends the same thing runs
     * in reverse, and the phone picks up where the TV stopped.
     *
     * No Play Services, no Cast: the set-up is guarded, and the phone player
     * simply stays in charge.
     */
    private fun setUpCast(listener: Player.Listener) {
        val context = runCatching { CastContext.getSharedInstance(this) }.getOrNull() ?: return
        val cast = CastPlayer(context, DefaultMediaItemConverter(), 30_000, 30_000)
        cast.addListener(listener)
        cast.setSessionAvailabilityListener(object : SessionAvailabilityListener {
            override fun onCastSessionAvailable() = handOverTo(cast)
            override fun onCastSessionUnavailable() {
                localPlayer?.let { handOverTo(it) }
            }
        })
        castPlayer = cast
        // Already casting when the service started (e.g. the app was reopened).
        if (cast.isCastSessionAvailable) handOverTo(cast)
    }

    private fun handOverTo(target: Player) {
        val current = player ?: return
        if (current === target) return

        // A Cast device can't read this phone's storage: downloaded items go
        // over as their online address instead.
        val items = List(current.mediaItemCount) { current.getMediaItemAt(it) }.map { item ->
            val remote = item.mediaMetadata.extras?.getString(PlayerConnection.REMOTE_URL)
            if (target is CastPlayer && item.localConfiguration?.uri?.scheme == "file" && remote != null) {
                item.buildUpon().setUri(remote).build()
            } else {
                item
            }
        }
        val index = current.currentMediaItemIndex.coerceAtLeast(0)
        val position = current.currentPosition.coerceAtLeast(0L)
        val playWhenReady = current.playWhenReady
        val speed = current.playbackParameters.speed

        savePosition(force = true)
        current.stop()
        current.clearMediaItems()

        if (items.isNotEmpty()) {
            target.setMediaItems(items, index, position)
            target.setPlaybackSpeed(speed)
            target.playWhenReady = playWhenReady
            target.prepare()
        }
        player = target
        session?.player = target
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibraryService.MediaLibrarySession? = session

    /*
     * No custom notification buttons.
     *
     * Three attempts to lay out back 30 / forward 30 by hand all ended with the
     * glyphs reversed on HyperOS — and the screenshots showed why the icon swap
     * couldn't work: the glyphs sat in exactly the same places before and after
     * it. HyperOS's lock screen and Hyper Island draw their own icons for these
     * slots and ignore ours, so there was never an icon of ours to fix.
     *
     * So the session now declares only what it can *do*, and every surface —
     * stock Android, HyperOS, a car, a watch — renders those abilities in its
     * own native way. The seek increments below (30s) are what the system's
     * rewind and fast-forward use when it chooses to show them.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        // Nothing playing and the task is gone — don't leave a dead notification behind.
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            savePosition(force = true)
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        effectsScope.cancel()
        runCatching { voiceBoost?.release() }
        voiceBoost = null
        savePosition(force = true)
        // Saves are coalesced a second behind; the service's process may not
        // live that long once it's destroyed, so the final position is written
        // now. A few hundred KB at most — one show's file.
        runCatching {
            kotlinx.coroutines.runBlocking { (application as GlassCastApp).feedStore.flushNow() }
        }
        SleepTimer.cancel()
        shakeDetector?.stop()
        shakeDetector = null
        castPlayer?.setSessionAvailabilityListener(null)
        castPlayer?.release()
        localPlayer?.release()
        session?.release()
        session = null
        player = null
        localPlayer = null
        castPlayer = null
        super.onDestroy()
    }

    /**
     * Lesson 4: MediaMetadataRetriever and itunes:duration both lie often enough
     * to matter, and a zero duration silently breaks the scrubber — the bar never
     * moves and remaining time shows -0:00. Once the item is prepared the player
     * knows the truth, so write it back.
     */
    private fun reconcileDuration() {
        val p = player ?: return
        val guid = p.currentMediaItem?.mediaId ?: return
        val real = p.duration
        if (real <= 0 || real == C.TIME_UNSET) return
        val store = (application as GlassCastApp).feedStore
        val ep = store.episodeByGuid(guid) ?: return
        if (ep.durationMs != real) store.updateEpisode(ep.copy(durationMs = real))
    }

    private fun savePosition(force: Boolean = false) {
        val p = player ?: return
        val guid = p.currentMediaItem?.mediaId ?: return
        val pos = p.currentPosition
        if (pos <= 0) return
        if (!force && kotlin.math.abs(pos - lastSavedPositionMs) < 5_000) return
        lastSavedPositionMs = pos
        val duration = if (p.duration > 0 && p.duration != C.TIME_UNSET) p.duration else 0L
        (application as GlassCastApp).feedStore.savePosition(guid, pos, duration)
    }

    /** Cheap wall-clock write-back so a crash or a kill doesn't lose the place. */
    private fun startPositionTicker() {
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                if (player?.isPlaying == true) savePosition()
                handler.postDelayed(this, 5_000)
            }
        }
        handler.postDelayed(tick, 5_000)
    }

    /**
     * Sleep timer. Runs here rather than in the UI (lesson 6). Fades over the
     * last 15 seconds instead of cutting mid-word — waking up because the audio
     * stopped abruptly defeats the point of the feature.
     */
    private fun startSleepTicker() {
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                val p = player
                if (p != null) {
                    val remaining = SleepTimer.remainingMs()
                    when {
                        remaining == null -> {
                            // Nothing armed by clock: make sure a canceled fade
                            // didn't leave the volume down.
                            if (!SleepTimer.endOfEpisode.value && p.canSetVolume() && p.volume < 1f) p.volume = 1f
                            if (!SleepTimer.isArmed && !shakeWindowOpen) shakeDetector?.stop()
                        }
                        remaining <= 0L -> disarmAndPause()
                        remaining <= FADE_MS -> if (p.canSetVolume()) p.volume = (remaining.toFloat() / FADE_MS).coerceIn(0f, 1f)
                        else -> {
                            if (p.canSetVolume() && p.volume < 1f) p.volume = 1f
                            if (SleepTimer.shakeEnabled.value) shakeDetector?.start()
                        }
                    }
                }
                handler.postDelayed(this, 250)
            }
        }
        handler.postDelayed(tick, 250)
    }

    private fun disarmAndPause() {
        val p = player ?: return
        p.pause()
        if (p.canSetVolume()) p.volume = 1f
        SleepTimer.cancel()
        savePosition(force = true)

        if (SleepTimer.shakeEnabled.value) {
            // Keep listening for a couple of minutes after the timer fires. The
            // whole point is the moment you realize you're still awake, which is
            // just after the audio stops — not while it's still playing.
            shakeWindowOpen = true
            shakeDetector?.start()
            android.os.Handler(mainLooper).postDelayed({
                if (!SleepTimer.isArmed) {
                    shakeWindowOpen = false
                    shakeDetector?.stop()
                }
            }, SHAKE_GRACE_MS)
        } else {
            shakeDetector?.stop()
        }
    }

    /**
     * Shake means "I'm still awake": re-arm the same duration and pick up where
     * the fade left off.
     *
     * Works during the fade as well as after the pause, so nobody has to wait
     * for silence before reaching for the phone.
     */
    private fun onShake() {
        val p = player ?: return
        shakeWindowOpen = false
        if (p.canSetVolume()) p.volume = 1f
        if (!p.isPlaying) p.play()
        SleepTimer.rearm()
    }

    /** The sleep fade needs player volume; a Cast receiver only has device volume. */
    // ------------------------------------------------------------ skip ads

    /** Where playback was at the last ad check; −1 after a pause or a new episode. */
    private var lastAdCheckMs = -1L

    private fun loadAdBreaks(guid: String?) {
        if (guid.isNullOrEmpty()) return
        effectsScope.launch {
            val store = (application as GlassCastApp).feedStore
            store.awaitLoaded()
            store.episodeByGuid(guid)?.let { AdSkipper.load(it) }
        }
    }

    private fun startAdTicker() {
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                checkAdBreak()
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(tick, 500)
    }

    /**
     * Skips a break that playback runs into — from before its start into it.
     * A break you land in by seeking, or were paused in, plays: you went
     * there on purpose. A jump bigger than half a second of playback at 2×
     * can explain is a seek, and just moves the reference point.
     */
    private fun checkAdBreak() {
        val p = player ?: return
        if (!p.isPlaying) {
            lastAdCheckMs = -1L
            return
        }
        val pos = p.currentPosition
        val prev = lastAdCheckMs
        lastAdCheckMs = pos
        val guid = p.currentMediaItem?.mediaId ?: return
        if (!(application as GlassCastApp).settings.skipAds.value) return
        val (forGuid, found) = AdSkipper.breaks.value
        if (forGuid != guid || found.isEmpty()) return
        val breaks = found.usable(p.duration)
        if (breaks.isEmpty()) return
        if (prev >= 0 && (pos < prev - 250 || pos - prev > 2_500)) return
        val hit = breaks.firstOrNull { b ->
            pos >= b.startMs && pos < b.endMs - 500 && (if (prev >= 0) prev <= b.startMs + 1_500 else pos <= b.startMs + 1_500)
        } ?: return
        p.seekTo(hit.endMs)
        lastAdCheckMs = hit.endMs
        AdSkipper.announceSkip(guid)
    }

    private fun Player.canSetVolume() = isCommandAvailable(Player.COMMAND_SET_VOLUME)
}
