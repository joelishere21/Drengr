package com.drengr.launcher.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class DrengrSpacing(
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    val pageHorizontal: Dp = 20.dp,
)

@Immutable
data class DrengrShapes(
    val small: Shape = RoundedCornerShape(12.dp),
    val medium: Shape = RoundedCornerShape(16.dp),
    val large: Shape = RoundedCornerShape(20.dp),
    val pill: Shape = RoundedCornerShape(percent = 50),
)

/** Sans-serif for UI text, monospace for the clock and technical details. */
@Immutable
data class DrengrTypography(
    val clock: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Light,
        fontSize = 76.sp,
        letterSpacing = (-3).sp,
    ),
    val headline: TextStyle = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        letterSpacing = (-0.5).sp,
    ),
    val title: TextStyle = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    val body: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 21.sp),
    val label: TextStyle = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
    ),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val mono: TextStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
    val monoLarge: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
    ),
)

/** Soft shadow that keeps text readable when it sits directly on a bright wallpaper. */
fun TextStyle.onWallpaper(): TextStyle =
    copy(shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), offset = Offset(0f, 2f), blurRadius = 8f))
