@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.ReceptionFilter
import io.music_assistant.client.ui.compose.common.items.labelRes
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.filter_amg
import musicassistantclient.composeapp.generated.resources.filter_clear_all
import musicassistantclient.composeapp.generated.resources.filter_dr
import musicassistantclient.composeapp.generated.resources.filter_dr_untagged
import musicassistantclient.composeapp.generated.resources.filter_label_aoty
import musicassistantclient.composeapp.generated.resources.filter_label_honorable_mention
import musicassistantclient.composeapp.generated.resources.filter_label_lit
import musicassistantclient.composeapp.generated.resources.filter_label_record_of_the_month
import musicassistantclient.composeapp.generated.resources.filter_label_rfu
import musicassistantclient.composeapp.generated.resources.filter_label_score_revised
import musicassistantclient.composeapp.generated.resources.filter_label_sitf
import musicassistantclient.composeapp.generated.resources.filter_label_tymhm
import musicassistantclient.composeapp.generated.resources.filter_label_ymio
import musicassistantclient.composeapp.generated.resources.filter_match
import musicassistantclient.composeapp.generated.resources.filter_match_all
import musicassistantclient.composeapp.generated.resources.filter_match_any
import musicassistantclient.composeapp.generated.resources.filter_personal_pick
import musicassistantclient.composeapp.generated.resources.filter_title
import musicassistantclient.composeapp.generated.resources.filter_tps
import musicassistantclient.composeapp.generated.resources.filter_untagged
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

// Ordered chip key -> label. Each map is the single source for both the chip list (its
// iteration order) and the chip's label, so there is no separate when()+else to fall out
// of sync. DR buckets reuse the shared DrQuality.labelRes(); "untagged" is the local
// special case. Keys are the server filter tokens (3.2.0 merged shape).
private val DR_BUCKET_LABELS: Map<String, StringResource> = linkedMapOf(
    "excellent" to DrQuality.EXCELLENT.labelRes(),
    "good" to DrQuality.GOOD.labelRes(),
    "fair" to DrQuality.FAIR.labelRes(),
    "poor" to DrQuality.POOR.labelRes(),
    "untagged" to Res.string.filter_dr_untagged,
)

// Awards first, then review-column kinds. AOTM is gone — folded into record_of_the_month.
// "review" is the default column and not a useful filter.
private val AWARD_ACCOLADE_LABELS: Map<String, StringResource> = linkedMapOf(
    "aoty" to Res.string.filter_label_aoty,
    "record_of_the_month" to Res.string.filter_label_record_of_the_month,
    "honorable_mention" to Res.string.filter_label_honorable_mention,
    "score_revised" to Res.string.filter_label_score_revised,
)

// The five review columns only ever appear on AMG entries, so they belong in the AMG
// row alone: the server scopes each accolade clause to its own source, so offering
// them under TPS makes a dead control that always filters to zero. Mirrors the web
// frontend's AWARD_ACCOLADE_KINDS / AMG_COLUMN_ACCOLADE_KINDS split.
private val AMG_COLUMN_ACCOLADE_LABELS: Map<String, StringResource> = linkedMapOf(
    "tymhm" to Res.string.filter_label_tymhm,
    "sitf" to Res.string.filter_label_sitf,
    "ymio" to Res.string.filter_label_ymio,
    "lit" to Res.string.filter_label_lit,
    "rfu" to Res.string.filter_label_rfu,
)

private val AMG_ACCOLADE_LABELS: Map<String, StringResource> =
    AWARD_ACCOLADE_LABELS + AMG_COLUMN_ACCOLADE_LABELS

private val TPS_ACCOLADE_LABELS: Map<String, StringResource> = AWARD_ACCOLADE_LABELS

