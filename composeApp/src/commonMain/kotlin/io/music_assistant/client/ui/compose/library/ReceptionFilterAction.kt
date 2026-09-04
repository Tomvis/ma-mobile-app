@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AccoladeKind
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.ReceptionFilter
import io.music_assistant.client.data.model.client.SourceFilter
import io.music_assistant.client.ui.compose.common.SettingsSheet.MultiChoiceChipsRow
import io.music_assistant.client.ui.compose.common.SettingsSheet.SingleChoiceChipsRow
import io.music_assistant.client.ui.compose.common.items.labelRes
import io.music_assistant.client.ui.compose.common.items.shortLabelRes
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_reception_filter
import musicassistantclient.composeapp.generated.resources.filter_amg
import musicassistantclient.composeapp.generated.resources.filter_clear_all
import musicassistantclient.composeapp.generated.resources.filter_dr
import musicassistantclient.composeapp.generated.resources.filter_dr_untagged
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
// of sync. DR buckets reuse the shared DrQuality.labelRes(); the "untagged" pseudo-bucket
// has no enum entry and is appended as a local special case by the DR chip row.
private val DR_BUCKET_LABELS: Map<DrQuality, StringResource> = linkedMapOf(
    DrQuality.EXCELLENT to DrQuality.EXCELLENT.labelRes(),
    DrQuality.GOOD to DrQuality.GOOD.labelRes(),
    DrQuality.FAIR to DrQuality.FAIR.labelRes(),
    DrQuality.POOR to DrQuality.POOR.labelRes(),
)

// Accolade kind -> chip label, ordered. Labels come from the shared
// AccoladeKind.shortLabelRes() so the sheet can't drift from the reception panel;
// UNKNOWN has no label and is never offered as a filter, so it drops out.
private fun accoladeLabels(vararg kinds: AccoladeKind): Map<AccoladeKind, StringResource> =
    kinds.mapNotNull { kind -> kind.shortLabelRes()?.let { kind to it } }
        .toMap(LinkedHashMap())

// Awards first, then review-column kinds. AOTM is gone — folded into RECORD_OF_THE_MONTH.
// REVIEW is the default column and not a useful filter.
private val AWARD_ACCOLADE_LABELS: Map<AccoladeKind, StringResource> = accoladeLabels(
    AccoladeKind.AOTY,
    AccoladeKind.RECORD_OF_THE_MONTH,
    AccoladeKind.HONORABLE_MENTION,
    AccoladeKind.SCORE_REVISED,
)

// The five review columns only ever appear on AMG entries, so they belong in the AMG
// row alone: the server scopes each accolade clause to its own source, so offering
// them under TPS makes a dead control that always filters to zero. Mirrors the web
// frontend's AWARD_ACCOLADE_KINDS / AMG_COLUMN_ACCOLADE_KINDS split.
private val AMG_COLUMN_ACCOLADE_LABELS: Map<AccoladeKind, StringResource> = accoladeLabels(
    AccoladeKind.TYMHM,
    AccoladeKind.SITF,
    AccoladeKind.YMIO,
    AccoladeKind.LIT,
    AccoladeKind.RFU,
)

private val AMG_ACCOLADE_LABELS: Map<AccoladeKind, StringResource> =
    AWARD_ACCOLADE_LABELS + AMG_COLUMN_ACCOLADE_LABELS

private val TPS_ACCOLADE_LABELS: Map<AccoladeKind, StringResource> = AWARD_ACCOLADE_LABELS

// The DR chips: the four buckets, then the "untagged" pseudo-bucket, which has no
// DrQuality entry and is carried as the null option (ReceptionFilter.drUntagged).
private val DR_BUCKET_OPTIONS: List<DrQuality?> = buildList {
    addAll(DR_BUCKET_LABELS.keys)
    add(null)
}

// The two match modes, in "all" (AND) then "any" (OR) order — the values of
// ReceptionFilter.matchAny.
private val MATCH_MODES: List<Boolean> = listOf(false, true)

/** Test handle on the sheet's scrolling section list — DR, AMG and TPS do not all fit one screen. */
object ReceptionFilterSemantics {
    const val CONTENT_TAG = "ReceptionFilterContent"
}

