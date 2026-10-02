package com.glasscast.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * GlassCast palette.
 *
 * Identical in structure to GlassBook — near-neutral grounds so the single
 * accent is the only saturated thing on screen. The accent is the one thing
 * that differs: purple, sampled from the app mark's gradient (#BA70EB → #7528B4)
 * rather than GlassBook's iOS system orange.
 *
 * The three accent stops keep the same value relationship GlassBook used
 * between #FF9500 / #E07B00 / #B86200, so buttons feel equally weighty.
 */

val AccentPurple        = Color(0xFFAF52DE) // default — reads on the dark ground
val AccentPurpleLight   = Color(0xFF9B37C3) // light mode — darker, more saturated
val AccentPurplePressed = Color(0xFF7D23A0)

// Dark ground — a grey, not a black. This is what SYSTEM and DARK resolve to.
val DarkGround0 = Color(0xFF17171B)
val DarkGround1 = Color(0xFF1E1E23)
val DarkGround2 = Color(0xFF24242A)
val DarkGround3 = Color(0xFF2C2C33)
val DarkGround4 = Color(0xFF35353D)
val DarkGround5 = Color(0xFF44444E)

// Lights out — true black for OLED, with the steps kept tight so surfaces are
// still distinguishable from the base rather than all reading as one void.
val OledGround0 = Color(0xFF000000)
val OledGround1 = Color(0xFF08080A)
val OledGround2 = Color(0xFF0D0D10)
val OledGround3 = Color(0xFF141418)
val OledGround4 = Color(0xFF1C1C21)
val OledGround5 = Color(0xFF2A2A31)

// Light ground
val LightGround0 = Color(0xFFFAFAFA)
val LightGround1 = Color(0xFFF3F3F5)
val LightGround2 = Color(0xFFE7E7EB)
val LightGround3 = Color(0xFFDCDCE2)

val DarkTextPrimary   = Color(0xFFF7F7F9)
// Floor of #A8A8B0 per the spec — #C4C4CE is the design value, kept above the floor.
val DarkTextSecondary = Color(0xFFC4C4CE)

val LightTextPrimary   = Color(0xFF101014)
val LightTextSecondary = Color(0xFF5C5C66)

val ErrorRed = Color(0xFFFF453A)
