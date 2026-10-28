package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumReviewTest {
    private fun md(
        sources: List<ReviewSource> = emptyList(),
        review: String? = null,
        description: String? = null,
    ) = Metadata(
        explicit = false,
        images = emptyList(),
        releaseDate = null,
        chapters = emptyList(),
        lyrics = null,
        lrcLyrics = null,
        criticalReception = CriticalReception(null, sources),
        review = review,
        description = description,
    )

    private fun src(
        source: String,
        review: String?,
        authors: List<String> = emptyList(),
        links: List<ReviewLink> = emptyList(),
        reviewType: String? = null,
    ) = ReviewSource(source, null, null, emptyList(), links, authors, review, reviewType)

    @Test fun tpsReviewBeatsAmgTymhm() {
        val r = albumReview(md(sources = listOf(src("AMG", "tymhm", reviewType = "tymhm"), src("TPS", "rev", reviewType = "review"))))!!
        assertEquals("rev", r.text)
        assertEquals("The Progressive Subway", r.site)
    }

    @Test fun fallbackOrderIsTymhmRfuYmioLit() {
        fun pick(vararg s: ReviewSource) = albumReview(md(sources = s.toList()))!!.text
        assertEquals("t", pick(src("TPS", "r", reviewType = "rfu"), src("AMG", "t", reviewType = "tymhm")))
        assertEquals("r", pick(src("AMG", "y", reviewType = "ymio"), src("TPS", "r", reviewType = "rfu")))
        assertEquals("y", pick(src("TPS", "l", reviewType = "lit"), src("AMG", "y", reviewType = "ymio")))
    }

    @Test fun untypedTextRanksAsAReview() {
        assertEquals("tps", albumReview(md(sources = listOf(src("AMG", "amg", reviewType = "tymhm"), src("TPS", "tps"))))!!.text)
    }

    @Test fun amgBeatsTpsAndProviderText() {
        val r = albumReview(
            md(
                sources = listOf(src("TPS", "tps text"), src("AMG", "amg text", listOf("Steel Druhm"))),
                review = "provider review",
                description = "description",
            ),
        )!!
        assertEquals("amg text", r.text)
        assertEquals("Angry Metal Guy", r.site)
        assertEquals(listOf("Steel Druhm"), r.authors)
    }

    @Test fun tpsWhenAmgHasNoText() {
        val r = albumReview(md(sources = listOf(src("AMG", "  "), src("TPS", "tps text", listOf("Doug")))))!!
        assertEquals("tps text", r.text)
        assertEquals("The Progressive Subway", r.site)
        assertEquals(listOf("Doug"), r.authors)
    }

    @Test fun authorEqualToSiteIsDropped() {
        val r = albumReview(md(sources = listOf(src("AMG", "text", listOf("Angry Metal Guy")))))!!
        assertEquals(emptyList(), r.authors)
    }

    @Test fun linkPrefersReviewPostThenFirst() {
        val links = listOf(ReviewLink("Album of the Year (2011)", "https://aoty"), ReviewLink("Review", "https://rev"))
        assertEquals("https://rev", albumReview(md(sources = listOf(src("AMG", "t", links = links))))!!.url)
        val other = listOf(ReviewLink("Album of the Year (2011)", "https://aoty"))
        assertEquals("https://aoty", albumReview(md(sources = listOf(src("AMG", "t", links = other))))!!.url)
        assertNull(albumReview(md(sources = listOf(src("AMG", "t"))))!!.url)
    }

    @Test fun nothingWithoutCriticText() {
        // The providers' review and description are upstream's header text block (#1277).
        assertNull(albumReview(md(review = "provider review", description = "description")))
        assertNull(albumReview(md()))
        assertNull(albumReview(null))
    }

    @Test fun keepsParagraphBreaksAndSqueezesExtraBlankLines() {
        val r = albumReview(md(sources = listOf(src("AMG", "\r\nOne.\r\n\r\n\r\nTwo\nlines.\n \n\nThree.\n"))))!!
        assertEquals("One.\n\nTwo\nlines.\n\nThree.", r.text)
    }
}
