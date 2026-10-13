package com.glasscast.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.GlassCastApp
import com.glasscast.app.data.AccountState
import com.glasscast.app.data.BackupStatus
import com.glasscast.app.data.BackupSummary
import com.glasscast.app.data.GoogleAuth
import com.glasscast.app.ui.theme.ErrorRed
import kotlinx.coroutines.launch

/** A backup waiting for the person to say yes. */
private data class PendingRestore(val json: String, val summary: BackupSummary)

/**
 * The account card at the top of Settings, as a phone's own Settings opens
 * with its account: signed out, "Sign in with Google"; signed in, the
 * photo, name and email, and when the library last synced. Tapping it when
 * signed in opens the account sheet (Sync now, Sign out). See GoogleAccount.
 */
@Composable
internal fun AccountCard() {
    val context = LocalContext.current
    val app = context.applicationContext as GlassCastApp
    val account by app.account.state.collectAsStateWithLifecycle()
    var sheetOpen by remember { mutableStateOf(false) }
    var signInError by remember { mutableStateOf<String?>(null) }
    var signingIn by remember { mutableStateOf(false) }
    val access = rememberGoogleAccess(onError = {
        signingIn = false
        signInError = it
    })

    fun signIn() {
        signInError = null
        signingIn = true
        access { token ->
            app.account.onSignedIn(token)
            signingIn = false
        }
    }

    Spacer(Modifier.height(16.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                if (account.signedIn && !account.needsSignIn) sheetOpen = true else signIn()
            }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(account.photoUrl.takeIf { account.signedIn }, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            if (account.signedIn) {
                Text(
                    text = account.name.ifBlank { account.email },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = tr("Sign in with Google"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            val status = accountStatus(context, account, signInError)
            if (status != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = status.first,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (status.second) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (account.syncing || signingIn) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            Chevron()
        }
    }

    if (sheetOpen) AccountSheet(onDismiss = { sheetOpen = false })
}

/** The line under the name: syncing, when it last synced, or what went wrong. Second is "is an error". */
private fun accountStatus(context: Context, account: AccountState, signInError: String?): Pair<String, Boolean>? = when {
    signInError != null -> signInError to true
    !account.signedIn -> null
    account.needsSignIn -> tr("Sign in again to keep syncing") to true
    account.syncing -> tr("Syncing…") to false
    account.error != null -> account.error to true
    account.lastSyncAt > 0 -> tr("Synced {0}", formatBackupTime(context, account.lastSyncAt)) to false
    else -> null
}

/** The account's photo in a circle, or a person glyph until there is one. */
@Composable
private fun Avatar(url: String?, size: Dp) {
    val store = LocalImageStore.current
    var bitmap by remember(url) { mutableStateOf(url?.let { store.peek(it, 192) }) }
    LaunchedEffect(url) {
        if (bitmap == null && !url.isNullOrBlank()) bitmap = store.load(url, 192)
    }
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                Icons.Outlined.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** Signed in: the account, Sync now, and signing out. */
@Composable
private fun AccountSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GlassCastApp
    val account by app.account.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    GlassSheet(onDismiss = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(account.photoUrl, 76.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                text = account.name.ifBlank { account.email },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (account.name.isNotBlank()) {
                Text(
                    text = account.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            accountStatus(context, account, null)?.let { (text, error) ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(22.dp))
            SheetPillButton(
                label = if (account.syncing) tr("Syncing…") else tr("Sync now"),
                icon = Icons.Outlined.Sync,
                fill = MaterialTheme.colorScheme.primary,
                ink = MaterialTheme.colorScheme.onPrimary,
                onClick = { if (!account.syncing) app.account.syncNow() }
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = tr("Sign out"),
                style = MaterialTheme.typography.labelLarge,
                color = ErrorRed,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        scope.launch {
                            app.account.signOut(context)
                            onDismiss()
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * Settings > Backup: the library as a file you keep (for moving phones
 * without an account). The same document as the account's (data/Backup.kt):
 * shows, played marks and places, the queue, bookmarks and settings.
 */
@Composable
internal fun BackupGroup() {
    val context = LocalContext.current
    val app = context.applicationContext as GlassCastApp
    val status by app.backup.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<PendingRestore?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) app.backup.saveTo(uri)
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val json = app.backup.readFile(uri)
            val summary = json?.let(app.backup::summarize)
            if (json == null || summary == null) {
                app.backup.setStatus(BackupStatus.Failed(tr("That file isn't a GlassCast backup")))
            } else {
                pending = PendingRestore(json, summary)
            }
        }
    }

    SettingsGroup(tr("BACKUP")) {
        GroupRow(
            icon = Icons.Outlined.SaveAlt,
            color = Badge.Green,
            label = tr("Save a backup file"),
            onClick = { runCatching { save.launch(app.backup.fileName()) } },
            trailing = { Chevron() }
        )
        GroupDivider()
        GroupRow(
            icon = Icons.Outlined.Restore,
            color = Badge.Orange,
            label = tr("Restore a backup file"),
            onClick = {
                runCatching { open.launch(arrayOf("application/json", "application/octet-stream", "text/*")) }
            },
            trailing = { Chevron() }
        )
        StatusLines(status)
    }

    pending?.let { p ->
        ConfirmSheet(
            artUrl = "",
            title = tr("Restore this backup?"),
            subtitle = listOf(p.summary.device, formatBackupTime(context, p.summary.createdAt))
                .filter { it.isNotBlank() }.joinToString(" · "),
            meta = tr("{0} SHOWS · {1} BOOKMARKS", p.summary.shows, p.summary.bookmarks),
            action = tr("Restore"),
            icon = Icons.Outlined.Restore,
            destructive = false,
            onConfirm = { app.backup.restore(p.json) },
            onDismiss = { pending = null }
        )
    }
}

/** Under the rows: what a backup or restore is doing, and how it ended. */
@Composable
private fun StatusLines(status: BackupStatus) {
    val app = LocalContext.current.applicationContext as GlassCastApp
    LaunchedEffect(status) {
        // A finished message stays a few seconds, then the group is quiet again.
        if (status is BackupStatus.Done || status is BackupStatus.Failed) {
            kotlinx.coroutines.delay(6_000)
            app.backup.clearStatus()
        }
    }
    when (status) {
        is BackupStatus.Working -> Column(Modifier.padding(start = 62.dp, end = 16.dp, bottom = 14.dp)) {
            Text(
                text = if (status.total > 0) "${status.label} ${tr("{0} of {1}", status.done, status.total)}" else status.label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            if (status.total > 0) {
                LinearProgressIndicator(
                    progress = { (status.done.toFloat() / status.total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            }
        }
        is BackupStatus.Done, is BackupStatus.Failed -> {
            val error = status is BackupStatus.Failed
            Text(
                text = if (status is BackupStatus.Done) status.message else (status as BackupStatus.Failed).message,
                style = MaterialTheme.typography.bodySmall,
                color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 62.dp, end = 16.dp, bottom = 12.dp)
            )
        }
        BackupStatus.Idle -> Unit
    }
}

/**
 * Asks Google for access and then runs the action with a token. The first
 * time, that's the account picker and consent screen; after that it's silent.
 */
@Composable
private fun rememberGoogleAccess(onError: (String) -> Unit): (suspend (String) -> Unit) -> Unit {
    val context = LocalContext.current
    val app = context.applicationContext as GlassCastApp
    val scope = rememberCoroutineScope()
    var afterConsent by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val next = afterConsent
        afterConsent = null
        when (val auth = app.account.resultFrom(context, result.data)) {
            is GoogleAuth.Granted -> if (next != null) scope.launch { next(auth.token) }
            is GoogleAuth.Failed -> onError(auth.message)
            is GoogleAuth.NeedsConsent -> onError(tr("Couldn't sign in with Google"))
        }
    }
    return { then ->
        scope.launch {
            when (val auth = app.account.authorize(context)) {
                is GoogleAuth.Granted -> then(auth.token)
                is GoogleAuth.NeedsConsent -> {
                    afterConsent = then
                    runCatching {
                        consent.launch(IntentSenderRequest.Builder(auth.intent.intentSender).build())
                    }.onFailure { onError(tr("Couldn't sign in with Google")) }
                }
                is GoogleAuth.Failed -> onError(auth.message)
            }
        }
    }
}

/** "Today, 14:32" or "3 Oct, 09:10", in the phone's 12- or 24-hour clock. */
private fun formatBackupTime(context: Context, ms: Long): String {
    if (ms <= 0) return ""
    val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(ms))
    return "${formatDate(ms)}, $time"
}
