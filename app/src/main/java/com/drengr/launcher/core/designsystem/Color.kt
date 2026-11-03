package com.drengr.launcher.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Semantic color tokens. Screens must use these instead of raw hex values. */
@Immutable
data class DrengrColors(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val accent: Color,
    val accentSoft: Color,
    val onAccent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val divider: Color,
    val scrim: Color,
)

/** User-selectable accents. Blue is the default from the product spec. */
enum class AccentColor(val displayName: String, val accent: Color, val soft: Color) {
    Blue("Blue", Color(0xFF579BFF), Color(0xFF203B60)),
    Teal("Teal", Color(0xFF4FD1C5), Color(0xFF1B4744)),
    Violet("Violet", Color(0xFFA78BFA), Color(0xFF392F5C)),
    Green("Green", Color(0xFF6CCB7A), Color(0xFF244530)),
    Amber("Amber", Color(0xFFF6B44B), Color(0xFF4F3C1D)),
    Rose("Rose", Color(0xFFF87194), Color(0xFF52263A));

    companion object {
        val Default = Blue
        fun fromName(name: String?): AccentColor = entries.firstOrNull { it.name == name } ?: Default
    }
}

fun drengrColors(accent: AccentColor): DrengrColors = DrengrColors(
    background = Color(0xFF101216),
    surface = Color(0xFF191D24),
    surfaceElevated = Color(0xFF222936),
    accent = accent.accent,
    accentSoft = accent.soft,
    onAccent = Color(0xFF0B0D10),
    textPrimary = Color(0xFFF1F4F9),
    textSecondary = Color(0xFF929BAA),
    divider = Color(0xFF303744),
    scrim = Color(0xCC07080A),
)
