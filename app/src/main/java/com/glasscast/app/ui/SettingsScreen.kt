package com.glasscast.app.ui

import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Opacity
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glasscast.app.data.GOOGLE_SIGN_IN
import com.glasscast.app.data.Settings
import com.glasscast.app.data.ShowSort
import com.glasscast.app.data.ThemeMode
import com.glasscast.app.data.Look
import com.glasscast.app.ui.seaglass.seaGlass
import com.glasscast.app.ui.seaglass.chromePill
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
    val look by settings.look.collectAsStateWithLifecycle()
    val glassLiquid by settings.glassLiquid.collectAsStateWithLifecycle()
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

    // Liquid Glass: Settings is a grouped page, gray by day with white cards.
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    val pageGround = liquid?.grouped ?: MaterialTheme.colorScheme.background
    Box(
        Modifier
            .fillMaxSize()
            .then(if (liquid != null) Modifier.background(pageGround) else Modifier)
    ) {
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
                // The look first: Original, Sea Glass or Liquid Glass, each
                // drawn as a little phone in that look (Sea Glass's with real
                // glass on it), then the brightness in whichever was picked.
                // All of them in the accent and with the bar as it is now.
                val miniKit = rememberMiniKit(MaterialTheme.colorScheme.primary, adaptiveTabBar)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Look.entries.forEach { option ->
                        LookTile(
                            look = option,
                            label = when (option) {
                                Look.ORIGINAL -> tr("Original")
                                Look.SEA_GLASS -> tr("Sea Glass")
                                Look.LIQUID_GLASS -> tr("Glass")
                            },
                            selected = option == look,
                            kit = miniKit,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (option == Look.SEA_GLASS) {
                                    clearScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        com.glasscast.app.ui.seaglass.GlassBody.warm()
                                    }
                                }
                                if (option == Look.LIQUID_GLASS) {
                                    clearScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                        com.glasscast.app.ui.liquid.LensShader.warm()
                                    }
                                }
                                settings.setLook(option)
                            }
                        )
                    }
                }
                // Glass comes frosted or liquid (clear), as Apple's does.
                if (look == Look.LIQUID_GLASS) {
                    GroupDivider()
                    GroupRow(
                        icon = Icons.Outlined.Opacity,
                        color = Badge.Indigo,
                        label = tr("Glass"),
                        trailing = {
                            Segmented(
                                options = listOf(
                                    SegmentOption(tr("Frosted")),
                                    SegmentOption(tr("Liquid"))
                                ),
                                selected = if (glassLiquid) 1 else 0,
                                onSelect = { settings.setGlassLiquid(it == 1) }
                            )
                        }
                    )
                }
                GroupDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeMode.entries.forEach { mode ->
                        ThemeTile(
                            mode = mode,
                            look = look,
                            kit = miniKit,
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
                AppIconRow()
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
                        AppSwitch(
                            checked = customColorOn,
                            onCheckedChange = { on ->
                                haptics.play(if (on) Haptic.ToggleOn else Haptic.ToggleOff)
                                settings.setCustomColorOn(on)
                            }
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
            pageColor = pageGround,
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
                    // Liquid Glass: Apple's bar title, plain on the page's fade, no pill.
                    .then(
                        if (com.glasscast.app.ui.liquid.LocalLiquid.current != null) Modifier.padding(horizontal = 8.dp, vertical = 11.dp)
                        else Modifier.chromePill(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f), seed = 12).padding(horizontal = 20.dp, vertical = 11.dp)
                    )
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
    // Liquid Glass: Apple's badge, a 30dp rounded square with a white glyph.
    if (com.glasscast.app.ui.liquid.LocalLiquid.current != null) {
        val side = if (size == 32.dp) 30.dp else size
        Box(
            Modifier
                .size(side)
                .clip(RoundedCornerShape(side * 0.23f))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = glyph, modifier = Modifier.size(side * 0.6f))
        }
        return
    }
    // Sea Glass: each badge is a small pebble of glass in its color, so a
    // setting is still found by color first.
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    if (tokens != null) {
        val pigment = com.glasscast.app.ui.seaglass.rememberPigment(color, tokens.dark)
        com.glasscast.app.ui.seaglass.GlassSurface(
            pigment = pigment,
            dark = tokens.dark,
            recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Pebble,
            seed = icon.name.hashCode(),
            corner = 0.62f,
            modifier = Modifier.size(size)
        ) {
            Icon(icon, contentDescription = null, tint = pigment.ink, modifier = Modifier.size(size * 0.56f))
        }
        return
    }
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
    // Liquid Glass: Apple's inset grouped section, the footnote above it and
    // a card with 26dp corners (iOS 26's).
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    if (liquid != null) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = liquid.secondary,
                modifier = Modifier.padding(start = 16.dp, top = 26.dp, bottom = 7.dp)
            )
        } else {
            Spacer(Modifier.height(18.dp))
        }
        // Each section a card of frosted glass, lifted off the page.
        Column(
            Modifier
                .fillMaxWidth()
                .then(
                    with(com.glasscast.app.ui.liquid.FrostedGlass) {
                        Modifier.frosted(liquid.card.copy(alpha = if (liquid.dark) 0.92f else 0.94f), liquid.dark, 26.dp)
                    }
                )
                .clip(RoundedCornerShape(26.dp)),
            content = content
        )
        return
    }
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
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            // Sea Glass: paper cards with a hairline edge.
            .then(if (tokens != null) Modifier.border(0.75.dp, tokens.hairline, RoundedCornerShape(24.dp)) else Modifier),
        content = content
    )
}

