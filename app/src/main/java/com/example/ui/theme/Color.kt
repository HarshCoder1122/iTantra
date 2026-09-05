package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// MINIMAL DESIGN SYSTEM (Linear / Things 3 / Notion aesthetic)
// Seed & Single Accent: #6C5CE7 (Refined Indigo-Violet)
// Strict rules: Single accent color. Two neutral surfaces (Light + Dark).
// No gradients, no skeuomorphic depth, no multiple clashing accents.
// =========================================================================

// Single Accent Color
val AccentIndigo = Color(0xFF6C5CE7)
val AccentIndigoLight = Color(0xFF5849D6)
val AccentIndigoDark = Color(0xFF7D6FF0)

// Light Theme Palette
val LightBackground = Color(0xFFFAFAFA)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimaryText = Color(0xFF111114)
val LightSecondaryText = Color(0xFF6B6B76)
val LightOutline = Color(0xFFE5E5EA)
val LightAccentContainer = Color(0xFFF0EEFD)

// Dark Theme Palette
val DarkBackground = Color(0xFF0E0E11)
val DarkSurface = Color(0xFF1A1A1F)
val DarkPrimaryText = Color(0xFFF5F5F7)
val DarkSecondaryText = Color(0xFF9A9AA5)
val DarkOutline = Color(0xFF2A2A31)
val DarkAccentContainer = Color(0xFF232038)

// Semantic Utility (Subdued, restrained alerts)
val MinimalError = Color(0xFFE53935)
val MinimalErrorDark = Color(0xFFEF5350)
val MinimalErrorContainerLight = Color(0xFFFDF0F0)
val MinimalErrorContainerDark = Color(0xFF2E1A1B)

@Immutable
data class MinimalColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val accent: Color,
    val accentContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val isDark: Boolean
)

val LocalMinimalColors = staticCompositionLocalOf {
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

// =========================================================================
// Backward-Compatibility Aliases (seamlessly mapped to minimal design tokens)
// =========================================================================
val IsroDarkCarbon = DarkBackground
val IsroSpaceNavy = DarkSurface
val IsroPanelSurface = DarkSurface
val IsroPanelElevated = DarkSurface
val IsroPanelHighlight = DarkOutline
val IsroBorderSubtle = DarkOutline
val IsroBorderStrong = DarkOutline
val IsroBorderSaffron = DarkOutline

val IsroCyan = AccentIndigo
val IsroCyanBright = AccentIndigoDark
val IsroCyanGlow = DarkAccentContainer
val IsroCyanContainer = DarkAccentContainer

val IsroSaffron = AccentIndigo
val IsroSaffronDark = AccentIndigo
val IsroSaffronContainer = DarkAccentContainer

val IsroGreen = AccentIndigo
val IsroGreenBright = AccentIndigo
val IsroGreenContainer = DarkAccentContainer
val IsroGreenSubtle = DarkAccentContainer

val IsroDistressRed = MinimalErrorDark
val IsroDistressRedDark = MinimalError
val IsroDistressRedContainer = MinimalErrorContainerDark
val IsroDistressRedGlow = MinimalErrorContainerDark
val IsroAmberWarning = AccentIndigo

val IsroWhite = DarkPrimaryText
val IsroOffWhite = DarkSecondaryText
val IsroMutedText = DarkSecondaryText
val IsroCyanText = DarkPrimaryText

val SpaceDark = DarkBackground
val CardSurfaceDark = DarkSurface
val CardSurfaceDarkSecondary = DarkSurface
val CardBorderDark = DarkOutline
val CardBorderSubtle = DarkOutline

val TextDarkPrimary = DarkPrimaryText
val TextDarkSecondary = DarkSecondaryText
val TextDarkMuted = DarkSecondaryText

val BrandGreen = AccentIndigo
val BrandGreenBright = AccentIndigo
val BrandGreenDark = AccentIndigo
val BrandGreenDeep = AccentIndigo
val BrandGreenContainer = DarkAccentContainer
val BrandSaffron = AccentIndigo
val BrandIndigo = AccentIndigo
val AlertRed = MinimalErrorDark
val SpaceCardBorder = DarkOutline
val TelemetryCyan = AccentIndigo
