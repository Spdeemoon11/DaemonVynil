package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Vinyl Monochromatic Dark Studio Color Scheme
 *
 * Implements strict hardware-inspired dark aesthetic:
 * - Pure deep background (#060606)
 * - Layered graphite and charcoal surfaces (#0F0F0F, #161616, #1E1E1E)
 * - Soft white primary typography (#F2F2F2)
 * - Precision cool-gray secondary typography (#8E8E93)
 * - Subtle brushed metal accents
 */
private val VinylColorScheme = darkColorScheme(
    primary = MachinedSilver,
    onPrimary = DeckSurface,
    primaryContainer = DeckSurfaceRaised,
    onPrimaryContainer = TextPrimary,
    secondary = TextSecondary,
    onSecondary = StudioBackground,
    secondaryContainer = DeckSurfaceVariant,
    onSecondaryContainer = TextPrimary,
    tertiary = AccentSubtle,
    onTertiary = StudioBackground,
    background = StudioBackground,
    onBackground = TextPrimary,
    surface = DeckSurface,
    onSurface = TextPrimary,
    surfaceVariant = DeckSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = MachinedBezel,
    outlineVariant = DeckSurfaceRaised
)

/**
 * Vinyl Application Theme
 *
 * Enforces the dark studio environment required for believable skeuomorphic turntable rendering.
 * Does not dynamically tint with system wallpaper colors in order to preserve the pristine
 * calibrated monochromatic appearance of the turntable hardware.
 *
 * @param content The composable tree to be styled.
 */
@Composable
fun VinylTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = VinylColorScheme,
        typography = Typography,
        content = content
    )
}
