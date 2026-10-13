package com.glasscast.app.ui

import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.ImportExport
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.GOOGLE_SIGN_IN
import com.glasscast.app.data.Settings
import com.glasscast.app.data.ShowSort
import com.glasscast.app.data.ThemeMode
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * Settings, as a page: the last tab, with the big title every page has.
 *
 * It was a tall sheet whose handle sat under the camera when fully open, and
 * which opened with the app's name as a header, repeating About. Now it's
 * grouped cards in the shape Cider and iOS use: each row has a small colored
 * badge, so a setting is found by color and icon before its words are read.
 *
 * Settings is for what applies to everything. What belongs to one part of the
 * player (shake to restart, skip silence, boost voices) stays with that part.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    appVersion: String,
    onCheckUpdates: () -> Unit,
    onOpenOpml: () -> Unit,
    onOpenSync: () -> Unit,
    syncConnected: Boolean,
    bottomInset: Dp,
    hazeState: HazeState
) {
    val scroll = rememberScrollState()
    val collapse = rememberTitleCollapse({ 0 }, { scroll.value })
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }

    val theme by settings.theme.collectAsStateWithLifecycle()
    val dynamicColor by settings.dynamicColor.collectAsStateWithLifecycle()
    val customColorOn by settings.customColorOn.collectAsStateWithLifecycle()
    val customColor by settings.customColor.collectAsStateWithLifecycle()
    val adaptiveTabBar by settings.adaptiveTabBar.collectAsStateWithLifecycle()
    var pickerOpen by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val skipSeconds by settings.skipSeconds.collectAsStateWithLifecycle()
    val headphoneSkip by settings.headphoneSkip.collectAsStateWithLifecycle()
    val showSort by settings.showSort.collectAsStateWithLifecycle()
    val newEpisodes by settings.newEpisodeNotifications.collectAsStateWithLifecycle()
    val hiddenFromDiscover by settings.discoverHidden.collectAsStateWithLifecycle()
    val hiddenInfo by settings.discoverHiddenInfo.collectAsStateWithLifecycle()
    val crashReport by com.glasscast.app.data.CrashLog.report.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val clearScope = rememberCoroutineScope()
    var notificationsAllowed by remember {
        mutableStateOf(com.glasscast.app.background.NewEpisodeNotifier.canPost(context))
    }
    val askPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> notificationsAllowed = granted }
    fun askForNotifications() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            askPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp)
                .padding(bottom = bottomInset + 24.dp)
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
        ) {
            Spacer(Modifier.statusBarsPadding().height(56.dp))
            Text(
                text = tr("Settings"),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.bigTitleFade { collapse.value }
            )

            if (GOOGLE_SIGN_IN) AccountCard()

            SettingsGroup(tr("APPEARANCE")) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeTile(
                            mode = mode,
                            label = when (mode) {
                                ThemeMode.SYSTEM -> tr("System")
                                ThemeMode.LIGHT -> tr("Light")
                                ThemeMode.DARK -> tr("Dark")
                            },
                            selected = mode == theme,
                            modifier = Modifier.weight(1f),
                            onClick = { settings.setTheme(mode) }
                        )
                    }
                }
                GroupDivider()
                GroupSwitch(
                    icon = Icons.Outlined.Palette,
                    color = Badge.Purple,
                    label = tr("Dynamic color"),
                    checked = dynamicColor,
                    onChange = settings::setDynamicColor
                )
                GroupDivider()
                // The row opens the picker; the switch turns the color on and
                // off without picking again.
                val swatch = Color(customColor)
                GroupRow(
                    icon = Icons.Outlined.ColorLens,
                    color = Badge.Pink,
                    label = tr("Custom color"),
                    onClick = { pickerOpen = true },
                    trailing = {
                        Box(
                            Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape)
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = customColorOn,
                            onCheckedChange = { on ->
                                haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                                settings.setCustomColorOn(on)
                            },
                            colors = glassSwitchColors()
                        )
                    }
                )
                GroupDivider()
                GroupSwitch(
                    icon = Icons.Outlined.ViewDay,
                    color = Badge.Indigo,
                    label = tr("Adaptive tab bar"),
                    checked = adaptiveTabBar,
                    onChange = settings::setAdaptiveTabBar
                )
            }
            if (pickerOpen) {
                ColorPickerSheet(
                    initial = customColor,
                    onPick = { argb ->
                        settings.setCustomColor(argb)
                        settings.setCustomColorOn(true)
                    },
                    onDismiss = { pickerOpen = false }
                )
            }

            SettingsGroup(tr("PLAYBACK")) {
                GroupRow(
                    icon = skipForwardIcon(skipSeconds),
                    color = Badge.Orange,
                    label = tr("Skip buttons"),
                    trailing = {
                        Segmented(
                            options = listOf(
                                SegmentOption(tr("{0}s", 15), skipBackIcon(15)),
                                SegmentOption(tr("{0}s", 30), skipBackIcon(30))
                            ),
                            selected = if (skipSeconds == 15) 0 else 1,
                            onSelect = { settings.setSkipSeconds(if (it == 0) 15 else 30) }
                        )
                    }
                )
                GroupDivider()
                GroupRow(
                    icon = Icons.Outlined.Headphones,
                    color = Badge.Teal,
                    label = tr("Headphone buttons"),
                    trailing = {
                        Segmented(
                            options = listOf(
                                SegmentOption(tr("Skip"), skipForwardIcon(skipSeconds)),
                                SegmentOption(tr("Next"), Icons.Filled.SkipNext)
                            ),
                            selected = if (headphoneSkip) 0 else 1,
                            onSelect = { settings.setHeadphoneSkip(it == 0) }
                        )
                    }
                )
            }

            SettingsGroup(tr("LIBRARY")) {
                GroupRow(icon = Icons.Outlined.SwapVert, color = Badge.Indigo, label = tr("Show order"))
                FlowRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 62.dp, end = 16.dp, bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Pill(
                        label = tr("Recently updated"),
                        active = showSort == ShowSort.RECENTLY_UPDATED,
                        onClick = { settings.setShowSort(ShowSort.RECENTLY_UPDATED) }
                    )
                    Pill(
                        label = tr("Recently played"),
                        active = showSort == ShowSort.RECENTLY_PLAYED,
                        onClick = { settings.setShowSort(ShowSort.RECENTLY_PLAYED) }
                    )
                }
                GroupDivider()
                GroupRow(
                    icon = Icons.Outlined.ImportExport,
                    color = Badge.Green,
                    label = tr("Import / export OPML"),
                    onClick = onOpenOpml,
                    trailing = { Chevron() }
                )
                GroupDivider()
                GroupRow(
                    icon = Icons.Outlined.Sync,
                    color = Badge.Blue,
                    label = tr("gPodder sync"),
                    onClick = onOpenSync,
                    trailing = {
                        if (syncConnected) {
                            Text(
                                text = tr("On"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Chevron()
                    }
                )
            }

            BackupGroup()

            SettingsGroup(tr("NOTIFICATIONS")) {
                GroupSwitch(
                    icon = Icons.Outlined.Notifications,
                    color = Badge.Red,
                    label = tr("New episodes"),
                    checked = newEpisodes,
                    onChange = { on ->
                        settings.setNewEpisodeNotifications(on)
                        if (on && !notificationsAllowed) askForNotifications()
                    }
                )
                if (newEpisodes && !notificationsAllowed) {
                    Text(
                        text = tr("Notifications are blocked — tap to allow"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(start = 62.dp, end = 16.dp, bottom = 12.dp)
                            .clickable { askForNotifications() }
                    )
                }
            }

            if (hiddenFromDiscover.isNotEmpty()) {
                var hiddenOpen by remember { mutableStateOf(false) }
                SettingsGroup(tr("DISCOVER")) {
                    GroupRow(
                        icon = Icons.Outlined.VisibilityOff,
                        color = Badge.Pink,
                        label = if (hiddenFromDiscover.size == 1) tr("{0} show marked not interested", 1)
                        else tr("{0} shows marked not interested", hiddenFromDiscover.size),
                        onClick = { hiddenOpen = !hiddenOpen },
                        trailing = {
                            Icon(
                                imageVector = if (hiddenOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    )
                    AnimatedVisibility(visible = hiddenOpen) {
                        Column(Modifier.padding(start = 62.dp, end = 16.dp, bottom = 10.dp)) {
                            hiddenFromDiscover.sortedBy { (hiddenInfo[it]?.title ?: it).lowercase() }.forEach { url ->
                                val info = hiddenInfo[url]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (info != null && info.artworkUrl.isNotBlank()) {
                                        Artwork(url = info.artworkUrl, sizeDp = 36.dp, corner = 9.dp)
                                        Spacer(Modifier.width(12.dp))
                                    }
                                    Text(
                                        text = info?.title?.takeIf { it.isNotBlank() }
                                            ?: (android.net.Uri.parse(url).host ?: url),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = tr("Show again"),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { settings.unhideFromDiscover(url) }
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    )
                                }
                            }
                            if (hiddenFromDiscover.size > 1) {
                                Text(
                                    text = tr("Show all again"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            settings.clearDiscoverHidden()
                                            hiddenOpen = false
                                        }
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            SettingsGroup(tr("ABOUT")) {
                GroupRow(
                    icon = Icons.Outlined.SystemUpdate,
                    color = Badge.Green,
                    label = tr("Check for updates"),
                    onClick = onCheckUpdates,
                    trailing = {
                        Text(
                            text = appVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                GroupDivider()
                GroupRow(
                    icon = Icons.Outlined.LocalCafe,
                    color = Badge.Yellow,
                    glyph = ChromeInk,
                    label = tr("Buy me a coffee"),
                    onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW,
                                    android.net.Uri.parse(BUY_ME_A_COFFEE)
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                    trailing = {
                        Icon(
                            Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                // Only there after a crash (or a hang the system ended), and
                // gone once sent. Sharing is the only way it leaves the device.
                crashReport?.let { report ->
                    GroupDivider()
                    GroupRow(
                        icon = Icons.Outlined.BugReport,
                        color = Badge.Orange,
                        label = tr("Crash report"),
                        onClick = {
                            val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_SUBJECT, "GlassCast crash")
                                .putExtra(android.content.Intent.EXTRA_TEXT, report)
                            runCatching {
                                context.startActivity(
                                    android.content.Intent.createChooser(send, null)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                                val app = context.applicationContext
                                clearScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    com.glasscast.app.data.CrashLog.clear(app)
                                }
                            }
                        },
                        trailing = {
                            Text(
                                text = tr("Send"),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                }
            }
        }

        TopGlassFade(
            hazeState = hazeState,
            pageColor = MaterialTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
            active = scrolled
        )

        // The bar: the small title arrives once the big one has gone.
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .graphicsLayer {
                        val shown = ((collapse.value - 0.55f) / 0.45f).coerceIn(0f, 1f)
                        alpha = shown
                        translationY = (1f - shown) * 8.dp.toPx()
                    }
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f))
                    .padding(horizontal = 20.dp, vertical = 11.dp)
            ) {
                Text(
                    text = tr("Settings"),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ------------------------------------------------------------------ the parts

/** The badge colors: Apple's system palette, which reads in light and dark alike. */
/** Jonathan's Buy Me a Coffee page, from Settings > About. */
private const val BUY_ME_A_COFFEE = "https://buymeacoffee.com/theglassmaker"

internal object Badge {
    val Yellow = Color(0xFFFFCC00)
    val Orange = Color(0xFFFF9500)
    val Pink = Color(0xFFFF2D55)
    val Indigo = Color(0xFF5856D6)
    val Blue = Color(0xFF0A84FF)
    val Teal = Color(0xFF30B0C7)
    val Green = Color(0xFF34C759)
    val Purple = Color(0xFFAF52DE)
    val Red = Color(0xFFFF3B30)
}

@Composable
internal fun IconBadge(icon: ImageVector, color: Color, size: Dp = 32.dp, glyph: Color = Color.White) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(size * 0.58f))
    }
}

/** A titled card of rows. */
@Composable
internal fun SettingsGroup(title: String?, content: @Composable ColumnScope.() -> Unit) {
    if (title != null) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp)
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        content = content
    )
}

/** A rule between rows, starting where the labels do, so the badges stay a column. */
@Composable
internal fun GroupDivider() {
    Box(
        Modifier
            .padding(start = 62.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    )
}

@Composable
internal fun GroupRow(
    icon: ImageVector,
    color: Color,
    label: String,
    glyph: Color = Color.White,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconBadge(icon, color, glyph = glyph)
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(Modifier.width(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { trailing() }
        }
    }
}

/** A row whose whole width toggles its switch. */
@Composable
internal fun GroupSwitch(
    icon: ImageVector,
    color: Color,
    label: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    val haptics = rememberHaptics()
    fun flip(to: Boolean) {
        haptics.play(if (to) Haptic.ToggleOn else Haptic.ToggleOff)
        onChange(to)
    }
    GroupRow(
        icon = icon,
        color = color,
        label = label,
        onClick = { flip(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = { flip(it) }, colors = glassSwitchColors()) }
    )
}

@Composable
internal fun Chevron() {
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

internal data class SegmentOption(val label: String, val icon: ImageVector? = null)

/** Two or three choices as one capsule; the chosen one is filled. */
@Composable
internal fun Segmented(options: List<SegmentOption>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val ink = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Row(
                Modifier
                    .clip(CircleShape)
                    .background(if (chosen) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable {
                        if (!chosen) {
                            haptics.play(Haptic.Select)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (option.icon != null) {
                    Icon(option.icon, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = ink,
                    maxLines = 1
                )
            }
        }
    }
}

/** GlassCast's icon gradient, top to bottom, for the little phones' covers. */
private val IconGradient = listOf(Color(0xFFD49BF9), Color(0xFF993CD6))

/**
 * A theme choice as a tiny phone drawn in that theme: the ground, a cover in
 * the icon's gradient, two lines of text, and the mini player at the foot.
 * System is the light one with its right half dark.
 */
@Composable
private fun ThemeTile(
    mode: ThemeMode,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptics = rememberHaptics()
    val ring = MaterialTheme.colorScheme.primary
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (!selected) haptics.play(Haptic.Select)
                onClick()
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.72f)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) ring else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind {
                        when (mode) {
                            ThemeMode.LIGHT -> drawMiniScreen(dark = false)
                            ThemeMode.DARK -> drawMiniScreen(dark = true)
                            ThemeMode.SYSTEM -> {
                                drawMiniScreen(dark = false)
                                clipRect(left = size.width / 2f) { drawMiniScreen(dark = true) }
                            }
                        }
                    }
            )
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(ring),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) ring else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun DrawScope.drawMiniScreen(dark: Boolean) {
    val ground = if (dark) Color(0xFF17171B) else Color(0xFFF3F3F5)
    val ink = if (dark) Color.White.copy(alpha = 0.85f) else Color(0xFF101014).copy(alpha = 0.80f)
    val faint = if (dark) Color.White.copy(alpha = 0.30f) else Color(0xFF101014).copy(alpha = 0.22f)
    // The mini player: Cider's chrome is dark in both themes, a step paler on
    // the dark ground so it still shows.
    val chrome = if (dark) Color(0xFF3A3540) else Color(0xFF34343A)
    val w = size.width
    val h = size.height
    drawRect(ground)
    val cover = w * 0.56f
    drawRoundRect(
        brush = Brush.linearGradient(IconGradient, Offset(0f, h * 0.12f), Offset(0f, h * 0.12f + cover)),
        topLeft = Offset((w - cover) / 2f, h * 0.12f),
        size = Size(cover, cover),
        cornerRadius = CornerRadius(w * 0.06f)
    )
    val lineY = h * 0.12f + cover + h * 0.06f
    drawRoundRect(ink, Offset(w * 0.2f, lineY), Size(w * 0.6f, h * 0.035f), CornerRadius(h))
    drawRoundRect(faint, Offset(w * 0.3f, lineY + h * 0.06f), Size(w * 0.4f, h * 0.03f), CornerRadius(h))
    drawRoundRect(chrome, Offset(w * 0.08f, h * 0.84f), Size(w * 0.84f, h * 0.1f), CornerRadius(h * 0.05f))
}
