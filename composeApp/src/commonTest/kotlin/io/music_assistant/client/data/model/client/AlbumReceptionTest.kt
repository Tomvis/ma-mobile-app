package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlbumReceptionTest {
    private fun cr(
        amgDr: Float? = null,
        sources: List<ReviewSource> = emptyList(),
    ) = CriticalReception(amgDr, sources)

    private fun src(
        source: String,
        rating: Float? = null,
        favorite: Boolean? = null,
        accolades: List<String> = emptyList(),
        links: List<ReviewLink> = emptyList(),
        authors: List<String> = emptyList(),
    ) = ReviewSource(source, rating, favorite, accolades, links, authors)

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

    @Test fun accoladeParsing() {
        assertEquals(AccoladeKind.AOTY, parseAccolade("Album of the Year (2024)").kind)
        assertEquals(2024, parseAccolade("Album of the Year (2024)").year)
        assertEquals(AccoladeKind.RECORD_OF_THE_MONTH, parseAccolade("Record of the Month (Sep 2024)").kind)
        assertEquals(9, parseAccolade("Record of the Month (Sep 2024)").month)
        assertEquals(AccoladeKind.HONORABLE_MENTION, parseAccolade("Honorable Mention (2023)").kind)
        assertEquals(AccoladeKind.SCORE_REVISED, parseAccolade("Score Revised").kind)
        assertEquals(AccoladeKind.TYMHM, parseAccolade("TYMHM").kind)
        assertTrue(parseAccolade("Album of the Year (2024)").isAward)
        assertFalse(parseAccolade("TYMHM").isAward)
        assertEquals(AccoladeKind.UNKNOWN, parseAccolade("Some Future Honor (2030)").kind)
    }

    @Test fun accoladeParsingRecognizesLegacyTokens() {
        assertEquals(AccoladeKind.AOTY, parseAccolade("AOTY-2024").kind)
        assertEquals("Album of the Year (2024)", parseAccolade("AOTY-2024").display)
        assertEquals(AccoladeKind.RECORD_OF_THE_MONTH, parseAccolade("AOTM-2024-03").kind)
        assertEquals("Record of the Month (Mar 2024)", parseAccolade("AOTM-2024-03").display)
        assertEquals(AccoladeKind.RECORD_OF_THE_MONTH, parseAccolade("RECORD_OF_THE_MONTH").kind)
        assertEquals(AccoladeKind.SCORE_REVISED, parseAccolade("SCORE_REVISED").kind)
        assertEquals(AccoladeKind.LIT, parseAccolade("LIT").kind)
    }

    @Test fun legacyTypesLabelsFoldedAndDeduped() {
        // type AOTM + labels RECORD_OF_THE_MONTH + AOTM-2024-09 collapse to one dated entry.
        val folded = legacyAccolades(
            listOf("Review", "AOTM"),
            listOf("AOTY-2024", "RECORD_OF_THE_MONTH", "AOTM-2024-09"),
        )
        assertEquals(
            listOf("Album of the Year (2024)", "Record of the Month (Sep 2024)", "Review"),
            folded,
        )
    }

    @Test fun linksMatchAccoladesByLabel() {
        val s = parseAlbumReception(
            cr(
                sources = listOf(
                    src(
                        "AMG", rating = 4.5f,
                        accolades = listOf("Review", "Album of the Year (2024)"),
                        links = listOf(
                            ReviewLink("Review", "https://x/r"),
                            ReviewLink("Album of the Year (2024)", "https://x/a"),
                        ),
                    ),
                ),
            ),
            null,
        ).amg!!
        assertEquals("https://x/r", reviewLink(s)?.url)
        val aoty = s.accolades.first { it.kind == AccoladeKind.AOTY }
        assertEquals("https://x/a", linksForAccolade(s, aoty).first().url)
    }

    @Test fun picksFirstUsableSourceWhenEarlierIsEmpty() {
        // An AMG row with no signal parses to null; a later AMG row with a rating wins.
        val t = parseAlbumReception(
            cr(sources = listOf(src("AMG"), src("AMG", rating = 3f))),
            null,
        )
        assertEquals(3f, t.amg?.rating)
    }

    @Test fun accoladeSortPriority() {
        val sorted = sortAccolades(listOf(parseAccolade("TYMHM"), parseAccolade("Album of the Year (2024)")))
        assertEquals(AccoladeKind.AOTY, sorted[0].kind)
        assertEquals(AccoladeKind.TYMHM, sorted[1].kind)
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

    @Test fun formatDrWholeVsDecimal() {
        assertEquals("12", formatDr(12f))
        assertEquals("12.3", formatDr(12.3f))
    }

    @Test fun sortAccoladesTieBreaksByYearDesc() {
        val sorted = sortAccolades(
            listOf(
                parseAccolade("Album of the Year (2019)"),
                parseAccolade("Album of the Year (2024)"),
                parseAccolade("Album of the Year (2021)"),
            ),
        )
        assertEquals(listOf(2024, 2021, 2019), sorted.map { it.year })
    }

    @Test fun hasAnyFalseForEmpty() {
        assertFalse(parseAlbumReception(null, null).hasAny)
    }
}