/**
 * The album reception filter: the top-bar icon button and the sheet it opens, on the
 * shared [FilterAction] / SettingsSheet chrome. Non-swipeable; the scrim tap and system
 * back both discard. Edits accumulate in a working copy and commit atomically through
 * [onFilterChanged] only when "Apply" is hit.
 */
@Composable
fun ReceptionFilterAction(
    filter: ReceptionFilter,
    onFilterChanged: (ReceptionFilter) -> Unit,
) {
    FilterAction(
        active = filter.isActive,
        state = { mutableStateOf(filter) },
        onApply = { onFilterChanged(it.value) },
        title = Res.string.filter_title,
        contentDescription = Res.string.cd_reception_filter,
    ) { state ->
        ReceptionFilters(state)
    }
}

/** The sheet body: every filter section, plus the "Clear all" row pinned below them. */
@Composable
private fun ColumnScope.ReceptionFilters(state: MutableState<ReceptionFilter>) {
    var working by state

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
            .testTag(ReceptionFilterSemantics.CONTENT_TAG),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SingleChoiceChipsRow(
            label = Res.string.filter_match,
            options = MATCH_MODES,
            selected = working.matchAny,
            optionLabel = {
                if (it) Res.string.filter_match_any else Res.string.filter_match_all
            },
            onSelect = { working = working.copy(matchAny = it) },
        )

        MultiChoiceChipsRow(
            label = Res.string.filter_dr,
            options = DR_BUCKET_OPTIONS,
            selected = working.selectedDrOptions(),
            optionLabel = { it?.let(DR_BUCKET_LABELS::getValue) ?: Res.string.filter_dr_untagged },
            onToggle = { working = working.toggleDr(it) },
        )

        SourceChipsRow(
            label = Res.string.filter_amg,
            accoladeLabels = AMG_ACCOLADE_LABELS,
            ratings = ReceptionFilter.AMG_RATINGS,
            ratingLabel = ::amgRatingLabel,
            filter = working.amg,
            onChange = { working = working.copy(amg = it) },
        )

        SourceChipsRow(
            label = Res.string.filter_tps,
            accoladeLabels = TPS_ACCOLADE_LABELS,
            ratings = ReceptionFilter.TPS_BANDS,
            ratingLabel = ::tpsBandLabel,
            filter = working.tps,
            onChange = { working = working.copy(tps = it) },
        )
    }

    // "Clear all" lives in the body because the shared sheet header carries only
    // "Apply". Like every other control here it edits the working copy, so clearing
    // still commits nothing until Apply is hit.
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = { working = ReceptionFilter() },
            enabled = working.isActive,
        ) {
            Text(stringResource(Res.string.filter_clear_all))
        }
    }
}

private fun ReceptionFilter.selectedDrOptions(): List<DrQuality?> = buildList {
    addAll(drBuckets)
    if (drUntagged) add(null)
}

private fun ReceptionFilter.toggleDr(option: DrQuality?): ReceptionFilter =
    if (option == null) {
        copy(drUntagged = !drUntagged)
    } else {
        copy(drBuckets = drBuckets.toggle(option))
    }

/**
 * One chip of a source's row. The row mixes rating chips — labelled from a number
 * ("4★", "7–8") rather than a string resource — with the resource-labelled accolade,
 * favorite and untagged chips, which is why it goes through the plain-String
 * [MultiChoiceChipsRow] overload.
 */
private sealed interface SourceChip {
    data class Rating(val value: Double) : SourceChip
    data class Accolade(val kind: AccoladeKind) : SourceChip
    data object Favorite : SourceChip
    data object Untagged : SourceChip
}

