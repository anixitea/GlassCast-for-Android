package com.glasscast.app.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.FeedStore
import com.glasscast.app.data.Opml
import com.glasscast.app.data.OpmlEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Moving in from another podcast app.
 *
 * OPML is the one format they all agree on, and it's AntennaPod's supported
 * export: Settings → Import/Export → OPML export. The file can be opened here,
 * or shared straight to GlassCast from AntennaPod's share sheet — the manifest
 * accepts it either way.
 *
 * [pendingUri] is set when the app was launched by a share or a file open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpmlSheet(
    store: FeedStore,
    pendingUri: Uri?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val progress by store.importProgress.collectAsStateWithLifecycle()

    var entries by remember { mutableStateOf<List<OpmlEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var exported by remember { mutableStateOf<String?>(null) }

    suspend fun read(uri: Uri) {
        error = null
        val parsed = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri).use { stream ->
                    if (stream == null) emptyList() else Opml.parse(stream)
                }
            }.getOrDefault(emptyList())
        }
        if (parsed.isEmpty()) {
            error = "No subscriptions found in that file."
        } else {
            entries = parsed
        }
    }

    // Arrived via share or file-open: read it immediately, no extra tap.
    LaunchedEffect(pendingUri) {
        if (pendingUri != null) read(pendingUri)
    }

    val openFile = rememberLauncherForActivityResult(
        // Not OpenDocument("text/x-opml"): exporters label OPML as text/xml,
        // application/xml, octet-stream or nothing at all, and a strict filter
        // greys out the very file the user came to pick.
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) scope.launch { read(uri) } }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { out ->
                            out.write(store.exportOpml().toByteArray())
                        }
                    }
                }
                exported = "Saved."
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            store.clearImportProgress()
            onDismiss()
        },
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
                text = "SUBSCRIPTIONS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Import or export OPML",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "In AntennaPod: Settings → Import/Export → OPML export, " +
                    "then open that file here or share it to GlassCast. " +
                    "Subscriptions transfer; play positions don't.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(20.dp))

            when {
                progress.running -> {
                    Text(
                        text = "Adding ${progress.done} of ${progress.total}…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }

                progress.total > 0 -> {
                    Text(
                        text = "${progress.added} added" +
                            (if (progress.skipped > 0) ", ${progress.skipped} already there" else "") +
                            (if (progress.failed > 0) ", ${progress.failed} couldn't be read" else ""),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                entries != null -> {
                    val found = entries.orEmpty()
                    Text(
                        text = "${found.size} subscriptions in that file.",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Pill(
                            label = "Add all",
                            active = true,
                            onClick = { scope.launch { store.importOpml(found) } }
                        )
                        Spacer(Modifier.size(10.dp))
                        Pill(label = "Choose another", onClick = { openFile.launch(arrayOf("*/*")) })
                    }
                }

                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Pill(
                            label = "Import file",
                            active = true,
                            onClick = { openFile.launch(arrayOf("*/*")) }
                        )
                        Pill(
                            label = "Export mine",
                            onClick = { saveFile.launch("glasscast-subscriptions.opml") }
                        )
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            exported?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (pendingUri != null && entries == null && error == null) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "Reading file…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
