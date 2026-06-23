package com.stellarbeats.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Generate a full Material3 dark color scheme from a single seed color.
 *
 * This is a simplified version of Material Color Utilities' HCT-based
 * tonal palettes. It uses HSL manipulation to produce a cohesive scheme
 * that works well for music player theming.
 *
 * The algorithm:
 * 1. Extract hue from the seed color
 * 2. Generate primary tones at standard M3 tonal elevations
 * 3. Shift hue slightly for secondary (+60°) and tertiary (-30°)
 * 4. Derive neutral/neutral-variant from desaturated primary
 * 5. Compute surface colors from neutral palette
 *
 * This avoids adding the ~2MB material-color-utilities library.
 */
object DynamicColor {

    /**
     * Generate a dark-theme ColorScheme from a dominant color.
     *
     * @param dominant The dominant color extracted from album art (ARGB int).
     * @return A [DynamicScheme] with all M3 color roles filled.
     */
    fun fromColor(dominant: Int): DynamicScheme {
        val seedColor = Color(dominant)
        val hsl = seedColor.toHsl()

        return DynamicScheme(
            primary = tone(hsl, 80),
            onPrimary = tone(hsl, 20),
            primaryContainer = tone(hsl, 30),
            onPrimaryContainer = tone(hsl, 90),

            secondary = tone(hsl.shiftHue(60f), 80),
            onSecondary = tone(hsl.shiftHue(60f), 20),
            secondaryContainer = tone(hsl.shiftHue(60f), 30),
            onSecondaryContainer = tone(hsl.shiftHue(60f), 90),

            tertiary = tone(hsl.shiftHue(-30f), 80),
            onTertiary = tone(hsl.shiftHue(-30f), 20),
            tertiaryContainer = tone(hsl.shiftHue(-30f), 30),
            onTertiaryContainer = tone(hsl.shiftHue(-30f), 90),

            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),

            background = Color(0xFF020202),
            onBackground = tone(hsl, 90),
            surface = Color(0xFF0A0A0A),
            onSurface = tone(hsl, 90),
            surfaceVariant = tone(hsl.desaturate(0.5f), 30),
            onSurfaceVariant = tone(hsl.desaturate(0.5f), 80),

            outline = tone(hsl.desaturate(0.5f), 60),
            outlineVariant = tone(hsl.desaturate(0.5f), 30),
            surfaceTint = tone(hsl, 80),
            inverseSurface = tone(hsl, 90),
            inverseOnSurface = Color(0xFF000000),
            inversePrimary = tone(hsl, 40),

            surfaceDim = Color(0xFF0A0A0A),
            surfaceBright = tone(hsl.desaturate(0.8f), 24),
            surfaceContainerLowest = Color(0xFF050505),
            surfaceContainerLow = tone(hsl.desaturate(0.8f), 10),
            surfaceContainer = tone(hsl.desaturate(0.8f), 12),
            surfaceContainerHigh = tone(hsl.desaturate(0.8f), 17),
            surfaceContainerHighest = tone(hsl.desaturate(0.8f), 22),

            seed = seedColor,
        )
    }

    /**
     * Generate a color at a specific tone (0=black, 100=white) from an HSL base.
     */
    private fun tone(hsl: Hsl, tone: Int): Color {
        val t = tone / 100f
        // In dark theme, we bias toward darker tones for surfaces
        // and lighter tones for on-color text
        val lightness = when {
            tone <= 10 -> t * 0.08f  // Very dark surfaces
            tone <= 30 -> 0.04f + (t - 0.1f) * 0.15f  // Dark containers
            tone <= 60 -> 0.10f + (t - 0.3f) * 0.30f  // Mid tones
            else -> 0.35f + (t - 0.6f) * 1.2f  // Light text/accent
        }

        return Color.hsl(
            hue = hsl.h,
            saturation = hsl.s * (0.3f + 0.7f * min(t * 2f, 1f)),
            lightness = lightness.coerceIn(0f, 1f),
        )
    }
}

/**
 * HSL representation. Hue: 0-360, Saturation: 0-1, Lightness: 0-1.
 */
data class Hsl(val h: Float, val s: Float, val l: Float)

fun Color.toHsl(): Hsl {
    val r = red
    val g = green
    val b = blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val l = (max + min) / 2f

    if (max == min) return Hsl(0f, 0f, l)

    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)

    val h = when (max) {
        r -> ((g - b) / d + if (g < b) 6f else 0f) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }

    return Hsl(h % 360f, s, l)
}

fun Hsl.shiftHue(degrees: Float): Hsl = Hsl((h + degrees + 360f) % 360f, s, l)

fun Hsl.desaturate(factor: Float): Hsl = Hsl(h, s * factor, l)

/**
 * Flat container for all Material3 color roles, used to construct
 * a real ColorScheme via the `.toMaterial3()` extension.
 */
data class DynamicScheme(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val onSecondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val tertiary: Color,
    val onTertiary: Color,
    val tertiaryContainer: Color,
    val onTertiaryContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val surfaceTint: Color,
    val inverseSurface: Color,
    val inverseOnSurface: Color,
    val inversePrimary: Color,
    val surfaceDim: Color,
    val surfaceBright: Color,
    val surfaceContainerLowest: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val seed: Color,
) {
    fun toMaterial3() = androidx.compose.material3.darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondaryContainer,
        onSecondaryContainer = onSecondaryContainer,
        tertiary = tertiary,
        onTertiary = onTertiary,
        tertiaryContainer = tertiaryContainer,
        onTertiaryContainer = onTertiaryContainer,
        error = error,
        onError = onError,
        errorContainer = errorContainer,
        onErrorContainer = onErrorContainer,
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        outline = outline,
        outlineVariant = outlineVariant,
        surfaceTint = surfaceTint,
        inverseSurface = inverseSurface,
        inverseOnSurface = inverseOnSurface,
        inversePrimary = inversePrimary,
        surfaceDim = surfaceDim,
        surfaceBright = surfaceBright,
        surfaceContainerLowest = surfaceContainerLowest,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
    )
}
