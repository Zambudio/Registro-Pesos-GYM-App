package com.zambudio.registropesos.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Orange40,
    onPrimary = Graphite98,
    primaryContainer = Orange90,
    onPrimaryContainer = Orange10,
    secondary = Graphite30,
    onSecondary = Graphite98,
    secondaryContainer = Graphite90,
    onSecondaryContainer = Graphite20,
    tertiary = Gold40,
    onTertiary = Graphite98,
    tertiaryContainer = Gold90,
    onTertiaryContainer = Gold10,
    background = Graphite96,
    onBackground = Graphite10,
    surface = Graphite96,
    onSurface = Graphite10,
    surfaceVariant = Graphite91,
    onSurfaceVariant = Graphite50,
    surfaceTint = Orange40,
    surfaceBright = Graphite98,
    surfaceDim = Graphite85,
    surfaceContainerLowest = Graphite98,
    // Las tarjetas (GymCard) usan surfaceContainerLow: blancas sobre el gris del fondo.
    surfaceContainerLow = White,
    surfaceContainer = Graphite94,
    surfaceContainerHigh = Graphite92,
    surfaceContainerHighest = Graphite88,
    outline = Graphite60,
    outlineVariant = Graphite85,
    error = Red40,
    onError = Graphite98,
    errorContainer = Red90,
    onErrorContainer = Red10,
)

private val DarkColors = darkColorScheme(
    primary = Orange80,
    onPrimary = Orange20,
    primaryContainer = Orange30,
    onPrimaryContainer = Orange90,
    secondary = Night80,
    onSecondary = Night17,
    secondaryContainer = Night20,
    onSecondaryContainer = Night90,
    tertiary = Gold80,
    onTertiary = Gold20,
    tertiaryContainer = Gold30,
    onTertiaryContainer = Gold95,
    background = Night6,
    onBackground = Night90,
    surface = Night6,
    onSurface = Night90,
    surfaceVariant = Night18,
    onSurfaceVariant = Night65,
    surfaceTint = Orange80,
    surfaceBright = Night17,
    surfaceDim = Night6,
    surfaceContainerLowest = Night4,
    surfaceContainerLow = Night9,
    surfaceContainer = Night11,
    surfaceContainerHigh = Night14,
    surfaceContainerHighest = Night17,
    outline = Night45,
    outlineVariant = Night21,
    error = Red80,
    onError = Red10,
    errorContainer = Red30,
    onErrorContainer = Red90,
)

private val GymShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * Tema de la app. El modo (sistema, claro u oscuro) lo decide el ajuste del usuario y lo resuelve
 * quien llama ([com.zambudio.registropesos.MainActivity]). Color dinámico desactivado a propósito:
 * identidad visual estable.
 */
@Composable
fun GymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GymTypography,
        shapes = GymShapes,
        content = content,
    )
}
