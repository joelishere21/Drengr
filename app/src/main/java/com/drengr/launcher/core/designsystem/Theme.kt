package com.drengr.launcher.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalDrengrColors = staticCompositionLocalOf { drengrColors(AccentColor.Default) }
val LocalDrengrSpacing = staticCompositionLocalOf { DrengrSpacing() }
val LocalDrengrShapes = staticCompositionLocalOf { DrengrShapes() }
val LocalDrengrTypography = staticCompositionLocalOf { DrengrTypography() }

object DrengrTheme {
    val colors: DrengrColors
        @Composable @ReadOnlyComposable get() = LocalDrengrColors.current
    val spacing: DrengrSpacing
        @Composable @ReadOnlyComposable get() = LocalDrengrSpacing.current
    val shapes: DrengrShapes
        @Composable @ReadOnlyComposable get() = LocalDrengrShapes.current
    val typography: DrengrTypography
        @Composable @ReadOnlyComposable get() = LocalDrengrTypography.current
}

/**
 * Root theme. Provides Drengr tokens and maps them onto Material 3 so stock components
 * (menus, switches, dialogs) match the launcher without per-screen styling.
 */
@Composable
fun DrengrTheme(
    accent: AccentColor = AccentColor.Default,
    content: @Composable () -> Unit,
) {
    val colors = remember(accent) { drengrColors(accent) }
    val scheme = remember(colors) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            primaryContainer = colors.accentSoft,
            onPrimaryContainer = colors.textPrimary,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accentSoft,
            onSecondaryContainer = colors.textPrimary,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surfaceElevated,
            onSurfaceVariant = colors.textSecondary,
            surfaceContainerLowest = colors.background,
            surfaceContainerLow = colors.surface,
            surfaceContainer = colors.surfaceElevated,
            surfaceContainerHigh = colors.surfaceElevated,
            surfaceContainerHighest = colors.surfaceElevated,
            outline = colors.divider,
            outlineVariant = colors.divider,
        )
    }
    CompositionLocalProvider(
        LocalDrengrColors provides colors,
        LocalDrengrSpacing provides DrengrSpacing(),
        LocalDrengrShapes provides DrengrShapes(),
        LocalDrengrTypography provides DrengrTypography(),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(20.dp),
            ),
        ) {
            CompositionLocalProvider(LocalContentColor provides colors.textPrimary, content = content)
        }
    }
}
