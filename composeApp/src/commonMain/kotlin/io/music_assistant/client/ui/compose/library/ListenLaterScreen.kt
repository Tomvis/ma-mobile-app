@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.library_empty
import musicassistantclient.composeapp.generated.resources.library_error
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
    Screen(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text(stringResource(Res.string.listen_later_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        when (val s = state) {
            is DataState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is DataState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.library_error),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            is DataState.NoData -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(Res.string.library_empty),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            is DataState.Data, is DataState.Stale -> {
                val items = s.dataOrNull.orEmpty()
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(Res.string.library_empty),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    AdaptiveMediaGrid(
                        items = items,
                        onNavigateClick = onNavigateClick,
                        onPlayClick = onPlayClick,
                        playlistActions = playlistActions,
                        libraryActions = libraryActions,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}
