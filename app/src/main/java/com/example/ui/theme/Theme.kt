package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val MidnightCinemaColorScheme = darkColorScheme(
    primary = RoyalVioletAccent,
    onPrimary = MidnightBg,
    primaryContainer = MidnightSecondaryBg,
    onPrimaryContainer = PrimaryWhiteText,
    secondary = OttomanGoldAccent,
    onSecondary = MidnightBg,
    secondaryContainer = MidnightElevatedCard,
    onSecondaryContainer = OttomanGoldAccent,
    tertiary = RoyalVioletDim,
    onTertiary = PrimaryWhiteText,
    background = MidnightBg,
    onBackground = PrimaryWhiteText,
    surface = MidnightCardSurface,
    onSurface = PrimaryWhiteText,
    surfaceVariant = MidnightElevatedCard,
    onSurfaceVariant = SecondaryMutedText,
    outline = MidnightBorder,
    error = CrimsonAlert,
    onError = PrimaryWhiteText
)

val MidnightShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = MidnightCinemaColorScheme,
        typography = Typography,
        shapes = MidnightShapes,
        content = content
    )
}
