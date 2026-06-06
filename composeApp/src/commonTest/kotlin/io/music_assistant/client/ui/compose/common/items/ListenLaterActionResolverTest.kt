package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenLaterActionResolverTest {
    @Test fun savedAlbumOffersRemove() {
        val actions = resolveDetailOverflowActions(
            item = AppMediaItemFixtures.album(listenLater = true),
            librarySupported = true,
            canAddToPlaylist = false,
        )
        assertTrue(ItemAction.RemoveFromLater in actions)
        assertFalse(ItemAction.SaveForLater in actions)
    }

    @Test fun unsavedAlbumOffersSave() {
        val actions = resolveDetailOverflowActions(
            item = AppMediaItemFixtures.album(listenLater = false),
            librarySupported = true,
            canAddToPlaylist = false,
        )
        assertTrue(ItemAction.SaveForLater in actions)
        assertFalse(ItemAction.RemoveFromLater in actions)
    }

    @Test fun nonAlbumOffersNeither() {
        val actions = resolveLongClickActions(
            item = AppMediaItemFixtures.artist(),
            librarySupported = true,
            canAddToPlaylist = false,
            canRemoveFromPlaylist = false,
            progressSupported = false,
        )
        assertFalse(ItemAction.SaveForLater in actions)
        assertFalse(ItemAction.RemoveFromLater in actions)
    }
}
