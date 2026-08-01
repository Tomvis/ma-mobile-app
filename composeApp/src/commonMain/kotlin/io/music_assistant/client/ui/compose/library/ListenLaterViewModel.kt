package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.repository.MediaItemChange
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListenLaterViewModel(
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    val state = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            mediaItemRepository.itemChanges.collect { change ->
                when (change) {
                    // Only albums can be in this list, so ignore deletions of any
                    // other media type instead of rescanning the whole list.
                    is MediaItemChange.Deleted -> (change.item as? Album)?.let { removeItem(it) }
                    // Added and Updated reconcile the same way: keep the album in the
                    // list while it's still saved-for-later, drop it otherwise. Saving an
                    // album from another surface fires Updated(listenLater=true) (and
                    // possibly Added), which the old remove-only collector ignored — so the
                    // open screen stayed stale on the very action it exists to support.
                    is MediaItemChange.Added -> reconcile(change.item)
                    is MediaItemChange.Updated -> reconcile(change.item)
                }
            }
        }
    }

    private fun reconcile(changed: AppMediaItem) {
        val album = changed as? Album ?: return
        if (album.listenLater) upsertItem(album) else removeItem(album)
    }

    private fun upsertItem(item: AppMediaItem) {
        _state.update { current ->
            val data = current as? DataState.Data ?: return@update current
            val existingIndex = data.data.indexOfFirst { it.matchesIdentityOf(item) }
            val newList = if (existingIndex >= 0) {
                // Already shown: replace in place so a metadata refresh lands without
                // disturbing the existing order.
                data.data.toMutableList().apply { this[existingIndex] = item }
            } else {
                // Newly saved: the list is ordered listen_later_added_at_desc
                // (newest first), so the fresh pick belongs at the front.
                listOf(item) + data.data
            }
            DataState.Data(newList)
        }
    }

    private fun removeItem(removed: AppMediaItem) {
        _state.update { current ->
            val data = current as? DataState.Data ?: return@update current
            val newList = data.data.filterNot { it.matchesIdentityOf(removed) }
            // Nothing matched: keep the current instance so collectors don't recompose.
            if (newList.size == data.data.size) return@update current
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
}
