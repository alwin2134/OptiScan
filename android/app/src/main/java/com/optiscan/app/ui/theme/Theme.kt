package com.optiscan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HighContrastColorScheme = darkColorScheme(
    primary = YellowAction,
    onPrimary = DarkActionText,
    secondary = CyanAction,
    onSecondary = WhiteActionText,
    background = DarkBackground,
    onBackground = LightText,
    surface = DarkSurface,
    onSurface = LightText,
    error = ErrorRed,
    onError = LightText
)

@Composable
fun OptiScanTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HighContrastColorScheme,
        typography = OptiTypography,
        content = content
    )
}
