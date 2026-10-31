package com.glasscast.app.ui.liquid

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/*
 * Liquid Glass, the third look (2.0): the app laid out and colored the way
 * Apple's own apps are, set in Inter, with the chrome that floats over the
 * pages (the tab bar, the search button, the mini player) made of refracting
 * glass (LiquidLens). Everything that changes for it asks LocalLiquid, which
 * is null in the other two looks.
 *
 * The colors are Apple's system ones: white pages and gray grouped cards by
 * day, black pages and charcoal cards at night, labels at full and secondary
 * strength, and the hairline separator.
 */
@Immutable
class LiquidTokens(
    val dark: Boolean,
    /** A plain page (the library, a show). */
    val ground: Color,
    /** A page of grouped cards (Settings), and those cards. */
    val grouped: Color,
    val card: Color,
    val ink: Color,
    val secondary: Color,
    val tertiary: Color,
    val separator: Color,
    /** The faint fill of a track, a segmented control, an inactive capsule. */
    val fill: Color,
    val accent: Color
)

/** Null unless Liquid Glass is the look. */
val LocalLiquid = staticCompositionLocalOf<LiquidTokens?> { null }

/** The Liquid Glass tokens when that look is on. */
val liquid: LiquidTokens?
    @Composable @ReadOnlyComposable get() = LocalLiquid.current

fun liquidTokens(dark: Boolean, accent: Color): LiquidTokens = if (!dark) {
    LiquidTokens(
        dark = false,
        ground = Color.White,
        grouped = Color(0xFFF2F2F7),
        card = Color.White,
        ink = Color.Black,
        secondary = Color(0xFF8A8A8E),
        tertiary = Color(0xFFC4C4C7),
        separator = Color(0xFFC6C6C8),
        fill = Color(0xFF787880).copy(alpha = 0.12f),
        accent = accent
    )
} else {
    LiquidTokens(
        dark = true,
        ground = Color.Black,
        grouped = Color.Black,
        card = Color(0xFF1C1C1E),
        ink = Color.White,
        secondary = Color(0xFF8D8D93),
        tertiary = Color(0xFF48484A),
        separator = Color(0xFF38383A),
        fill = Color(0xFF787880).copy(alpha = 0.24f),
        accent = accent
    )
}

/** Every role named (lesson 2 in Theme.kt), from the tokens. */
fun liquidScheme(t: LiquidTokens): ColorScheme {
    val onAccent = if (t.accent.luminance() > 0.62f) Color(0xFF101014) else Color.White
    val accentSoft = t.accent.copy(alpha = if (t.dark) 0.24f else 0.14f).compositeOver(t.card)
    return if (!t.dark) {
        lightColorScheme(
            primary = t.accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = t.ink,
            inversePrimary = t.accent,
            secondary = t.secondary,
            onSecondary = Color.White,
            secondaryContainer = t.grouped,
            onSecondaryContainer = t.ink,
            tertiary = t.accent,
            onTertiary = onAccent,
            tertiaryContainer = t.grouped,
            onTertiaryContainer = t.ink,
            background = t.ground,
            onBackground = t.ink,
            surface = t.ground,
            onSurface = t.ink,
            surfaceVariant = t.grouped,
            onSurfaceVariant = t.secondary,
            surfaceTint = t.accent,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = Color(0xFFF9F9FB),
            surfaceContainer = t.grouped,
            surfaceContainerHigh = t.grouped,
            surfaceContainerHighest = Color(0xFFE5E5EA),
            surfaceBright = Color.White,
            surfaceDim = Color(0xFFE5E5EA),
            inverseSurface = Color(0xFF1C1C1E),
            inverseOnSurface = Color.White,
            error = Color(0xFFFF3B30),
            onError = Color.White,
            errorContainer = Color(0xFFFFE1DE),
            onErrorContainer = Color(0xFF410002),
            outline = t.separator,
            outlineVariant = Color(0xFFE5E5EA),
            scrim = Color.Black
        )
    } else {
        darkColorScheme(
            primary = t.accent,
            onPrimary = onAccent,
            primaryContainer = accentSoft,
            onPrimaryContainer = t.ink,
            inversePrimary = t.accent,
            secondary = t.secondary,
            onSecondary = Color.Black,
            secondaryContainer = t.card,
            onSecondaryContainer = t.ink,
            tertiary = t.accent,
            onTertiary = onAccent,
            tertiaryContainer = t.card,
            onTertiaryContainer = t.ink,
            background = t.ground,
            onBackground = t.ink,
            surface = t.ground,
            onSurface = t.ink,
            surfaceVariant = t.card,
            onSurfaceVariant = t.secondary,
            surfaceTint = t.accent,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0E0E10),
            surfaceContainer = t.card,
            surfaceContainerHigh = t.card,
            surfaceContainerHighest = Color(0xFF2C2C2E),
            surfaceBright = Color(0xFF2C2C2E),
            surfaceDim = Color.Black,
            inverseSurface = Color(0xFFF2F2F7),
            inverseOnSurface = Color.Black,
            error = Color(0xFFFF453A),
            onError = Color.Black,
            errorContainer = Color(0xFF3B0F0C),
            onErrorContainer = Color(0xFFFFDAD5),
            outline = t.separator,
            outlineVariant = Color(0xFF2C2C2E),
            scrim = Color.Black
        )
    }
}
