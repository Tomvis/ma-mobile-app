package io.music_assistant.client.data.model.client

/**
 * Client-side critical-reception data for an album, mapped from
 * [io.music_assistant.client.data.model.server.ServerCriticalReception].
 * Unusable (blank-source) entries are dropped at the mapping boundary, which also
 * folds any pre-3.2.0 `types`/`labels` into `accolades` and a pre-3.3.0 `review_url`
 * into a "Review" link — so the client model only ever carries the current shape.
 */
data class CriticalReception(
    val amgDr: Float?,
    val sources: List<ReviewSource>,
)

data class ReviewSource(
    val source: String,            // "AMG" | "TPS" | other
    val rating: Float?,
    val favorite: Boolean?,
    // editorial honors as human-readable display strings (3.2.0+), e.g.
    // ["Review", "Album of the Year (2024)"].
    val accolades: List<String>,
    // labeled post links (3.3.0+), one per post; `label` mirrors an `accolades` value.
    val links: List<ReviewLink>,
    val authors: List<String>,
    // the review's full text, plain with blank-line paragraph breaks (3.6.0+).
    val review: String? = null,
)

/** One labeled post link for a review source (3.3.0+). Several links can share a label. */
data class ReviewLink(
    val label: String,
    val url: String,
)
