package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// =========================================================================
// MINIMAL THEME IMPLEMENTATION
// Single accent: #6C5CE7 (refined indigo-violet)
// Light & Dark themes with precise neutral surfaces
// =========================================================================

private val MinimalLightColorScheme = lightColorScheme(
    primary = AccentIndigo,
    onPrimary = Color.White,
    primaryContainer = LightAccentContainer,
    onPrimaryContainer = AccentIndigoLight,
    secondary = AccentIndigo,
    onSecondary = Color.White,
    secondaryContainer = LightAccentContainer,
    onSecondaryContainer = AccentIndigoLight,
    tertiary = AccentIndigo,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightSecondaryText,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = MinimalError,
    onError = Color.White,
    errorContainer = MinimalErrorContainerLight,
    onErrorContainer = MinimalError
)

private val MinimalDarkColorScheme = darkColorScheme(
    primary = AccentIndigoDark,
    onPrimary = Color.White,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkPrimaryText,
    secondary = AccentIndigoDark,
    onSecondary = Color.White,
    secondaryContainer = DarkAccentContainer,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = AccentIndigoDark,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSecondaryText,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = MinimalErrorDark,
    onError = Color.White,
    errorContainer = MinimalErrorContainerDark,
    onErrorContainer = MinimalErrorDark
)

val MinimalColorsInstance: MinimalColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMinimalColors.current

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MinimalDarkColorScheme else MinimalLightColorScheme
    val minimalTokens = if (darkTheme) {
        MinimalColors(
            background = DarkBackground,
            surface = DarkSurface,
            textPrimary = DarkPrimaryText,
            textSecondary = DarkSecondaryText,
            outline = DarkOutline,
            accent = AccentIndigoDark,
            accentContainer = DarkAccentContainer,
            error = MinimalErrorDark,
            errorContainer = MinimalErrorContainerDark,
            isDark = true
        )
    } else {
        MinimalColors(
            background = LightBackground,
            surface = LightSurface,
            textPrimary = LightPrimaryText,
            textSecondary = LightSecondaryText,
            outline = LightOutline,
            accent = AccentIndigo,
            accentContainer = LightAccentContainer,
            error = MinimalError,
            errorContainer = MinimalErrorContainerLight,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalMinimalColors provides minimalTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
