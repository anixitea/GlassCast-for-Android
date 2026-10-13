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
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.Dp

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
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val bg = if (active) accent.copy(alpha = 0.20f)
    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.10f)

    Box(
        modifier
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
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
        days < 1 -> tr("Today")
        days < 2 -> tr("Yesterday")
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

/**
 * A list's reading width: at most [max] wide, centered. A phone is narrower
 * than that and sees no change; on a tablet a list stops stretching edge to
 * edge.
 */
fun Modifier.readableWidth(max: Dp = 720.dp): Modifier = this
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = max)
    .fillMaxWidth()

/**
 * How far a page's big title has gone up under the top bar: 0 at rest, 1
 * after 100dp of scroll (or once the first item is gone). It used to be raw
 * pixels over 190, which on a dense phone is only about 70dp.
 */
@androidx.compose.runtime.Composable
fun rememberTitleCollapse(index: () -> Int, offset: () -> Int): androidx.compose.runtime.State<Float> {
    val travel = with(androidx.compose.ui.platform.LocalDensity.current) { 100.dp.toPx() }
    return androidx.compose.runtime.remember(travel) {
        androidx.compose.runtime.derivedStateOf {
            if (index() > 0) 1f else (offset() / travel).coerceIn(0f, 1f)
        }
    }
}

/**
 * The big title fades as it goes up, and is gone by 0.5, before the top bar's
 * small title starts arriving at 0.55, so the two never show at once. Read in
 * the layer, so scrolling doesn't recompose the page.
 */
fun Modifier.bigTitleFade(collapse: () -> Float): Modifier =
    this.graphicsLayer { alpha = (1f - collapse() / 0.5f).coerceIn(0f, 1f) }

/**
 * The app's switch colors. Material's defaults draw an off switch's thumb and
 * rim in the theme's outline color, and the light theme's outline is the same
 * gray as the switch's track, so an off switch was an empty oval. Off is now
 * a gray thumb inside a rimmed track, in light and dark alike; on is the
 * accent with a white thumb (dark on a pale custom accent).
 */
@Composable
internal fun glassSwitchColors(accent: Color = MaterialTheme.colorScheme.primary): SwitchColors {
    val ink = MaterialTheme.colorScheme.onSurface
    return SwitchDefaults.colors(
        checkedTrackColor = accent,
        checkedThumbColor = if (accent.luminance() > 0.6f) Color(0xFF16141A) else Color.White,
        checkedBorderColor = accent,
        uncheckedTrackColor = ink.copy(alpha = 0.06f),
        uncheckedThumbColor = ink.copy(alpha = 0.42f),
        uncheckedBorderColor = ink.copy(alpha = 0.26f)
    )
}
