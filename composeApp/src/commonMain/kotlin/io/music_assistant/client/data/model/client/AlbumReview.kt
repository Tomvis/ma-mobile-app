package io.music_assistant.client.data.model.client

/** Where an album's review text came from; decides its heading and whether it is signed. */
enum class AlbumReviewKind { CRITIC, PROVIDER_REVIEW, DESCRIPTION }

/**
 * The text the album page shows under its critic data. A [AlbumReviewKind.CRITIC] text is
 * signed "— [authors], [site]", the site linking to [url] when there is one.
 */
data class AlbumReview(
    val text: String,
    val kind: AlbumReviewKind,
    val authors: List<String> = emptyList(),
    val site: String? = null,
    val url: String? = null,
)

/** Site names that sign a critic review, in the order their text is preferred. */
private val REVIEW_SITES = listOf(
    ReviewSourceKind.AMG to "Angry Metal Guy",
    ReviewSourceKind.TPS to "The Progressive Subway",
)

private val EXTRA_BLANK_LINES = Regex("""\n[ \t]*\n(?:[ \t]*\n)+""")

/** Unify line endings and squeeze runs of blank lines to one paragraph break. */
private fun tidy(text: String?): String? = text
    ?.replace("\r\n", "\n")
    ?.replace(EXTRA_BLANK_LINES, "\n\n")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * The album's review: AMG's text, then TPS's (both from our own file tags), then the review
 * or description its metadata providers found. Mirrors the web's `albumReview()`.
 */
fun albumReview(metadata: Metadata?): AlbumReview? {
    val sources = metadata?.criticalReception?.sources.orEmpty()
    return REVIEW_SITES.firstNotNullOfOrNull { (kind, site) ->
        sources.filter { it.source == kind.serverKey }
            .firstNotNullOfOrNull { source -> tidy(source.review)?.let { criticReview(source, it, site) } }
    }
        ?: tidy(metadata?.review)?.let { AlbumReview(it, AlbumReviewKind.PROVIDER_REVIEW) }
        ?: tidy(metadata?.description)?.let { AlbumReview(it, AlbumReviewKind.DESCRIPTION) }
}

private fun criticReview(source: ReviewSource, text: String, site: String): AlbumReview {
    val link = source.links.firstOrNull { it.label == "Review" } ?: source.links.firstOrNull()
    return AlbumReview(
        text = text,
        kind = AlbumReviewKind.CRITIC,
        // AMG's founder writes as "Angry Metal Guy": don't sign his reviews twice.
        authors = source.authors.filter { it.isNotBlank() && it != site },
        site = site,
        url = link?.url?.takeIf { it.isNotBlank() },
    )
}