/** A rule between rows, starting where the labels do, so the badges stay a column. */
@Composable
internal fun GroupDivider() {
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    Box(
        Modifier
            .padding(start = if (liquid != null) 60.dp else 62.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(liquid?.separator ?: MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
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
        val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
        Text(
            text = label,
            // Liquid Glass: Apple's body text, 17 and regular.
            style = if (liquid != null) MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, lineHeight = 22.sp)
            else MaterialTheme.typography.titleSmall,
            fontWeight = if (liquid != null) FontWeight.Normal else FontWeight.Medium,
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
        trailing = { AppSwitch(checked = checked, onCheckedChange = { flip(it) }) }
    )
}

@Composable
internal fun Chevron() {
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    Icon(
        Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = liquid?.tertiary ?: MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = if (liquid != null) Modifier.size(22.dp) else Modifier
    )
}

internal data class SegmentOption(val label: String, val icon: ImageVector? = null)

/** Two or three choices as one capsule; the chosen one is filled. */
@Composable
internal fun Segmented(options: List<SegmentOption>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    // Sea Glass: a groove in the page, the chosen option a capsule of the
    // accent's glass resting in it.
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    // Liquid Glass: Apple's segmented control, the chosen segment a raised
    // white (charcoal at night) capsule in a faint track, in the label color.
    val liquid = com.glasscast.app.ui.liquid.LocalLiquid.current
    if (liquid != null) {
        LiquidSegmented(options, selected, onSelect, liquid)
        return
    }
    Row(
        Modifier
            .clip(CircleShape)
            .background(
                if (tokens != null) com.glasscast.app.ui.seaglass.quietFill(tokens)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
            .then(if (tokens != null) Modifier.border(0.75.dp, tokens.hairline, CircleShape) else Modifier)
            .padding(3.dp)
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            val ink = when {
                tokens != null && chosen -> tokens.brand.ink
                chosen -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                Modifier
                    .then(
                        if (tokens != null && chosen) {
                            Modifier.seaGlass(
                                pigment = tokens.brand,
                                recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Quiet,
                                seed = 3 + index,
                                dark = tokens.dark,
                                ground = false
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .background(if (chosen && tokens == null) MaterialTheme.colorScheme.primary else Color.Transparent)
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

/*
 * The Appearance choices (2.0, round two). Each is a little screen in a
 * frame, the name under it, and the chosen one ringed in the accent with a
 * check. One design for every look: in the first 2.0 build each look drew
 * its own (Liquid Glass had radio buttons under narrower screens), so the
 * tiles jumped in size and style as you picked between them.
 *
 * The screens are drawn the way the app looks right now: the look tiles
 * each show their own look, the brightness tiles show the look you're in,
 * and all of them take the accent (Dynamic color or Custom color), follow
 * light and dark, and in the Original look show the bar turning pale over
 * dark pages when Adaptive tab bar is on. Each look's name is set in its own
 * typeface. Sea Glass's play pebble is real glass, as in the app.
 */

/** GlassCast's icon gradient, top to bottom, and a blue to sit beside it: the little screens' covers. */
private val IconGradient = listOf(Color(0xFFD49BF9), Color(0xFF993CD6))
private val CoverBlue = listOf(Color(0xFF8FB8F0), Color(0xFF4C7CD0))

/** What the little screens draw with: the tab glyphs, the accent, the Adaptive tab bar setting. */
private class MiniKit(val glyphs: List<androidx.compose.ui.graphics.painter.Painter>, val accent: Color, val adaptive: Boolean)

@Composable
private fun rememberMiniKit(accent: Color, adaptive: Boolean): MiniKit {
    val glyphs = listOf(
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Library),
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Latest),
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Downloads),
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Discover),
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Search),
        androidx.compose.ui.graphics.vector.rememberVectorPainter(com.glasscast.app.ui.liquid.LiquidIcons.Settings)
    )
    return remember(glyphs, accent, adaptive) { MiniKit(glyphs, accent, adaptive) }
}

private fun DrawScope.miniLook(look: Look, dark: Boolean, kit: MiniKit) = when (look) {
    Look.ORIGINAL -> drawOriginalMini(dark, kit)
    Look.SEA_GLASS -> drawSeaMini(dark, kit)
    Look.LIQUID_GLASS -> drawLiquidMini(dark, kit)
}

private fun DrawScope.miniGlyph(kit: MiniKit, index: Int, cx: Float, cy: Float, side: Float, color: Color) {
    val painter = kit.glyphs[index]
    translate(cx - side / 2f, cy - side / 2f) {
        with(painter) { draw(Size(side, side), 1f, androidx.compose.ui.graphics.ColorFilter.tint(color)) }
    }
}

/** The page every look shares: a title, an accent chip, two covers with their names. */
private fun DrawScope.miniPage(ink: Color, faint: Color, accent: Color, titleH: Float, coverR: Float, pools: List<Color>? = null) {
    val w = size.width
    val h = size.height
    drawRoundRect(ink, Offset(w * 0.10f, h * 0.085f), Size(w * 0.50f, titleH), CornerRadius(titleH / 2f))
    drawRoundRect(accent, Offset(w * 0.10f, h * 0.085f + titleH + h * 0.03f), Size(w * 0.22f, h * 0.042f), CornerRadius(h * 0.021f))
    val top = h * 0.235f
    val s = w * 0.37f
    listOf(w * 0.10f to IconGradient, w * 0.53f to CoverBlue).forEachIndexed { i, (x, gradient) ->
        pools?.getOrNull(i)?.let { pool ->
            drawRect(
                Brush.radialGradient(listOf(pool.copy(alpha = 0.55f), pool.copy(alpha = 0f)), center = Offset(x + s / 2f, top + s / 2f), radius = s * 0.85f),
                topLeft = Offset(x - s * 0.4f, top - s * 0.4f),
                size = Size(s * 1.8f, s * 1.8f)
            )
        }
        drawRoundRect(Brush.linearGradient(gradient, Offset(0f, top), Offset(0f, top + s)), Offset(x, top), Size(s, s), CornerRadius(coverR))
        drawRoundRect(faint, Offset(x, top + s + h * 0.03f), Size(s * 0.85f, h * 0.026f), CornerRadius(h))
        drawRoundRect(faint.copy(alpha = faint.alpha * 0.7f), Offset(x, top + s + h * 0.068f), Size(s * 0.55f, h * 0.022f), CornerRadius(h))
    }
}

/** Where a mini's mini player sits, as fractions of the height. */
private const val MiniCardTop = 0.705f
private const val MiniCardHeight = 0.115f

/** The Original look: the dark card with the wave and play, the dark bar with the tab you're on spelled out. */
private fun DrawScope.drawOriginalMini(dark: Boolean, kit: MiniKit) {
    val w = size.width
    val h = size.height
    drawRect(if (dark) Color(0xFF17171B) else Color(0xFFF3F3F5))
    val ink = if (dark) Color.White.copy(alpha = 0.88f) else Color(0xFF101014).copy(alpha = 0.85f)
    val faint = if (dark) Color.White.copy(alpha = 0.30f) else Color(0xFF101014).copy(alpha = 0.22f)
    miniPage(ink, faint, kit.accent, h * 0.05f, w * 0.05f)
    // Over a dark page the chrome is dark too, unless Adaptive tab bar turns it pale.
    val pale = dark && kit.adaptive
    val card = if (pale) Color(0xFFE9E3EE) else if (dark) Color(0xFF3A2F44) else Color(0xFF34303A)
    val bar = if (pale) Color(0xFFDCD5E3) else if (dark) Color(0xFF2A2430) else Color(0xFF25222A)
    val onBar = if (pale) Color(0xFF16141A) else Color(0xFFF4F3F7)
    val button = if (pale) Color(0xFF5B4A6A) else Color(0xFFE9D9F7)
    val y = h * MiniCardTop
    val ch = h * MiniCardHeight
    drawRoundRect(card, Offset(w * 0.05f, y), Size(w * 0.90f, ch), CornerRadius(ch * 0.42f))
    drawRoundRect(Brush.linearGradient(IconGradient, Offset(0f, y + ch * 0.17f), Offset(0f, y + ch * 0.83f)), Offset(w * 0.08f, y + ch * 0.17f), Size(ch * 0.66f, ch * 0.66f), CornerRadius(ch * 0.16f))
    val textX = w * 0.08f + ch * 0.8f
    drawRoundRect(onBar.copy(alpha = 0.85f), Offset(textX, y + ch * 0.30f), Size(w * 0.34f, ch * 0.12f), CornerRadius(ch))
    val wave = androidx.compose.ui.graphics.Path()
    val waveY = y + ch * 0.66f
    val seg = w * 0.30f / 6f
    wave.moveTo(textX, waveY)
    for (i in 0 until 6) {
        wave.quadraticTo(textX + seg * (i + 0.5f), waveY + (if (i % 2 == 0) -1f else 1f) * ch * 0.10f, textX + seg * (i + 1), waveY)
    }
    drawPath(wave, button, style = Stroke(width = ch * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
    drawCircle(button, radius = ch * 0.33f, center = Offset(w * 0.93f - ch * 0.5f, y + ch / 2f))
    val by = h * 0.845f
    val bh = h * 0.10f
    drawRoundRect(bar, Offset(w * 0.05f, by), Size(w * 0.90f, bh), CornerRadius(bh / 2f))
    drawRoundRect(onBar.copy(alpha = 0.16f), Offset(w * 0.07f, by + bh * 0.13f), Size(w * 0.30f, bh * 0.74f), CornerRadius(bh * 0.37f))
    miniGlyph(kit, 0, w * 0.07f + bh * 0.55f, by + bh / 2f, bh * 0.42f, onBar)
    drawRoundRect(onBar, Offset(w * 0.07f + bh * 0.95f, by + bh * 0.43f), Size(w * 0.11f, bh * 0.14f), CornerRadius(bh))
    for (i in 1..5) miniGlyph(kit, i, w * 0.44f + (i - 1) * w * 0.105f, by + bh / 2f, bh * 0.36f, onBar.copy(alpha = 0.8f))
}

/** Where Sea Glass's play pebble sits in its mini, for the real glass laid over it. */
private const val SeaMiniPebbleRight = 0.93f
private const val SeaMiniPebbleW = 0.8f
private const val SeaMiniPebbleH = 0.64f

/** Sea Glass: paper (or tide at night) washed with color, covers in pools, the paper card and the pebbles. */
private fun DrawScope.drawSeaMini(dark: Boolean, kit: MiniKit) {
    val w = size.width
    val h = size.height
    val ground = if (dark) com.glasscast.app.ui.seaglass.SeaTide else com.glasscast.app.ui.seaglass.SeaPaper
    val ink = if (dark) Color(0xFFE8EEEA) else Color(0xFF233C37)
    val wash = if (dark) Color(0xFF52405E) else Color(0xFFE1CDE8)
    drawRect(ground)
    drawRect(Brush.radialGradient(listOf(wash.copy(alpha = if (dark) 0.5f else 0.85f), wash.copy(alpha = 0f)), center = Offset(w * 0.5f, h * 0.25f), radius = w * 0.95f))
    miniPage(ink.copy(alpha = 0.85f), ink.copy(alpha = 0.30f), kit.accent, h * 0.042f, w * 0.075f, pools = listOf(Color(0xFFC79BEA), Color(0xFF8FB8F0)))
    val card = androidx.compose.ui.graphics.lerp(if (dark) Color(0xFF17221F) else Color(0xFFFBFBF7), wash, 0.25f)
    val y = h * MiniCardTop
    val ch = h * MiniCardHeight
    drawRoundRect(card, Offset(w * 0.05f, y), Size(w * 0.90f, ch), CornerRadius(ch * 0.42f))
    drawRoundRect(Brush.linearGradient(IconGradient, Offset(0f, y + ch * 0.17f), Offset(0f, y + ch * 0.83f)), Offset(w * 0.08f, y + ch * 0.17f), Size(ch * 0.66f, ch * 0.66f), CornerRadius(ch * 0.2f))
    drawRoundRect(ink.copy(alpha = 0.7f), Offset(w * 0.08f + ch * 0.8f, y + ch * 0.30f), Size(w * 0.34f, ch * 0.12f), CornerRadius(ch))
    drawRoundRect(ink.copy(alpha = 0.3f), Offset(w * 0.08f + ch * 0.8f, y + ch * 0.58f), Size(w * 0.24f, ch * 0.10f), CornerRadius(ch))
    val pebble = androidx.compose.ui.graphics.lerp(wash, kit.accent, 0.25f)
    drawOval(pebble, Offset(w * SeaMiniPebbleRight - ch * (SeaMiniPebbleW + 0.15f), y + ch * (1f - SeaMiniPebbleH) / 2f), Size(ch * SeaMiniPebbleW, ch * SeaMiniPebbleH))
    val by = h * 0.845f
    val bh = h * 0.10f
    drawRoundRect(if (dark) Color(0xFF414B48) else Color(0xFFE9E9E2), Offset(w * 0.05f, by), Size(w * 0.90f, bh), CornerRadius(bh / 2f))
    drawOval(androidx.compose.ui.graphics.lerp(wash, kit.accent, 0.3f), Offset(w * 0.07f, by + bh * 0.12f), Size(w * 0.20f, bh * 0.76f))
    for (i in 0..5) miniGlyph(kit, i, w * 0.17f + i * w * 0.135f, by + bh / 2f, bh * 0.36f, ink.copy(alpha = if (i == 0) 0.9f else 0.75f))
}

/** Liquid Glass: white (or black), a bold title, the glass mini player with its bubble and ring, the capsule of tabs. */
private fun DrawScope.drawLiquidMini(dark: Boolean, kit: MiniKit) {
    val w = size.width
    val h = size.height
    val ink = if (dark) Color.White else Color.Black
    drawRect(if (dark) Color.Black else Color.White)
    miniPage(ink, ink.copy(alpha = if (dark) 0.28f else 0.16f), kit.accent, h * 0.058f, w * 0.045f)
    val accent = com.glasscast.app.ui.liquid.legibleAccent(kit.accent, dark)
    val glass = if (dark) Color(0xFF2C2C2E).copy(alpha = 0.92f) else Color(0xFFF4F4F7).copy(alpha = 0.94f)
    val rim = Color.White.copy(alpha = if (dark) 0.22f else 1f)
    val shade = Color.Black.copy(alpha = if (dark) 0.30f else 0.10f)
    fun capsule(top: Float, height: Float) {
        drawRoundRect(shade, Offset(w * 0.05f, top + h * 0.006f), Size(w * 0.90f, height), CornerRadius(height / 2f))
        drawRoundRect(glass, Offset(w * 0.05f, top), Size(w * 0.90f, height), CornerRadius(height / 2f))
        drawRoundRect(rim, Offset(w * 0.05f, top), Size(w * 0.90f, height), CornerRadius(height / 2f), style = Stroke(1f))
    }
    val y = h * 0.70f
    val ah = h * 0.105f
    capsule(y, ah)
    // The mini player: the cover, the title and show, the line of how far in.
    val art = ah * 0.68f
    drawRoundRect(Brush.linearGradient(IconGradient, Offset(0f, y + (ah - art) / 2f), Offset(0f, y + (ah + art) / 2f)), Offset(w * 0.05f + ah * 0.16f, y + (ah - art) / 2f), Size(art, art), CornerRadius(art * 0.24f))
    val textX = w * 0.05f + ah * 0.16f + art + ah * 0.2f
    drawRoundRect(ink.copy(alpha = 0.85f), Offset(textX, y + ah * 0.22f), Size(w * 0.38f, ah * 0.13f), CornerRadius(ah))
    drawRoundRect(ink.copy(alpha = 0.35f), Offset(textX, y + ah * 0.45f), Size(w * 0.26f, ah * 0.11f), CornerRadius(ah))
    drawRoundRect(ink.copy(alpha = 0.13f), Offset(textX, y + ah * 0.68f), Size(w * 0.50f, ah * 0.07f), CornerRadius(ah))
    drawRoundRect(accent, Offset(textX, y + ah * 0.68f), Size(w * 0.17f, ah * 0.07f), CornerRadius(ah))
    val by = h * 0.835f
    val bh = h * 0.115f
    capsule(by, bh)
    // As the bar is: the tab you're on widened with its name, the rest icons.
    drawRoundRect(ink.copy(alpha = if (dark) 0.15f else 0.075f), Offset(w * 0.07f, by + bh * 0.12f), Size(w * 0.30f, bh * 0.76f), CornerRadius(bh * 0.38f))
    miniGlyph(kit, 0, w * 0.07f + bh * 0.5f, by + bh / 2f, bh * 0.36f, accent)
    drawRoundRect(accent, Offset(w * 0.07f + bh * 0.88f, by + bh * 0.44f), Size(w * 0.11f, bh * 0.12f), CornerRadius(bh))
    for (i in 1..5) miniGlyph(kit, i, w * 0.44f + (i - 1) * w * 0.105f, by + bh / 2f, bh * 0.32f, ink)
}

/**
 * One Appearance choice: the little screen in its frame, the check when it's
 * chosen, the name under it. [overlay] is laid over the screen (Sea Glass's
 * real pebble).
 */
@Composable
private fun AppearanceTile(
    label: String,
    labelStyle: androidx.compose.ui.text.TextStyle,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    overlay: (@Composable androidx.compose.foundation.layout.BoxWithConstraintsScope.() -> Unit)? = null,
    draw: DrawScope.() -> Unit
) {
    val haptics = rememberHaptics()
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable {
                if (!selected) haptics.play(Haptic.Select)
                onClick()
            }
            .semantics {
                role = androidx.compose.ui.semantics.Role.RadioButton
                this.selected = selected
            }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.70f)
                    .border(
                        width = if (selected) 2.5.dp else 1.dp,
                        color = if (selected) accent else ink.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .drawBehind(draw)
            ) {
                if (overlay != null) {
                    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize(), content = overlay)
                }
            }
            if (selected) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(accent),
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
            style = labelStyle,
            color = if (selected) accent else ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A look's name in that look's own typeface, the same whichever look you're in. */
@Composable
private fun lookLabelStyle(look: Look, selected: Boolean): androidx.compose.ui.text.TextStyle = when (look) {
    Look.ORIGINAL -> com.glasscast.app.ui.theme.GlassType.labelLarge.copy(
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
    )
    Look.SEA_GLASS -> MaterialTheme.typography.labelLarge.copy(
        fontFamily = com.glasscast.app.ui.theme.SeaSerif,
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
        fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.None
    )
    Look.LIQUID_GLASS -> MaterialTheme.typography.labelLarge.copy(
        fontFamily = com.glasscast.app.ui.theme.LiquidSans,
        fontSize = 14.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        letterSpacing = 0.sp
    )
}

@Composable
private fun LookTile(
    look: Look,
    label: String,
    selected: Boolean,
    kit: MiniKit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dark = com.glasscast.app.ui.theme.LocalIsDark.current
    AppearanceTile(
        label = label,
        labelStyle = lookLabelStyle(look, selected),
        selected = selected,
        modifier = modifier,
        onClick = onClick,
        overlay = if (look == Look.SEA_GLASS) {
            {
                // Real glass: the play pebble, in the accent's glass.
                val pigment = remember(kit.accent, dark) { com.glasscast.app.ui.seaglass.seaPigment(kit.accent, dark) }
                val ch = maxHeight * MiniCardHeight
                Box(
                    Modifier
                        .offset(
                            x = maxWidth * SeaMiniPebbleRight - ch * (SeaMiniPebbleW + 0.15f),
                            y = maxHeight * MiniCardTop + ch * (1f - SeaMiniPebbleH) / 2f
                        )
                        .size(width = ch * SeaMiniPebbleW, height = ch * SeaMiniPebbleH)
                        .seaGlass(
                            pigment = pigment,
                            recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Tumbled,
                            seed = 8,
                            dark = dark
                        )
                )
            }
        } else {
            null
        }
    ) {
        miniLook(look, dark, kit)
    }
}

@Composable
private fun ThemeTile(
    mode: ThemeMode,
    look: Look,
    label: String,
    selected: Boolean,
    kit: MiniKit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppearanceTile(
        label = label,
        labelStyle = MaterialTheme.typography.labelLarge.copy(
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        ),
        selected = selected,
        modifier = modifier,
        onClick = onClick
    ) {
        when (mode) {
            ThemeMode.LIGHT -> miniLook(look, false, kit)
            ThemeMode.DARK -> miniLook(look, true, kit)
            ThemeMode.SYSTEM -> {
                miniLook(look, false, kit)
                clipRect(left = size.width / 2f) { miniLook(look, true, kit) }
            }
        }
    }
}

/** Apple's segmented control (Liquid Glass). */
@Composable
private fun LiquidSegmented(
    options: List<SegmentOption>,
    selected: Int,
    onSelect: (Int) -> Unit,
    tokens: com.glasscast.app.ui.liquid.LiquidTokens
) {
    val haptics = rememberHaptics()
    // Frosted Glass: a groove of the system fill, the chosen segment a
    // capsule of frosted glass resting in it.
    Row(
        Modifier
            .clip(CircleShape)
            .background(tokens.fill)
            .padding(2.dp)
    ) {
        options.forEachIndexed { index, option ->
            val chosen = index == selected
            Row(
                Modifier
                    .then(
                        if (chosen) {
                            with(com.glasscast.app.ui.liquid.FrostedGlass) {
                                Modifier.frosted(if (tokens.dark) Color(0xFF636366).copy(alpha = 0.9f) else Color.White.copy(alpha = 0.96f), tokens.dark)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .clip(CircleShape)
                    .clickable {
                        if (!chosen) {
                            haptics.play(Haptic.Select)
                            onSelect(index)
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (option.icon != null) {
                    Icon(option.icon, contentDescription = null, tint = tokens.ink, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Medium,
                    color = tokens.ink,
                    maxLines = 1
                )
            }
        }
    }
}
