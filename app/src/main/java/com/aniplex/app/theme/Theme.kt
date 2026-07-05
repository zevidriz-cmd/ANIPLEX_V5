package com.aniplex.app.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // badges
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),    // cards
    large = RoundedCornerShape(12.dp),     // dialogs / menus
    extraLarge = RoundedCornerShape(12.dp) // buttons
)

private val DarkColorScheme = darkColorScheme(
    primary = CrunchyrollOrange,
    secondary = CrunchyrollOrange,
    background = BackgroundVoid,
    surface = SurfaceDark,
    surfaceVariant = SurfaceDarkVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    error = ErrorColor
)

@Composable
fun ANIPLEXTheme(
    accentColor: Color = CrunchyrollOrange,
    content: @Composable () -> Unit
) {
    val dynamicColorScheme = darkColorScheme(
        primary = accentColor,
        secondary = accentColor,
        background = BackgroundVoid,
        surface = SurfaceDark,
        surfaceVariant = SurfaceDarkVariant,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = TextSecondary,
        error = ErrorColor
    )

    MaterialTheme(
        colorScheme = dynamicColorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
