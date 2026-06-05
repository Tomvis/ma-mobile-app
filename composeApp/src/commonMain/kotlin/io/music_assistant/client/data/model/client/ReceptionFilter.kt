package io.music_assistant.client.data.model.client

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
    val amgLabels: Set<String> = emptySet(),     // aoty/aotm/honorable_mention/record_of_the_month
    val amgFavorite: Boolean = false,
    val amgUntagged: Boolean = false,
    val tpsRatings: Set<Int> = emptySet(),       // band selectors 1,3,5,7,9
    val tpsLabels: Set<String> = emptySet(),
    val tpsFavorite: Boolean = false,
    val tpsUntagged: Boolean = false,
    val matchAny: Boolean = false,               // false = "all" (AND), true = "any" (OR)
) {
    val isActive: Boolean
        get() = drBuckets.isNotEmpty() || amgRatings.isNotEmpty() || amgLabels.isNotEmpty() ||
            tpsRatings.isNotEmpty() || tpsLabels.isNotEmpty() ||
            amgFavorite || amgUntagged || tpsFavorite || tpsUntagged

    val activeCount: Int
        get() = drBuckets.size + amgRatings.size + amgLabels.size +
            tpsRatings.size + tpsLabels.size +
            listOf(amgFavorite, amgUntagged, tpsFavorite, tpsUntagged).count { it }

    fun toRequestArgs(): Map<String, JsonElement> = buildMap {
        if (drBuckets.isNotEmpty()) {
            put("dr_buckets", buildJsonArray { drBuckets.sorted().forEach { add(JsonPrimitive(it)) } })
        }
        if (amgRatings.isNotEmpty()) {
            put("amg_ratings", buildJsonArray { amgRatings.sorted().forEach { add(JsonPrimitive(it)) } })
        }
        if (amgLabels.isNotEmpty()) {
            put("amg_labels", buildJsonArray { amgLabels.sorted().forEach { add(JsonPrimitive(it)) } })
        }
        if (amgFavorite) put("amg_favorite", JsonPrimitive(true))
        if (amgUntagged) put("amg_untagged", JsonPrimitive(true))
        if (tpsRatings.isNotEmpty()) {
            put("tps_ratings", buildJsonArray { tpsRatings.sorted().forEach { add(JsonPrimitive(it)) } })
        }
        if (tpsLabels.isNotEmpty()) {
            put("tps_labels", buildJsonArray { tpsLabels.sorted().forEach { add(JsonPrimitive(it)) } })
        }
        if (tpsFavorite) put("tps_favorite", JsonPrimitive(true))
        if (tpsUntagged) put("tps_untagged", JsonPrimitive(true))
        if (matchAny) put("critical_reception_match", JsonPrimitive("any"))
    }

    companion object {
        val DR_BUCKETS = listOf("excellent", "good", "fair", "poor", "untagged")
        val AMG_RATINGS = listOf(1, 2, 3, 4, 5)
        val TPS_BANDS = listOf(1, 3, 5, 7, 9)
        val ACCOLADE_LABELS = listOf("aoty", "aotm", "honorable_mention", "record_of_the_month")
    }
}
