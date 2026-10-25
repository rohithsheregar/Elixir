package com.cellclaw.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Light Palette - Refined Indigo & Slate with rich semantic accents
val PrimaryLight = Color(0xFF4338CA)        // Indigo 700
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFE0E7FF) // Indigo 100
val OnPrimaryContainerLight = Color(0xFF1E1B4B) // Indigo 950

val SecondaryLight = Color(0xFF6366F1)      // Indigo 500
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFEEF2FF) // Indigo 50
val OnSecondaryContainerLight = Color(0xFF312E81)

val TertiaryLight = Color(0xFF0D9488)       // Teal 600
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFCCFBF1) // Teal 100
val OnTertiaryContainerLight = Color(0xFF134E4A)

val BackgroundLight = Color(0xFFF8FAFC)     // Slate 50
val OnBackgroundLight = Color(0xFF0F172A)   // Slate 900
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9) // Slate 100
val OnSurfaceVariantLight = Color(0xFF475569) // Slate 600
val OutlineLight = Color(0xFFCBD5E1)        // Slate 300
val OutlineVariantLight = Color(0xFFE2E8F0) // Slate 200

// Dark Palette - Deep Slate with luminous Indigo & soft tones
val PrimaryDark = Color(0xFF818CF8)         // Indigo 400
val OnPrimaryDark = Color(0xFF1E1B4B)       // Indigo 950
val PrimaryContainerDark = Color(0xFF3730A3) // Indigo 800
val OnPrimaryContainerDark = Color(0xFFE0E7FF)

val SecondaryDark = Color(0xFFA5B4FC)       // Indigo 300
val OnSecondaryDark = Color(0xFF312E81)
val SecondaryContainerDark = Color(0xFF4338CA)
val OnSecondaryContainerDark = Color(0xFFEEF2FF)

val TertiaryDark = Color(0xFF2DD4BF)        // Teal 400
val OnTertiaryDark = Color(0xFF042F2E)
val TertiaryContainerDark = Color(0xFF115E59)
val OnTertiaryContainerDark = Color(0xFFCCFBF1)

val BackgroundDark = Color(0xFF0B0F19)      // Very deep slate
val OnBackgroundDark = Color(0xFFF8FAFC)
val SurfaceDark = Color(0xFF151D2F)         // Elevated slate
val OnSurfaceDark = Color(0xFFF8FAFC)
val SurfaceVariantDark = Color(0xFF1E293B)  // Slate 800
val OnSurfaceVariantDark = Color(0xFF94A3B8) // Slate 400
val OutlineDark = Color(0xFF334155)         // Slate 700
val OutlineVariantDark = Color(0xFF1E293B)

/**
 * Semantic extension colors for PlacementOS application states, criteria evaluations,
 * verification gates, and badges.
 */
@Immutable
data class ElixirColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,

    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,

    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,

    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color,

    val badgeEligible: Color,
    val onBadgeEligible: Color,
    val badgeNotEligible: Color,
    val onBadgeNotEligible: Color,
    val badgeUnknown: Color,
    val onBadgeUnknown: Color,

    val cardBorder: Color,
    val divider: Color
)

val LightElixirColors = ElixirColors(
    success = Color(0xFF059669),            // Emerald 600
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFD1FAE5),   // Emerald 100
    onSuccessContainer = Color(0xFF065F46), // Emerald 800

    warning = Color(0xFFD97706),            // Amber 600
    onWarning = Color(0xFFFFFFFF),
    warningContainer = Color(0xFFFEF3C7),   // Amber 100
    onWarningContainer = Color(0xFF92400E), // Amber 800

    error = Color(0xFFE11D48),              // Rose 600
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE4E6),     // Rose 100
    onErrorContainer = Color(0xFF9F1239),   // Rose 800

    info = Color(0xFF2563EB),               // Blue 600
    onInfo = Color(0xFFFFFFFF),
    infoContainer = Color(0xFFDBEAFE),      // Blue 100
    onInfoContainer = Color(0xFF1E40AF),    // Blue 800

    badgeEligible = Color(0xFFD1FAE5),
    onBadgeEligible = Color(0xFF065F46),
    badgeNotEligible = Color(0xFFFFE4E6),
    onBadgeNotEligible = Color(0xFF9F1239),
    badgeUnknown = Color(0xFFFEF3C7),
    onBadgeUnknown = Color(0xFF92400E),

    cardBorder = Color(0xFFE2E8F0),
    divider = Color(0xFFE2E8F0)
)

val DarkElixirColors = ElixirColors(
    success = Color(0xFF34D399),            // Emerald 400
    onSuccess = Color(0xFF064E3B),          // Emerald 900
    successContainer = Color(0xFF065F46),   // Emerald 800
    onSuccessContainer = Color(0xFFD1FAE5), // Emerald 100

    warning = Color(0xFFFBBF24),            // Amber 400
    onWarning = Color(0xFF78350F),          // Amber 900
    warningContainer = Color(0xFF92400E),   // Amber 800
    onWarningContainer = Color(0xFFFEF3C7), // Amber 100

    error = Color(0xFFFB7185),              // Rose 400
    onError = Color(0xFF881337),            // Rose 900
    errorContainer = Color(0xFF9F1239),     // Rose 800
    onErrorContainer = Color(0xFFFFE4E6),   // Rose 100

    info = Color(0xFF60A5FA),               // Blue 400
    onInfo = Color(0xFF1E3A8A),             // Blue 900
    infoContainer = Color(0xFF1E40AF),      // Blue 800
    onInfoContainer = Color(0xFFDBEAFE),    // Blue 100

    badgeEligible = Color(0xFF064E3B),
    onBadgeEligible = Color(0xFF6EE7B7),
    badgeNotEligible = Color(0xFF881337),
    onBadgeNotEligible = Color(0xFFFDA4AF),
    badgeUnknown = Color(0xFF78350F),
    onBadgeUnknown = Color(0xFFFCD34D),

    cardBorder = Color(0xFF334155),
    divider = Color(0xFF1E293B)
)

val LocalElixirColors = staticCompositionLocalOf { LightElixirColors }
