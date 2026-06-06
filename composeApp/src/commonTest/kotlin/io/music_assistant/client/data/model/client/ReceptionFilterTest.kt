package io.music_assistant.client.data.model.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceptionFilterTest {
    @Test fun emptyFilterIsInactiveWithNoArgs() {
        val f = ReceptionFilter()
        assertFalse(f.isActive)
        assertEquals(0, f.activeCount)
        assertTrue(f.toRequestArgs().isEmpty())
    }

    @Test fun drBucketsSerializeSortedArray() {
        val args = ReceptionFilter(drBuckets = setOf("good", "excellent")).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("excellent"), JsonPrimitive("good"))),
            args["dr_buckets"],
        )
    }

    @Test fun ratingsSerializeSortedIntArrays() {
        val args = ReceptionFilter(
            amgRatings = setOf(5, 3),
            tpsRatings = setOf(9, 1),
        ).toRequestArgs()
        assertEquals(listOf(3, 5), (args["amg_ratings"] as JsonArray).map { it.toString().toInt() })
        assertEquals(listOf(1, 9), (args["tps_ratings"] as JsonArray).map { it.toString().toInt() })
    }

    @Test fun labelsSerializeSortedArrays() {
        val args = ReceptionFilter(
            amgAccolades = setOf("record_of_the_month", "aoty"),
            tpsAccolades = setOf("honorable_mention"),
        ).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("aoty"), JsonPrimitive("record_of_the_month"))),
            args["amg_accolades"],
        )
        assertEquals(JsonArray(listOf(JsonPrimitive("honorable_mention"))), args["tps_accolades"])
    }

    @Test fun flagsEmittedOnlyWhenTrue() {
        val args = ReceptionFilter(
            amgFavorite = true,
            tpsUntagged = true,
        ).toRequestArgs()
        assertEquals(JsonPrimitive(true), args["amg_favorite"])
        assertEquals(JsonPrimitive(true), args["tps_untagged"])
        assertNull(args["amg_untagged"])
        assertNull(args["tps_favorite"])
    }

    @Test fun matchEmittedOnlyForAny() {
        assertNull(ReceptionFilter(matchAny = false).toRequestArgs()["critical_reception_match"])
        assertEquals(
            JsonPrimitive("any"),
            ReceptionFilter(matchAny = true, drBuckets = setOf("good")).toRequestArgs()["critical_reception_match"],
        )
    }

    @Test fun matchAnyAloneEmitsNothing() {
        // matchAny with no actual filter clauses is inactive and produces no args.
        assertTrue(ReceptionFilter(matchAny = true).toRequestArgs().isEmpty())
    }

    @Test fun activeCountSumsSelectionsAndFlagsNotMatch() {
        val f = ReceptionFilter(
            drBuckets = setOf("good", "fair"),
            amgRatings = setOf(4),
            amgAccolades = setOf("aoty"),
            amgFavorite = true,
            tpsUntagged = true,
            matchAny = true,
        )
        assertTrue(f.isActive)
        assertEquals(6, f.activeCount)
    }
}
