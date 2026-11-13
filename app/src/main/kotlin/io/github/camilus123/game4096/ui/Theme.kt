package io.github.camilus123.game4096.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF9C7654),
    onPrimary = Color(0xFFFFFAF2),
    secondary = Color(0xFFF84526),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAD6BA),
    onSecondaryContainer = Color(0xFF6E5F52),
    background = Color(0xFFFCF4E2),
    onBackground = Color(0xFF6E5F52),
    surface = Color(0xFFFCF4E2),
    onSurface = Color(0xFF6E5F52),
    surfaceVariant = Color(0xFFC2A68A),
    onSurfaceVariant = Color(0xFFFFFAF2),
    outline = Color(0xFFC2A68A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF00BFA6),
    onSecondary = Color(0xFF0C0E19),
    secondaryContainer = Color(0xFF2C3460),
    onSecondaryContainer = Color(0xFFE6EAFF),
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

/** Outlined button whose label is always readable on the screen background. */
@Composable
fun QuietButton(
    onClick: () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onBackground,
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        content = content,
    )
}
