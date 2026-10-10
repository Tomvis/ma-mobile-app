package io.music_assistant.client.data.model.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Per-source (AMG / TPS) half of the reception filter. Both sources take the exact same
 * four selectors, so they share one type and one [toRequestArgs] parameterised by the
 * `amg`/`tps` request-arg prefix.
 */
data class SourceFilter(
    // AMG: half-star selectors 0.5..5.0, each matching one exact step ([4.5, 5.0) etc).
    // TPS: band selectors 1,3,5,7,9, each covering two points of the /10 scale.
    val ratings: Set<Double> = emptySet(),
    val accolades: Set<AccoladeKind> = emptySet(),
    val favorite: Boolean = false,
    val untagged: Boolean = false,
) {
    val activeCount: Int
        get() = ratings.size + accolades.size +
            (if (favorite) 1 else 0) + (if (untagged) 1 else 0)

    fun toRequestArgs(prefix: String): Map<String, JsonElement> = buildMap {
        if (ratings.isNotEmpty()) put("${prefix}_ratings", ratings.toSortedDoubleJsonArray())
        if (accolades.isNotEmpty()) {
            put("${prefix}_accolades", accolades.map { it.filterToken }.toSortedJsonArray())
        }
        if (favorite) put("${prefix}_favorite", JsonPrimitive(true))
        if (untagged) put("${prefix}_untagged", JsonPrimitive(true))
    }
}

/**
 * Album critical-reception filter state, mapped to `music/albums/library_items`
 * request args. All collections serialize sorted for deterministic requests.
 */
data class ReceptionFilter(
    val drBuckets: Set<DrQuality> = emptySet(),
    val drUntagged: Boolean = false,             // "untagged" pseudo-bucket, folded into dr_buckets
    val amg: SourceFilter = SourceFilter(),
    val tps: SourceFilter = SourceFilter(),
    val matchAny: Boolean = false,               // false = "all" (AND), true = "any" (OR)
) {
    val isActive: Boolean get() = activeCount > 0

    val activeCount: Int
        get() = drBuckets.size + (if (drUntagged) 1 else 0) + amg.activeCount + tps.activeCount

    fun toRequestArgs(): Map<String, JsonElement> = buildMap {
        // The server takes "untagged" as one more dr_buckets entry, not a separate arg.
        val drTokens = buildList {
            drBuckets.forEach { add(it.filterToken) }
            if (drUntagged) add(UNTAGGED_BUCKET)
        }
        if (drTokens.isNotEmpty()) put("dr_buckets", drTokens.toSortedJsonArray())
        putAll(amg.toRequestArgs("amg"))
        putAll(tps.toRequestArgs("tps"))
        // Match mode only matters alongside actual reception clauses.
        if (matchAny && isActive) put("critical_reception_match", JsonPrimitive("any"))
    }

    companion object {
        private const val UNTAGGED_BUCKET = "untagged"

        // Chip labels for the DR buckets and accolade kinds live in
        // ReceptionFilterAction.kt, their only consumer; the taxonomies themselves are
        // DrQuality/AccoladeKind. These numeric selectors have no enum, so they stay here.
        // Both sources score in half points (AMG 0.5..5.0 stars, TPS 0.5..10) and the
        // server buckets each selector as one exact step, so a 4-star album and a
        // 4½-star one are separately selectable; an off-grid TPS 8.75 lands in 8.5.
        val AMG_RATINGS = (1..10).map { it / 2.0 }
        val TPS_RATINGS = (1..20).map { it / 2.0 }
    }
}

private fun Iterable<String>.toSortedJsonArray(): JsonArray =
    buildJsonArray { sorted().forEach { add(JsonPrimitive(it)) } }

private fun Iterable<Double>.toSortedDoubleJsonArray(): JsonArray =
    buildJsonArray { sorted().forEach { add(JsonPrimitive(it)) } }
