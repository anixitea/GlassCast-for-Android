package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.GPodderSync
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSheet(sync: GPodderSync, onDismiss: () -> Unit) {
    val status by sync.status.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = tr("SYNC"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "gPodder",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(20.dp))

            if (status.connected) {
                Text(
                    text = status.username,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = status.server.removePrefix("https://").removePrefix("http://"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        status.syncing -> tr("Syncing…")
                        status.error != null -> status.error!!
                        status.lastSync > 0 -> tr("Synced {0}", agoText(status.lastSync))
                        else -> tr("Not synced yet")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.error != null && !status.syncing) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SheetButton(
                        label = tr("Sync now"),
                        busy = status.syncing,
                        primary = true,
                        onClick = { scope.launch { sync.sync() } }
                    )
                    SheetButton(label = tr("Sign out"), onClick = { sync.disconnect() })
                }
            } else {
                var kind by remember { mutableStateOf(GPodderSync.Kind.GPODDER) }
                var server by remember { mutableStateOf("") }
                var user by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                var busy by remember { mutableStateOf(false) }
                var error by remember { mutableStateOf<String?>(null) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        label = "gPodder",
                        active = kind == GPodderSync.Kind.GPODDER,
                        onClick = { kind = GPodderSync.Kind.GPODDER }
                    )
                    Pill(
                        label = "Nextcloud",
                        active = kind == GPodderSync.Kind.NEXTCLOUD,
                        onClick = { kind = GPodderSync.Kind.NEXTCLOUD }
                    )
                }
                Spacer(Modifier.height(16.dp))
                SyncField(
                    value = server,
                    onChange = { server = it },
                    label = tr("Server"),
                    placeholder = if (kind == GPodderSync.Kind.GPODDER) "gpodder.net" else "cloud.example.com",
                    keyboard = KeyboardType.Uri
                )
                Spacer(Modifier.height(10.dp))
                SyncField(value = user, onChange = { user = it }, label = tr("Username"))
                Spacer(Modifier.height(10.dp))
                SyncField(
                    value = password,
                    onChange = { password = it },
                    label = if (kind == GPodderSync.Kind.NEXTCLOUD) tr("App password") else tr("Password"),
                    keyboard = KeyboardType.Password,
                    secret = true
                )
                error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(20.dp))
                SheetButton(
                    label = tr("Connect"),
                    busy = busy,
                    primary = true,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            error = sync.connect(kind, server, user, password)
                            busy = false
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun SyncField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Text,
    secret: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        visualTransformation = if (secret) PasswordVisualTransformation()
        else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SheetButton(label: String, busy: Boolean = false, primary: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = fg, modifier = Modifier.size(18.dp))
        } else {
            Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
        }
    }
}

internal fun agoText(time: Long): String {
    val mins = ((System.currentTimeMillis() - time) / 60_000L).coerceAtLeast(0L)
    return when {
        mins < 1 -> tr("just now")
        mins < 60 -> tr("{0} min ago", mins)
        mins < 60 * 24 -> tr("{0} h ago", mins / 60)
        else -> tr("{0} d ago", mins / (60 * 24))
    }
}
