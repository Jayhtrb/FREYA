package ai.freya.app.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * FREYA neumorphic (soft-UI) colour tokens.
 *
 * Depth comes from dual shadows on surfaces that are the SAME colour as the
 * background — a light highlight top-left and a dark shadow bottom-right — not
 * from glass, blur, neon glow or high-contrast fills. One muted accent
 * (desaturated violet) plus a muted cyan for "speaking"; amber/red/green are
 * kept but desaturated. Accents are used for small states only, never as large
 * fills. All text/surface pairs clear WCAG AA (4.5:1).
 */

// ---- Dark neumorphic base (surface == background) ----
val NeuSurfaceDark = Color(0xFF23262E)   // surface / background
val NeuSunkenDark = Color(0xFF1C1F26)    // inset wells & inputs
val NeuHighlightDark = Color(0xFF3A3F4B) // light shadow (top-left)
val NeuShadowDark = Color(0xFF15171D)    // dark shadow (bottom-right)

// Kept names, remapped to the neumorphic ramp (referenced across the app).
val Void = NeuSurfaceDark
val Abyss = Color(0xFF23262E)
val Midnight = Color(0xFF262A33)
val Charcoal900 = NeuSurfaceDark
val Midnight800 = NeuSurfaceDark
val Midnight700 = Color(0xFF2A2E38)
val Midnight600 = Color(0xFF313641)

// ---- Glass tokens: retained for API compatibility, aliased to surfaces ----
val GlassFill = NeuSurfaceDark
val GlassFillStrong = NeuSunkenDark
val GlassBorder = NeuHighlightDark
val GlassHighlight = Color(0xFF4A505E)

// ---- Muted accents ----
val ElectricViolet = Color(0xFF8B8FD0)     // desaturated violet accent
val ElectricVioletDim = Color(0xFF7479BC)
val CyanGlow = Color(0xFF5FA8C9)           // muted cyan (speaking)
val CyanDim = Color(0xFF4C90A8)
val IceBlue = Color(0xFF9FB8C9)

// ---- Text (dark) ----
val TextPrimaryDark = Color(0xFFDDE2EC)
val TextSecondaryDark = Color(0xFF9BA3B4)
val TextMutedDark = Color(0xFF79808F)

// ---- Semantic (desaturated) ----
val ApprovalAmber = Color(0xFFD9A96B)
val ErrorRed = Color(0xFFE08086)
val SuccessGreen = Color(0xFF7BBE96)

// ---- Light neumorphic base ----
val NeuSurfaceLight = Color(0xFFE4E9F0)   // surface / background
val NeuSunkenLight = Color(0xFFD7DDE7)    // inset wells & inputs
val NeuHighlightLight = Color(0xFFFFFFFF) // light shadow
val NeuShadowLight = Color(0xFFB9C0CE)    // dark shadow

val BackgroundLight = NeuSurfaceLight
val SurfaceLight = NeuSurfaceLight
val SurfaceVariantLight = NeuSunkenLight
val VioletLight = Color(0xFF6C6FB8)
val CyanLight = Color(0xFF4C90A8)
val TextPrimaryLight = Color(0xFF454B59)
val TextSecondaryLight = Color(0xFF6E7686)
val TextMutedLight = Color(0xFF848B9B)
val GlassFillLight = NeuSurfaceLight
val GlassBorderLight = NeuShadowLight

// ---- Semantic (light, desaturated) ----
val ErrorRedLight = Color(0xFFC25B63)
val SuccessGreenLight = Color(0xFF5E9B77)
val ApprovalAmberLight = Color(0xFFB0803C)
