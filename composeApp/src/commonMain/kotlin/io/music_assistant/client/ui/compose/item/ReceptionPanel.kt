@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AmgDrInfo
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.DrInfo
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.DrSource
import io.music_assistant.client.data.model.client.LabelKind
import io.music_assistant.client.data.model.client.ParsedLabel
import io.music_assistant.client.data.model.client.ReceptionTags
import io.music_assistant.client.data.model.client.SourceTags
import io.music_assistant.client.data.model.client.formatDr
import io.music_assistant.client.data.model.client.formatScore
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.parseAlbumReception
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_fallback
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_reported
import musicassistantclient.composeapp.generated.resources.reception_dr_excellent
import musicassistantclient.composeapp.generated.resources.reception_dr_fair
import musicassistantclient.composeapp.generated.resources.reception_dr_good
import musicassistantclient.composeapp.generated.resources.reception_dr_poor
import musicassistantclient.composeapp.generated.resources.reception_dynamic_range
import musicassistantclient.composeapp.generated.resources.reception_label_aotm
import musicassistantclient.composeapp.generated.resources.reception_label_aoty
import musicassistantclient.composeapp.generated.resources.reception_label_honorable_mention
import musicassistantclient.composeapp.generated.resources.reception_label_record_of_the_month
import musicassistantclient.composeapp.generated.resources.reception_label_score_revised
import musicassistantclient.composeapp.generated.resources.reception_personal_pick
import musicassistantclient.composeapp.generated.resources.reception_score_with_max
import musicassistantclient.composeapp.generated.resources.reception_title
import musicassistantclient.composeapp.generated.resources.reception_type_lit
import musicassistantclient.composeapp.generated.resources.reception_type_review
import musicassistantclient.composeapp.generated.resources.reception_type_rfu
import musicassistantclient.composeapp.generated.resources.reception_type_sitf
import musicassistantclient.composeapp.generated.resources.reception_type_tymhm
import musicassistantclient.composeapp.generated.resources.reception_type_ymio
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumReceptionPanel(album: Album, modifier: Modifier = Modifier) {
    val tags = parseAlbumReception(
        album.metadata?.criticalReception,
        album.metadata?.dynamicRange,
    )
    if (!tags.hasAny) return
    ReceptionPanel(tags, modifier)
}

@Composable
private fun ReceptionPanel(tags: ReceptionTags, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(Res.string.reception_title),
                style = MaterialTheme.typography.titleMedium,
            )
            tags.dr?.let { DrRow(it, tags.amgDr) }
            tags.amg?.let { SourceRow(it) }
            tags.tps?.let { SourceRow(it) }
        }
    }
}

@Composable
private fun drVerdict(quality: DrQuality): String = stringResource(
    when (quality) {
        DrQuality.EXCELLENT -> Res.string.reception_dr_excellent
        DrQuality.GOOD -> Res.string.reception_dr_good
        DrQuality.FAIR -> Res.string.reception_dr_fair
        DrQuality.POOR -> Res.string.reception_dr_poor
    },
)

@Composable
private fun DrRow(dr: DrInfo, amgDr: AmgDrInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.reception_dynamic_range),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (dr.source == DrSource.AMG) {
                Text(
                    text = stringResource(Res.string.reception_amg_dr_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val verdict = drVerdict(dr.quality)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = formatDr(dr.value), style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { (dr.value / 20f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { stateDescription = "${formatDr(dr.value)} / 20 — $verdict" },
            )
            Text(text = verdict, style = MaterialTheme.typography.labelLarge)
        }
        amgDr?.let {
            Text(
                text = stringResource(Res.string.reception_amg_dr_reported, formatDr(it.value)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(s: SourceTags) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = s.source,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp),
            )
            if (s.rating != null) {
                Text(
                    text = stringResource(Res.string.reception_score_with_max, formatScore(s.rating), s.scale),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { (s.rating / s.scale).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { stateDescription = "${formatScore(s.rating)} / ${s.scale}" },
                )
            } else if (s.favorite) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.reception_personal_pick), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (s.labels.isNotEmpty() || s.types.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.labels.forEach { LabelChip(it) }
                s.types.forEach { TypeChip(it) }
            }
        }
        if (s.authors.isNotEmpty()) {
            Text(
                text = "— " + s.authors.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun typeLabelText(raw: String): String = when (raw) {
    "Review" -> stringResource(Res.string.reception_type_review)
    "TYMHM" -> stringResource(Res.string.reception_type_tymhm)
    "SITF" -> stringResource(Res.string.reception_type_sitf)
    "YMIO" -> stringResource(Res.string.reception_type_ymio)
    "LIT" -> stringResource(Res.string.reception_type_lit)
    "RFU" -> stringResource(Res.string.reception_type_rfu)
    else -> raw
}

@Composable
private fun labelText(label: ParsedLabel): String = when (label.kind) {
    LabelKind.AOTY -> stringResource(Res.string.reception_label_aoty, label.year ?: 0)
    LabelKind.AOTM -> stringResource(Res.string.reception_label_aotm, label.year ?: 0, label.month ?: 0)
    LabelKind.HONORABLE_MENTION -> stringResource(Res.string.reception_label_honorable_mention, label.year ?: 0)
    LabelKind.RECORD_OF_THE_MONTH -> stringResource(Res.string.reception_label_record_of_the_month)
    LabelKind.SCORE_REVISED -> stringResource(Res.string.reception_label_score_revised)
    LabelKind.TYMHM -> typeLabelText(label.raw)
    LabelKind.SITF -> typeLabelText(label.raw)
    LabelKind.YMIO -> typeLabelText(label.raw)
    LabelKind.LIT -> typeLabelText(label.raw)
    LabelKind.RFU -> typeLabelText(label.raw)
    LabelKind.UNKNOWN -> label.raw
}

private fun isAccolade(kind: LabelKind): Boolean = kind == LabelKind.AOTY ||
    kind == LabelKind.AOTM || kind == LabelKind.RECORD_OF_THE_MONTH || kind == LabelKind.HONORABLE_MENTION

@Composable
private fun LabelChip(label: ParsedLabel) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(labelText(label)) },
        leadingIcon = if (isAccolade(label.kind)) {
            { Icon(Icons.Default.EmojiEvents, contentDescription = null,
                tint = AssistChipDefaults.assistChipColors().labelColor) }
        } else null,
    )
}

@Composable
private fun TypeChip(type: String) {
    val text = typeLabelText(type)
    AssistChip(onClick = {}, enabled = false, label = { Text(text) })
}

@Preview
@Composable
private fun ReceptionPanelPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()))
}

@Preview
@Composable
private fun ReceptionPanelEmptyPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album())
}

@Preview
@Composable
private fun ReceptionPanelFallbackPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadataFallback()))
}
