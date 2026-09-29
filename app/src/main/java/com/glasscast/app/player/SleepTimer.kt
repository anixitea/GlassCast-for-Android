package com.glasscast.app.player

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Sleep timer state, shared between the UI and [PlaybackService].
 *
 * Lesson 6: the timer itself runs in the service, not the Activity — a timer in
 * the Activity dies when the screen locks, which is exactly when someone using
 * a sleep timer has fallen asleep. This object only holds the *target*; the
 * service does the counting, the fade and the pause.
 *
 * A process-wide object rather than session commands because the service runs
 * in the app's own process. If it ever moves to :playback, this becomes a
 * SessionCommand pair and nothing else changes.
 */
object SleepTimer {

    /** Wall-clock deadline on the elapsed-realtime clock, or null when disarmed. */
    private val _endsAtMs = MutableStateFlow<Long?>(null)
    val endsAtMs: StateFlow<Long?> = _endsAtMs.asStateFlow()

    /**
     * Stop when the current episode finishes. Deliberately not converted into a
     * deadline up front: at 1.5× the episode takes two thirds as long, and the
     * listener can change speed after arming. Waiting for STATE_ENDED gets this
     * right for free.
     */
    private val _endOfEpisode = MutableStateFlow(false)
    val endOfEpisode: StateFlow<Boolean> = _endOfEpisode.asStateFlow()

    /** Remembered so a shake can re-arm the same duration the listener chose. */
    private val _lastDurationMs = MutableStateFlow(0L)
    val lastDurationMs: StateFlow<Long> = _lastDurationMs.asStateFlow()

    /** Mirrored from settings by the app, read by the service. */
    private val _shakeEnabled = MutableStateFlow(false)
    val shakeEnabled: StateFlow<Boolean> = _shakeEnabled.asStateFlow()

    fun setShakeEnabled(enabled: Boolean) {
        _shakeEnabled.value = enabled
    }

    val isArmed: Boolean
        get() = _endsAtMs.value != null || _endOfEpisode.value

    fun armMinutes(minutes: Int) {
        _endOfEpisode.value = false
        _lastDurationMs.value = minutes * 60_000L
        _endsAtMs.value = SystemClock.elapsedRealtime() + minutes * 60_000L
    }

    /** Re-arm whatever was last set, for shake-to-restart. */
    fun rearm() {
        val duration = _lastDurationMs.value.takeIf { it > 0 } ?: DEFAULT_DURATION_MS
        _endOfEpisode.value = false
        _endsAtMs.value = SystemClock.elapsedRealtime() + duration
    }

    fun armEndOfEpisode() {
        _endsAtMs.value = null
        _endOfEpisode.value = true
    }

    /** Extends a running countdown rather than restarting it. */
    fun addMinutes(minutes: Int) {
        val current = _endsAtMs.value ?: return
        _endsAtMs.value = current + minutes * 60_000L
    }

    private const val DEFAULT_DURATION_MS = 15 * 60_000L

    fun cancel() {
        _endsAtMs.value = null
        _endOfEpisode.value = false
    }

    /** Milliseconds left, or null when nothing is armed by clock. */
    fun remainingMs(): Long? =
        _endsAtMs.value?.let { (it - SystemClock.elapsedRealtime()).coerceAtLeast(0L) }
}
