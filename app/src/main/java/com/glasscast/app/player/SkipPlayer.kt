package com.glasscast.app.player

import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

/**
 * What the media session sees: whichever player is playing (the phone's or
 * Cast's), with back and forward following the skip setting.
 *
 * ExoPlayer and CastPlayer take their skip amounts once, when they're built,
 * so a setting can't change them afterwards. Everything outside the app (the
 * notification, Android Auto, a headset's rewind and fast-forward keys) skips through the
 * session's seekBack and seekForward, so those are answered here instead,
 * reading the setting each time. The app's own buttons seek directly.
 */
internal class SkipPlayer(player: Player, private val skipMs: () -> Long) : ForwardingPlayer(player) {

    override fun getSeekBackIncrement(): Long = skipMs()

    override fun getSeekForwardIncrement(): Long = skipMs()

    override fun seekBack() = seekBy(-skipMs())

    override fun seekForward() = seekBy(skipMs())

    private fun seekBy(deltaMs: Long) {
        val p = wrappedPlayer
        var target = p.currentPosition + deltaMs
        val duration = p.duration
        if (duration != C.TIME_UNSET) target = minOf(target, duration)
        p.seekTo(maxOf(target, 0L))
    }
}
