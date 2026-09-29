package com.glasscast.app.player

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Tells the Cast SDK which receiver to launch.
 *
 * Google's Default Media Receiver: it plays a URL with artwork and a title,
 * which is exactly what a podcast episode is, and it needs no registration or
 * receiver app of our own. A styled receiver can come later without changing
 * anything on the phone side.
 *
 * Remote-to-local is on, so when casting stops the episode carries on from
 * the phone where the TV left off rather than stopping dead.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions =
        CastOptions.Builder()
            .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            .setRemoteToLocalEnabled(true)
            .build()

    override fun getAdditionalSessionProviders(context: Context): List<SessionProvider>? = null
}
