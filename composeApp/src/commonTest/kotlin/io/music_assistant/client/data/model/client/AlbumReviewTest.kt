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
    ) = ReviewSource(source, null, null, emptyList(), links, authors, review)

    @Test fun amgBeatsTpsAndProviderText() {
        val r = albumReview(
            md(
                sources = listOf(src("TPS", "tps text"), src("AMG", "amg text", listOf("Steel Druhm"))),
                review = "provider review",
                description = "description",
            ),
        )!!
        assertEquals("amg text", r.text)
        assertEquals(AlbumReviewKind.CRITIC, r.kind)
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

    @Test fun providerReviewThenDescription() {
        assertEquals(
            AlbumReview("provider review", AlbumReviewKind.PROVIDER_REVIEW),
            albumReview(md(review = "provider review", description = "description")),
        )
        assertEquals(
            AlbumReview("description", AlbumReviewKind.DESCRIPTION),
            albumReview(md(review = " ", description = "description")),
        )
    }

    @Test fun nothingWhenNoText() {
        assertNull(albumReview(md()))
        assertNull(albumReview(null))
    }

    @Test fun keepsParagraphBreaksAndSqueezesExtraBlankLines() {
        val r = albumReview(md(description = "\r\nOne.\r\n\r\n\r\nTwo\nlines.\n \n\nThree.\n"))!!
        assertEquals("One.\n\nTwo\nlines.\n\nThree.", r.text)
    }
}
