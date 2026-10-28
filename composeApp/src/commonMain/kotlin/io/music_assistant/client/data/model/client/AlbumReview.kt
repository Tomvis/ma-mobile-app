package io.music_assistant.client.data.model.client

/**
 * The critic review the album page shows under its critic data, signed "— [authors], [site]",
 * the site linking to [url] when there is one.
 */
data class AlbumReview(
    val text: String,
    val site: String,
    val authors: List<String> = emptyList(),
    val url: String? = null,
)

/** Site names that sign a critic review; the first wins among equally ranked texts. */
private val REVIEW_SITES = listOf(
    ReviewSourceKind.AMG to "Angry Metal Guy",
    ReviewSourceKind.TPS to "The Progressive Subway",
)

/**
 * Post types whose text a review source carries, best first (MUSIC-182): a review, then
 * AMG's TYMHM, TPS's RFU, AMG's YMIO, TPS's LIT. Untyped text predates the type tag and
 * ranks as a review.
 */
private val REVIEW_TYPE_RANK = listOf("review", "tymhm", "rfu", "ymio", "lit")

private fun reviewRank(source: ReviewSource): Int =
    REVIEW_TYPE_RANK.indexOf(source.reviewType ?: "review").let { if (it < 0) REVIEW_TYPE_RANK.size else it }

private val EXTRA_BLANK_LINES = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")

/** Unify line endings and squeeze runs of blank lines to one paragraph break. */
private fun tidy(text: String?): String? = text
    ?.replace("\r\n", "\n")
    ?.replace(EXTRA_BLANK_LINES, "\n\n")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * The album's critic review: the best-ranked AMG/TPS text (from our own file tags), AMG's
 * first among equals. The metadata providers' review or description is upstream's text block
 * under the header (#1277), so it is not repeated here.
 */
fun albumReview(metadata: Metadata?): AlbumReview? {
    val sources = metadata?.criticalReception?.sources.orEmpty()
    return REVIEW_SITES.withIndex().flatMap { (order, site) ->
        sources.filter { it.source == site.first.serverKey }.mapNotNull { source ->
            tidy(source.review)?.let { Triple(reviewRank(source), order, criticReview(source, it, site.second)) }
        }
    }.minWithOrNull(compareBy({ it.first }, { it.second }))?.third
}

private fun criticReview(source: ReviewSource, text: String, site: String): AlbumReview {
    val link = source.links.firstOrNull { it.label == "Review" } ?: source.links.firstOrNull()
    return AlbumReview(
        text = text,
        // AMG's founder writes as "Angry Metal Guy": don't sign his reviews twice.
        authors = source.authors.filter { it.isNotBlank() && it != site },
        site = site,
        url = link?.url?.takeIf { it.isNotBlank() },
    )
}
