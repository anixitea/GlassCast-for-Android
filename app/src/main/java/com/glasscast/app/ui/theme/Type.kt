package com.glasscast.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.glasscast.app.R

/**
 * Figtree, bundled.
 *
 * Google Sans is what this is reaching for and it can't be shipped — it's
 * proprietary to Google, not on Google Fonts, and not licensed for third-party
 * apps. Figtree is the closest open face: the same geometric-humanist
 * construction, single-storey g, tall x-height, open apertures. It reads as
 * that family without being it, and the OFL lets it ship.
 *
 * Bundled rather than fetched through downloadable fonts, which would mean a
 * Play Services dependency, a certificate array, and a frame of fallback text
 * on cold start.
 *
 * Sizes stay iOS-leaning rather than Material's defaults. Letter-spacing is
 * expressed relative to size so it scales with the user's font setting.
 */
private val Sans = FontFamily(
    Font(R.font.figtree_regular, FontWeight.Normal),
    Font(R.font.figtree_medium, FontWeight.Medium),
    Font(R.font.figtree_semibold, FontWeight.SemiBold),
    Font(R.font.figtree_bold, FontWeight.Bold)
)

private val TunedType = Typography(
    displaySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Bold,
        fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.8 / 34).em
    ),
    headlineMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 29.sp, letterSpacing = (-0.4 / 24).em
    ),
    titleMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.2 / 17).em
    ),
    titleSmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2 / 15).em
    ),
    bodyMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 21.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Sans, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = (0.6 / 12).em
    )
)

/**
 * The app's type: every Material style in Figtree.
 *
 * Only the styles above are tuned; the rest keep Material's sizes. They used
 * to keep Material's font too — the system sans — so anything set in, say,
 * titleLarge or bodyLarge (the show info sheet) came out in the wrong face.
 */
val GlassType: Typography = TunedType.allFigtree()

/** This typography with Figtree on all fifteen styles. The TV's type goes through it too. */
fun Typography.allFigtree(): Typography = Typography(
    displayLarge = displayLarge.copy(fontFamily = Sans),
    displayMedium = displayMedium.copy(fontFamily = Sans),
    displaySmall = displaySmall.copy(fontFamily = Sans),
    headlineLarge = headlineLarge.copy(fontFamily = Sans),
    headlineMedium = headlineMedium.copy(fontFamily = Sans),
    headlineSmall = headlineSmall.copy(fontFamily = Sans),
    titleLarge = titleLarge.copy(fontFamily = Sans),
    titleMedium = titleMedium.copy(fontFamily = Sans),
    titleSmall = titleSmall.copy(fontFamily = Sans),
    bodyLarge = bodyLarge.copy(fontFamily = Sans),
    bodyMedium = bodyMedium.copy(fontFamily = Sans),
    bodySmall = bodySmall.copy(fontFamily = Sans),
    labelLarge = labelLarge.copy(fontFamily = Sans),
    labelMedium = labelMedium.copy(fontFamily = Sans),
    labelSmall = labelSmall.copy(fontFamily = Sans)
)
