package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.MainDataSource
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.model.client.items.Genre
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListenLaterViewModel(
    private val apiClient: ServiceClient,
    private val mainDataSource: MainDataSource,
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                when (change) {
                    is MediaItemChange.Deleted -> removeItem(change.item)
                    is MediaItemChange.Updated -> {
                        val album = change.item as? Album ?: return@collect
                        if (!album.listenLater) removeItem(album)
                    }
                    else -> Unit
                }
            }
        }
    }

    private fun removeItem(removed: AppMediaItem) {
        _state.update { current ->
            val data = current as? DataState.Data ?: return@update current
            val newList = data.data.filterNot { it.provider == removed.provider && it.itemId == removed.itemId }
            DataState.Data(newList)
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { DataState.Loading() }
            val result = mediaItemRepository.fetchMediaItems(
                Request.Album.listLibrary(
                    listenLater = true,
                    orderBy = "listen_later_added_at_desc",
                ),
            )
            result.getOrNull()
                ?.let { items -> _state.update { DataState.Data(items) } }
                ?: _state.update { DataState.Error() }
        }
    }

    fun onPlayClick(item: AppMediaItem, option: QueueOption, radio: Boolean) {
        viewModelScope.launch {
            val queueId = mainDataSource.selectedPlayer?.queueOrPlayerId ?: return@launch
            item.mediaUri?.let { mediaUri ->
                apiClient.sendRequest(
                    Request.Library.play(
                        media = listOf(mediaUri),
                        queueOrPlayerId = queueId,
                        option = option,
                        radioMode = radio && item !is Genre,
                    ),
                )
            }
        }
    }
}
