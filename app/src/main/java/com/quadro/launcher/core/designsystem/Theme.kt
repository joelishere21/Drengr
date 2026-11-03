package com.quadro.launcher.core.designsystem

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

val LocalQuadroColors = staticCompositionLocalOf { quadroColors(AccentColor.Default) }
val LocalQuadroSpacing = staticCompositionLocalOf { QuadroSpacing() }
val LocalQuadroShapes = staticCompositionLocalOf { QuadroShapes() }
val LocalQuadroTypography = staticCompositionLocalOf { QuadroTypography() }

object QuadroTheme {
    val colors: QuadroColors
        @Composable @ReadOnlyComposable get() = LocalQuadroColors.current
    val spacing: QuadroSpacing
        @Composable @ReadOnlyComposable get() = LocalQuadroSpacing.current
    val shapes: QuadroShapes
        @Composable @ReadOnlyComposable get() = LocalQuadroShapes.current
    val typography: QuadroTypography
        @Composable @ReadOnlyComposable get() = LocalQuadroTypography.current
}

/**
 * Root theme. Provides Quadro tokens and maps them onto Material 3 so stock components
 * (menus, switches, dialogs) match the launcher without per-screen styling.
 */
@Composable
fun QuadroTheme(
    accent: AccentColor = AccentColor.Default,
    content: @Composable () -> Unit,
) {
    val colors = remember(accent) { quadroColors(accent) }
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
        LocalQuadroColors provides colors,
        LocalQuadroSpacing provides QuadroSpacing(),
        LocalQuadroShapes provides QuadroShapes(),
        LocalQuadroTypography provides QuadroTypography(),
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
