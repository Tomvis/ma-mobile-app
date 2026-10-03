package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumMetadataMappingTest {
    private val factory = MediaItemFactory(StubServiceClient())

    private fun albumJson() = """
        {"item_id":"a1","provider":"library","name":"Album",
         "media_type":"${MediaType.ALBUM.serverValue}",
         "metadata":{
           "dynamic_range": 12.0,
           "critical_reception": {
             "amg_dr": 11.0,
             "sources": [
               {"source":"AMG","rating":4.5},
               {"source":"","rating":9.9},
               {"source":"None","rating":7.0}
             ]
           },
           "last_refresh": null
         }}
    """.trimIndent()

    @Test
    fun mapsReceptionAndDropsBlankSource() {
        val album = factory.create(
            myJson.decodeFromString<ServerMediaItem>(albumJson()),
        ) as Album

        assertEquals(12f, album.metadata?.dynamicRange)
        assertEquals(11f, album.metadata?.criticalReception?.amgDr)
        // blank-source entry dropped; only AMG survives
        assertEquals(1, album.metadata?.criticalReception?.sources?.size)
        assertEquals("AMG", album.metadata?.criticalReception?.sources?.get(0)?.source)
        assertEquals(4.5f, album.metadata?.criticalReception?.sources?.get(0)?.rating)
    }

    @Test
    fun mapsReviewTextAndProviderDescription() {
        val album = factory.create(
            myJson.decodeFromString<ServerMediaItem>(
                """
                {"item_id":"a3","provider":"library","name":"A","media_type":"${MediaType.ALBUM.serverValue}",
                 "metadata":{"review":"provider","description":"blurb","critical_reception":{"sources":[
                   {"source":"AMG","review":"One.\n\nTwo."},{"source":"TPS","review":" "}]}}}
                """.trimIndent(),
            ),
        ) as Album

        assertEquals("provider", album.metadata?.review)
        assertEquals("blurb", album.metadata?.description)
        assertEquals("One.\n\nTwo.", album.metadata?.criticalReception?.sources?.get(0)?.review)
        assertNull(album.metadata?.criticalReception?.sources?.get(1)?.review)
    }

    @Test
    fun nullMetadataMapsNull() {
        val album = factory.create(
            myJson.decodeFromString<ServerMediaItem>(
                """{"item_id":"a2","provider":"library","name":"A","media_type":"${MediaType.ALBUM.serverValue}"}""",
            ),
        ) as Album
        assertNull(album.metadata)
    }
}
