package com.glasscast.app.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glasscast.app.ui.theme.LocalIsDark
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

/** Side inset for floating chrome, so the page shows past it on both edges. */
val GlassGutter = 14.dp

/** Corner radius shared by the mini player and the tab bar, so they stack as one object. */
val GlassCorner = 26.dp

/**
 * The hairline that makes glass read as glass. A light top edge is what a real
 * pane catches, and without it a blurred panel just looks like a smudge.
 */
@Composable
fun glassEdge(): Color =
    if (LocalIsDark.current) Color.White.copy(alpha = 0.12f)
    else Color.White.copy(alpha = 0.55f)

/**
 * Frosted panel. Falls back to a solid container when blur isn't available —
 * Haze needs RenderEffect (API 31+), and below that a translucent panel over
 * unblurred content is worse than an opaque one.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun Modifier.glassPanel(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    container: Color = MaterialTheme.colorScheme.surfaceContainer
): Modifier {
    val edge = glassEdge()
    return this
        .clip(shape)
        .hazeEffect(state = hazeState, style = HazeMaterials.regular(container))
        .border(0.5.dp, edge, shape)
}

/**
 * The blur ramp under the status bar.
 *
 * A uniform pane of glass across the top is a rectangle laid on the page, and
 * its bottom edge is a line — the same kind of line the artwork wash spends its
 * whole gradient avoiding. Fading the blur out instead gives the title and the
 * back arrow something to be legible against and leaves the artwork
 * uninterrupted.
 *
 * The ramp stops short of full blur at its peak on purpose: a blur has nothing
 * to sample past the top of its own layer, so pushed all the way it turns into
 * a band of flat material colour — exactly the artefact it was added to remove.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun TopGlassFade(
    hazeState: HazeState,
    pageColor: Color,
    modifier: Modifier = Modifier
) {
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier
            .fillMaxWidth()
            .height(statusInset + 52.dp + 96.dp)
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin(pageColor)) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0.72f,
                    endIntensity = 0f
                )
                // Uniform across the layer, so it would show as texture over the
                // untouched foot of the ramp — the edge being hidden.
                noiseFactor = 0f
            }
    )
}

/**
 * The blur ramp under the floating chrome — the bottom counterpart to
 * [TopGlassFade].
 *
 * The panels themselves are glass, but content was sliding under them against a
 * hard edge: sharp text right up to the panel's border, blurred text inside it.
 * A ramp behind them blurs the page on the way in, so the transition happens
 * over 100dp instead of at a line.
 */
@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun BottomGlassFade(
    hazeState: HazeState,
    pageColor: Color,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin(pageColor)) {
                progressive = HazeProgressive.verticalGradient(
                    startIntensity = 0f,
                    endIntensity = 0.72f
                )
                noiseFactor = 0f
            }
    )
}

data class GlassTab(val label: String, val icon: ImageVector)

/** Spacer under scrolling content so the last row clears the floating chrome. */
@Composable
fun glassBottomInset(miniPlayerVisible: Boolean): androidx.compose.ui.unit.Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navInset + 66.dp + if (miniPlayerVisible) 78.dp else 0.dp
}
