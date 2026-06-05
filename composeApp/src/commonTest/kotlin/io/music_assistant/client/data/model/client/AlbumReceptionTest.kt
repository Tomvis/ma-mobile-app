package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AlbumReceptionTest {

    private fun cr(
        amgDr: Float? = null,
        sources: List<ReviewSource> = emptyList(),
    ) = CriticalReception(amgDr, sources)

    private fun src(
        source: String,
        rating: Float? = null,
        favorite: Boolean? = null,
        types: List<String> = emptyList(),
        labels: List<String> = emptyList(),
        authors: List<String> = emptyList(),
    ) = ReviewSource(source, rating, favorite, types, labels, authors)

    @Test fun drBands() {
        assertEquals(DrQuality.EXCELLENT, drQuality(14f))
        assertEquals(DrQuality.GOOD, drQuality(13.9f))
        assertEquals(DrQuality.GOOD, drQuality(10f))
        assertEquals(DrQuality.FAIR, drQuality(9.9f))
        assertEquals(DrQuality.FAIR, drQuality(7f))
        assertEquals(DrQuality.POOR, drQuality(6.9f))
    }

    @Test fun measuredDrWins() {
        val t = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = 12f)
        assertEquals(12f, t.dr?.value)
        assertEquals(DrSource.MEASURED, t.dr?.source)
    }

    @Test fun amgDrFallbackWhenNoMeasured() {
        val t = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = null)
        assertEquals(8f, t.dr?.value)
        assertEquals(DrSource.AMG, t.dr?.source)
    }

    @Test fun divergenceOnlyWhenBothAndRoundsDiffer() {
        val diverge = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = 12f)
        assertEquals(8f, diverge.amgDr?.value)
        val agree = parseAlbumReception(cr(amgDr = 11.6f), albumDynamicRange = 12f)
        assertNull(agree.amgDr)
        assertNull(parseAlbumReception(cr(amgDr = 8f), null).amgDr)
    }

    @Test fun amgScale5TpsScale10() {
        val t = parseAlbumReception(
            cr(sources = listOf(src("AMG", rating = 4.5f), src("TPS", rating = 8.4f))),
            null,
        )
        assertEquals(5, t.amg?.scale)
        assertEquals(4.5f, t.amg?.rating)
        assertEquals(10, t.tps?.scale)
        assertEquals(8.4f, t.tps?.rating)
    }

    @Test fun ratingWinsOverFavorite() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG", rating = 4f, favorite = true))), null)
        assertEquals(4f, t.amg?.rating)
        assertFalse(t.amg?.favorite ?: true)
    }

    @Test fun favoriteWhenNoRating() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG", favorite = true))), null)
        assertNull(t.amg?.rating)
        assertTrue(t.amg?.favorite ?: false)
    }

    @Test fun emptySourceDropped() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG"))), null)
        assertNull(t.amg)
        assertFalse(t.hasAny)
    }

    @Test fun labelParsing() {
        assertEquals(LabelKind.AOTY, parseLabel("AOTY-2024").kind)
        assertEquals(2024, parseLabel("AOTY-2024").year)
        assertEquals(LabelKind.AOTM, parseLabel("AOTM-2024-03").kind)
        assertEquals(3, parseLabel("AOTM-2024-03").month)
        assertEquals(LabelKind.HONORABLE_MENTION, parseLabel("HONORABLE_MENTION-2023").kind)
        assertEquals(LabelKind.RECORD_OF_THE_MONTH, parseLabel("RECORD_OF_THE_MONTH").kind)
        assertEquals(LabelKind.TYMHM, parseLabel("TYMHM").kind)
        assertEquals(LabelKind.UNKNOWN, parseLabel("WHATEVER").kind)
    }

    @Test fun labelSortPriority() {
        val sorted = sortLabels(listOf(parseLabel("TYMHM"), parseLabel("AOTY-2024")))
        assertEquals(LabelKind.AOTY, sorted[0].kind)
        assertEquals(LabelKind.TYMHM, sorted[1].kind)
    }

    @Test fun authorRolesScoredVsUnscored() {
        val scored = inferAuthorRoles(listOf("A", "B", "C"), hasScored = true)
        assertEquals(AuthorRole.CANONICAL, scored[0].role)
        assertEquals(AuthorRole.SECONDARY, scored[1].role)
        assertEquals(AuthorRole.LIST_PICK, scored[2].role)
        val unscored = inferAuthorRoles(listOf("A"), hasScored = false)
        assertEquals(AuthorRole.LIST_PICK, unscored[0].role)
    }

    @Test fun formatScoreMatchesWeb() {
        assertEquals("4.0", formatScore(4f))
        assertEquals("4.5", formatScore(4.5f))
        assertEquals("8.4", formatScore(8.4f))
    }

    @Test fun hasAnyFalseForEmpty() {
        assertFalse(parseAlbumReception(null, null).hasAny)
    }
}
