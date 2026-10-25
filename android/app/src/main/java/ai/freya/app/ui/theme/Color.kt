package ai.freya.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * FREYA colour tokens.
 *
 * The dark palette is the primary experience: near-black "void" with a
 * midnight-blue lift, and three restrained accents (violet, cyan, ice).
 * Accents are used for light and state, never as large fills. All text/surface
 * pairs below were chosen to clear WCAG AA (4.5:1) for body text.
 */

// ---- Dark base ----
val Void = Color(0xFF05060B)            // window / deepest background
val Abyss = Color(0xFF080B16)           // backdrop gradient end
val Midnight = Color(0xFF0C1226)        // midnight-blue lift behind the core
val Charcoal900 = Color(0xFF0A0C14)     // kept for older references
val Midnight800 = Color(0xFF10141F)     // surface
val Midnight700 = Color(0xFF161B2A)     // raised surface
val Midnight600 = Color(0xFF222838)     // outline variant

// ---- Glass (translucent panels drawn over the backdrop) ----
val GlassFill = Color(0x14A9B8FF)       // ~8% icy tint
val GlassFillStrong = Color(0x24A9B8FF) // ~14% for pressed / selected
val GlassBorder = Color(0x2EBFD0FF)     // ~18% hairline
val GlassHighlight = Color(0x1FFFFFFF)  // top-edge sheen

// ---- Accents ----
val ElectricViolet = Color(0xFF9A8CFF)
val ElectricVioletDim = Color(0xFF6C5CE7)
val CyanGlow = Color(0xFF6FE3FF)
val CyanDim = Color(0xFF2FA9C4)
val IceBlue = Color(0xFFCFE8FF)

// ---- Text (dark) ----
val TextPrimaryDark = Color(0xFFEEF1FA)
val TextSecondaryDark = Color(0xFFAEB6CC)
val TextMutedDark = Color(0xFF7F88A3)

// ---- Semantic state colours ----
val ApprovalAmber = Color(0xFFFFC46B)
val ErrorRed = Color(0xFFFF6B72)
val SuccessGreen = Color(0xFF4FD18B)

// ---- Light palette (optional theme; keeps the same hues, inverted luminance) ----
val BackgroundLight = Color(0xFFF3F5FB)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFE6EAF5)
val VioletLight = Color(0xFF5141C9)
val CyanLight = Color(0xFF00788F)
val TextPrimaryLight = Color(0xFF111522)
val TextSecondaryLight = Color(0xFF465069)
val TextMutedLight = Color(0xFF6D7690)
val GlassFillLight = Color(0x99FFFFFF)
val GlassBorderLight = Color(0x1F1B2A55)
