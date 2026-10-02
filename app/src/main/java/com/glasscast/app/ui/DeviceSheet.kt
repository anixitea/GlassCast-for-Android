package com.glasscast.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent

private val Ink = Color.White
private val InkDim = Color.White.copy(alpha = 0.62f)

/**
 * Where it plays: this phone, a Cast device, or another output.
 *
 * This is GlassCast's own list rather than the system's output picker, which
 * on HyperOS offered only "This phone" and Bluetooth. Owning the list is also
 * the groundwork for the Mac app: once it speaks the handoff protocol it
 * becomes one more row here, beside the TV.
 *
 * Discovery runs only while the sheet is open — scanning the network for Cast
 * devices costs battery, and a list nobody is looking at doesn't need to be
 * current.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    surface: Color,
    accent: Color,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val router = remember { runCatching { MediaRouter.getInstance(context) }.getOrNull() }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
            )
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
    }

    var routes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
    var selectedId by remember { mutableStateOf(router?.selectedRoute?.id) }
    var connectingId by remember { mutableStateOf<String?>(null) }

    DisposableEffect(router) {
        if (router == null) return@DisposableEffect onDispose { }
        fun refresh() {
            routes = router.routes.filter { !it.isDefault && it.isEnabled && it.matchesSelector(selector) }
            selectedId = router.selectedRoute.id
            if (connectingId != null && selectedId == connectingId) connectingId = null
        }
        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refresh()
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = refresh()
        }
        router.addCallback(selector, callback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refresh()
        onDispose { router.removeCallback(callback) }
    }

    val casting = routes.any { it.id == selectedId }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = surface,
        contentColor = Ink,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(Ink.copy(alpha = 0.34f))
            )
        }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = tr("Play on"),
                style = MaterialTheme.typography.headlineMedium,
                color = Ink,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
            )

            DeviceRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                name = tr("This phone"),
                detail = if (casting) "Tap to bring it back" else tr("Playing here"),
                selected = !casting,
                accent = accent
            ) {
                router?.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            }

            if (routes.isEmpty()) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = InkDim,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = tr("Looking for TVs and speakers on your Wi-Fi…"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkDim
                    )
                }
            }

            routes.forEach { route ->
                val isSelected = route.id == selectedId
                val isConnecting = route.id == connectingId
                DeviceRow(
                    icon = when {
                        isSelected -> Icons.Filled.CastConnected
                        route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_TV -> Icons.Filled.Tv
                        route.deviceType == MediaRouter.RouteInfo.DEVICE_TYPE_SPEAKER -> Icons.Filled.Speaker
                        else -> Icons.Filled.Cast
                    },
                    name = route.name,
                    detail = when {
                        isConnecting -> tr("Connecting…")
                        isSelected -> tr("Playing here")
                        else -> route.description?.takeIf { it.isNotBlank() } ?: "Google Cast"
                    },
                    selected = isSelected,
                    busy = isConnecting,
                    accent = accent
                ) {
                    if (!isSelected) {
                        connectingId = route.id
                        route.select()
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            DeviceRow(
                icon = Icons.Filled.Bluetooth,
                name = tr("Bluetooth and other outputs"),
                detail = tr("Headphones, car, speakers"),
                selected = false,
                accent = accent
            ) {
                openOutputSwitcher(context)
            }
        }
    }
}

@Composable
private fun DeviceRow(
    icon: ImageVector,
    name: String,
    detail: String,
    selected: Boolean,
    accent: Color,
    busy: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Ink.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) accent else Ink.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected && accent.luminance() > 0.62f) Color(0xFF16141A) else Ink,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = InkDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            busy -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                color = Ink,
                modifier = Modifier.size(18.dp)
            )
            selected -> Icon(Icons.Filled.Check, contentDescription = null, tint = accent)
        }
    }
}
