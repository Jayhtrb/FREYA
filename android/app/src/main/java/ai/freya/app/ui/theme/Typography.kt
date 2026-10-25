package ai.freya.app.ui.theme

import ai.freya.app.R
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Type system.
 *  - Display: Unbounded (wide, cinematic) — wordmark, headlines, state titles. Used sparingly.
 *  - Text:    Manrope — body, titles, buttons. Calm and highly legible at small sizes.
 *  - Telemetry: IBM Plex Mono — model names, ids, latencies, status chips.
 * All sizes are in sp, so the user's font scale is respected.
 * Fonts are SIL OFL 1.1 (licences in assets/licenses/).
 */

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val DisplayFamily = FontFamily(
    variable(R.font.unbounded_variable, 300),
    variable(R.font.unbounded_variable, 400),
    variable(R.font.unbounded_variable, 500),
)

val TextFamily = FontFamily(
    variable(R.font.manrope_variable, 400),
    variable(R.font.manrope_variable, 500),
    variable(R.font.manrope_variable, 600),
    variable(R.font.manrope_variable, 700),
)

val MonoFamily = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
)

val FreyaTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(300), fontSize = 30.sp, lineHeight = 38.sp, letterSpacing = 1.sp),
    headlineLarge = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(300), fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.5.sp),
    headlineMedium = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(300), fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = 0.5.sp),
    headlineSmall = TextStyle(fontFamily = DisplayFamily, fontWeight = FontWeight(400), fontSize = 18.sp, lineHeight = 26.sp, letterSpacing = 0.4.sp),
    titleLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 21.sp, letterSpacing = 0.15.sp),
    bodySmall = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 17.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontFamily = TextFamily, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.8.sp),
)
