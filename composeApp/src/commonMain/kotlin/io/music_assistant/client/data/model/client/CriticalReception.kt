package io.music_assistant.client.data.model.client

/**
 * Client-side critical-reception data for an album, mapped from
 * [io.music_assistant.client.data.model.server.ServerCriticalReception].
 * Unusable (blank-source) entries are dropped at the mapping boundary.
 */
data class CriticalReception(
    val amgDr: Float?,
    val sources: List<ReviewSource>,
)

data class ReviewSource(
    val source: String,            // "AMG" | "TPS" | other
    val rating: Float?,
    val favorite: Boolean?,
    val types: List<String>,
    val labels: List<String>,
    val authors: List<String>,
)
