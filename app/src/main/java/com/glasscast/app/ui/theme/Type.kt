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

/*
 * Sea Glass's type: Newsreader for titles, Figtree for everything else.
 *
 * Newsreader is Production Type's editorial serif (OFL; see
 * NEWSREADER-OFL.txt), bundled as two fixed cuts of its variable font: a
 * display cut (optical size 72, weight 400) for page titles and the player's
 * title, and a text cut (optical size 16, weight 500) for names in lists.
 * It replaced Fraunces in 1.9's third round: Fraunces' soft, wonky shapes
 * read as odd on a phone. One weight each, so nothing may ask for bold: synthesis
 * is off, and a bold request draws the regular cut rather than a smeared one.
 * Hangul has no Newsreader glyphs and falls back to the system's, as it does
 * for Figtree.
 */
private val SeaDisplay = FontFamily(Font(R.font.newsreader_display, FontWeight.Normal))
private val SeaText = FontFamily(Font(R.font.newsreader_text, FontWeight.Normal))

/** Newsreader's text cut, for a name set in a list row (Sea Glass only). */
val SeaSerif: FontFamily get() = SeaText

private fun TextStyle.serif(family: FontFamily, tracking: Float = 0f) = copy(
    fontFamily = family,
    fontWeight = FontWeight.Normal,
    fontSynthesis = androidx.compose.ui.text.font.FontSynthesis.None,
    letterSpacing = tracking.em
)

/** GlassType with the titles in Newsreader. */
val SeaType: Typography = GlassType.let { t ->
    t.copy(
        displayLarge = t.displayLarge.serif(SeaDisplay, -0.02f),
        displayMedium = t.displayMedium.serif(SeaDisplay, -0.02f),
        displaySmall = t.displaySmall.copy(fontSize = 38.sp, lineHeight = 44.sp).serif(SeaDisplay, -0.015f),
        headlineLarge = t.headlineLarge.serif(SeaDisplay, -0.01f),
        headlineMedium = t.headlineMedium.copy(fontSize = 27.sp, lineHeight = 33.sp).serif(SeaDisplay, -0.01f),
        headlineSmall = t.headlineSmall.serif(SeaDisplay, -0.005f),
        titleLarge = t.titleLarge.serif(SeaText),
        titleMedium = t.titleMedium.copy(fontSize = 18.sp, lineHeight = 23.sp).serif(SeaText, -0.005f),
        // Labels set in capitals (the section names in Settings, a show's
        // episode count) are spaced out, as an eyebrow should be.
        labelMedium = t.labelMedium.copy(letterSpacing = 0.12.em, fontWeight = FontWeight.SemiBold)
    )
}

/*
 * Liquid Glass's type (2.0): Inter for everything, in two optical sizes.
 *
 * Apple's San Francisco is licensed for Apple platforms only, so it can't ship
 * in an Android app. Inter (OFL; see INTER-OFL.txt) is the nearest open face:
 * the same neutral grotesque shapes, tall lowercase and tight spacing at
 * display sizes. It comes at two optical sizes, the way SF switches from Text
 * to Display at 20 pt: opsz 16 for text and opsz 32 for titles.
 *
 * Static files, one per weight, cut from Inter 4.1's variable font and
 * subset to Latin (about 64 KB each). 2.0's first build shipped the variable
 * font and picked weights with variation settings, and on the Xiaomi every
 * weight came out Regular: Compose applies them through a shared Paint, and
 * that path isn't reliable everywhere. A static file per weight can't fail.
 *
 * Tracking follows Inter's own dynamic metrics (a + b·e^(c·size)), so large
 * titles close up and small labels open slightly, as SF's do.
 */
private val InterText = FontFamily(
    Font(R.font.inter_text_400, FontWeight.Normal),
    Font(R.font.inter_text_500, FontWeight.Medium),
    Font(R.font.inter_text_600, FontWeight.SemiBold),
    Font(R.font.inter_text_700, FontWeight.Bold)
)
private val InterDisplay = FontFamily(
    Font(R.font.inter_display_400, FontWeight.Normal),
    Font(R.font.inter_display_600, FontWeight.SemiBold),
    Font(R.font.inter_display_700, FontWeight.Bold)
)

/** Inter's dynamic tracking for a size in sp, in ems. */
private fun interTracking(size: Float): Float = (-0.0223f + 0.185f * kotlin.math.exp(-0.1745f * size))

private fun TextStyle.inter(display: Boolean): TextStyle {
    val size = if (fontSize.isSp) fontSize.value else 15f
    return copy(
        fontFamily = if (display) InterDisplay else InterText,
        letterSpacing = interTracking(size).em
    )
}

/** GlassType's sizes, in Inter. Titles a weight up, as Apple sets them. */
val LiquidType: Typography = GlassType.let { t ->
    Typography(
        displayLarge = t.displayLarge.inter(display = true),
        displayMedium = t.displayMedium.inter(display = true),
        displaySmall = t.displaySmall.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold).inter(display = true),
        headlineLarge = t.headlineLarge.inter(display = true),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold).inter(display = true),
        headlineSmall = t.headlineSmall.inter(display = true),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold).inter(display = true),
        titleMedium = t.titleMedium.inter(display = false),
        titleSmall = t.titleSmall.inter(display = false),
        bodyLarge = t.bodyLarge.inter(display = false),
        bodyMedium = t.bodyMedium.inter(display = false),
        bodySmall = t.bodySmall.inter(display = false),
        labelLarge = t.labelLarge.inter(display = false),
        // Labels in capitals (section names) sit at Apple's footnote: no
        // extra spacing, which is how iOS sets them.
        labelMedium = t.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal).inter(display = false),
        labelSmall = t.labelSmall.inter(display = false)
    )
}

/** Inter's text cut, for a label that names Liquid Glass outside that look (its tile in Settings). */
val LiquidSans: FontFamily get() = InterText
