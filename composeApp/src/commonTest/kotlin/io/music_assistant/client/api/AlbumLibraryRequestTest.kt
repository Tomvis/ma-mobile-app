package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.ReceptionFilter
import io.music_assistant.client.data.model.client.SortField
import io.music_assistant.client.data.model.client.SortOption
import io.music_assistant.client.data.model.client.SourceFilter
import io.music_assistant.client.data.model.server.ListSort
import io.music_assistant.client.data.model.server.SortWire
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumLibraryRequestTest {
    @Test
    fun listLibraryMergesFilterArgs() {
        val req = Request.Album.listLibrary(
            sort = ListSort(SortOption(SortField.DR, descending = true), SortWire.SORT_FIELD),
            receptionFilter = ReceptionFilter(
                drBuckets = setOf(DrQuality.EXCELLENT),
                amg = SourceFilter(ratings = setOf(4.5, 5.0), favorite = true),
                matchAny = true,
            ),
        )
        val args = req.args!!
        assertEquals(JsonPrimitive("dr"), args["sort_field"])
        assertEquals(JsonPrimitive("desc"), args["sort_direction"])
        assertEquals(JsonArray(listOf(JsonPrimitive("excellent"))), args["dr_buckets"])
        assertEquals(
            listOf(4.5, 5.0),
            (args["amg_ratings"] as JsonArray).map { it.toString().toDouble() },
        )
        assertEquals(JsonPrimitive(true), args["amg_favorite"])
        assertEquals(JsonPrimitive("any"), args["critical_reception_match"])
    }

    @Test
    fun listLibraryWithoutFilterHasNoFilterArgs() {
        val args = Request.Album.listLibrary(sort = ListSort(SortOption(SortField.NAME), SortWire.SORT_FIELD)).args!!
        assertNull(args["dr_buckets"])
        assertNull(args["critical_reception_match"])
    }
}
