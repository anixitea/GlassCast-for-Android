package com.glasscast.app.tv

import com.glasscast.app.ui.tr
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glasscast.app.data.Episode
import com.glasscast.app.data.Feed
import com.glasscast.app.ui.Artwork
import com.glasscast.app.ui.ArtworkColors
import com.glasscast.app.ui.chromeButton
import com.glasscast.app.ui.chromeSurface
import com.glasscast.app.ui.formatCompact
import com.glasscast.app.ui.formatDate
import com.glasscast.app.ui.requestWhenReady
import com.glasscast.app.ui.stripHtml
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Block
import com.glasscast.app.data.DirectoryResult
import com.glasscast.app.ui.rememberArtworkColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import com.glasscast.app.ui.requestSafely

// ------------------------------------------------------------------ toast

/**
 * A confirmation that something happened — "Added to Up Next" — as a pill
 * with the cover, low on the screen, gone after two seconds. Nothing on the TV
 * said an episode had been queued; this is how it says so now.
 */
@Stable
class TvToastState {
    internal var current by mutableStateOf<Pair<Long, Pair<String, String>>?>(null)
    fun show(text: String, artUrl: String = "") {
        current = System.nanoTime() to (text to artUrl)
    }
}

@Composable
fun TvToastHost(state: TvToastState, accent: Color, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf("" to "") }
    val current = state.current
    LaunchedEffect(current?.first) {
        val c = current ?: return@LaunchedEffect
        shown = c.second
        visible = true
        delay(2_200)
        visible = false
    }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut()
        ) {
            Row(
                Modifier
                    .padding(bottom = TvSpacing.overscanV + 12.dp)
                    .clip(RoundedCornerShape(30.dp))
                    .background(Color(0xF01C1A22))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(30.dp))
                    .padding(start = 10.dp, end = 26.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (shown.second.isNotBlank()) {
                    Artwork(url = shown.second, sizeDp = 40.dp, corner = 10.dp)
                    Spacer(Modifier.width(14.dp))
                }
                Text(shown.first, style = MaterialTheme.typography.titleMedium, color = Color.White)
            }
        }
    }
}

// ------------------------------------------------------------ episode menu

/**
 * What holding select on an episode opens: its cover, title and notes (the
 * "info"), and the actions — play now, play next, add to Up Next, mark
 * played. Back closes it.
 */
@Composable
fun TvEpisodeMenu(
    episode: Episode,
    feed: Feed?,
    colors: ArtworkColors,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onTogglePlayed: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    BackHandler { onDismiss() }
    val accent = colors.chromeButton
    val played = episode.effectivelyPlayed
    val notes = remember(episode.description) { stripHtml(episode.description) }
    val detail = remember(episode.guid) {
        listOfNotNull(
            feed?.title,
            episode.pubDate.takeIf { it > 0 }?.let { formatDate(it) },
            episode.durationMs.takeIf { it > 0 }?.let { formatCompact(it) }
        ).joinToString(" · ")
    }

    Box(
        Modifier
            .fillMaxSize()
            // Opened by holding select: ignore that press's repeats and release.
            .ignoreHeldSelect()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .width(900.dp)
                .tvPanel(RoundedCornerShape(34.dp), container = colors.chromeSurface.copy(alpha = 0.98f))
                .padding(32.dp)
        ) {
            Artwork(url = episode.imageUrl.ifBlank { feed?.imageUrl.orEmpty() }, sizeDp = 200.dp, corner = 22.dp)
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(episode.title, style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(detail, style = MaterialTheme.typography.labelLarge, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvMenuItem(Icons.Filled.PlayArrow, tr("Play now"), accent, Modifier.focusRequester(first)) { onPlay(); onDismiss() }
                    TvMenuItem(Icons.Filled.QueuePlayNext, tr("Play next"), accent) { onPlayNext(); onDismiss() }
                    TvMenuItem(Icons.AutoMirrored.Filled.PlaylistAdd, tr("Add to Up Next"), accent) { onAddToQueue(); onDismiss() }
                    TvMenuItem(
                        if (played) Icons.Filled.RemoveDone else Icons.Filled.CheckCircle,
                        if (played) tr("Mark as unplayed") else tr("Mark as played"),
                        accent
                    ) { onTogglePlayed(); onDismiss() }
                }
            }
        }
    }
}

