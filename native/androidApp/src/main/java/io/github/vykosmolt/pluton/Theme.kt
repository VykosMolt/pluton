package io.github.vykosmolt.pluton

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The web app's palette, kept so the native app feels like Pluton rather than a default Material demo.
private val Light = lightColorScheme(
    primary = Color(0xFF3D5AFE),
    onPrimary = Color.White,
    secondary = Color(0xFF12B76A),
    tertiary = Color(0xFFFF8A00),
    error = Color(0xFFFF4F64),
    background = Color(0xFFFFF8EF),
    surface = Color.White,
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8091FF),
    onPrimary = Color(0xFF14122B),
    secondary = Color(0xFF3DDC97),
    tertiary = Color(0xFFFFA94D),
    error = Color(0xFFFF6B7D),
    background = Color(0xFF14122B),
    surface = Color(0xFF1F1C3D),
)

@Composable
fun PlutonTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
