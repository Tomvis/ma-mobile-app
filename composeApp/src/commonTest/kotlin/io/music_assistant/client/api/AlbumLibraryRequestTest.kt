package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.ReceptionFilter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumLibraryRequestTest {
    @Test
    fun listLibraryMergesFilterArgs() {
        val req = Request.Album.listLibrary(
            orderBy = "dr_desc",
            receptionFilter = ReceptionFilter(
                drBuckets = setOf("excellent"),
                amgRatings = setOf(4, 5),
                amgFavorite = true,
                matchAny = true,
            ),
        )
        val args = req.args!!
        assertEquals(JsonPrimitive("dr_desc"), args["order_by"])
        assertEquals(JsonArray(listOf(JsonPrimitive("excellent"))), args["dr_buckets"])
        assertEquals(listOf(4, 5), (args["amg_ratings"] as JsonArray).map { it.toString().toInt() })
        assertEquals(JsonPrimitive(true), args["amg_favorite"])
        assertEquals(JsonPrimitive("any"), args["critical_reception_match"])
    }

    @Test
    fun listLibraryWithoutFilterHasNoFilterArgs() {
        val args = Request.Album.listLibrary(orderBy = "sort_name").args!!
        assertNull(args["dr_buckets"])
        assertNull(args["critical_reception_match"])
    }
}