@Composable
fun ReceptionFilterSheet(
    filter: ReceptionFilter,
    onChange: (ReceptionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (filter.isActive) {
                    TextButton(onClick = { onChange(ReceptionFilter()) }) {
                        Text(stringResource(Res.string.filter_clear_all))
                    }
                }
            }

            SectionLabel(stringResource(Res.string.filter_match))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !filter.matchAny,
                    onClick = { onChange(filter.copy(matchAny = false)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(Res.string.filter_match_all)) }
                SegmentedButton(
                    selected = filter.matchAny,
                    onClick = { onChange(filter.copy(matchAny = true)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(Res.string.filter_match_any)) }
            }

            SectionLabel(stringResource(Res.string.filter_dr))
            ChipRow {
                DR_BUCKET_LABELS.forEach { (bucket, labelRes) ->
                    FilterChip(
                        selected = bucket in filter.drBuckets,
                        onClick = { onChange(filter.copy(drBuckets = filter.drBuckets.toggle(bucket))) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }

            SectionLabel(stringResource(Res.string.filter_amg))
            ChipRow {
                ReceptionFilter.AMG_RATINGS.forEach { n ->
                    FilterChip(
                        selected = n in filter.amgRatings,
                        onClick = { onChange(filter.copy(amgRatings = filter.amgRatings.toggle(n))) },
                        label = { Text("$n★") },
                    )
                }
            }
            SourceAccoladeRow(
                labels = AMG_ACCOLADE_LABELS,
                accolades = filter.amgAccolades,
                favorite = filter.amgFavorite,
                untagged = filter.amgUntagged,
                onToggleAccolade = { onChange(filter.copy(amgAccolades = filter.amgAccolades.toggle(it))) },
                onToggleFavorite = { onChange(filter.copy(amgFavorite = !filter.amgFavorite)) },
                onToggleUntagged = { onChange(filter.copy(amgUntagged = !filter.amgUntagged)) },
            )

            SectionLabel(stringResource(Res.string.filter_tps))
            ChipRow {
                ReceptionFilter.TPS_BANDS.forEach { lo ->
                    FilterChip(
                        selected = lo in filter.tpsRatings,
                        onClick = { onChange(filter.copy(tpsRatings = filter.tpsRatings.toggle(lo))) },
                        label = { Text(tpsBandLabel(lo)) },
                    )
                }
            }
            SourceAccoladeRow(
                labels = TPS_ACCOLADE_LABELS,
                accolades = filter.tpsAccolades,
                favorite = filter.tpsFavorite,
                untagged = filter.tpsUntagged,
                onToggleAccolade = { onChange(filter.copy(tpsAccolades = filter.tpsAccolades.toggle(it))) },
                onToggleFavorite = { onChange(filter.copy(tpsFavorite = !filter.tpsFavorite)) },
                onToggleUntagged = { onChange(filter.copy(tpsUntagged = !filter.tpsUntagged)) },
            )
        }
    }
}

@Composable
private fun SourceAccoladeRow(
    labels: Map<String, StringResource>,
    accolades: Set<String>,
    favorite: Boolean,
    untagged: Boolean,
    onToggleAccolade: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleUntagged: () -> Unit,
) {
    ChipRow {
        labels.forEach { (kind, labelRes) ->
            FilterChip(
                selected = kind in accolades,
                onClick = { onToggleAccolade(kind) },
                label = { Text(stringResource(labelRes)) },
            )
        }
        FilterChip(
            selected = favorite,
            onClick = onToggleFavorite,
            label = { Text(stringResource(Res.string.filter_personal_pick)) },
        )
        FilterChip(
            selected = untagged,
            onClick = onToggleUntagged,
            label = { Text(stringResource(Res.string.filter_untagged)) },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

// TPS bands: selector `lo` covers [lo, lo+2) on the /10 scale; 9 is the open top band.
// Label with the inclusive integer span [lo, lo+1] so adjacent chips don't share an
// endpoint (e.g. "7–8", not "7–9" which wrongly implies the 7-band covers 9).
private fun tpsBandLabel(lo: Int): String = if (lo >= 9) "9+" else "$lo–${lo + 1}"

@Preview
@Composable
private fun ReceptionFilterSheetEmptyPreview() {
    ReceptionFilterSheet(filter = ReceptionFilter(), onChange = {}, onDismiss = {})
}

@Preview
@Composable
private fun ReceptionFilterSheetPopulatedPreview() {
    ReceptionFilterSheet(
        filter = ReceptionFilter(
            drBuckets = setOf("excellent", "good"),
            amgRatings = setOf(4, 5),
            tpsRatings = setOf(7),
            amgAccolades = setOf("aoty"),
            amgFavorite = true,
            matchAny = true,
        ),
        onChange = {},
        onDismiss = {},
    )
}
