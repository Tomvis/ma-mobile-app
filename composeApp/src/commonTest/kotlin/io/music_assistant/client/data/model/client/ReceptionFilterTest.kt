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
        val args = ReceptionFilter(drBuckets = setOf(DrQuality.GOOD, DrQuality.EXCELLENT)).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("excellent"), JsonPrimitive("good"))),
            args["dr_buckets"],
        )
    }

    @Test fun drUntaggedFoldsIntoBucketArray() {
        // "untagged" is a pseudo-bucket: the server takes it as one more dr_buckets entry.
        assertEquals(
            JsonArray(listOf(JsonPrimitive("good"), JsonPrimitive("untagged"))),
            ReceptionFilter(drBuckets = setOf(DrQuality.GOOD), drUntagged = true).toRequestArgs()["dr_buckets"],
        )
        assertEquals(
            JsonArray(listOf(JsonPrimitive("untagged"))),
            ReceptionFilter(drUntagged = true).toRequestArgs()["dr_buckets"],
        )
    }

    @Test fun ratingsSerializeSortedNumberArrays() {
        // AMG selectors are half stars, so the array has to survive the .5 unrounded.
        val args = ReceptionFilter(
            amg = SourceFilter(ratings = setOf(5.0, 3.5)),
            tps = SourceFilter(ratings = setOf(9.0, 1.0)),
        ).toRequestArgs()
        assertEquals(
            listOf(3.5, 5.0),
            (args["amg_ratings"] as JsonArray).map { it.toString().toDouble() },
        )
        assertEquals(
            listOf(1.0, 9.0),
            (args["tps_ratings"] as JsonArray).map { it.toString().toDouble() },
        )
    }

    @Test fun labelsSerializeSortedArrays() {
        val args = ReceptionFilter(
            amg = SourceFilter(accolades = setOf(AccoladeKind.RECORD_OF_THE_MONTH, AccoladeKind.AOTY)),
            tps = SourceFilter(accolades = setOf(AccoladeKind.HONORABLE_MENTION)),
        ).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("aoty"), JsonPrimitive("record_of_the_month"))),
            args["amg_accolades"],
        )
        assertEquals(JsonArray(listOf(JsonPrimitive("honorable_mention"))), args["tps_accolades"])
    }

    @Test fun flagsEmittedOnlyWhenTrue() {
        val args = ReceptionFilter(
            amg = SourceFilter(favorite = true),
            tps = SourceFilter(untagged = true),
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
            ReceptionFilter(
                matchAny = true,
                drBuckets = setOf(DrQuality.GOOD),
            ).toRequestArgs()["critical_reception_match"],
        )
    }

    @Test fun matchAnyAloneEmitsNothing() {
        // matchAny with no actual filter clauses is inactive and produces no args.
        assertTrue(ReceptionFilter(matchAny = true).toRequestArgs().isEmpty())
    }

    @Test fun activeCountSumsSelectionsAndFlagsNotMatch() {
        val f = ReceptionFilter(
            drBuckets = setOf(DrQuality.GOOD, DrQuality.FAIR),
            amg = SourceFilter(
                ratings = setOf(4.5),
                accolades = setOf(AccoladeKind.AOTY),
                favorite = true,
            ),
            tps = SourceFilter(untagged = true),
            matchAny = true,
        )
        assertTrue(f.isActive)
        assertEquals(6, f.activeCount)
    }
}
