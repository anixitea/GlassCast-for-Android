package com.glasscast.app.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentSize
import com.glasscast.app.ui.liquid.liquidGlass
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.border
import com.glasscast.app.ui.seaglass.seaGlass
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.player.SleepTimer
import kotlin.math.roundToInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmark

enum class PanelTab { UP_NEXT, INFO, BOOKMARKS, SPEED, TIMER }

/** Height of the panel's header — also the collapsed strip, since they're one surface. */
val PanelHeaderHeight: Dp = 80.dp

/*
 * The panel's text color: white on the dark panel of the Original look, ink
 * on Sea Glass's paper. Provided by PlayerPanel to everything inside it.
 */
private val LocalPanelInk = androidx.compose.runtime.staticCompositionLocalOf { Color.White }
private val Ink: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPanelInk.current
private val InkDim: Color
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPanelInk.current.copy(alpha = 0.62f)

/**
 * The pull-up panel: Up Next, episode info, speed and the sleep timer, inside
 * the player rather than in sheets laid over it.
 *
 * One surface in two states. Collapsed, its header is the strip along the
 * player's foot; pulled up, that same header rides to the top and the body
 * comes into view below it.
 *
 * **Opaque, in the cover's hue.** The first version was a translucent dark
 * layer over the artwork, and the artwork showed straight through it — faces
 * and cover type behind the queue, which is what made it look broken. It's a
 * solid surface now, the cover's own hue at a fixed dark lightness, so it
 * belongs to the episode and still reads cleanly. And it renders under a full
 * dark color scheme rather than a patched copy of the app's, so nothing in it
 * can pick up a light-theme color — which is why light mode looked worse.
 */
@Composable
fun PlayerPanel(
    expand: Float,
    tab: PanelTab,
    art: String,
    upNextCount: Int,
    bookmarkCount: Int,
    speed: Float,
    timerArmed: Boolean,
    surface: Color,
    accent: Color,
    /** The text color: white on the Original look's dark panel, ink on Sea Glass's paper. */
    ink: Color = Color.White,
    /** Sea Glass: the cover's glass, for the capsule of buttons. */
    glass: com.glasscast.app.ui.seaglass.Pigment? = null,
    /** Liquid Glass: the player's picture, for the panel to be glass over. */
    glassBackdrop: com.glasscast.app.ui.liquid.LiquidBackdrop? = null,
    onTab: (PanelTab) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable (PanelTab) -> Unit
) {
    androidx.compose.runtime.CompositionLocalProvider(LocalPanelInk provides ink) {
        if (glassBackdrop != null) {
            LiquidPanelContent(expand, tab, upNextCount, bookmarkCount, speed, timerArmed, accent, glassBackdrop, onTab, onDrag, onDragEnd, modifier, body)
        } else {
            PanelContent(expand, tab, art, upNextCount, bookmarkCount, speed, timerArmed, surface, accent, glass, onTab, onDrag, onDragEnd, modifier, body)
        }
    }
}

/** The panel's title for a tab, and the line under it. */
@Composable
private fun panelTitle(shown: PanelTab?): String = when (shown) {
    PanelTab.INFO -> tr("Episode info")
    PanelTab.BOOKMARKS -> tr("Bookmarks")
    PanelTab.SPEED -> tr("Speed & sound")
    PanelTab.TIMER -> tr("Sleep timer")
    else -> tr("Playing Next")
}

@Composable
private fun panelSubtitle(shown: PanelTab?, upNextCount: Int, bookmarkCount: Int, speed: Float, timerArmed: Boolean): String = when {
    shown == PanelTab.INFO -> tr("Notes, chapters and transcript")
    shown == PanelTab.BOOKMARKS -> when (bookmarkCount) {
        0 -> tr("No bookmarks")
        1 -> tr("1 bookmark")
        else -> tr("{0} bookmarks", bookmarkCount)
    }
    shown == PanelTab.SPEED -> formatSpeed(speed)
    shown == PanelTab.TIMER -> if (timerArmed) tr("Running") else tr("Off")
    upNextCount == 0 -> tr("Nothing queued")
    upNextCount == 1 -> tr("1 episode")
    else -> tr("{0} episodes", upNextCount)
}

