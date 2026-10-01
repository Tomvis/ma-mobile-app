package io.music_assistant.client.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.rubik_vf
import org.jetbrains.compose.resources.Font

// Home theme (HW-48), fork-only. Tokens: Tomvis/homelab-stacks theme/dist/tokens.resolved.json.
// Slate = structure, mist = surface; cyan ("lit") is reserved for "on right now", so no role uses it.
// Hooked into AppTheme with two lines so upstream's Color.kt/Theme.kt merge cleanly.

private val homeLightScheme = lightColorScheme(
    primary = Color(0xFF466A77),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBAD2DE),
    onPrimaryContainer = Color(0xFF16222A),
    secondary = Color(0xFF34505A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE6E8),
    onSecondaryContainer = Color(0xFF16222A),
    tertiary = Color(0xFFB0233D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFDCE6E8),
    onTertiaryContainer = Color(0xFF16222A),
    error = Color(0xFFB0233D),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFDCE6E8),
    onErrorContainer = Color(0xFFB0233D),
    background = Color(0xFFEAF0F0),
    onBackground = Color(0xFF16222A),
    surface = Color(0xFFEAF0F0),
    onSurface = Color(0xFF16222A),
    surfaceVariant = Color(0xFFDCE6E8),
    onSurfaceVariant = Color(0xFF34505A),
    surfaceTint = Color(0xFF466A77),
    outline = Color(0xFF587E8D),
    outlineVariant = Color(0xFFBAD2DE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF16222A),
    inverseOnSurface = Color(0xFFEAF0F0),
    inversePrimary = Color(0xFF8DB0BD),
    surfaceDim = Color(0xFFDCE6E8),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFA),
    surfaceContainer = Color(0xFFF1F5F5),
    surfaceContainerHigh = Color(0xFFE6EDEE),
    surfaceContainerHighest = Color(0xFFDCE6E8),
)

private val homeDarkScheme = darkColorScheme(
    primary = Color(0xFF8DB0BD),
    onPrimary = Color(0xFF16222A),
    primaryContainer = Color(0xFF3A525D),
    onPrimaryContainer = Color(0xFFEAF0F0),
    secondary = Color(0xFFBAD2DE),
    onSecondary = Color(0xFF16222A),
    secondaryContainer = Color(0xFF283B45),
    onSecondaryContainer = Color(0xFFEAF0F0),
    tertiary = Color(0xFFFF8AA0),
    onTertiary = Color(0xFF16222A),
    tertiaryContainer = Color(0xFF283B45),
    onTertiaryContainer = Color(0xFFEAF0F0),
    error = Color(0xFFFF8AA0),
    onError = Color(0xFF16222A),
    errorContainer = Color(0xFF283B45),
    onErrorContainer = Color(0xFFFF8AA0),
    background = Color(0xFF16222A),
    onBackground = Color(0xFFEAF0F0),
    surface = Color(0xFF16222A),
    onSurface = Color(0xFFEAF0F0),
    surfaceVariant = Color(0xFF283B45),
    onSurfaceVariant = Color(0xFFBAD2DE),
    surfaceTint = Color(0xFF8DB0BD),
    outline = Color(0xFF6E8F9B),
    outlineVariant = Color(0xFF3A525D),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFEAF0F0),
    inverseOnSurface = Color(0xFF16222A),
    inversePrimary = Color(0xFF466A77),
    surfaceDim = Color(0xFF16222A),
    surfaceBright = Color(0xFF283B45),
    surfaceContainerLowest = Color(0xFF111B21),
    surfaceContainerLow = Color(0xFF1A2830),
    surfaceContainer = Color(0xFF1F2F38),
    surfaceContainerHigh = Color(0xFF24353F),
    surfaceContainerHighest = Color(0xFF283B45),
)

/** The home palette in place of upstream's scheme (receiver kept so the call site stays a one-liner). */
@Suppress("UnusedReceiverParameter")
internal fun ColorScheme.withHomeColors(darkTheme: Boolean): ColorScheme =
    if (darkTheme) homeDarkScheme else homeLightScheme

/** Material 3 type scale in Rubik (bundled variable font) with tabular figures. */
@Composable
internal fun homeTypography(): Typography {
    val rubik = FontFamily(
        Font(Res.font.rubik_vf, FontWeight.Light),
        Font(Res.font.rubik_vf, FontWeight.Normal),
        Font(Res.font.rubik_vf, FontWeight.Medium),
        Font(Res.font.rubik_vf, FontWeight.SemiBold),
        Font(Res.font.rubik_vf, FontWeight.Bold),
    )
    return remember(rubik) {
        val base = Typography()
        fun TextStyle.home() = copy(fontFamily = rubik, fontFeatureSettings = "tnum")
        base.copy(
            displayLarge = base.displayLarge.home(),
            displayMedium = base.displayMedium.home(),
            displaySmall = base.displaySmall.home(),
            headlineLarge = base.headlineLarge.home(),
            headlineMedium = base.headlineMedium.home(),
            headlineSmall = base.headlineSmall.home(),
            titleLarge = base.titleLarge.home(),
            titleMedium = base.titleMedium.home(),
            titleSmall = base.titleSmall.home(),
            bodyLarge = base.bodyLarge.home(),
            bodyMedium = base.bodyMedium.home(),
            bodySmall = base.bodySmall.home(),
            labelLarge = base.labelLarge.home(),
            labelMedium = base.labelMedium.home(),
            labelSmall = base.labelSmall.home(),
        )
    }
}
