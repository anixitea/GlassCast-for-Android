package com.glasscast.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Lesson 2: both schemes are specified in full. Leaving surfaceContainer /
 * onSurfaceVariant and friends at Material's baseline is exactly how bottom
 * sheets and dropdown menus end up unreadable in dark mode.
 */
private fun darkScheme(
    g0: Color, g1: Color, g2: Color, g3: Color, g4: Color, g5: Color,
    lowest: Color
) = darkColorScheme(
    primary = AccentPurple,
    onPrimary = Color(0xFF15040F),
    primaryContainer = AccentPurplePressed,
    onPrimaryContainer = DarkTextPrimary,
    inversePrimary = AccentPurpleLight,

    secondary = DarkTextSecondary,
    onSecondary = g0,
    secondaryContainer = g4,
    onSecondaryContainer = DarkTextPrimary,

    tertiary = AccentPurple,
    onTertiary = g0,
    tertiaryContainer = g4,
    onTertiaryContainer = DarkTextPrimary,

    background = g0,
    onBackground = DarkTextPrimary,

    surface = g0,
    onSurface = DarkTextPrimary,
    surfaceVariant = g3,
    onSurfaceVariant = DarkTextSecondary,
    surfaceTint = AccentPurple,

    surfaceContainerLowest = lowest,
    surfaceContainerLow = g1,
    surfaceContainer = g2,
    surfaceContainerHigh = g3,
    surfaceContainerHighest = g4,
    surfaceBright = g4,
    surfaceDim = g0,

    inverseSurface = LightGround0,
    inverseOnSurface = LightTextPrimary,

    error = ErrorRed,
    onError = Color(0xFF140202),
    errorContainer = Color(0xFF3B0F0C),
    onErrorContainer = Color(0xFFFFDAD5),

    outline = g5,
    outlineVariant = g4,
    scrim = Color(0xFF000000)
)

private val DarkScheme = darkScheme(
    DarkGround0, DarkGround1, DarkGround2, DarkGround3, DarkGround4, DarkGround5,
    lowest = Color(0xFF101013)
)

private val LightScheme = lightColorScheme(
    primary = AccentPurpleLight,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEEDFF8),
    onPrimaryContainer = Color(0xFF2C0A3C),
    inversePrimary = AccentPurple,

    secondary = LightTextSecondary,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightGround2,
    onSecondaryContainer = LightTextPrimary,

    tertiary = AccentPurpleLight,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = LightGround2,
    onTertiaryContainer = LightTextPrimary,

    background = LightGround0,
    onBackground = LightTextPrimary,

    surface = LightGround0,
    onSurface = LightTextPrimary,
    surfaceVariant = LightGround2,
    onSurfaceVariant = LightTextSecondary,
    surfaceTint = AccentPurpleLight,

    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = LightGround0,
    surfaceContainer = LightGround1,
    surfaceContainerHigh = LightGround2,
    surfaceContainerHighest = LightGround3,
    surfaceBright = Color(0xFFFFFFFF),
    surfaceDim = LightGround3,

    inverseSurface = DarkGround1,
    inverseOnSurface = DarkTextPrimary,

    error = Color(0xFFC0362C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),

    outline = LightGround3,
    outlineVariant = LightGround2,
    scrim = Color(0xFF000000)
)

/**
 * Sea Glass's schemes: paper and ink by day, tide and foam by night, every
 * role named (lesson 2 above). Containers here are paper laid on the ground,
 * so they're lighter than it in the day, where the Original look's are a
 * step darker. The accent is the brand glass's ink: deep on paper, pale at night.
 */
private fun seaScheme(t: com.glasscast.app.ui.seaglass.SeaTokens, accent: Color): androidx.compose.material3.ColorScheme =
    if (!t.dark) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = t.brand.body,
            onPrimaryContainer = t.brand.ink,
            inversePrimary = t.brand.edge,
            secondary = t.muted,
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0xFFE9ECE4),
            onSecondaryContainer = t.ink,
            tertiary = accent,
            onTertiary = Color(0xFFFFFFFF),
            tertiaryContainer = Color(0xFFE9ECE4),
            onTertiaryContainer = t.ink,
            background = t.ground,
            onBackground = t.ink,
            surface = t.ground,
            onSurface = t.ink,
            surfaceVariant = Color(0xFFE9ECE4),
            onSurfaceVariant = t.muted,
            surfaceTint = accent,
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF8F8F3),
            surfaceContainer = t.card,
            surfaceContainerHigh = Color(0xFFFDFCF8),
            surfaceContainerHighest = Color(0xFFE6EAE2),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceDim = Color(0xFFE6EAE2),
            inverseSurface = Color(0xFF16211F),
            inverseOnSurface = Color(0xFFE8EEEA),
            error = Color(0xFFB0412E),
            onError = Color(0xFFFFFFFF),
            errorContainer = Color(0xFFF8DDD5),
            onErrorContainer = Color(0xFF4A1206),
            outline = t.hairline,
            outlineVariant = Color(0xFFE7ECE4),
            scrim = Color(0xFF0E1716)
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF0E1716),
            primaryContainer = t.brand.body,
            onPrimaryContainer = t.brand.ink,
            inversePrimary = t.brand.body,
            secondary = t.muted,
            onSecondary = t.ground,
            secondaryContainer = Color(0xFF23302D),
            onSecondaryContainer = t.ink,
            tertiary = accent,
            onTertiary = t.ground,
            tertiaryContainer = Color(0xFF23302D),
            onTertiaryContainer = t.ink,
            background = t.ground,
            onBackground = t.ink,
            surface = t.ground,
            onSurface = t.ink,
            surfaceVariant = Color(0xFF23302D),
            onSurfaceVariant = t.muted,
            surfaceTint = accent,
            surfaceContainerLowest = Color(0xFF09100F),
            surfaceContainerLow = Color(0xFF121C1A),
            surfaceContainer = t.card,
            surfaceContainerHigh = t.raised,
            surfaceContainerHighest = Color(0xFF24312E),
            surfaceBright = Color(0xFF2A3734),
            surfaceDim = t.ground,
            inverseSurface = Color(0xFFF4F5F0),
            inverseOnSurface = Color(0xFF233C37),
            error = Color(0xFFF2836F),
            onError = Color(0xFF2A0A04),
            errorContainer = Color(0xFF4A1C14),
            onErrorContainer = Color(0xFFFFDAD2),
            outline = t.hairline,
            outlineVariant = Color(0xFF1F2A28),
            scrim = Color(0xFF000000)
        )
    }

