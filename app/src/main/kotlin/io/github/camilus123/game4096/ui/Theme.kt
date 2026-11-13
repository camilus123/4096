package io.github.camilus123.game4096.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8F7A66),
    onPrimary = Color(0xFFFBF7F0),
    secondary = Color(0xFFF2603F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF7EE),
    onBackground = Color(0xFF746B62),
    surface = Color(0xFFFAF7EE),
    onSurface = Color(0xFF746B62),
    surfaceVariant = Color(0xFFB9AB9D),
    onSurfaceVariant = Color(0xFFFBF7F0),
    outline = Color(0xFFB9AB9D),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00BFA6),
    onSecondary = Color(0xFF0C0E19),
    background = Color(0xFF0C0E19),
    onBackground = Color(0xFFE6EAFF),
    surface = Color(0xFF0C0E19),
    onSurface = Color(0xFFE6EAFF),
    surfaceVariant = Color(0xFF20253D),
    onSurfaceVariant = Color(0xFFE6EAFF),
    outline = Color(0xFF37427F),
)

@Composable
fun GameTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