/**
 * Liquid Glass's panel. At rest it's a capsule of glass floating over the
 * foot of the player, the five buttons spread along it. Pulled up, the
 * capsule grows out to the edges and up into a sheet of the same glass, the
 * buttons gather to the right and the tab's title comes in at the left. It
 * refracts the player's picture ([backdrop]) all the way, so the cover's
 * color carries on through it.
 */
@Composable
private fun LiquidPanelContent(
    expand: Float,
    tab: PanelTab,
    upNextCount: Int,
    bookmarkCount: Int,
    speed: Float,
    timerArmed: Boolean,
    accent: Color,
    backdrop: com.glasscast.app.ui.liquid.LiquidBackdrop,
    onTab: (PanelTab) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier,
    body: @Composable (PanelTab) -> Unit
) {
    val e = expand.coerceIn(0f, 1f)
    val open = e > 0.5f
    // The capsule: in from the sides and off the top edge at rest, the
    // height of the header strip less its margins.
    val capsule = PanelHeaderHeight - 14.dp
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val inset = 12.dp * (1f - e)
        val top = 6.dp * (1f - e)
        val shapeH = capsule + (maxHeight - capsule) * e
        val shape = RoundedCornerShape(34.dp)
        // Pulled up, the glass reaches a little past the screen's sides and
        // foot (2% wider), so the lines of light along its rim are out of
        // sight there; only the top edge, rounded, keeps them. At the screen's
        // edge they read as stray white lines.
        val overhang = maxWidth * 0.01f * e
        Box(
            Modifier
                .offset(inset - overhang, top)
                // Allowed past the panel's bounds. A plain size can't be wider
                // than the screen: it was cut back to the screen's width while
                // still shifted left, which left a gap down the right side.
                .wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(maxWidth - inset * 2 + overhang * 2, shapeH + 24.dp * e)
                .liquidGlass(backdrop, com.glasscast.app.ui.liquid.LiquidMaterial.panel(), corner = 34.dp, dark = true)
        )
        Column(
            Modifier
                .offset(inset, top)
                .size(maxWidth - inset * 2, maxHeight - top)
                .clip(shape)
                // The panel owns every touch that lands on it.
                .pointerInput(Unit) {}
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(capsule)
                    .pointerInput(Unit) {
                        var velocity = 0f
                        detectVerticalDragGestures(
                            onDragStart = { velocity = 0f },
                            onVerticalDrag = { change, amount ->
                                change.consume()
                                velocity = amount
                                onDrag(amount)
                            },
                            onDragEnd = { onDragEnd(velocity * 60f) },
                            onDragCancel = { onDragEnd(0f) }
                        )
                    }
                    .padding(top = 7.dp)
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 32.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Ink.copy(alpha = 0.36f))
                )
                Spacer(Modifier.height(5.dp))
                androidx.compose.foundation.layout.BoxWithConstraints(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(horizontal = 10.dp)
                ) {
                    // Spread along the capsule at rest; gathered into a
                    // pill at the right once it's open.
                    val packed = 46.dp * 5 + 8.dp
                    val rowW = maxWidth + (packed - maxWidth) * e
                    val titleShown = ((e - 0.35f) / 0.5f).coerceIn(0f, 1f)
                    if (titleShown > 0f) {
                        Column(
                            Modifier
                                .align(Alignment.CenterStart)
                                .width((maxWidth - rowW - 10.dp).coerceAtLeast(0.dp))
                                .padding(start = 8.dp)
                                .graphicsLayer { alpha = titleShown }
                        ) {
                            val shown = if (open) tab else null
                            Text(
                                text = panelTitle(shown),
                                style = MaterialTheme.typography.titleMedium,
                                color = Ink,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = panelSubtitle(shown, upNextCount, bookmarkCount, speed, timerArmed),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // Gathered, the buttons sit in a pill of glass set into
                    // the panel; each open tab (or a press) is a lighter
                    // drop of glass under its button.
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .width(rowW)
                    ) {
                        if (e > 0.01f) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .graphicsLayer { alpha = e }
                                    .liquidGlass(backdrop, com.glasscast.app.ui.liquid.LiquidMaterial.inset(), corner = 20.dp, dark = true)
                            )
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PanelTabButton(open && tab == PanelTab.UP_NEXT, { onTab(PanelTab.UP_NEXT) }, backdrop) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, tr("Up Next"), Modifier.size(21.dp), tint = Ink)
                            }
                            PanelTabButton(open && tab == PanelTab.INFO, { onTab(PanelTab.INFO) }, backdrop) {
                                Icon(Icons.Outlined.Info, tr("Episode info"), Modifier.size(21.dp), tint = Ink)
                            }
                            PanelTabButton(open && tab == PanelTab.BOOKMARKS, { onTab(PanelTab.BOOKMARKS) }, backdrop) {
                                Icon(
                                    if (bookmarkCount > 0) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    tr("Bookmarks"),
                                    Modifier.size(21.dp),
                                    tint = Ink
                                )
                            }
                            PanelTabButton(open && tab == PanelTab.SPEED, { onTab(PanelTab.SPEED) }, backdrop) {
                                Text(formatSpeed(speed), style = MaterialTheme.typography.titleSmall, color = Ink)
                            }
                            PanelTabButton((open && tab == PanelTab.TIMER) || timerArmed, { onTab(PanelTab.TIMER) }, backdrop) {
                                Icon(
                                    Icons.Filled.Bedtime,
                                    tr("Sleep timer"),
                                    Modifier.size(20.dp),
                                    tint = if (timerArmed) accent else Ink
                                )
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .graphicsLayer { alpha = ((e - 0.2f) / 0.5f).coerceIn(0f, 1f) }
            ) {
                PanelColors(accent) { body(tab) }
            }
        }
    }
}

