package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import home.theme.HomeTheme
import home.theme.HomeThemes
import io.music_assistant.client.ui.theme.HomeMode
import io.music_assistant.client.ui.theme.HomeSource
import io.music_assistant.client.ui.theme.HomeThemeRepository
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.home_theme_follow
import musicassistantclient.composeapp.generated.resources.home_theme_hint
import musicassistantclient.composeapp.generated.resources.home_theme_mode_automatic
import musicassistantclient.composeapp.generated.resources.home_theme_mode_dark
import musicassistantclient.composeapp.generated.resources.home_theme_mode_light
import musicassistantclient.composeapp.generated.resources.home_theme_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Fork-only (HW-65): "Follow home theme" or any home theme for this MA user, stored server-side like the
 * web UI's choice. Light/dark stays on upstream's sun/moon toggle, which writes the same choice.
 * Hidden on servers without the home_theme provider.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeThemeSection(repository: HomeThemeRepository = koinInject()) {
    val state by repository.state.collectAsStateWithLifecycle()
    val effective by repository.effective.collectAsStateWithLifecycle()
    val shown = effective?.takeIf { state != null } ?: return
    val claim = state?.claim
    val home = HomeThemes.byId(claim?.theme)
    val homeMode = HomeMode.fromWire(claim?.mode) ?: HomeMode.Automatic
    val follow = stringResource(Res.string.home_theme_follow, home.name, homeMode.label())
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)

    SectionCard {
        SectionTitle(stringResource(Res.string.home_theme_title))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, shape)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val current = HomeThemes.byId(shown.theme)
                Swatch(current)
                Text(
                    text = if (shown.source == HomeSource.App) current.name else follow,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    leadingIcon = { Swatch(home) },
                    text = { Text(follow, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        repository.choose(null, shown.mode)
                        expanded = false
                    },
                )
                HomeThemes.all.forEach { theme ->
                    DropdownMenuItem(
                        leadingIcon = { Swatch(theme) },
                        text = { Text(theme.name) },
                        onClick = {
                            repository.choose(theme.id, shown.mode)
                            expanded = false
                        },
                    )
                }
            }
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(Res.string.home_theme_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Light background with its primary, then the dark pair: the theme at a glance in either mode. */
@Composable
private fun Swatch(theme: HomeTheme) {
    Row(modifier = Modifier.padding(end = 12.dp)) {
        listOf(theme.light.background, theme.light.primary, theme.dark.background, theme.dark.primary)
            .forEach { argb ->
                Spacer(
                    Modifier
                        .size(14.dp)
                        .background(Color(argb), CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
    }
}

@Composable
private fun HomeMode.label(): String = stringResource(
    when (this) {
        HomeMode.Automatic -> Res.string.home_theme_mode_automatic
        HomeMode.Light -> Res.string.home_theme_mode_light
        HomeMode.Dark -> Res.string.home_theme_mode_dark
    },
)
