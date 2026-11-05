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

/** Soft pastel gradient that drifts slowly and cross-fades to a new palette when [seed] changes. */
@Composable
fun GradientBackground(
    seed: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val palettes = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) DarkPalettes else LightPalettes
    val p = palettes[(seed.hashCode() and Int.MAX_VALUE) % palettes.size]
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
