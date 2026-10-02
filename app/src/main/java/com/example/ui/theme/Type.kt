package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

/**
 * Editorial display font family inspired by TAN Nimbus:
 * high-fashion, retro-modern display serif with soft organic curves,
 * elegant proportions, and distinctive serif structure.
 * Statically loaded once and cached as an application-wide font asset.
 */
val EditorialDisplayFontFamily = FontFamily(
    Font(R.font.fraunces, FontWeight.Normal),
    Font(R.font.fraunces, FontWeight.Medium),
    Font(R.font.fraunces, FontWeight.SemiBold),
    Font(R.font.fraunces, FontWeight.Bold)
)

/**
 * Vinyl Typography System
 *
 * Implements a clean, high-precision typography hierarchy designed to resemble
 * the engraved and silkscreened labeling of premium audio electronics.
 *
 * Characteristics:
 * - Generous letter spacing for high legibility on dark surfaces
 * - Clear contrast between Track Title (hero), Artist (secondary), and Album/Metadata (technical)
 * - Strict use of scalable units (sp) for accessibility compliance
 */
val Typography = Typography(
    // Large Track Title: Prominent, elegant, restrained
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),

    // Medium Track Title: Used on compact screens or secondary headers
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp,
        color = TextPrimary
    ),

    // Artist Name: Clean, medium weight with subtle tracking
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.3.sp,
        color = TextSecondary
    ),

    // Album / Sub-caption: Technical, restrained
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Light,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp,
        color = TextTertiary
    ),

    // Hardware status indicators (e.g. "33 ⅓ RPM", "STEREO HI-FI", timestamps)
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.0.sp,
        color = TextSecondary
    ),

    // Center record label typography
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp,
        color = TextSecondary
    )
)
