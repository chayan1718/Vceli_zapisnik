package cz.vcely.zapisnik.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Honey = Color(0xFFF2A900)
private val HoneyDark = Color(0xFFB07800)
private val Brown = Color(0xFF3E2723)

private val LightColors = lightColorScheme(
    primary = Honey,
    onPrimary = Color.White,
    secondary = HoneyDark,
    background = Color(0xFFFFFBF3),
    surface = Color(0xFFFFF6E6),
)

private val DarkColors = darkColorScheme(
    primary = Honey,
    onPrimary = Brown,
    secondary = HoneyDark,
)

@Composable
fun VceliTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
