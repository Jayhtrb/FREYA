package ai.freya.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = ElectricViolet,
    onPrimary = Charcoal900,
    primaryContainer = ElectricVioletDim,
    onPrimaryContainer = TextPrimaryDark,
    secondary = CyanGlow,
    onSecondary = Charcoal900,
    secondaryContainer = CyanDim,
    onSecondaryContainer = Charcoal900,
    tertiary = CyanDim,
    onTertiary = Charcoal900,
    background = Charcoal900,
    onBackground = TextPrimaryDark,
    surface = Midnight800,
    onSurface = TextPrimaryDark,
    surfaceVariant = Midnight700,
    onSurfaceVariant = TextSecondaryDark,
    outline = TextMutedDark,
    outlineVariant = Midnight600,
    error = ErrorRed,
    onError = Color.White,
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
    error = ErrorRed,
    onError = Color.White,
)

@Composable
fun FreyaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = FreyaTypography,
        content = content,
    )
}
