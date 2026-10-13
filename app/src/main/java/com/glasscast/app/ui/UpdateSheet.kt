package com.glasscast.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
                    text = tr("GlassCast {0} is out", release?.version.orEmpty()),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = tr("See what's new"),
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
                    contentDescription = tr("Not now"),
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

    GlassSheet(
        onDismiss = onDismiss,
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
                    append(tr("You have {0}", updater.currentVersion))
                    if (release != null && release.sizeBytes > 0) append(" · ${formatSize(release.sizeBytes)} download")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (release != null) {
                val blocks = remember(release.notes) { noteBlocks(release.notes) }
                if (blocks.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    val pictures = blocks.any { it is NoteBlock.Picture }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = if (pictures) 420.dp else 260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        blocks.forEach { block ->
                            when (block) {
                                is NoteBlock.Words -> Text(
                                    text = block.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                is NoteBlock.Picture -> NotePicture(block)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            when (val s = state) {
                is UpdateState.Checking -> StatusLine(tr("Checking GitHub…"))
                is UpdateState.UpToDate -> StatusLine(tr("You're on the latest version."))
                is UpdateState.Available -> ActionButton(tr("Update")) { updater.download(s.release) }
                is UpdateState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { s.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape)
                    )
                    Spacer(Modifier.height(10.dp))
                    StatusLine(tr("Downloading… {0}%", (s.progress * 100).toInt()))
                }
                is UpdateState.ReadyToInstall -> {
                    StatusLine(tr("Downloaded and verified. Confirm in Android's installer."))
                    Spacer(Modifier.height(12.dp))
                    ActionButton(tr("Install")) { updater.install(s.release, s.file) }
                }
                is UpdateState.NeedsPermission -> {
                    StatusLine(
                        "Android needs your OK for GlassCast to install its own updates. " +
                            tr("It asks once — switch it on, then come back.")
                    )
                    Spacer(Modifier.height(12.dp))
                    ActionButton(tr("Allow")) { updater.openInstallPermission() }
                }
                is UpdateState.Failed -> {
                    StatusLine(s.message, error = true)
                    Spacer(Modifier.height(12.dp))
                    ActionButton(tr("Try again")) {
                        val r = s.release
                        if (r != null) updater.download(r) else updater.check(manual = true)
                    }
                }
                UpdateState.Idle -> ActionButton(tr("Check for updates")) { updater.check(manual = true) }
            }

            if (release != null && release.pageUrl.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = tr("View on GitHub"),
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

/**
 * An image attached to the release, at its own shape, rounded like the box.
 * Loaded through the artwork pipeline (so it's cached and downsampled to the
 * sheet's width); a placeholder holds its place meanwhile, and one that won't
 * load leaves nothing behind.
 */
@Composable
private fun NotePicture(picture: NoteBlock.Picture) {
    val store = LocalImageStore.current
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        .coerceIn(320, 1080)
    var bitmap by remember(picture.url) { mutableStateOf(store.peek(picture.url, widthPx)) }
    var failed by remember(picture.url) { mutableStateOf(false) }
    LaunchedEffect(picture.url) {
        if (bitmap == null) {
            bitmap = store.load(picture.url, widthPx)
            failed = bitmap == null
        }
    }
    val image = bitmap
    when {
        image != null -> Image(
            bitmap = image.asImageBitmap(),
            contentDescription = picture.alt.ifBlank { null },
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio((image.width.toFloat() / image.height).coerceIn(0.3f, 4f))
                .clip(RoundedCornerShape(12.dp))
        )
        !failed -> Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
        )
    }
}

/** The release notes, as text and the images between it. */
internal sealed interface NoteBlock {
    data class Words(val text: String) : NoteBlock
    data class Picture(val url: String, val alt: String) : NoteBlock
}

/*
 * How GitHub puts an image in a release: dragging one into the editor writes
 * an HTML tag (<img width=… alt=… src="https://github.com/user-attachments/
 * assets/…">), older ones Markdown (![alt](https://…)), sometimes wrapped in
 * a link. These used to show up in the notes as code. Only https addresses
 * are loaded.
 */
private val LinkedImage = Regex("""\[\s*!\[([^\]]*)]\(\s*<?(https://[^)\s>]+)>?[^)]*\)\s*]\([^)]*\)""")
private val MarkdownImage = Regex("""!\[([^\]]*)]\(\s*<?(https://[^)\s>]+)>?[^)]*\)""")
private val HtmlImage = Regex("""<img\b[^>]*?\bsrc\s*=\s*["'](https://[^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
private val HtmlAlt = Regex("""\balt\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)

internal fun noteBlocks(markdown: String): List<NoteBlock> {
    data class Found(val range: IntRange, val url: String, val alt: String)
    val found = (
        LinkedImage.findAll(markdown).map { Found(it.range, it.groupValues[2], it.groupValues[1]) } +
            MarkdownImage.findAll(markdown).map { Found(it.range, it.groupValues[2], it.groupValues[1]) } +
            HtmlImage.findAll(markdown).map {
                Found(it.range, it.groupValues[1], HtmlAlt.find(it.value)?.groupValues?.get(1).orEmpty())
            }
        ).sortedWith(compareBy<Found> { it.range.first }.thenByDescending { it.range.last })
    val out = mutableListOf<NoteBlock>()
    var at = 0
    for (f in found) {
        if (f.range.first < at) continue // inside one already taken (a linked image's inner image)
        plainNotes(markdown.substring(at, f.range.first)).takeIf { it.isNotBlank() }?.let { out += NoteBlock.Words(it) }
        out += NoteBlock.Picture(f.url, f.alt)
        at = f.range.last + 1
    }
    plainNotes(markdown.substring(at)).takeIf { it.isNotBlank() }?.let { out += NoteBlock.Words(it) }
    return out
}

/** Release notes are Markdown, with some HTML; this is enough to read them as text. */
internal fun plainNotes(markdown: String): String =
    markdown.lines().joinToString("\n") { line ->
        line.trim()
            .removePrefix("### ").removePrefix("## ").removePrefix("# ")
            .replace(Regex("^[-*] "), "• ")
            .replace("**", "")
            .replace("`", "")
            // [text](address) reads as its text; leftover tags (<p>, <br>) go.
            .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
            .replace(Regex("""</?[a-zA-Z][^>]*>"""), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
    }.replace(Regex("""\n{3,}"""), "\n\n").trim()
