package io.music_assistant.client.ui.compose.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_apply
import musicassistantclient.composeapp.generated.resources.filter_none_selected
import musicassistantclient.composeapp.generated.resources.filter_selected_count
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Bottom sheet for editing settings that can then be saved with [onApply] which will be passed the
 * current settings state. The initial state for settings is determined by [stateFactory] and
 * provided to [content] for the sheet UI to interact with.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SettingsSheet(
    title: String,
    stateFactory: () -> T,
    onApply: (state: T) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.(state: T) -> Unit,
) {
    val state = remember { stateFactory() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        // Non-swipeable: kills drag/swipe-to-dismiss while keeping scrim + back
        // (both routed through onDismissRequest). confirmValueChange would wrongly
        // block those too.
        sheetGesturesEnabled = false,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(SHEET_HEIGHT_FRACTION)
                // contentWindowInsets is zeroed above so the sheet can draw its
                // background behind the system bars; that makes the bottom inset ours
                // to apply. Doing it here — inside the height fraction, so the sheet
                // doesn't grow — keeps every sheet's last control (a pinned button, the
                // tail of a scrolling list) clear of the navigation bar.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            Header(title = title, onApply = { onApply(state) })
            content(state)
        }
    }
}

/** Sheet height as a fraction of the screen — matches the "80% height" spec. */
private const val SHEET_HEIGHT_FRACTION = 0.8f

@Composable
private fun Header(title: String, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(start = 24.dp, top = 12.dp, bottom = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onApply) {
            Text(stringResource(Res.string.common_apply))
        }
    }
}

object SettingsSheet {
    @Composable
    fun SwitchRow(
        label: StringResource,
        checked: Boolean,
        onChange: (Boolean) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onChange(!checked)
                }
                .padding(vertical = 8.dp, horizontal = ROW_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(label),
                modifier = Modifier.weight(1f),
            )

            Switch(checked = checked, onCheckedChange = onChange)
        }
    }

    @Composable
    fun <T> SingleChoiceChipsRow(
        label: StringResource,
        options: List<T>,
        selected: T,
        optionLabel: (T) -> StringResource,
        onSelect: (T) -> Unit,
    ) {
        ChoiceSection(label) {
            ChipsFlowRow(
                options = options,
                isSelected = { it == selected },
                optionLabel = { stringResource(optionLabel(it)) },
                onClick = onSelect,
            )
        }
    }

    @Composable
    fun <T> MultiChoiceChipsRow(
        label: StringResource,
        options: List<T>,
        selected: List<T>,
        optionLabel: (T) -> StringResource,
        onToggle: (T) -> Unit,
    ) {
        ChoiceSection(label) {
            ChipsFlowRow(
                options = options,
                isSelected = { it in selected },
                optionLabel = { stringResource(optionLabel(it)) },
                onClick = onToggle,
            )
        }
    }

    /**
     * [MultiChoiceChipsRow] for option sets whose chip text isn't a plain string
     * resource — a rating rendered from a number ("4★"), a band ("7–8"), or a mix of
     * both and resource-backed labels in one row. The label lambda is composable so
     * callers can still resolve a [StringResource] for the options that have one.
     */
    @Composable
    fun <T> MultiChoiceChipsRow(
        label: StringResource,
        options: List<T>,
        selected: List<T>,
        optionText: @Composable (T) -> String,
        onToggle: (T) -> Unit,
    ) {
        ChoiceSection(label) {
            ChipsFlowRow(
                options = options,
                isSelected = { it in selected },
                optionLabel = optionText,
                onClick = onToggle,
            )
        }
    }

    /** The chip row itself: shared spacing/padding for every chips section. */
    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun <T> ChipsFlowRow(
        options: List<T>,
        isSelected: (T) -> Boolean,
        optionLabel: @Composable (T) -> String,
        onClick: (T) -> Unit,
    ) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = 4.dp,
                    start = ROW_HORIZONTAL_PADDING,
                    end = ROW_HORIZONTAL_PADDING,
                ),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val text = optionLabel(option)

                FilterChip(
                    selected = isSelected(option),
                    onClick = { onClick(option) },
                    label = { Text(text) },
                )
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    fun ChoiceSection(
        label: StringResource,
        content: @Composable () -> Unit,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        ) {
            Text(
                modifier = Modifier.padding(horizontal = ROW_HORIZONTAL_PADDING),
                text = stringResource(label),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            content()
        }
    }

    /**
     * A sheet row summarising a multi-select option (label + "N selected"), tappable
     * to open the picker dialog. Rendered even before options load — the count comes
     * from the persisted selection, not the option list.
     */
    @Composable
    fun PickerRow(
        label: StringResource,
        selectedCount: Int,
        onClick: () -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = ROW_HORIZONTAL_PADDING),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(label), modifier = Modifier.weight(1f))
            Text(
                text = if (selectedCount > 0) {
                    stringResource(Res.string.filter_selected_count, selectedCount)
                } else {
                    stringResource(Res.string.filter_none_selected)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }

    private val ROW_HORIZONTAL_PADDING = 24.dp
}
