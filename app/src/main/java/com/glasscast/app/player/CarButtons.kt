package com.glasscast.app.player

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.glasscast.app.ui.tr

/**
 * Back and forward buttons in the car.
 *
 * Android Auto draws its controls from the system's copy of the session,
 * and so does the phone's own media notification (Android 13 and later). A
 * button for the car is a button on the phone too; it can't be given to one
 * alone. So they're added only while a car's media app is connected, and the
 * phone's notification otherwise stays exactly as the system lays it out
 * (see PlaybackService on why).
 *
 * Next and previous keep their places: while the car is connected both slots
 * are reserved, so the skip buttons sit beside them rather than taking one
 * over when nothing is queued.
 *
 * Connections are counted from the car's side: a car app connecting turns
 * them on, and the last media browser unbinding from the service turns them
 * off ([PlaybackService.onUnbind]). Media3 doesn't report a browser
 * disconnecting, and drops idle car controllers after five minutes, so its
 * own connect and disconnect callbacks can't be used for the "off" half.
 */
internal class CarButtons(private val skipSeconds: () -> Int) {

    private val main = Handler(Looper.getMainLooper())
    private var session: MediaSession? = null
    private var connected = false

    fun attach(session: MediaSession) {
        this.session = session
    }

    fun detach() {
        main.removeCallbacksAndMessages(null)
        session = null
        connected = false
    }

    /** From onConnect, before the controller is connected: applied just after. */
    fun carConnected() {
        if (connected) return
        connected = true
        main.post { apply() }
    }

    fun carGone() {
        if (!connected) return
        connected = false
        main.post { apply() }
    }

    /** Re-labels the buttons when the skip setting changes. */
    fun refresh() {
        if (connected) apply()
    }

    private fun apply() {
        val s = session ?: return
        s.setSessionExtras(if (connected) reserved() else Bundle.EMPTY)
        s.setCustomLayout(if (connected) buttons() else emptyList())
    }

    private fun reserved() = Bundle().apply {
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_PREV, true)
        putBoolean(MediaConstants.EXTRAS_KEY_SLOT_RESERVATION_SEEK_TO_NEXT, true)
    }

    private fun buttons(): List<CommandButton> {
        val seconds = skipSeconds()
        val short = seconds <= 15
        return listOf(
            CommandButton.Builder(if (short) CommandButton.ICON_SKIP_BACK_15 else CommandButton.ICON_SKIP_BACK_30)
                .setSessionCommand(SKIP_BACK)
                .setDisplayName(tr("Back {0} seconds", seconds))
                .build(),
            CommandButton.Builder(if (short) CommandButton.ICON_SKIP_FORWARD_15 else CommandButton.ICON_SKIP_FORWARD_30)
                .setSessionCommand(SKIP_FORWARD)
                .setDisplayName(tr("Forward {0} seconds", seconds))
                .build()
        )
    }

    companion object {
        val SKIP_BACK = SessionCommand("com.glasscast.app.SKIP_BACK", Bundle.EMPTY)
        val SKIP_FORWARD = SessionCommand("com.glasscast.app.SKIP_FORWARD", Bundle.EMPTY)
    }
}
