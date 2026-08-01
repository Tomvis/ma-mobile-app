package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import io.music_assistant.client.ui.compose.common.SettingsSheet
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_filter
import musicassistantclient.composeapp.generated.resources.filter_sheet_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Filter icon button plus the sheet it opens. [title] and [contentDescription] are
 * parameterised because a screen can carry more than one filter action (the albums list
 * has both the library filter and the reception filter), and they must not read alike.
 */
@Composable
fun <T> FilterAction(
    active: Boolean,
    state: () -> T,
    onApply: (T) -> Unit,
    title: StringResource = Res.string.filter_sheet_title,
    contentDescription: StringResource = Res.string.cd_filter,
    filters: @Composable ColumnScope.(state: T) -> Unit,
) {
    var showFilterSheet by remember { mutableStateOf(false) }

    IconButton(
        modifier = Modifier.semantics {
            toggleableState = ToggleableState(active)
        },
        onClick = { showFilterSheet = true },
    ) {
        Icon(
            imageVector = Icons.Default.FilterList,
            contentDescription = stringResource(contentDescription),
            tint = if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                LocalContentColor.current
            },
        )
    }

    if (showFilterSheet) {
        SettingsSheet(
            title = stringResource(title),
            state,
            onApply = {
                onApply(it)
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false },
        ) {
            filters(it)
        }
    }
}
