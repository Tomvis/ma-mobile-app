package io.music_assistant.client.data.model.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * Album critical-reception filter state, mapped to `music/albums/library_items`
 * request args. All collections serialize sorted for deterministic requests.
 */
data class ReceptionFilter(
    val drBuckets: Set<String> = emptySet(),    // excellent/good/fair/poor/untagged
    val amgRatings: Set<Int> = emptySet(),       // 1..5
    val amgAccolades: Set<String> = emptySet(),  // aoty/record_of_the_month/honorable_mention
    val amgFavorite: Boolean = false,
    val amgUntagged: Boolean = false,
    val tpsRatings: Set<Int> = emptySet(),       // band selectors 1,3,5,7,9
    val tpsAccolades: Set<String> = emptySet(),
    val tpsFavorite: Boolean = false,
    val tpsUntagged: Boolean = false,
    val matchAny: Boolean = false,               // false = "all" (AND), true = "any" (OR)
) {
    val isActive: Boolean get() = activeCount > 0

    val activeCount: Int
        get() = drBuckets.size + amgRatings.size + amgAccolades.size +
            tpsRatings.size + tpsAccolades.size +
            (if (amgFavorite) 1 else 0) + (if (amgUntagged) 1 else 0) +
            (if (tpsFavorite) 1 else 0) + (if (tpsUntagged) 1 else 0)

    fun toRequestArgs(): Map<String, JsonElement> = buildMap {
        if (drBuckets.isNotEmpty()) put("dr_buckets", drBuckets.toSortedJsonArray())
        if (amgRatings.isNotEmpty()) put("amg_ratings", amgRatings.toSortedIntJsonArray())
        if (amgAccolades.isNotEmpty()) put("amg_accolades", amgAccolades.toSortedJsonArray())
        if (amgFavorite) put("amg_favorite", JsonPrimitive(true))
        if (amgUntagged) put("amg_untagged", JsonPrimitive(true))
        if (tpsRatings.isNotEmpty()) put("tps_ratings", tpsRatings.toSortedIntJsonArray())
        if (tpsAccolades.isNotEmpty()) put("tps_accolades", tpsAccolades.toSortedJsonArray())
        if (tpsFavorite) put("tps_favorite", JsonPrimitive(true))
        if (tpsUntagged) put("tps_untagged", JsonPrimitive(true))
        // Match mode only matters alongside actual reception clauses.
        if (matchAny && isActive) put("critical_reception_match", JsonPrimitive("any"))
    }

    companion object {
        val DR_BUCKETS = listOf("excellent", "good", "fair", "poor", "untagged")
        val AMG_RATINGS = listOf(1, 2, 3, 4, 5)
        val TPS_BANDS = listOf(1, 3, 5, 7, 9)

        // Accolade kinds the server can filter on (3.2.0 merged shape); shared by AMG
        // and TPS. Awards first, then review-column kinds. AOTM is gone — folded into
        // record_of_the_month. "review" is the default column and not a useful filter.
        val ACCOLADE_KINDS = listOf(
            "aoty", "record_of_the_month", "honorable_mention", "score_revised",
            "tymhm", "sitf", "ymio", "lit", "rfu",
        )
    }
}

private fun Set<String>.toSortedJsonArray(): JsonArray =
    buildJsonArray { sorted().forEach { add(JsonPrimitive(it)) } }

private fun Set<Int>.toSortedIntJsonArray(): JsonArray =
    buildJsonArray { sorted().forEach { add(JsonPrimitive(it)) } }
