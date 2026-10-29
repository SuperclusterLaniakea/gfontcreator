package com.gfontcreator.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 宣纸、松烟墨、青玉与朱砂——手写字体工坊的配色
private val Jade = Color(0xFF1B6B4A)
private val JadeDark = Color(0xFF0F4A32)
private val Cinnabar = Color(0xFFA84B30)
private val Ochre = Color(0xFF8A6A2F)
private val RicePaper = Color(0xFFFAF7EF)
private val Paper = Color(0xFFFFFCF5)
private val Ink = Color(0xFF24211B)
private val Mist = Color(0xFFEDE6D8)

private val LightColors = lightColorScheme(
    primary = Jade,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE9DB),
    onPrimaryContainer = Color(0xFF05291A),
    secondary = Cinnabar,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF6DDD3),
    onSecondaryContainer = Color(0xFF3D1206),
    tertiary = Ochre,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF4E6C9),
    onTertiaryContainer = Color(0xFF2C2005),
    background = RicePaper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Mist,
    onSurfaceVariant = Color(0xFF5B564B),
    outline = Color(0xFF8B8577),
    outlineVariant = Color(0xFFD6CFC0),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surfaceTint = Jade,
    surfaceDim = Color(0xFFE2DCCF),
    surfaceBright = Paper,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F1E6),
    surfaceContainer = Color(0xFFF0EADD),
    surfaceContainerHigh = Color(0xFFEAE3D4),
    surfaceContainerHighest = Color(0xFFE4DCCC),
    inverseSurface = Color(0xFF332F28),
    inverseOnSurface = Color(0xFFF6F0E4),
    inversePrimary = Color(0xFF8CD4B0),
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8CD4B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = JadeDark,
    onPrimaryContainer = Color(0xFFC9EFD9),
    secondary = Color(0xFFE7A48B),
    onSecondary = Color(0xFF45190C),
    secondaryContainer = Color(0xFF4F2116),
    onSecondaryContainer = Color(0xFFFFDBD0),
    tertiary = Color(0xFFD9B879),
    onTertiary = Color(0xFF3B2D0B),
    tertiaryContainer = Color(0xFF4A3A14),
    onTertiaryContainer = Color(0xFFF5E2B8),
    background = Color(0xFF121613),
    onBackground = Color(0xFFE9E4D8),
    surface = Color(0xFF181D1A),
    onSurface = Color(0xFFE9E4D8),
    surfaceVariant = Color(0xFF2A322C),
    onSurfaceVariant = Color(0xFFC4C0B4),
    outline = Color(0xFF8F9289),
    outlineVariant = Color(0xFF3D453E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceTint = Color(0xFF8CD4B0),
    surfaceDim = Color(0xFF121613),
    surfaceBright = Color(0xFF343A35),
    surfaceContainerLowest = Color(0xFF0C100D),
    surfaceContainerLow = Color(0xFF171C18),
    surfaceContainer = Color(0xFF1B211D),
    surfaceContainerHigh = Color(0xFF252B26),
    surfaceContainerHighest = Color(0xFF303631),
    inverseSurface = Color(0xFFE9E4D8),
    inverseOnSurface = Color(0xFF2B302C),
    inversePrimary = Jade,
    scrim = Color.Black,
)

private val Serif = FontFamily.Serif

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 48.sp, lineHeight = 56.sp, letterSpacing = 2.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = 1.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 30.sp, lineHeight = 38.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 26.sp, lineHeight = 34.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.Medium,
        fontSize = 23.sp, lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp, lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp, lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Serif, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, lineHeight = 15.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun GFontTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