@Composable
private fun PanelContent(
    expand: Float,
    tab: PanelTab,
    art: String,
    upNextCount: Int,
    bookmarkCount: Int,
    speed: Float,
    timerArmed: Boolean,
    surface: Color,
    accent: Color,
    glass: com.glasscast.app.ui.seaglass.Pigment?,
    onTab: (PanelTab) -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (Float) -> Unit,
    modifier: Modifier,
    body: @Composable (PanelTab) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
    val open = expand > 0.5f
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current

    Column(
        modifier
            // Sea Glass: a sheet of paper laid over the page, its edge
            // lifted by a soft shadow above it and a hairline.
            .then(
                if (tokens != null) {
                    Modifier.drawBehind {
                        val lift = 14.dp.toPx()
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to Color.Black.copy(alpha = if (tokens.dark) 0.28f else 0.06f),
                                startY = -lift,
                                endY = 30.dp.toPx()
                            ),
                            topLeft = androidx.compose.ui.geometry.Offset(0f, -lift),
                            size = androidx.compose.ui.geometry.Size(size.width, lift + 30.dp.toPx())
                        )
                    }
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .background(surface)
            .then(if (tokens != null) Modifier.border(0.75.dp, tokens.hairline, shape) else Modifier)
            // The panel owns every touch that lands on it.
            .pointerInput(Unit) {}
    ) {
        // ---- header: the strip when collapsed, the title bar when open ----
        Column(
            Modifier
                .fillMaxWidth()
                .height(PanelHeaderHeight)
                .pointerInput(Unit) {
                    var velocity = 0f
                    detectVerticalDragGestures(
                        onDragStart = { velocity = 0f },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            velocity = amount
                            onDrag(amount)
                        },
                        onDragEnd = { onDragEnd(velocity * 60f) },
                        onDragCancel = { onDragEnd(0f) }
                    )
                }
                .padding(top = 8.dp)
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.34f))
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The cover takes up no room once the panel is open: its width
                // shrinks with it, so the title slides left into its place
                // instead of being left indented beside a gap.
                val k = (1f - expand * 1.4f).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .width(52.dp * k)
                        .graphicsLayer {
                            alpha = k
                            scaleX = 0.6f + 0.4f * k
                            scaleY = 0.6f + 0.4f * k
                        }
                ) {
                    Artwork(url = art, sizeDp = 40.dp, corner = 10.dp)
                }

                AnimatedContent(
                    targetState = if (open) tab else null,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    modifier = Modifier.weight(1f),
                    label = "panelTitle"
                ) { shown ->
                    Column {
                        Text(
                            text = panelTitle(shown),
                            // Sea Glass's serif runs wider: a size down, and an
                            // ellipsis rather than losing the second word.
                            style = if (tokens != null) com.glasscast.app.ui.seaglass.rowTitleStyle() else MaterialTheme.typography.titleMedium,
                            color = Ink,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = panelSubtitle(shown, upNextCount, bookmarkCount, speed, timerArmed),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Cider's button pill, with speed and timer joining Up Next and
                // info. Each opens the panel on its own tab; the lit one is the
                // tab showing, and only while the panel is actually open.
                Row(
                    Modifier
                        .then(
                            if (tokens != null && glass != null) {
                                Modifier.seaGlass(
                                    pigment = glass,
                                    recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Quiet,
                                    seed = 17,
                                    dark = tokens.dark
                                )
                            } else {
                                Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Ink.copy(alpha = 0.10f))
                            }
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // On glass, the icons take the glass's ink.
                    val onCapsule = if (tokens != null && glass != null) glass.ink else Ink
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalPanelInk provides onCapsule,
                        com.glasscast.app.ui.seaglass.LocalSeaTint provides glass
                    ) {
                    PanelTabButton(open && tab == PanelTab.UP_NEXT, { onTab(PanelTab.UP_NEXT) }) {
                        Icon(Icons.AutoMirrored.Filled.QueueMusic, tr("Up Next"), Modifier.size(21.dp), tint = Ink)
                    }
                    PanelTabButton(open && tab == PanelTab.INFO, { onTab(PanelTab.INFO) }) {
                        Icon(Icons.Outlined.Info, tr("Episode info"), Modifier.size(21.dp), tint = Ink)
                    }
                    PanelTabButton(open && tab == PanelTab.BOOKMARKS, { onTab(PanelTab.BOOKMARKS) }) {
                        Icon(
                            if (bookmarkCount > 0) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                            tr("Bookmarks"),
                            Modifier.size(21.dp),
                            tint = Ink
                        )
                    }
                    PanelTabButton(open && tab == PanelTab.SPEED, { onTab(PanelTab.SPEED) }) {
                        Text(formatSpeed(speed), style = MaterialTheme.typography.titleSmall, color = Ink)
                    }
                    // Lit while a timer runs, not only while its tab is open —
                    // with the accent moon, the timer's only status in the player.
                    PanelTabButton((open && tab == PanelTab.TIMER) || timerArmed, { onTab(PanelTab.TIMER) }) {
                        Icon(
                            Icons.Filled.Bedtime,
                            tr("Sleep timer"),
                            Modifier.size(20.dp),
                            tint = if (timerArmed && glass == null) accent else Ink
                        )
                    }
                    }
                }
            }
        }

        // ---- body ----
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { alpha = ((expand - 0.2f) / 0.5f).coerceIn(0f, 1f) }
        ) {
            PanelColors(accent) { body(tab) }
        }
    }
}

