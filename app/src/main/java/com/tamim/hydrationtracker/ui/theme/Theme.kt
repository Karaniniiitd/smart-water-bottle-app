package com.tamim.hydrationtracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkScheme = darkColorScheme(
    primary = TealPrimaryDark,
    secondary = AmberWarning,
    tertiary = PurpleAccent,
    background = BgDark,
    surface = BgDark
)

private val LightScheme = lightColorScheme(
    primary = TealPrimary,
    secondary = AmberWarning,
    tertiary = PurpleAccent,
    background = BgLight,
    surface = BgLight
)

@Composable
fun HydrationTrackerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
