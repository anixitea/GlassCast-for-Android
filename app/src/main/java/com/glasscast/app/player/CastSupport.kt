package com.glasscast.app.player

import android.content.Context
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouterParams
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Google Cast, where this device can do it.
 *
 * The Cast SDK lives in Google Play services. A device without them (or with
 * them switched off, or out of date, as on some China-market tablets) can't
 * cast, so every step here is guarded: such a device simply never offers
 * Cast, and the player's device sheet lists what the system can find.
 *
 * Started off the main thread, once per process, by the playback service.
 * It used to start in GlassCastApp.onCreate with the synchronous
 * CastContext.getSharedInstance(context), which loads the Cast module on the
 * main thread before the first frame. runCatching stopped that from crashing
 * where Play services is missing; it didn't stop it from blocking, and a
 * Play services that's present but disabled or outdated is the slow case.
 *
 * It still starts at launch rather than when the device sheet opens, because
 * Cast devices only show up once the SDK is running.
 *
 * Main thread only.
 */
object CastSupport {

    private var castContext: CastContext? = null
    private var starting = false
    private val waiting = ArrayList<(CastContext) -> Unit>()

    private val _ready = MutableStateFlow(false)
    /** True once Cast can be offered. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /**
     * Calls [block] with the CastContext once it's up, at once if it already
     * is. Never called where Cast can't run.
     */
    fun whenReady(context: Context, block: (CastContext) -> Unit) {
        castContext?.let {
            block(it)
            return
        }
        waiting += block
        if (starting) return
        starting = true
        val app = context.applicationContext
        // The asynchronous overload: the module loads on the IO pool, and the
        // Task's listeners come back on the main thread.
        val task = try {
            CastContext.getSharedInstance(app, Dispatchers.IO.asExecutor())
        } catch (t: Throwable) {
            null
        }
        if (task == null) {
            waiting.clear()
            return
        }
        task.addOnSuccessListener { ctx -> onStarted(app, ctx) }
        task.addOnFailureListener { waiting.clear() }
    }

    private fun onStarted(app: Context, ctx: CastContext) {
        val ok = runCatching { enableOutputSwitcher(app) }.isSuccess
        if (!ok) {
            waiting.clear()
            return
        }
        castContext = ctx
        _ready.value = true
        val blocks = waiting.toList()
        waiting.clear()
        blocks.forEach { block -> runCatching { block(ctx) } }
    }

    /*
     * The system's output switcher (the media notification's output chip, the
     * volume panel's) lists Cast devices for apps that ask, and can hand
     * playback from the TV back to the phone. Built on whatever the router
     * already has, so nothing the Cast SDK set is undone; GlassCast used to
     * replace the params outright.
     */
    private fun enableOutputSwitcher(app: Context) {
        val router = MediaRouter.getInstance(app)
        val current = router.routerParams
        router.setRouterParams(
            (if (current != null) MediaRouterParams.Builder(current) else MediaRouterParams.Builder())
                .setOutputSwitcherEnabled(true)
                .setTransferToLocalEnabled(true)
                .build()
        )
    }
}