@Composable
private fun PanelTabButton(
    active: Boolean,
    onClick: () -> Unit,
    glass: com.glasscast.app.ui.liquid.LiquidBackdrop? = null,
    content: @Composable () -> Unit
) {
    // Frosted Glass: a drop of clear glass under the open tab, which also
    // comes up under a finger and swells a little while it's held, as the
    // switches' knobs do.
    if (glass != null) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val lit by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (active || pressed) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(if (pressed) 110 else 260),
            label = "panelDrop"
        )
        val swell by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (pressed) 1.1f else 1f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = 700f),
            label = "panelDropSwell"
        )
        Box(
            Modifier
                .size(width = 46.dp, height = 40.dp)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (lit > 0.01f) {
                Box(
                    Modifier
                        .size(width = 44.dp, height = 36.dp)
                        .graphicsLayer {
                            alpha = lit
                            val k = (0.84f + 0.16f * lit) * swell
                            scaleX = k
                            scaleY = k
                        }
                        .liquidGlass(glass, com.glasscast.app.ui.liquid.LiquidMaterial.disc(), corner = 18.dp, dark = true)
                )
            }
            content()
        }
        return
    }
    // Sea Glass: no rectangle of ripple on a pebble. The button gives a
    // little under the finger, and the open tab is a small, clearer pebble
    // set into the capsule.
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    val tint = com.glasscast.app.ui.seaglass.LocalSeaTint.current
    if (tokens != null && tint != null) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val squash by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (pressed) 0.86f else 1f,
            animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 700f),
            label = "panelTabPress"
        )
        val lit = remember(tint, tokens.dark) {
            com.glasscast.app.ui.seaglass.Pigment(
                body = androidx.compose.ui.graphics.lerp(tint.edge, Color.White, if (tokens.dark) 0.1f else 0.45f),
                edge = Color.White,
                ink = tint.ink
            )
        }
        Box(
            Modifier
                .size(width = 46.dp, height = 40.dp)
                .graphicsLayer {
                    scaleX = squash
                    scaleY = squash
                }
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (active) {
                Box(
                    Modifier
                        .size(width = 42.dp, height = 34.dp)
                        .seaGlass(
                            pigment = lit,
                            recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Pebble,
                            seed = 23,
                            dark = tokens.dark,
                            ground = false
                        )
                )
            }
            content()
        }
        return
    }
    Box(
        Modifier
            .size(width = 46.dp, height = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Ink.copy(alpha = 0.18f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * A complete dark scheme, not a patched copy of the app's. Patching left every
 * field I didn't name at its light-theme value — which is what broke light mode.
 */
@Composable
private fun PanelColors(accent: Color, content: @Composable () -> Unit) {
    val ink = Ink
    // Ink on paper in Sea Glass: the same roles, on a light scheme.
    val dark = ink.luminance() > 0.5f
    MaterialTheme(
        colorScheme = if (!dark) androidx.compose.material3.lightColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White,
            background = Color.Transparent,
            onBackground = ink,
            surface = Color.Transparent,
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.62f),
            surfaceVariant = ink.copy(alpha = 0.06f),
            surfaceContainer = ink.copy(alpha = 0.05f),
            surfaceContainerHigh = ink.copy(alpha = 0.08f),
            surfaceContainerHighest = ink.copy(alpha = 0.11f),
            outline = ink.copy(alpha = 0.16f),
            outlineVariant = ink.copy(alpha = 0.10f)
        ) else darkColorScheme(
            primary = accent,
            onPrimary = if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White,
            background = Color.Transparent,
            onBackground = ink,
            surface = Color.Transparent,
            onSurface = ink,
            onSurfaceVariant = ink.copy(alpha = 0.62f),
            surfaceVariant = ink.copy(alpha = 0.10f),
            surfaceContainer = ink.copy(alpha = 0.08f),
            surfaceContainerHigh = ink.copy(alpha = 0.12f),
            surfaceContainerHighest = ink.copy(alpha = 0.16f),
            outline = ink.copy(alpha = 0.20f),
            outlineVariant = ink.copy(alpha = 0.12f)
        ),
        typography = MaterialTheme.typography,
        content = content
    )
}

// ------------------------------------------------------------------ queue

/**
 * Up Next as Cider lays it out: cover, title, show, duration.
 *
 * Built here rather than borrowed from the old sheet, whose rows had the same
 * flaw the episode rows once did — a "Remove" reveal drawn permanently behind
 * a transparent row. Here each row is opaque in the panel's own color and the
 * reveal draws only mid-swipe. Unlike queueing, removing *is* a dismissal, so
 * the swipe is allowed to complete and the row leaves the list.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PanelQueue(
    upNext: List<Episode>,
    feedFor: (Episode) -> Feed?,
    surface: Color,
    onPlayAt: (Int) -> Unit,
    onRemove: (Episode) -> Unit,
    onClear: () -> Unit,
    bottomInset: Dp
) {
    if (upNext.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = InkDim,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(tr("Nothing queued"), style = MaterialTheme.typography.titleMedium, color = Ink)
            Spacer(Modifier.height(4.dp))
            Text(
                text = tr("Swipe any episode left to add it here."),
                style = MaterialTheme.typography.bodyMedium,
                color = InkDim
            )
            Spacer(Modifier.height(bottomInset + 60.dp))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .panelQuiet(0.10f)
                    .clickable(onClick = onClear)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(tr("Clear"), style = MaterialTheme.typography.titleSmall, color = Ink)
            }
        }
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(upNext, key = { _, ep -> ep.guid }) { index, episode ->
                val latestRemove by rememberUpdatedState { onRemove(episode) }
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            latestRemove()
                            true
                        } else {
                            false
                        }
                    },
                    positionalThreshold = { it * 0.35f }
                )
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFB3261E).copy(alpha = 0.55f))
                                    .padding(end = 28.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Filled.DeleteOutline, tr("Remove"), tint = Ink)
                            }
                        }
                    }
                ) {
                    val feed = feedFor(episode)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .clickable { onPlayAt(index) }
                            .padding(horizontal = 22.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Artwork(
                            url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() },
                            sizeDp = 54.dp,
                            corner = 12.dp
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = episode.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = feed?.title.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = InkDim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = formatCompact(episode.durationMs),
                            style = MaterialTheme.typography.bodySmall,
                            color = InkDim
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ bookmarks

/**
 * Bookmarks in this episode, as an audiobook player keeps them: a place to
 * come back to, with a memo if you want one. Bookmark adds one where you are
 * and opens its memo; tap a row to go there; swipe it left to delete it.
 */
@Composable
fun PanelBookmarks(
    bookmarks: List<com.glasscast.app.data.Bookmark>,
    positionMs: Long,
    surface: Color,
    accent: Color,
    onAdd: () -> Long,
    onSeekTo: (Long) -> Unit,
    onMemo: (Long, String) -> Unit,
    onRemove: (Long) -> Unit,
    bottomInset: Dp
) {
    val haptics = rememberHaptics()
    // The bookmark whose memo is open for typing.
    var editing by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
            Row(
                Modifier
                    .then(
                        if (tokens != null) {
                            Modifier.seaGlass(
                                pigment = com.glasscast.app.ui.seaglass.rememberPigment(accent, tokens.dark),
                                recipe = com.glasscast.app.ui.seaglass.GlassRecipe.Quiet,
                                seed = 44,
                                dark = tokens.dark
                            )
                        } else {
                            Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(accent.copy(alpha = 0.22f))
                        }
                    )
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = if (tokens != null) null else androidx.compose.foundation.LocalIndication.current
                    ) {
                        haptics.play(Haptic.Select)
                        editing = onAdd()
                    }
                    .padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.BookmarkAdd, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = tr("Bookmark {0}", formatTime(positionMs)),
                    style = MaterialTheme.typography.titleSmall,
                    color = Ink
                )
            }
        }

        if (bookmarks.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    tint = InkDim,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(tr("No bookmarks"), style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(bottomInset + 60.dp))
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomInset + 16.dp),
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
        ) {
            items(bookmarks, key = { it.id }) { bookmark ->
                val latestRemove by rememberUpdatedState { onRemove(bookmark.id) }
                val state = rememberSwipeToDismissBoxState(
                    confirmValueChange = { value ->
                        if (value == SwipeToDismissBoxValue.EndToStart) {
                            latestRemove()
                            true
                        } else {
                            false
                        }
                    },
                    positionalThreshold = { it * 0.35f }
                )
                SwipeToDismissBox(
                    state = state,
                    enableDismissFromStartToEnd = false,
                    modifier = Modifier.animateItem(),
                    backgroundContent = {
                        if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFB3261E).copy(alpha = 0.55f))
                                    .padding(end = 28.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Filled.DeleteOutline, tr("Remove"), tint = Ink)
                            }
                        }
                    }
                ) {
                    BookmarkRow(
                        bookmark = bookmark,
                        surface = surface,
                        accent = accent,
                        editing = editing == bookmark.id,
                        onClick = {
                            haptics.play(Haptic.Tap)
                            onSeekTo(bookmark.positionMs)
                        },
                        onEdit = { editing = bookmark.id },
                        onDone = { memo ->
                            onMemo(bookmark.id, memo)
                            editing = null
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: com.glasscast.app.data.Bookmark,
    surface: Color,
    accent: Color,
    editing: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDone: (String) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(surface)
            .clickable(enabled = !editing, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.18f))
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = formatTime(bookmark.positionMs),
                style = MaterialTheme.typography.titleSmall,
                color = accent
            )
        }
        Spacer(Modifier.width(14.dp))
        if (editing) {
            var text by remember(bookmark.id) {
                mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(bookmark.memo))
            }
            val focus = remember { androidx.compose.ui.focus.FocusRequester() }
            androidx.compose.runtime.LaunchedEffect(Unit) { focus.requestFocus() }
            Box(Modifier.weight(1f)) {
                if (text.text.isEmpty()) {
                    Text(tr("Memo"), style = MaterialTheme.typography.bodyMedium, color = InkDim)
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Ink),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(accent),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone(text.text) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable { onDone(text.text) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = tr("Done"),
                    tint = if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            Column(Modifier.weight(1f)) {
                if (bookmark.memo.isNotBlank()) {
                    Text(
                        text = bookmark.memo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Ink,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = formatDate(bookmark.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = InkDim
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = tr("Memo"), tint = InkDim, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ------------------------------------------------------------------ speed

private val SpeedPresets = listOf(0.8f, 1f, 1.2f, 1.5f, 1.8f, 2f)

/**
 * Speed as a panel rather than a sheet: the number large, a slider for fine
 * control, and the common speeds as one-tap chips. The chips are what people
 * actually use; the slider is for the 1.35× crowd.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpeedPanel(
    speed: Float,
    accent: Color,
    onSpeedChange: (Float) -> Unit,
    bottomInset: Dp,
    skipSilence: Boolean = false,
    voiceBoost: Boolean = false,
    onSkipSilence: (Boolean) -> Unit = {},
    onVoiceBoost: (Boolean) -> Unit = {},
    skipAds: Boolean = false,
    onSkipAds: (Boolean) -> Unit = {}
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(top = 12.dp, bottom = bottomInset + 16.dp)
    ) {
        Text(
            text = formatSpeed(speed),
            style = MaterialTheme.typography.displaySmall,
            color = Ink
        )
        Spacer(Modifier.height(10.dp))
        Slider(
            value = speed,
            onValueChange = { onSpeedChange(((it * 20).roundToInt() / 20f).coerceIn(0.5f, 3f)) },
            valueRange = 0.5f..3f,
            steps = 49,
            colors = SliderDefaults.colors(
                thumbColor = Ink,
                activeTrackColor = accent,
                inactiveTrackColor = Ink.copy(alpha = 0.18f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(14.dp))
        // One row, six equal chips — a lone 2× wrapping onto a second line
        // looked like an afterthought. Equal widths fill the row exactly.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
            SpeedPresets.forEach { preset ->
                val active = kotlin.math.abs(preset - speed) < 0.001f
                if (tokens != null) {
                    // Sea Glass: the chosen speed is a pebble of the cover's
                    // glass; the others quiet grooves.
                    val glass = com.glasscast.app.ui.seaglass.rememberPigment(accent, tokens.dark)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .then(
                                if (active) {
                                    Modifier.seaGlass(glass, com.glasscast.app.ui.seaglass.GlassRecipe.Pebble, seed = 40 + (preset * 10).roundToInt(), dark = tokens.dark)
                                } else {
                                    Modifier
                                        .clip(RoundedCornerShape(percent = 50))
                                        .background(com.glasscast.app.ui.seaglass.quietFill(tokens))
                                        .border(0.75.dp, tokens.hairline, RoundedCornerShape(percent = 50))
                                }
                            )
                            .clickable(
                                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                indication = null
                            ) { onSpeedChange(preset) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = formatSpeed(preset),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (active) glass.ink else Ink,
                            maxLines = 1
                        )
                    }
                    return@forEach
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(if (active) accent else Ink.copy(alpha = 0.10f))
                        .clickable { onSpeedChange(preset) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatSpeed(preset),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (active && accent.luminance() > 0.62f) Color(0xFF101014) else Ink,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            text = tr("SOUND"),
            style = MaterialTheme.typography.labelMedium,
            color = InkDim
        )
        Spacer(Modifier.height(4.dp))
        PanelSwitchRow(
            title = tr("Skip silence"),
            checked = skipSilence,
            accent = accent,
            onChange = onSkipSilence
        )
        PanelSwitchRow(
            title = tr("Boost voices"),
            checked = voiceBoost,
            accent = accent,
            onChange = onVoiceBoost
        )
    }
}

@Composable
private fun PanelSwitchRow(
    title: String,
    detail: String? = null,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = InkDim)
        }
        Spacer(Modifier.width(12.dp))
        AppSwitch(
            checked = checked,
            onCheckedChange = onChange,
            accent = accent,
            colors = SwitchDefaults.colors(
                checkedTrackColor = accent,
                checkedThumbColor = Color(0xFF16141A),
                uncheckedTrackColor = Ink.copy(alpha = 0.14f),
                uncheckedThumbColor = InkDim,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ------------------------------------------------------------------ timer

private val TimerPresets = listOf(5, 10, 15, 30, 45, 60, 90)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TimerPanel(
    armed: Boolean,
    remainingLabel: String,
    accent: Color,
    shakeToRestart: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    bottomInset: Dp
) {
    // Scrolls, for a short open panel (a phone on its side): the chips and
    // the switch were cut off below the panel's edge with no way to reach them.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 26.dp)
            .padding(top = 12.dp, bottom = bottomInset + 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (armed) remainingLabel else tr("Off"),
                style = MaterialTheme.typography.displaySmall,
                color = if (armed) accent else Ink
            )
            Spacer(Modifier.weight(1f))
            if (armed) {
                Box(
                    Modifier
                        .panelQuiet(0.12f)
                        .clickable { SleepTimer.cancel() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(tr("Cancel"), style = MaterialTheme.typography.titleSmall, color = Ink)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TimerPresets.forEach { minutes ->
                PanelChip(label = tr("{0} min", minutes), active = false, accent = accent) {
                    SleepTimer.armMinutes(minutes)
                }
            }
            PanelChip(label = tr("End of episode"), active = false, accent = accent) {
                SleepTimer.armEndOfEpisode()
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable { onShakeToggle(!shakeToRestart) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(tr("Shake to restart"), style = MaterialTheme.typography.titleSmall, color = Ink)
            }
            Spacer(Modifier.width(12.dp))
            AppSwitch(
                checked = shakeToRestart,
                onCheckedChange = onShakeToggle,
                accent = accent,
                colors = SwitchDefaults.colors(
                    checkedTrackColor = accent,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Ink.copy(alpha = 0.14f),
                    uncheckedThumbColor = InkDim,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun PanelChip(label: String, active: Boolean, accent: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .then(if (active) Modifier.clip(RoundedCornerShape(percent = 50)).background(accent) else Modifier.panelQuiet(0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (active) (if (accent.luminance() > 0.62f) Color(0xFF101014) else Color.White) else Ink
        )
    }
}

fun formatSpeed(speed: Float): String {
    val rounded = (speed * 100).roundToInt() / 100f
    val text = if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString().trimEnd('0')
    return "${text}×"
}

/**
 * The system's own output picker — speakers, Bluetooth, and Cast devices for
 * apps that register them.
 */
fun openOutputSwitcher(context: Context) {
    runCatching {
        androidx.mediarouter.app.SystemOutputSwitcherDialogController.showDialog(context)
    }
}

/**
 * A quiet capsule in the panel: a faint fill of the panel's ink, or in Sea
 * Glass a groove in the paper with a hairline, as on every other page.
 */
@Composable
private fun Modifier.panelQuiet(alpha: Float): Modifier {
    val tokens = com.glasscast.app.ui.seaglass.LocalSea.current
    val shape = RoundedCornerShape(percent = 50)
    return if (tokens != null) {
        this
            .clip(shape)
            .background(com.glasscast.app.ui.seaglass.quietFill(tokens))
            .border(0.75.dp, tokens.hairline, shape)
    } else {
        this
            .clip(shape)
            .background(Ink.copy(alpha = alpha))
    }
}
