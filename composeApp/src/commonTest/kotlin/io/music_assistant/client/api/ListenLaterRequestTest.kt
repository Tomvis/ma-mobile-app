package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListenLaterRequestTest {
    @Test fun addUsesItemUri() {
        val req = Request.Album.listenLaterAdd("library://album/5")
        assertEquals("music/albums/listen_later_add", req.command)
        assertEquals(JsonPrimitive("library://album/5"), req.args!!["item"])
    }

    @Test fun removeUsesLibraryItemId() {
        val req = Request.Album.listenLaterRemove("5")
        assertEquals("music/albums/listen_later_remove", req.command)
        assertEquals(JsonPrimitive("5"), req.args!!["library_item_id"])
    }

    @Test fun listLibraryEmitsListenLaterWhenSet() {
        assertEquals(
            JsonPrimitive(true),
            Request.Album.listLibrary(listenLater = true).args!!["listen_later"],
        )
        assertNull(Request.Album.listLibrary().args!!["listen_later"])
    }
}
