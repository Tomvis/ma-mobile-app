@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.nav.TopBarLayout
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.listen_later_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ListenLaterScreen(
    viewModel: ListenLaterViewModel,
    contentPadding: PaddingValues,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    onBack: () -> Unit,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TopBarLayout(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.listen_later_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
            )
        },
    ) {
        when (val s = state) {
            is DataState.Loading -> LoadingState()
            is DataState.Error -> ErrorState()
            is DataState.NoData -> EmptyState()
            is DataState.Data, is DataState.Stale -> {
                val items = s.dataOrNull.orEmpty()
                if (items.isEmpty()) {
                    EmptyState()
                } else {
                    AdaptiveMediaGrid(
                        items = items,
                        onNavigateClick = onNavigateClick,
                        onPlayClick = { item, option, radio, _ ->
                            onPlayClick(item, option, radio)
                        },
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}