@Composable
private fun TvMenuItem(icon: ImageVector, label: String, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .tvFocusableRow(accent = accent, surface = Color.White, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

// -------------------------------------------------------------- show menu

/**
 * What holding select on a Discover card opens: the show, and the two things
 * you'd want from here without opening it — Follow, or Not interested.
 *
 * In the show's own hue rather than the now-playing one, since it's about
 * this show. It opens while select is still held, so it ignores that press
 * (see [ignoreHeldSelect]); Back closes it.
 */
@Composable
fun TvShowMenu(
    result: DirectoryResult,
    onDismiss: () -> Unit,
    onFollow: () -> Unit,
    onNotInterested: () -> Unit
) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { first.requestWhenReady() }
    BackHandler { onDismiss() }
    val (showColors, _) = rememberArtworkColors(result.artworkUrl)
    val container by animateColorAsState(showColors.chromeSurface, tween(300), label = "showMenuSurface")
    val accent by animateColorAsState(showColors.chromeButton, tween(300), label = "showMenuAccent")

    Box(
        Modifier
            .fillMaxSize()
            .ignoreHeldSelect()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            Modifier
                .width(780.dp)
                .tvPanel(RoundedCornerShape(34.dp), container = container.copy(alpha = 0.98f))
                .padding(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(url = result.artworkUrl, sizeDp = 200.dp, corner = 22.dp)
            Spacer(Modifier.width(32.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    result.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (result.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        result.author,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (result.episodeCount > 0) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tr("{0} EPISODES", result.episodeCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Spacer(Modifier.height(22.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    TvMenuItem(Icons.Filled.Add, tr("Follow"), accent, Modifier.focusRequester(first)) { onFollow() }
                    TvMenuItem(Icons.Outlined.Block, tr("Not interested"), accent) { onNotInterested() }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- confirm

/** The TV's confirmation, worded as the phone's. Focus starts on Cancel. */
@Composable
fun TvConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    colors: ArtworkColors,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { cancel.requestWhenReady() }
    BackHandler { onDismiss() }
    val accent = colors.chromeButton
    Box(
        Modifier
            .fillMaxSize()
            // Opened by holding select: ignore that press's repeats and
            // release, or letting go would choose the first option.
            .ignoreHeldSelect()
            .background(Color.Black.copy(alpha = 0.62f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .width(620.dp)
                .tvPanel(RoundedCornerShape(30.dp), container = colors.chromeSurface.copy(alpha = 0.98f))
                .padding(32.dp)
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvDialogButton(tr("Cancel"), accent, filled = false, modifier = Modifier.focusRequester(cancel), onClick = onDismiss)
                TvDialogButton(confirmLabel, Color(0xFFFF6B6B), filled = true) { onConfirm(); onDismiss() }
            }
        }
    }
}

@Composable
private fun TvDialogButton(label: String, accent: Color, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(54.dp)
            .tvFocusable(shape = RoundedCornerShape(27.dp), accent = Color.White, onClick = onClick)
            .background(if (filled) accent else Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (filled && accent.luminance() > 0.5f) Color(0xFF15121A) else Color.White
        )
    }
}

// ------------------------------------------------------------- text field

/**
 * A text field for the remote. Moving through it does nothing; select opens
 * the keyboard.
 *
 * The field used to be the focus stop itself, and a text field that gains
 * focus raises the keyboard — so scrolling down the gPodder settings popped
 * the keyboard at every field on the way past. Now the stop is the box around
 * it, and the text field can't take focus until the box is selected; then it
 * takes focus and the keyboard opens. Done on the keyboard, or moving off
 * the field, hands focus back and puts it out of reach again.
 */
@Composable
fun TvTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text
) {
    var editing by remember { mutableStateOf(false) }
    val box = remember { FocusRequester() }
    val field = remember { FocusRequester() }
    val boxInteraction = remember { MutableInteractionSource() }
    val boxFocused by boxInteraction.collectIsFocusedAsState()
    var fieldFocused by remember { mutableStateOf(false) }
    val lit = boxFocused || fieldFocused
    val shape = RoundedCornerShape(16.dp)
    LaunchedEffect(editing) { if (editing) field.requestWhenReady() }

    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.65f))
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .focusRequester(box)
                .clip(shape)
                .background(Color.White.copy(alpha = if (lit) 0.16f else 0.09f))
                // Conditional, not border(0.dp): 0dp is Dp.Hairline — a
                // one-pixel line in the accent on every unfocused field.
                .then(if (lit) Modifier.border(2.dp, accent, shape) else Modifier)
                .focusable(interactionSource = boxInteraction)
                .clickable(interactionSource = boxInteraction, indication = null) { editing = true }
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.White),
                cursorBrush = SolidColor(accent),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    // Focus back to the box first: switching the field off
                    // while it still held focus would leave focus nowhere.
                    box.requestSafely()
                    editing = false
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(field)
                    .focusProperties { canFocus = editing }
                    .onFocusChanged { state ->
                        if (fieldFocused && !state.isFocused) editing = false
                        fieldFocused = state.isFocused
                    }
            )
        }
    }
}
