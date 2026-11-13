package com.example.puzzlecounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val HighEndDarkColorScheme = darkColorScheme(
    primary = CyberBlue,
    onPrimary = TextPureWhite,
    primaryContainer = CyberBlueVariant,
    onPrimaryContainer = TextSoftWhite,

    secondary = EmeraldSuccess,
    onSecondary = DeepNight,
    secondaryContainer = EmeraldSuccess.copy(alpha = 0.2f),
    onSecondaryContainer = EmeraldSuccess,

    tertiary = WarmGoldWarning,
    onTertiary = DeepNight,
    tertiaryContainer = WarmGoldWarning.copy(alpha = 0.2f),
    onTertiaryContainer = WarmGoldWarning,

    error = CoralError,
    onError = TextPureWhite,
    errorContainer = CoralError.copy(alpha = 0.2f),
    onErrorContainer = CoralError,

    background = DeepNight,
    onBackground = TextPureWhite,

    surface = SlateSurface,
    onSurface = TextSoftWhite,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextMutedGray,

    outline = HairlineBorder,
    outlineVariant = HairlineBorder.copy(alpha = 0.5f)
)

/** The app is always dark: better contrast for the scanner look. */
@Composable
fun PuzzleCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HighEndDarkColorScheme, content = content)
}
