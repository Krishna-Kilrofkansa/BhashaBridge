package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElegantHeaderBlue,
    onPrimary = ElegantSwitchText,
    secondary = ElegantOutputText,
    onSecondary = ElegantOutputCardBg,
    background = ElegantDarkBg,
    onBackground = ElegantTextMain,
    surface = ElegantInputCardBg,
    onSurface = ElegantInputText,
    surfaceVariant = ElegantSelectionGray,
    onSurfaceVariant = ElegantTextMain,
    tertiary = ElegantOutputBorder,
    outline = ElegantInputBorder
)

// Since 'Elegant Dark' is the designated design, we also map the LightColorScheme
// to Elegant Dark to ensure consistent premium dark branding.
private val LightColorScheme = darkColorScheme(
    primary = ElegantHeaderBlue,
    onPrimary = ElegantSwitchText,
    secondary = ElegantOutputText,
    onSecondary = ElegantOutputCardBg,
    background = ElegantDarkBg,
    onBackground = ElegantTextMain,
    surface = ElegantInputCardBg,
    onSurface = ElegantInputText,
    surfaceVariant = ElegantSelectionGray,
    onSurfaceVariant = ElegantTextMain,
    tertiary = ElegantOutputBorder,
    outline = ElegantInputBorder
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Elegant Dark theme for consistent aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
