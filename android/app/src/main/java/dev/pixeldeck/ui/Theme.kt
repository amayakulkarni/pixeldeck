package dev.pixeldeck.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Magenta = Color(0xFFFF267C)
val PanelBg = Color(0xFF0E0E0E)
val PanelEdge = Color(0xFF242424)
val Muted = Color(0xFF9A9A9A)
val Good = Color(0xFF3DDC84)
val Bad = Color(0xFFFF5252)

private val Scheme = darkColorScheme(
    primary = Magenta,
    onPrimary = Color.White,
    secondary = Color(0xFFFF7AAE),
    background = Color.Black,
    onBackground = Color.White,
    surface = PanelBg,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Muted,
    outline = PanelEdge,
    error = Bad,
)

@Composable
fun PixelDeckTheme(content: @Composable () -> Unit) {
    // Pure black background: on the Pixel 8's OLED panel unlit pixels are off, which saves power and burn-in.
    MaterialTheme(colorScheme = Scheme, typography = Typography(), content = content)
}
