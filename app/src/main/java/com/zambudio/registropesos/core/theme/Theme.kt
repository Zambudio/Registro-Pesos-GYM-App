package com.zambudio.registropesos.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Steel40,
    onPrimary = Slate99,
    primaryContainer = Steel90,
    onPrimaryContainer = Steel10,
    secondary = Amber40,
    onSecondary = Slate99,
    secondaryContainer = Amber90,
    onSecondaryContainer = Amber30,
    background = Slate99,
    onBackground = Slate10,
    surface = Slate99,
    onSurface = Slate10,
    surfaceVariant = Slate90,
    onSurfaceVariant = Slate30,
    surfaceContainer = Slate95,
    outline = Slate50,
    error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Steel80,
    onPrimary = Steel20,
    primaryContainer = Steel30,
    onPrimaryContainer = Steel90,
    secondary = Amber80,
    onSecondary = Amber30,
    secondaryContainer = Amber40,
    onSecondaryContainer = Amber90,
    background = Slate10,
    onBackground = Slate90,
    surface = Slate10,
    onSurface = Slate90,
    surfaceVariant = Slate30,
    onSurfaceVariant = Slate80,
    surfaceContainer = Slate20,
    outline = Slate50,
    error = Red80,
)

/** Tema de la app. Color dinámico desactivado a propósito: identidad visual estable. */
@Composable
fun GymTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
