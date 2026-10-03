package io.music_assistant.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.Color
import home.theme.HomeScheme
import home.theme.HomeThemes
import kotlinx.coroutines.flow.MutableStateFlow

// Home theme (HW-48, per person since HW-65), fork-only. Colors: home/theme/HomeThemes.kt, vendored from
// Tomvis/homelab-stacks theme/dist/android (M3 roles per theme x mode; cyan "lit" is in no role, and
// tertiary = primary so upstream's favoriteTint shows a lasting choice, not an alarm).
// Hooked into AppTheme with one line so upstream's Color.kt/Theme.kt merge cleanly.

/** The theme id to paint; [HomeThemeRepository] sets it. Global so previews and AppTheme need no DI. */
internal val homeThemeId = MutableStateFlow(HomeThemes.DEFAULT)

internal fun HomeScheme.toColorScheme(dark: Boolean): ColorScheme =
    if (dark) {
        darkColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            surfaceBright = Color(surfaceBright),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            surfaceDim = Color(surfaceDim),
        )
    } else {
        lightColorScheme(
            primary = Color(primary),
            onPrimary = Color(onPrimary),
            primaryContainer = Color(primaryContainer),
            onPrimaryContainer = Color(onPrimaryContainer),
            inversePrimary = Color(inversePrimary),
            secondary = Color(secondary),
            onSecondary = Color(onSecondary),
            secondaryContainer = Color(secondaryContainer),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiary),
            onTertiary = Color(onTertiary),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiaryContainer = Color(onTertiaryContainer),
            background = Color(background),
            onBackground = Color(onBackground),
            surface = Color(surface),
            onSurface = Color(onSurface),
            surfaceVariant = Color(surfaceVariant),
            onSurfaceVariant = Color(onSurfaceVariant),
            surfaceTint = Color(surfaceTint),
            inverseSurface = Color(inverseSurface),
            inverseOnSurface = Color(inverseOnSurface),
            error = Color(error),
            onError = Color(onError),
            errorContainer = Color(errorContainer),
            onErrorContainer = Color(onErrorContainer),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            scrim = Color(scrim),
            surfaceBright = Color(surfaceBright),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            surfaceContainerHighest = Color(surfaceContainerHighest),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainerLowest = Color(surfaceContainerLowest),
            surfaceDim = Color(surfaceDim),
        )
    }

/** The person's home palette in place of upstream's scheme (receiver kept so the call site stays a one-liner). */
@Suppress("UnusedReceiverParameter")
@Composable
internal fun ColorScheme.withHomeColors(darkTheme: Boolean): ColorScheme {
    val theme = HomeThemes.byId(homeThemeId.collectAsState().value)
    return (if (darkTheme) theme.dark else theme.light).toColorScheme(darkTheme)
}