/** True when the *in-app* theme setting resolves to dark, not the system's. */
val LocalIsDark = compositionLocalOf { true }

/** The brand accent for the active mode. Artwork-derived accents override this locally. */
val LocalBrandAccent = compositionLocalOf { AccentPurple }

@Composable
fun GlassCastTheme(
    themeMode: com.glasscast.app.data.ThemeMode,
    /** Supplied when dynamic color is on and something is playing. */
    showAccent: Color? = null,
    /** Original, Sea Glass or Glass (Settings > Appearance, on the phone and the TV). */
    look: com.glasscast.app.data.Look = com.glasscast.app.data.Look.ORIGINAL,
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        com.glasscast.app.data.ThemeMode.SYSTEM -> isSystemInDarkTheme()
        com.glasscast.app.data.ThemeMode.LIGHT -> false
        com.glasscast.app.data.ThemeMode.DARK -> true
    }

    val base = if (dark) DarkScheme else LightScheme

    /*
     * The player's accent is clamped for the player's ground, which is dark
     * whatever the theme. Reusing it here drops a 0.7-lightness yellow onto a
     * white library screen, where it is a highlighter — so it gets a second
     * clamp for the app's own ground before becoming the primary.
     */
    // Dynamic color is decided by the caller: an accent arrives only when it's
    // on and something is playing, in light or dark alike.
    val tinted = showAccent?.let { com.glasscast.app.ui.themeAccent(it, dark) }

    val scheme = if (tinted == null) {
        base
    } else {
        base.copy(
            primary = tinted,
            secondary = tinted,
            tertiary = tinted,
            surfaceTint = tinted,
            onPrimary = if (tinted.luminanceOf() > 0.62f) Color(0xFF101014) else Color.White
        )
    }

    // Sea Glass: its own grounds and type, and glass in the accent's color
    // (GlassCast's purple, the cover's, or the one picked).
    val seaGlass = look == com.glasscast.app.data.Look.SEA_GLASS
    val seaTokens = if (seaGlass) {
        androidx.compose.runtime.remember(dark, showAccent) {
            com.glasscast.app.ui.seaglass.seaTokens(dark, showAccent ?: if (dark) AccentPurple else AccentPurpleLight)
        }
    } else {
        null
    }
    // Liquid Glass: Apple's grounds and labels, Inter, and the accent as it is
    // (GlassCast's purple, the cover's, or the one picked).
    val liquidTokens = if (look == com.glasscast.app.data.Look.LIQUID_GLASS) {
        val accent = tinted ?: if (dark) AccentPurple else AccentPurpleLight
        androidx.compose.runtime.remember(dark, accent) { com.glasscast.app.ui.liquid.liquidTokens(dark, accent) }
    } else {
        null
    }
    val finalScheme = when {
        // The accent is the brand glass's ink, so a button in it sits with the glass.
        seaTokens != null -> seaScheme(seaTokens, accent = if (dark) seaTokens.brand.edge else seaTokens.brand.ink)
        liquidTokens != null -> androidx.compose.runtime.remember(liquidTokens) { com.glasscast.app.ui.liquid.liquidScheme(liquidTokens) }
        else -> scheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Bars follow the in-app theme, not the system's.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            // The window behind the app is the page's ground (lesson: window
            // colors equal grounds). The resources hold the Original look's,
            // for the launch screen; Sea Glass repaints it once it's running.
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(finalScheme.background.toArgb()))
        }
    }

    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalBrandAccent provides (if (seaTokens != null) finalScheme.primary else tinted ?: if (dark) AccentPurple else AccentPurpleLight),
        com.glasscast.app.ui.seaglass.LocalSea provides seaTokens,
        com.glasscast.app.ui.liquid.LocalLiquid provides liquidTokens
    ) {
        MaterialTheme(
            colorScheme = finalScheme,
            typography = when {
                seaTokens != null -> SeaType
                liquidTokens != null -> LiquidType
                else -> GlassType
            },
            content = content
        )
    }
}


private fun Color.luminanceOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
