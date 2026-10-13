package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * The one repeated container. Rounded 18dp, onBackground at 10%, accent-tinted
 * at 20% when active. Speed, timer, filters, secondary actions.
 */
@Composable
fun Pill(
    label: String,
    active: Boolean = false,
    accent: Color = MaterialTheme.colorScheme.primary,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    onClick: (() -> Unit)? = null
) {
    val bg = if (active) accent.copy(alpha = 0.20f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)

    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

/** Hairline rule. One physical-ish line, not Material's 1dp divider. */
@Composable
fun Hairline(modifier: Modifier = Modifier, alpha: Float = 1f) {
    Box(
        modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = alpha))
    )
}

/** 1:02:03 or 12:34. Never -0:00 — that shape is what a zero duration looks like. */
fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatRemaining(positionMs: Long, durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    return "-" + formatTime((durationMs - positionMs).coerceAtLeast(0))
}

/** "3m26s" — the chapter-list shape, reused for episode length. */
fun formatCompact(ms: Long): String {
    if (ms <= 0) return ""
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        else -> "${total}s"
    }
}

private val dayFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val yearFormat = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

fun formatDate(epochMs: Long): String {
    if (epochMs <= 0) return ""
    val now = System.currentTimeMillis()
    val days = abs(now - epochMs) / 86_400_000L
    return when {
        days < 1 -> "Today"
        days < 2 -> "Yesterday"
        days < 330 -> dayFormat.format(Date(epochMs))
        else -> yearFormat.format(Date(epochMs))
    }
}

/** Strips the HTML that feeds put in <description> so list rows stay one line. */
fun stripHtml(raw: String): String =
    raw.replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace(Regex("\\s+"), " ")
        .trim()

/**
 * Ask for focus once the node actually exists.
 *
 * `FocusRequester.requestFocus()` throws if the requester is not attached to a
 * composed node, and on this screen that is the normal case rather than the
 * exception: a rail tap changes the tab and asks the *new* screen for focus in
 * the same frame, before it has composed. Worse, most of these requesters live
 * on the first item of a lazy list, which does not exist until that list
 * measures — and stops existing again once it scrolls away.
 *
 * So: try, yield a frame, try again, and give up quietly. Focus landing a frame
 * late is invisible; an exception is not.
 */
suspend fun FocusRequester.requestWhenReady(attempts: Int = 6) {
    repeat(attempts) {
        if (runCatching { requestFocus() }.isSuccess) return
        delay(40)
    }
}

/** Fire-and-forget version for callbacks, where there is no scope to suspend in. */
fun FocusRequester.requestSafely() {
    runCatching { requestFocus() }
}
