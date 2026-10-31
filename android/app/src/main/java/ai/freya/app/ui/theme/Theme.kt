package ai.freya.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Color(0xFF14151B),
    primaryContainer = NeuSunkenDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = CyanGlow,
    onSecondary = Color(0xFF14151B),
    secondaryContainer = NeuSunkenDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = IceBlue,
    onTertiary = Color(0xFF14151B),
    background = NeuSurfaceDark,
    onBackground = TextPrimaryDark,
    surface = NeuSurfaceDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = NeuSunkenDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = NeuSurfaceDark,
    surfaceContainerHigh = Midnight700,
    outline = TextMutedDark,
    outlineVariant = NeuHighlightDark,
    error = ErrorRed,
    onError = Color(0xFF2A1518),
)

private val LightColors = lightColorScheme(
    primary = VioletLight,
    onPrimary = Color.White,
    primaryContainer = NeuSunkenLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = NeuSunkenLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = CyanLight,
    onTertiary = Color.White,
    background = NeuSurfaceLight,
    onBackground = TextPrimaryLight,
    surface = NeuSurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = NeuSunkenLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = TextMutedLight,
    outlineVariant = NeuShadowLight,
    error = ErrorRedLight,
    onError = Color.White,
)

/** Theme-dependent tokens that Material's ColorScheme has no slot for. */
@Immutable
data class FreyaColors(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    // Neumorphic depth: same-colour surfaces with dual (light/dark) shadows.
    val surface: Color,
    val sunken: Color,
    val neuHighlight: Color,
    val neuShadow: Color,
    // Legacy glass aliases (kept so older call sites keep compiling); they now
    // resolve to neumorphic surfaces rather than translucent glass.
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    // Accents (muted, small-state only).
    val violet: Color,
    val cyan: Color,
    val ice: Color,
    // Semantic (desaturated).
    val approval: Color,
    val error: Color,
    val success: Color,
    val textMuted: Color,
)

private val DarkFreyaColors = FreyaColors(
    isDark = true,
    backdropTop = Color(0xFF262A33),
    backdropMid = NeuSurfaceDark,
    backdropBottom = NeuSurfaceDark,
    surface = NeuSurfaceDark,
    sunken = NeuSunkenDark,
    neuHighlight = NeuHighlightDark,
    neuShadow = NeuShadowDark,
    glassFill = GlassFill,
    glassFillStrong = GlassFillStrong,
    glassBorder = GlassBorder,
    glassHighlight = GlassHighlight,
    violet = ElectricViolet,
    cyan = CyanGlow,
    ice = IceBlue,
    approval = ApprovalAmber,
    error = ErrorRed,
    success = SuccessGreen,
    textMuted = TextMutedDark,
)

private val LightFreyaColors = FreyaColors(
    isDark = false,
    backdropTop = Color(0xFFEDF1F6),
    backdropMid = NeuSurfaceLight,
    backdropBottom = NeuSurfaceLight,
    surface = NeuSurfaceLight,
    sunken = NeuSunkenLight,
    neuHighlight = NeuHighlightLight,
    neuShadow = NeuShadowLight,
    glassFill = GlassFillLight,
    glassFillStrong = NeuSunkenLight,
    glassBorder = GlassBorderLight,
    glassHighlight = NeuHighlightLight,
    violet = VioletLight,
    cyan = CyanLight,
    ice = Color(0xFF4A6E9A),
    approval = ApprovalAmberLight,
    error = ErrorRedLight,
    success = SuccessGreenLight,
    textMuted = TextMutedLight,
)

/** User-selected appearance. DARK is the default and the primary design target. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Motion preferences resolved from the system animator scale and the in-app toggle. */
@Immutable
data class FreyaMotionPrefs(val reduceMotion: Boolean)

val LocalFreyaColors = staticCompositionLocalOf { DarkFreyaColors }
val LocalFreyaMotion = staticCompositionLocalOf { FreyaMotionPrefs(reduceMotion = false) }

object FreyaTheme {
    val colors: FreyaColors @Composable get() = LocalFreyaColors.current
    val motion: FreyaMotionPrefs @Composable get() = LocalFreyaMotion.current
}

@Composable
fun FreyaTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(
        LocalFreyaColors provides if (dark) DarkFreyaColors else LightFreyaColors,
        LocalFreyaMotion provides FreyaMotionPrefs(reduceMotion),
    ) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = FreyaTypography,
            content = content,
        )
    }
}
