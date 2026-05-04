package com.gramayatri.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ─── Brand Colors ──────────────────────────────────────────────────────────
// Inspired by rural India: terracotta earth, saffron sun, leaf green, sky blue

object GramaColors {
    // Primary — Deep saffron (bus orange)
    val SaffronDeep = Color(0xFFE65100)
    val SaffronMedium = Color(0xFFFF6D00)
    val SaffronLight = Color(0xFFFF9E40)
    val SaffronContainer = Color(0xFFFFE0B2)

    // Secondary — Earthy green
    val LeafGreen = Color(0xFF2E7D32)
    val LeafGreenLight = Color(0xFF4CAF50)
    val LeafContainer = Color(0xFFE8F5E9)

    // Tertiary — Sky blue
    val SkyBlue = Color(0xFF0277BD)
    val SkyBlueLight = Color(0xFF29B6F6)

    // Status colors
    val BusPassed = Color(0xFF9E9E9E)
    val BusHere = Color(0xFF4CAF50)
    val EtaHighConf = Color(0xFF1B5E20)
    val EtaMedConf = Color(0xFFE65100)
    val EtaLowConf = Color(0xFF9E9E9E)

    // Alert colors
    val AlertRed = Color(0xFFC62828)
    val AlertOrange = Color(0xFFEF6C00)
    val AlertGreen = Color(0xFF2E7D32)
    val AlertBg = Color(0xFFFFF3E0)

    // Neutral
    val Surface = Color(0xFFFFFBF5)
    val SurfaceDark = Color(0xFF1A1207)
    val OnSurface = Color(0xFF1A1207)
    val Outline = Color(0xFFD7C4A8)
}

private val LightColorScheme = lightColorScheme(
    primary = GramaColors.SaffronDeep,
    onPrimary = Color.White,
    primaryContainer = GramaColors.SaffronContainer,
    onPrimaryContainer = Color(0xFF3E1300),
    secondary = GramaColors.LeafGreen,
    onSecondary = Color.White,
    secondaryContainer = GramaColors.LeafContainer,
    onSecondaryContainer = Color(0xFF002106),
    tertiary = GramaColors.SkyBlue,
    onTertiary = Color.White,
    background = GramaColors.Surface,
    onBackground = GramaColors.OnSurface,
    surface = Color.White,
    onSurface = GramaColors.OnSurface,
    surfaceVariant = Color(0xFFF5EDD9),
    onSurfaceVariant = Color(0xFF4E4539),
    outline = GramaColors.Outline,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColorScheme = darkColorScheme(
    primary = GramaColors.SaffronLight,
    onPrimary = Color(0xFF5C1900),
    primaryContainer = Color(0xFF832500),
    onPrimaryContainer = GramaColors.SaffronContainer,
    secondary = GramaColors.LeafGreenLight,
    onSecondary = Color(0xFF003A0D),
    secondaryContainer = Color(0xFF005319),
    onSecondaryContainer = GramaColors.LeafContainer,
    tertiary = GramaColors.SkyBlueLight,
    background = GramaColors.SurfaceDark,
    onBackground = Color(0xFFF2E5CF),
    surface = Color(0xFF241810),
    onSurface = Color(0xFFF2E5CF),
    surfaceVariant = Color(0xFF4E4539),
    onSurfaceVariant = Color(0xFFD3C5B2),
    outline = Color(0xFF9C8E7D)
)

@Composable
fun GramaYatriTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = GramaTypography,
        content = content
    )
}
