package com.familyapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val BotanicalDarkColorScheme = darkColorScheme(
    primary = ForestGreenDark,
    onPrimary = OnForestGreenDark,
    primaryContainer = ForestGreenContainerDark,
    onPrimaryContainer = OnForestGreenContainerDark,
    secondary = TerracottaDark,
    onSecondary = OnTerracottaDark,
    secondaryContainer = TerracottaContainerDark,
    onSecondaryContainer = OnTerracottaContainerDark,
    tertiary = OchreDark,
    onTertiary = OnOchreDark,
    tertiaryContainer = OchreContainerDark,
    onTertiaryContainer = OnOchreContainerDark,
    background = DarkBotanicalBackground,
    onBackground = OnDarkSurface,
    surface = DarkBotanicalSurface,
    onSurface = OnDarkSurface,
    surfaceVariant = DarkBotanicalSurfaceVariant,
    onSurfaceVariant = OnDarkSurfaceVariant,
    outline = OutlineWarmDark,
    outlineVariant = OutlineVariantWarmDark,
    error = ErrorRedDark,
    onError = OnForestGreenLight,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark
)

private val BotanicalLightColorScheme = lightColorScheme(
    primary = ForestGreenLight,
    onPrimary = OnForestGreenLight,
    primaryContainer = ForestGreenContainerLight,
    onPrimaryContainer = OnForestGreenContainerLight,
    secondary = TerracottaLight,
    onSecondary = OnTerracottaLight,
    secondaryContainer = TerracottaContainerLight,
    onSecondaryContainer = OnTerracottaContainerLight,
    tertiary = OchreLight,
    onTertiary = OnOchreLight,
    tertiaryContainer = OchreContainerLight,
    onTertiaryContainer = OnOchreContainerLight,
    background = WarmPaperBackgroundLight,
    onBackground = OnSurfaceDark,
    surface = SurfacePureWhiteLight,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceWarmVariantLight,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineWarmLight,
    outlineVariant = OutlineVariantWarmLight,
    error = ErrorRed,
    onError = OnForestGreenLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight
)

@Composable
fun FamilyAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Preserve botanical brand identity while allowing dynamic color if explicitly desired
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> BotanicalDarkColorScheme
        else -> BotanicalLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
