package com.glasscast.app.ui

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.update.AppRelease
import com.glasscast.app.update.AppUpdater
import com.glasscast.app.update.UpdateState

/**
 * "GlassCast 1.2 is out" — dropped in from the top, where the toast lives.
 * Dismissing it retires that version: it won't come back on its own, though
 * Settings › Check for updates always shows what's there.
 */
@Composable
fun UpdateBanner(
    release: AppRelease?,
    visible: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && release != null,
        enter = slideInVertically(spring(dampingRatio = 0.7f, stiffness = 400f)) { -it } + fadeIn(tween(160)),
        exit = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(160)),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(24.dp)
        Row(
            Modifier
                .padding(horizontal = 16.dp)
                .shadow(14.dp, shape, clip = false)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "GlassCast ${release?.version.orEmpty()} is out",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "See what's new",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Not now",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * The update itself: what's new, how big, and one button that walks the whole
 * way — download (with progress), verify, then Android's installer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(updater: AppUpdater, onDismiss: () -> Unit) {
    val state by updater.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from "Allow from this source": carry on with the install
    // rather than making the user find the button again.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, state) {
        val observer = LifecycleEventObserver { _, event ->
            val waiting = state
            if (event == Lifecycle.Event.ON_RESUME && waiting is UpdateState.NeedsPermission &&
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls())
            ) {
                updater.install(waiting.release, waiting.file)
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val release = when (val s = state) {
        is UpdateState.Available -> s.release
        is UpdateState.Downloading -> s.release
        is UpdateState.ReadyToInstall -> s.release
        is UpdateState.NeedsPermission -> s.release
        is UpdateState.Failed -> s.release
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(
                text = if (release != null) "GlassCast ${release.version}" else "GlassCast",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = buildString {
                    append("You have ${updater.currentVersion}")
                    if (release != null && release.sizeBytes > 0) append(" · ${formatSize(release.sizeBytes)} download")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (release != null) {
                val notes = remember(release.notes) { plainNotes(release.notes) }
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is UpdateState.Checking -> StatusLine("Checking GitHub…")
                is UpdateState.UpToDate -> StatusLine("You're on the latest version.")
                is UpdateState.Available -> ActionButton("Update") { updater.download(s.release) }
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(10.dp))
                    StatusLine("Downloading… ${(s.progress * 100).toInt()}%")
                }
                is UpdateState.ReadyToInstall -> {
                    StatusLine("Downloaded and verified. Confirm in Android's installer.")
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Install") { updater.install(s.release, s.file) }
                }
                is UpdateState.NeedsPermission -> {
                    StatusLine(
                        "Android needs your OK for GlassCast to install its own updates. " +
                            "It asks once — switch it on, then come back."
                    )
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Allow") { updater.openInstallPermission() }
                }
                is UpdateState.Failed -> {
                    StatusLine(s.message, error = true)
                    Spacer(Modifier.height(12.dp))
                    ActionButton("Try again") {
                        val r = s.release
                        if (r != null) updater.download(r) else updater.check(manual = true)
                    }
                }
                UpdateState.Idle -> ActionButton("Check for updates") { updater.check(manual = true) }
            }

            if (release != null && release.pageUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "View on GitHub",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { updater.openReleasePage(release) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private fun formatSize(bytes: Long): String =
    if (bytes >= 1_000_000) String.format("%.1f MB", bytes / 1_000_000.0) else "${bytes / 1000} KB"

/** Release notes are Markdown; this is enough to read them as text. */
internal fun plainNotes(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        line.trim()
            .removePrefix("### ").removePrefix("## ").removePrefix("# ")
            .replace(Regex("^[-*] "), "• ")
            .replace("**", "")
            .replace("`", "")
    }.trim()
