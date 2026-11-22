package app.nagham.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val NaghamDark = darkColorScheme(
    primary = Color(0xFF9D8FFF),
    onPrimary = Color(0xFF14102E),
    primaryContainer = Color(0xFF2A2557),
    onPrimaryContainer = Color(0xFFE2DEFF),
    secondary = Color(0xFF2DD4BF),
    onSecondary = Color(0xFF00201C),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFECEEF3),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFECEEF3),
    surfaceVariant = Color(0xFF1B1E26),
    onSurfaceVariant = Color(0xFFA6ABBA),
    surfaceContainer = Color(0xFF151820),
    surfaceContainerHigh = Color(0xFF1B1E26),
    error = Color(0xFFFF8A80),
)

@Composable
fun NaghamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NaghamDark, content = content)
}