// One source's (AMG / TPS) chips: the ratings, then the accolades, then favorite and
// untagged. Both sources share the same [SourceFilter] state shape; only the chip
// vocabulary differs, which is what [accoladeLabels], [ratings] and [ratingLabel] carry in.
@Composable
private fun SourceChipsRow(
    label: StringResource,
    accoladeLabels: Map<AccoladeKind, StringResource>,
    ratings: List<Double>,
    ratingLabel: (Double) -> String,
    filter: SourceFilter,
    onChange: (SourceFilter) -> Unit,
) {
    val chips = remember(ratings, accoladeLabels) {
        ratings.map { SourceChip.Rating(it) } +
            accoladeLabels.keys.map { SourceChip.Accolade(it) } +
            listOf(SourceChip.Favorite, SourceChip.Untagged)
    }
    MultiChoiceChipsRow(
        label = label,
        options = chips,
        selected = filter.selectedChips(),
        optionText = { it.label(accoladeLabels, ratingLabel) },
        onToggle = { onChange(filter.toggle(it)) },
    )
}

private fun SourceFilter.selectedChips(): List<SourceChip> = buildList {
    ratings.forEach { add(SourceChip.Rating(it)) }
    accolades.forEach { add(SourceChip.Accolade(it)) }
    if (favorite) add(SourceChip.Favorite)
    if (untagged) add(SourceChip.Untagged)
}

private fun SourceFilter.toggle(chip: SourceChip): SourceFilter = when (chip) {
    is SourceChip.Rating -> copy(ratings = ratings.toggle(chip.value))
    is SourceChip.Accolade -> copy(accolades = accolades.toggle(chip.kind))
    SourceChip.Favorite -> copy(favorite = !favorite)
    SourceChip.Untagged -> copy(untagged = !untagged)
}

@Composable
private fun SourceChip.label(
    accoladeLabels: Map<AccoladeKind, StringResource>,
    ratingLabel: (Double) -> String,
): String = when (this) {
    is SourceChip.Rating -> ratingLabel(value)
    is SourceChip.Accolade -> stringResource(accoladeLabels.getValue(kind))
    SourceChip.Favorite -> stringResource(Res.string.filter_personal_pick)
    SourceChip.Untagged -> stringResource(Res.string.filter_untagged)
}

// AMG half-star selectors: each covers one exact step, so 4 and 4.5 are separate chips.
// Whole stars keep the plain "4★" label and halves take a vulgar fraction ("4½★", "½★"),
// which keeps a half-step chip as compact as a whole-star one.
private fun amgRatingLabel(rating: Double): String {
    val stars = rating.toInt()
    val whole = if (stars == 0) "" else stars.toString()
    val half = if (rating - stars >= 0.5) "½" else ""
    return "$whole$half★"
}

// TPS bands: selector `lo` covers [lo, lo+2) on the /10 scale; 9 is the open top band.
// Label with the inclusive integer span [lo, lo+1] so adjacent chips don't share an
// endpoint (e.g. "7–8", not "7–9" which wrongly implies the 7-band covers 9).
private fun tpsBandLabel(lo: Double): String {
    val band = lo.toInt()
    return if (band >= 9) "9+" else "$band–${band + 1}"
}

// The body puts the sections in a Modifier.weight(1f) child, which measures to zero in a
// wrap-content host — the preview would then show only the "Clear all" row. Give the host
// a fixed height so the weighted section list gets real space, as the sheet's
// fillMaxHeight(0.8f) column does at runtime.
private val PREVIEW_HEIGHT = 640.dp

@Preview
@Composable
private fun ReceptionFiltersEmptyPreview() {
    Column(modifier = Modifier.height(PREVIEW_HEIGHT)) {
        ReceptionFilters(remember { mutableStateOf(ReceptionFilter()) })
    }
}

@Preview
@Composable
private fun ReceptionFiltersPopulatedPreview() {
    Column(modifier = Modifier.height(PREVIEW_HEIGHT)) {
        ReceptionFilters(
            remember {
                mutableStateOf(
                    ReceptionFilter(
                        drBuckets = setOf(DrQuality.EXCELLENT, DrQuality.GOOD),
                        amg = SourceFilter(
                            ratings = setOf(4.5, 5.0),
                            accolades = setOf(AccoladeKind.AOTY),
                            favorite = true,
                        ),
                        tps = SourceFilter(ratings = setOf(7.0)),
                        matchAny = true,
                    ),
                )
            },
        )
    }
}
