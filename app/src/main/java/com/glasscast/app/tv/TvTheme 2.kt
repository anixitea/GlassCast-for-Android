package com.glasscast.app.tv

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glasscast.app.R

/**
 * Ten-foot typography and spacing.
 *
 * Nothing here is the phone scale nudged up. A TV is read from three metres on
 * a panel four times the size, which inverts the usual relationship: the type
 * is far larger in absolute terms but *smaller* relative to the screen, so the
 * layout gets sparser rather than denser. Body copy below about 18sp is
 * genuinely unreadable from a sofa, and that floor is what drives every other
 * number on this page.
 */
private val Sans = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

val TvType = Typography(
    displayLarge = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = (-1.2 / 54).em
    ),
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 38.sp, lineHeight = 44.sp, letterSpacing = (-0.8 / 38).em
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.4 / 28).em
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp, lineHeight = 28.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 19.sp, lineHeight = 25.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 18.sp, lineHeight = 26.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 22.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (1.0 / 15).em
    )
)

/**
 * Overscan margin.
 *
 * Television panels still crop the edges of the picture — 5% horizontally and
 * vertically is the long-standing safe area, and on a 1080p surface that is
 * 48dp by 27dp. Anything outside it may simply not exist on someone's set.
 * Content scrolls under this margin; it never starts inside it.
 */
object TvSpacing {
    val overscanH = 48.dp
    val overscanV = 27.dp

    /** The nav rail's resting and expanded widths. */
    val railCollapsed = 92.dp
    val railExpanded = 248.dp

    /** Gap between focusable tiles — wider than touch, because focus needs room to read. */
    val tileGap = 24.dp
}

/** True while the D-pad, not a finger, is driving. Always true on TV. */
val LocalIsTv = staticCompositionLocalOf { false }

@Composable
fun ProvideTv(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsTv provides true, content = content)
}
