package pm.antani.resentin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import pm.antani.resentin.data.prefs.AppFontFamily

private val DarkColorScheme = darkColorScheme(
    primary = ResentinPrimaryContainer,
    onPrimary = ResentinOnPrimaryContainer,
    primaryContainer = ResentinPrimary,
    onPrimaryContainer = ResentinOnPrimary,
    secondary = ResentinSecondaryContainer,
    onSecondary = ResentinOnSecondaryContainer,
    secondaryContainer = ResentinSecondary,
    onSecondaryContainer = ResentinOnSecondary,
    tertiary = ResentinTertiaryContainer,
    onTertiary = ResentinOnTertiaryContainer,
    tertiaryContainer = ResentinTertiary,
    onTertiaryContainer = ResentinOnTertiary,
    background = ResentinDarkBackground,
    onBackground = ResentinDarkOnSurface,
    surface = ResentinDarkSurface,
    onSurface = ResentinDarkOnSurface,
    surfaceVariant = ResentinDarkSurfaceVariant,
    onSurfaceVariant = ResentinDarkOnSurfaceVariant,
    surfaceDim = ResentinDarkSurfaceDim,
    surfaceBright = ResentinDarkSurfaceBright,
    surfaceContainerLowest = ResentinDarkContainerLowest,
    surfaceContainerLow = ResentinDarkContainerLow,
    surfaceContainer = ResentinDarkContainer,
    surfaceContainerHigh = ResentinDarkContainerHigh,
    surfaceContainerHighest = ResentinDarkContainerHighest,
    surfaceTint = ResentinPrimaryContainer,
    inverseSurface = ResentinDarkInverseSurface,
    inverseOnSurface = ResentinDarkInverseOnSurface,
    inversePrimary = ResentinPrimary,
    outline = ResentinDarkOutline,
    outlineVariant = ResentinDarkOutlineVariant,
    scrim = ResentinDarkInverseOnSurface,
    error = ResentinDarkError,
    onError = ResentinDarkOnError,
    errorContainer = ResentinDarkErrorContainer,
    onErrorContainer = ResentinDarkOnErrorContainer,
)

private val LightColorScheme = lightColorScheme(
    primary = ResentinPrimary,
    onPrimary = ResentinOnPrimary,
    primaryContainer = ResentinPrimaryContainer,
    onPrimaryContainer = ResentinOnPrimaryContainer,
    secondary = ResentinSecondary,
    onSecondary = ResentinOnSecondary,
    secondaryContainer = ResentinSecondaryContainer,
    onSecondaryContainer = ResentinOnSecondary,
    tertiary = ResentinTertiary,
    onTertiary = ResentinOnTertiary,
    tertiaryContainer = ResentinTertiaryContainer,
    onTertiaryContainer = ResentinOnTertiaryContainer,
    background = ResentinLightBackground,
    onBackground = ResentinLightOnSurface,
    surface = ResentinLightSurface,
    onSurface = ResentinLightOnSurface,
    surfaceVariant = ResentinLightSurfaceVariant,
    onSurfaceVariant = ResentinLightOnSurfaceVariant,
    surfaceDim = ResentinLightSurfaceDim,
    surfaceBright = ResentinLightSurfaceBright,
    surfaceContainerLowest = ResentinLightContainerLowest,
    surfaceContainerLow = ResentinLightContainerLow,
    surfaceContainer = ResentinLightContainer,
    surfaceContainerHigh = ResentinLightContainerHigh,
    surfaceContainerHighest = ResentinLightContainerHighest,
    surfaceTint = ResentinPrimary,
    inverseSurface = ResentinLightInverseSurface,
    inverseOnSurface = ResentinLightInverseOnSurface,
    inversePrimary = ResentinPrimaryContainer,
    outline = ResentinLightOutline,
    outlineVariant = ResentinLightOutlineVariant,
    scrim = ResentinLightInverseSurface,
    error = ResentinLightError,
    onError = ResentinLightOnError,
    errorContainer = ResentinLightErrorContainer,
    onErrorContainer = ResentinLightOnErrorContainer,
)

private val ResentinShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
)

@Composable
fun ResentinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: Float = 1f,
    lineHeightScale: Float = 1f,
    fontFamilyChoice: AppFontFamily = AppFontFamily.SYSTEM,
    chatFontFamilyChoice: AppFontFamily = AppFontFamily.SYSTEM,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val fontFamily = fontFamilyChoice.toComposeFontFamily()
    val chatFontFamily = chatFontFamilyChoice.toComposeFontFamily()
    val codeFontFamily = chatFontFamilyChoice.toComposeCodeFontFamily()
    CompositionLocalProvider(
        LocalResentinFontFamily provides fontFamily,
        LocalResentinChatFontFamily provides chatFontFamily,
        LocalResentinCodeFontFamily provides codeFontFamily,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographyFor(fontFamily).scaled(fontScale, lineHeightScale),
            shapes = ResentinShapes,
            content = content,
        )
    }
}

/** Global text-size multiplier (see the font-size slider in Settings): scales every
 * type style's size and line height, leaving the custom palette untouched.
 * [lineHeightScale] is the separate interlinea setting, applied on top. */
private fun Typography.scaled(scale: Float, lineHeightScale: Float = 1f): Typography {
    if (scale == 1f && lineHeightScale == 1f) return this
    fun TextStyle.scaled(): TextStyle =
        copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale * lineHeightScale)
    return copy(
        displayLarge = displayLarge.scaled(),
        displayMedium = displayMedium.scaled(),
        displaySmall = displaySmall.scaled(),
        headlineLarge = headlineLarge.scaled(),
        headlineMedium = headlineMedium.scaled(),
        headlineSmall = headlineSmall.scaled(),
        titleLarge = titleLarge.scaled(),
        titleMedium = titleMedium.scaled(),
        titleSmall = titleSmall.scaled(),
        bodyLarge = bodyLarge.scaled(),
        bodyMedium = bodyMedium.scaled(),
        bodySmall = bodySmall.scaled(),
        labelLarge = labelLarge.scaled(),
        labelMedium = labelMedium.scaled(),
        labelSmall = labelSmall.scaled(),
    )
}
