package com.wavebox.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val LightScheme = lightColorScheme(
    primary = Color(0xFF6C5CD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE5DFFF),
    onPrimaryContainer = Color(0xFF221A5C),
    secondary = Color(0xFFC2669A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD9EC),
    onSecondaryContainer = Color(0xFF4A1A36),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF241F30),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF241F30),
    onSurfaceVariant = Color(0xFF5E5870),
    surfaceVariant = Color(0xFFEAE4F5)
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFC9BFFF),
    onPrimary = Color(0xFF2B1F7A),
    primaryContainer = Color(0xFF4B3FA6),
    onPrimaryContainer = Color(0xFFE8E2FF),
    secondary = Color(0xFFFFB0DA),
    onSecondary = Color(0xFF4A1A36),
    secondaryContainer = Color(0xFF6A3055),
    onSecondaryContainer = Color(0xFFFFD9EC),
    background = Color(0xFF14111C),
    onBackground = Color(0xFFEAE4F7),
    surface = Color(0xFF211C2E),
    onSurface = Color(0xFFEAE4F7),
    onSurfaceVariant = Color(0xFFB9B2CC),
    surfaceVariant = Color(0xFF3A3450)
)

@Composable
fun WaveboxTheme(mode: Int = 0, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val scheme = if (dark) DarkScheme else LightScheme
    MaterialTheme(colorScheme = scheme) {
        // Default text colour for everything drawn outside a Surface (fixes black-on-dark text)
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

private val LightPalettes = listOf(
    listOf(Color(0xFFFFE0EA), Color(0xFFE6E0FF), Color(0xFFD8F0FF)),
    listOf(Color(0xFFFFEFD6), Color(0xFFFFD9E8), Color(0xFFE0E8FF)),
    listOf(Color(0xFFD8FFEE), Color(0xFFDCEFFF), Color(0xFFF1E0FF)),
    listOf(Color(0xFFFFF6D2), Color(0xFFFFE0D6), Color(0xFFE8DCFF)),
    listOf(Color(0xFFDFF6FF), Color(0xFFE6FFDF), Color(0xFFFFDFF3)),
    listOf(Color(0xFFF3E0FF), Color(0xFFFFE3E0), Color(0xFFDFFAFF))
)

private val DarkPalettes = listOf(
    listOf(Color(0xFF2D1E3D), Color(0xFF1E2748), Color(0xFF3A1F3F)),
    listOf(Color(0xFF3A2A2A), Color(0xFF3A1F36), Color(0xFF1F2744)),
    listOf(Color(0xFF173A32), Color(0xFF1B2D44), Color(0xFF30214A)),
    listOf(Color(0xFF3A3420), Color(0xFF3A241F), Color(0xFF2B2244)),
    listOf(Color(0xFF173544), Color(0xFF1F3A25), Color(0xFF3F2038)),
    listOf(Color(0xFF332044), Color(0xFF402322), Color(0xFF173A40))
)

/** A named gradient the user can pick in Settings. */
class GradientStyle(val name: String, val light: List<Color>, val dark: List<Color>)

/** Settings value 0 = "Auto" (changes with the playing song); value n = GradientStyles[n - 1]. */
val GradientStyles = listOf(
    GradientStyle("Aurora",
        listOf(Color(0xFFD6FFF0), Color(0xFFD9E6FF), Color(0xFFEBDDFF)),
        listOf(Color(0xFF0F3D3A), Color(0xFF1A2F5C), Color(0xFF3A1F5C))),
    GradientStyle("Sunset",
        listOf(Color(0xFFFFE3C2), Color(0xFFFFC9C9), Color(0xFFFFD6EC)),
        listOf(Color(0xFF4A2410), Color(0xFF4A1A2A), Color(0xFF3A1A4A))),
    GradientStyle("Ocean",
        listOf(Color(0xFFD2F1FF), Color(0xFFCFE0FF), Color(0xFFD6F7F5)),
        listOf(Color(0xFF0B2F4A), Color(0xFF10285C), Color(0xFF0B3F40))),
    GradientStyle("Mint",
        listOf(Color(0xFFDDFBEA), Color(0xFFE6FFF3), Color(0xFFD6F5F0)),
        listOf(Color(0xFF11382A), Color(0xFF0F2F2F), Color(0xFF1A3A22))),
    GradientStyle("Rose",
        listOf(Color(0xFFFFE0EA), Color(0xFFFFD1DC), Color(0xFFFFEBF1)),
        listOf(Color(0xFF3F1A2A), Color(0xFF4A1F33), Color(0xFF2D1523))),
    GradientStyle("Dusk",
        listOf(Color(0xFFE6DDFF), Color(0xFFFFD9E8), Color(0xFFFFE6D1)),
        listOf(Color(0xFF2A2050), Color(0xFF4A2450), Color(0xFF4A2A2A))),
    GradientStyle("Lavender",
        listOf(Color(0xFFEBE0FF), Color(0xFFE0E6FF), Color(0xFFF6E0FF)),
        listOf(Color(0xFF2B2050), Color(0xFF1F2850), Color(0xFF40205A))),
    GradientStyle("Peach",
        listOf(Color(0xFFFFE8D6), Color(0xFFFFF0D6), Color(0xFFFFDDDD)),
        listOf(Color(0xFF4A2E1A), Color(0xFF4A3A1A), Color(0xFF4A2424))),
    GradientStyle("Midnight",
        listOf(Color(0xFFD9E1F2), Color(0xFFE4E8F5), Color(0xFFDDE0EE)),
        listOf(Color(0xFF0A1020), Color(0xFF141B38), Color(0xFF0D0D1F)))
)

/** Colours used for the little preview circle in Settings (style 0 = Auto). */
@Composable
fun gradientPreview(style: Int): List<Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val g = GradientStyles.getOrNull(style - 1) ?: return listOf(Color(0xFFFFB3C7), Color(0xFFC9BFFF), Color(0xFF9ED8FF))
    return if (dark) g.dark else g.light
}

/** Soft gradient that drifts slowly. [style] 0 = Auto (palette follows [seed], i.e. the song); 1.. = a fixed named gradient. */
@Composable
fun GradientBackground(
    seed: String,
    animate: Boolean,
    style: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val palettes = if (isDark) DarkPalettes else LightPalettes
    val fixed = GradientStyles.getOrNull(style - 1)
    val p = if (fixed != null) (if (isDark) fixed.dark else fixed.light)
    else palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
    val c1 by animateColorAsState(p[0], tween(1800), label = "c1")
    val c2 by animateColorAsState(p[1], tween(1800), label = "c2")
    val c3 by animateColorAsState(p[2], tween(1800), label = "c3")
    val t = rememberInfiniteTransition(label = "bg")
    val a by t.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a"
    )
    val b by t.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "b"
    )
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val k = if (animate) a else 0.5f
                val k2 = if (animate) b else 0.5f
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(c1, c2, c3),
                        start = Offset(w * k2 * 0.7f, 0f),
                        end = Offset(w * (1f - k * 0.6f), h)
                    )
                )
                val c = Offset(w * k, h * 0.22f)
                drawCircle(
                    Brush.radialGradient(listOf(c3.copy(alpha = 0.65f), Color.Transparent), center = c, radius = w * 0.9f),
                    radius = w * 0.9f, center = c
                )
                val d = Offset(w * (1f - k2), h * 0.82f)
                drawCircle(
                    Brush.radialGradient(listOf(c2.copy(alpha = 0.65f), Color.Transparent), center = d, radius = w * 0.9f),
                    radius = w * 0.9f, center = d
                )
            },
        content = content
    )
}
