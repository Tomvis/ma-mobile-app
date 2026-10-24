package io.music_assistant.client.ui.compose.settings

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.ui.language.AppLocales
import io.music_assistant.client.ui.language.HomeLanguageRepository
import io.music_assistant.client.ui.language.LanguagePick
import io.music_assistant.client.ui.language.LanguageSource
import io.music_assistant.client.ui.language.matchShipped
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.home_language_device
import musicassistantclient.composeapp.generated.resources.home_language_follow
import musicassistantclient.composeapp.generated.resources.home_language_hint
import musicassistantclient.composeapp.generated.resources.home_language_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Fork-only (HW-191): "Follow home language", "Match device" or any language this build ships,
 * for this MA user; a language pick is stored server-side like the web UI's, "Match device" on
 * this device only. Hidden on servers without home_language and where the app's language can't be set.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeLanguageSection(repository: HomeLanguageRepository = koinInject()) {
    val state by repository.state.collectAsStateWithLifecycle()
    val effective by repository.effective.collectAsStateWithLifecycle()
    val current = state ?: return
    val shown = effective ?: return
    val shipped = remember { AppLocales.shipped().sortedBy { AppLocales.nativeName(it).lowercase() } }
    val home = current.claim?.let { AppLocales.nativeName(matchShipped(it.language, shipped)) }
    val follow = stringResource(Res.string.home_language_follow, home ?: AppLocales.nativeName(AppLocales.device()))
    val device = stringResource(Res.string.home_language_device, AppLocales.nativeName(AppLocales.device()))
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        SectionTitle(stringResource(Res.string.home_language_title))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when (shown.source) {
                        LanguageSource.App -> shown.tag?.let(AppLocales::nativeName) ?: follow
                        LanguageSource.Device -> if (current.claim == null) follow else device
                        LanguageSource.Home -> follow
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded)
            }
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                val pick = { choice: LanguagePick ->
                    repository.choose(choice)
                    expanded = false
                }
                DropdownMenuItem(text = { Text(follow) }, onClick = { pick(LanguagePick.Follow) })
                DropdownMenuItem(text = { Text(device) }, onClick = { pick(LanguagePick.Device) })
                shipped.forEach { tag ->
                    DropdownMenuItem(
                        text = { Text(AppLocales.nativeName(tag)) },
                        onClick = { pick(LanguagePick.Language(tag)) },
                    )
                }
            }
        }
        Text(
            modifier = Modifier.padding(top = 8.dp),
            text = stringResource(Res.string.home_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
