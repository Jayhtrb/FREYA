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
    onPrimary = Void,
    primaryContainer = ElectricVioletDim,
    onPrimaryContainer = TextPrimaryDark,
    secondary = CyanGlow,
    onSecondary = Void,
    secondaryContainer = CyanDim,
    onSecondaryContainer = Void,
    tertiary = IceBlue,
    onTertiary = Void,
    background = Void,
    onBackground = TextPrimaryDark,
    surface = Midnight800,
    onSurface = TextPrimaryDark,
    surfaceVariant = Midnight700,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = Midnight800,
    surfaceContainerHigh = Midnight700,
    outline = TextMutedDark,
    outlineVariant = Midnight600,
    error = ErrorRed,
    onError = Void,
)

private val LightColors = lightColorScheme(
    primary = VioletLight,
    onPrimary = Color.White,
    primaryContainer = SurfaceVariantLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = CyanLight,
    onSecondary = Color.White,
    secondaryContainer = SurfaceVariantLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = CyanLight,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = TextSecondaryLight,
    outline = TextMutedLight,
    outlineVariant = SurfaceVariantLight,
    error = Color(0xFFC62E3A),
    onError = Color.White,
)

/** Theme-dependent tokens that Material's ColorScheme has no slot for. */
@Immutable
data class FreyaColors(
    val isDark: Boolean,
    val backdropTop: Color,
    val backdropMid: Color,
    val backdropBottom: Color,
    val glassFill: Color,
    val glassFillStrong: Color,
    val glassBorder: Color,
    val glassHighlight: Color,
    val violet: Color,
    val cyan: Color,
    val ice: Color,
    val approval: Color,
    val error: Color,
    val success: Color,
    val textMuted: Color,
)

private val DarkFreyaColors = FreyaColors(
    isDark = true,
    backdropTop = Abyss,
    backdropMid = Midnight,
    backdropBottom = Void,
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
    backdropTop = Color(0xFFEAEFFC),
    backdropMid = Color(0xFFDDE5FA),
    backdropBottom = BackgroundLight,
    glassFill = GlassFillLight,
    glassFillStrong = Color(0xCCFFFFFF),
    glassBorder = GlassBorderLight,
    glassHighlight = Color(0x66FFFFFF),
    violet = VioletLight,
    cyan = CyanLight,
    ice = Color(0xFF3F6FB5),
    approval = Color(0xFFA86400),
    error = Color(0xFFC62E3A),
    success = Color(0xFF1E8A52),
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
