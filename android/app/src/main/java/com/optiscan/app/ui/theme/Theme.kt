package com.optiscan.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val OptiColorScheme = darkColorScheme(
    primary = ActionPrimaryBlue,
    onPrimary = TextWhite,
    primaryContainer = ActionPrimaryBlueHover,
    onPrimaryContainer = TextWhite,
    secondary = ActionSecondaryGreen,
    onSecondary = TextWhite,
    secondaryContainer = ActionSecondaryGreenHover,
    onSecondaryContainer = TextWhite,
    background = BackgroundNearBlack,
    onBackground = TextWhite,
    surface = SurfaceDarkCharcoal,
    onSurface = TextWhite,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextLightGrey,
    outline = BorderSubtle,
    error = AlertError,
    onError = TextWhite
)

@Composable
fun OptiScanTheme(
    textScale: Float = 1.0f,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = OptiColorScheme,
        typography = createOptiTypography(textScale),
        content = content
    )
}
