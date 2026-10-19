package com.glasscast.app.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.GlassCastApp
import com.glasscast.app.data.GOOGLE_SIGN_IN
import com.glasscast.app.data.TvSignIn
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.chromeButton
import com.glasscast.app.ui.chromeSurface
import com.glasscast.app.ui.formatBackupTime
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.tr
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Google sign-in on the TV, in Settings: who's signed in and when it last
 * synced, with Sync now and Sign out; or Sign in with Google, which opens the
 * QR code (see [TvSignInOverlay]). Not shown when this build has no TV client
 * (see TvSignIn).
 */
@Composable
internal fun TvAccountSettings(colors: ArtworkColors, signingIn: Boolean, onSignIn: () -> Unit) {
    if (!GOOGLE_SIGN_IN) return
    val context = LocalContext.current
    val account = (context.applicationContext as GlassCastApp).account
    if (!account.tv.configured) return
    val state by account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // Back here when the QR code closes, on whichever button is first now.
    val first = remember { FocusRequester() }
    var opened by remember { mutableStateOf(false) }
    LaunchedEffect(signingIn) {
        if (signingIn) {
            opened = true
        } else if (opened) {
            opened = false
            first.requestWhenReady()
        }
    }

    Column {
        TvSettingGroup(tr("GOOGLE ACCOUNT"), colors) {
            when {
                !state.signedIn || state.needsSignIn -> TvPillButton(
                    label = tr("Sign in with Google"),
                    icon = null,
                    colors = colors,
                    filled = true,
                    modifier = Modifier.focusRequester(first),
                    onClick = onSignIn
                )
                else -> TvPillButton(
                    label = if (state.syncing) tr("Syncing…") else tr("Sync now"),
                    icon = null,
                    colors = colors,
                    filled = true,
                    busy = state.syncing,
                    modifier = Modifier.focusRequester(first),
                    onClick = { account.syncNow() }
                )
            }
            if (state.signedIn) {
                TvPillButton(
                    label = tr("Sign out"),
                    icon = null,
                    colors = colors,
                    onClick = { scope.launch { account.signOut(context) } }
                )
            }
        }
        if (state.signedIn) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = listOfNotNull(
                    state.name.ifBlank { null },
                    state.email.ifBlank { null },
                    when {
                        state.needsSignIn -> tr("Sign in again to keep syncing")
                        state.error != null -> state.error
                        state.lastSyncAt > 0 -> tr("Synced {0}", formatBackupTime(context, state.lastSyncAt))
                        else -> null
                    }
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null || state.needsSignIn) Color(0xFFFF8A80) else colors.contentVariant
            )
        }
    }
}

/**
 * Signing in by phone: a QR code for Google's device page and the code to
 * type there. It asks Google for a code, shows it, and checks every few
 * seconds until the person has signed in on their phone; then it hands the
 * token to the account (the first sync starts) and closes. An expired code
 * offers a new one. Back cancels.
 */
@Composable
internal fun TvSignInOverlay(colors: ArtworkColors, onDone: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val account = (context.applicationContext as GlassCastApp).account
    var code by remember { mutableStateOf<TvSignIn.DeviceCode?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var expired by remember { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    var granted by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val second = remember { FocusRequester() }
    BackHandler { onDismiss() }

    LaunchedEffect(attempt) {
        code = null
        expired = false
        status = null
        val device = account.tv.start()
        if (device == null) {
            status = tr("Couldn't sign in with Google")
            expired = true
            return@LaunchedEffect
        }
        code = device
        var interval = device.intervalMs
        while (true) {
            delay(interval)
            when (val result = account.tv.poll(device, interval)) {
                is TvSignIn.Poll.Waiting -> interval = result.intervalMs
                is TvSignIn.Poll.Granted -> {
                    granted = true
                    account.onSignedIn(result.accessToken)
                    onDone()
                    return@LaunchedEffect
                }
                TvSignIn.Poll.Denied -> {
                    status = tr("Couldn't sign in with Google")
                    expired = true
                    return@LaunchedEffect
                }
                TvSignIn.Poll.Expired -> {
                    status = tr("That code has expired")
                    expired = true
                    return@LaunchedEffect
                }
                is TvSignIn.Poll.Failed -> {
                    android.util.Log.w("GlassCast", "TV sign-in: ${result.message}")
                    status = tr("Couldn't sign in with Google")
                    expired = true
                    return@LaunchedEffect
                }
            }
        }
    }
    LaunchedEffect(expired) { first.requestWhenReady() }

    Box(
        Modifier
            .fillMaxSize()
            .ignoreHeldSelect()
            // Focus stays in the panel: left and right move between its
            // buttons, and nothing reaches the page underneath.
            .onPreviewKeyEvent { e ->
                when (e.key) {
                    Key.DirectionLeft -> { if (e.type == KeyEventType.KeyDown) runCatching { first.requestFocus() }; true }
                    Key.DirectionRight -> { if (e.type == KeyEventType.KeyDown && expired) runCatching { second.requestFocus() }; true }
                    Key.DirectionUp, Key.DirectionDown -> true
                    else -> false
                }
            }
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .tvPanel(RoundedCornerShape(34.dp), container = tvPanelColor(colors).copy(alpha = 0.98f))
                .padding(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The QR code: dark modules on white, with the quiet zone the
            // standard asks for, so a phone's camera finds it at a distance.
            Box(
                Modifier
                    .size(300.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White)
                    .padding(22.dp),
                contentAlignment = Alignment.Center
            ) {
                val url = code?.verificationUrl
                val qr = remember(url) { url?.let { QrCode.encode(it) } }
                if (qr != null && !granted) {
                    Canvas(Modifier.fillMaxSize()) {
                        val cell = size.minDimension / qr.size
                        for (y in 0 until qr.size) for (x in 0 until qr.size) {
                            if (qr[x, y]) {
                                drawRect(Color(0xFF111316), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                            }
                        }
                    }
                } else if (!expired || granted) {
                    CircularProgressIndicator(color = Color(0xFF111316))
                }
            }
            Spacer(Modifier.width(44.dp))
            Column(Modifier.width(440.dp)) {
                Text(tr("Sign in with Google"), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                Spacer(Modifier.height(16.dp))
                val shown = code
                if (shown != null && !expired && !granted) {
                    Text(
                        text = tr("Go to {0} and enter this code", shown.verificationUrl.removePrefix("https://").removePrefix("www.")),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = shown.userCode,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.12.em
                        ),
                        color = colors.chromeButton
                    )
                }
                status?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
                }
                Spacer(Modifier.height(26.dp))
                val accent = colors.chromeButton
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (expired) {
                        TvDialogButton(tr("Get a new code"), accent, filled = true, modifier = Modifier.focusRequester(first)) { attempt++ }
                        TvDialogButton(tr("Cancel"), accent, filled = false, modifier = Modifier.focusRequester(second), onClick = onDismiss)
                    } else {
                        TvDialogButton(tr("Cancel"), accent, filled = false, modifier = Modifier.focusRequester(first), onClick = onDismiss)
                    }
                }
            }
        }
    }
}
