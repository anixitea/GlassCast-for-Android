package com.glasscast.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * A small confirmation that floats up above the chrome and leaves on its own —
 * the "Added to Up Next" that a swipe should always answer with.
 *
 * Deliberately not a Snackbar. A Snackbar is a bar anchored to the screen edge
 * with room for an action; this is a pill that says one thing and goes, which
 * is what an action that already succeeded needs. It carries the cover so you
 * can see *which* episode went where without reading the title.
 */
data class ToastMessage(
    val text: String,
    val artUrl: String = "",
    val icon: ImageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
    val id: Long = System.nanoTime()
)

@Stable
class ToastState {
    var current by mutableStateOf<ToastMessage?>(null)
        private set

    fun show(text: String, artUrl: String = "", icon: ImageVector = Icons.AutoMirrored.Filled.PlaylistAdd) {
        // A new id each time, so two quick swipes re-trigger the timer and the
        // second confirmation isn't swallowed by the first one's exit.
        current = ToastMessage(text, artUrl, icon)
    }

    fun dismiss(id: Long) {
        if (current?.id == id) current = null
    }
}

val LocalToast = staticCompositionLocalOf { ToastState() }

@Composable
fun ToastHost(state: ToastState, modifier: Modifier = Modifier) {
    val message = state.current
    // Held separately so the pill keeps its content while it animates out.
    var shown by remember { mutableStateOf<ToastMessage?>(null) }
    if (message != null) shown = message

    LaunchedEffect(message?.id) {
        val id = message?.id ?: return@LaunchedEffect
        delay(2_200)
        state.dismiss(id)
    }

    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        // Drops in from above now that it lives at the top of the screen.
        enter = slideInVertically(spring(dampingRatio = 0.62f, stiffness = 420f)) { -it } +
            scaleIn(initialScale = 0.85f) + fadeIn(tween(140)),
        exit = slideOutVertically(tween(200)) { -it / 2 } + fadeOut(tween(180))
    ) {
        val m = shown ?: return@AnimatedVisibility
        Row(
            Modifier
                .widthIn(max = 340.dp)
                .shadow(14.dp, RoundedCornerShape(percent = 50), clip = false)
                .clip(RoundedCornerShape(percent = 50))
                .background(Color(0xF02A292F))
                .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (m.artUrl.isNotBlank()) {
                Artwork(url = m.artUrl, sizeDp = 34.dp, corner = 17.dp)
            } else {
                Icon(
                    m.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .size(22.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                m.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = m.text,
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
