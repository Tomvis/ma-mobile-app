package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.server.FakeClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenLaterMappingTest {
    private val factory = MediaItemFactory(FakeClient())

    private fun albumJson(listenLater: Boolean) = """
        {"item_id":"a1","provider":"library","name":"Album",
         "media_type":"${MediaType.ALBUM.serverValue}","listen_later":$listenLater}
    """.trimIndent()

    @Test fun mapsListenLaterTrue() {
        val album = factory.create(myJson.decodeFromString<ServerMediaItem>(albumJson(true))) as Album
        assertTrue(album.listenLater)
    }

    @Test fun defaultsListenLaterFalseWhenAbsent() {
        val json = """{"item_id":"a2","provider":"library","name":"A","media_type":"${MediaType.ALBUM.serverValue}"}"""
        val album = factory.create(myJson.decodeFromString<ServerMediaItem>(json)) as Album
        assertFalse(album.listenLater)
    }
}
