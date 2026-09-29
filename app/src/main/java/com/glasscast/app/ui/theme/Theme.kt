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

/** True when the *in-app* theme setting resolves to dark, not the system's. */
val LocalIsDark = compositionLocalOf { true }

/** The brand accent for the active mode. Artwork-derived accents override this locally. */
val LocalBrandAccent = compositionLocalOf { AccentPurple }

@Composable
fun GlassCastTheme(
    themeMode: com.glasscast.app.data.ThemeMode,
    /** Supplied when dynamic colour is on and something is playing. */
    showAccent: Color? = null,
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
    // Dynamic colour is decided by the caller: an accent arrives only when it's
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Bars follow the in-app theme, not the system's.
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalIsDark provides dark,
        LocalBrandAccent provides (tinted ?: if (dark) AccentPurple else AccentPurpleLight)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GlassType,
            content = content
        )
    }
}


private fun Color.luminanceOf(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
