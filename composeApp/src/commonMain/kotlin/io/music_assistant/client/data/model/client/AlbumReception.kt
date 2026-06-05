package io.music_assistant.client.data.model.client

import kotlin.math.round

enum class DrQuality { EXCELLENT, GOOD, FAIR, POOR }
enum class DrSource { MEASURED, AMG }
enum class LabelKind {
    RECORD_OF_THE_MONTH, AOTY, AOTM, HONORABLE_MENTION, SCORE_REVISED,
    TYMHM, SITF, YMIO, LIT, RFU, UNKNOWN,
}
enum class AuthorRole { CANONICAL, SECONDARY, LIST_PICK }

data class DrInfo(val value: Float, val quality: DrQuality, val source: DrSource)
data class AmgDrInfo(val value: Float, val quality: DrQuality)
data class ParsedLabel(val raw: String, val kind: LabelKind, val year: Int? = null, val month: Int? = null)
data class AuthorWithRole(val name: String, val role: AuthorRole)

data class SourceTags(
    val source: String,
    val scale: Int,                 // 5 (AMG) or 10 (TPS)
    val rating: Float?,
    val favorite: Boolean,
    val types: List<String>,
    val labels: List<ParsedLabel>,
    val authors: List<AuthorWithRole>,
)

data class ReceptionTags(
    val dr: DrInfo?,
    val amgDr: AmgDrInfo?,
    val amg: SourceTags?,
    val tps: SourceTags?,
) {
    val hasAny: Boolean get() = dr != null || amgDr != null || amg != null || tps != null
}

private object DrThresholds { const val EXCELLENT = 14f; const val GOOD = 10f; const val FAIR = 7f }

fun drQuality(value: Float): DrQuality = when {
    value >= DrThresholds.EXCELLENT -> DrQuality.EXCELLENT
    value >= DrThresholds.GOOD -> DrQuality.GOOD
    value >= DrThresholds.FAIR -> DrQuality.FAIR
    else -> DrQuality.POOR
}

private fun isPositiveFinite(n: Float?): Boolean = n != null && n.isFinite() && n > 0f

private val LABEL_PRIORITY: Map<LabelKind, Int> = mapOf(
    LabelKind.AOTY to 0, LabelKind.RECORD_OF_THE_MONTH to 1, LabelKind.AOTM to 2,
    LabelKind.HONORABLE_MENTION to 3, LabelKind.SCORE_REVISED to 4, LabelKind.LIT to 5,
    LabelKind.RFU to 6, LabelKind.TYMHM to 7, LabelKind.SITF to 8, LabelKind.YMIO to 9,
    LabelKind.UNKNOWN to 100,
)

private val AOTY_RE = Regex("""^AOTY-(\d{4})$""")
private val AOTM_RE = Regex("""^AOTM-(\d{4})-(\d{1,2})$""")
private val HM_RE = Regex("""^HONORABLE_MENTION-(\d{4})$""")

fun parseLabel(raw: String): ParsedLabel = when (raw) {
    "RECORD_OF_THE_MONTH" -> ParsedLabel(raw, LabelKind.RECORD_OF_THE_MONTH)
    "SCORE_REVISED" -> ParsedLabel(raw, LabelKind.SCORE_REVISED)
    "TYMHM" -> ParsedLabel(raw, LabelKind.TYMHM)
    "SITF" -> ParsedLabel(raw, LabelKind.SITF)
    "YMIO" -> ParsedLabel(raw, LabelKind.YMIO)
    "LIT" -> ParsedLabel(raw, LabelKind.LIT)
    "RFU" -> ParsedLabel(raw, LabelKind.RFU)
    else -> {
        AOTY_RE.find(raw)?.let { return ParsedLabel(raw, LabelKind.AOTY, year = it.groupValues[1].toInt()) }
        AOTM_RE.find(raw)?.let {
            return ParsedLabel(raw, LabelKind.AOTM, year = it.groupValues[1].toInt(), month = it.groupValues[2].toInt())
        }
        HM_RE.find(raw)?.let { return ParsedLabel(raw, LabelKind.HONORABLE_MENTION, year = it.groupValues[1].toInt()) }
        ParsedLabel(raw, LabelKind.UNKNOWN)
    }
}

fun sortLabels(labels: List<ParsedLabel>): List<ParsedLabel> = labels.sortedWith(
    compareBy<ParsedLabel> { LABEL_PRIORITY[it.kind] ?: 100 }
        .thenByDescending { it.year ?: 0 }
        .thenByDescending { it.month ?: 0 },
)

fun inferAuthorRoles(names: List<String>, hasScored: Boolean): List<AuthorWithRole> =
    names.mapIndexed { idx, name ->
        val role = when {
            !hasScored -> AuthorRole.LIST_PICK
            idx == 0 -> AuthorRole.CANONICAL
            idx == 1 -> AuthorRole.SECONDARY
            else -> AuthorRole.LIST_PICK
        }
        AuthorWithRole(name, role)
    }

private fun parseSource(entry: ReviewSource): SourceTags? {
    val scale = if (entry.source == "AMG") 5 else 10
    val rating = if (isPositiveFinite(entry.rating)) entry.rating else null
    val favorite = rating == null && entry.favorite == true
    val types = entry.types
    val labels = sortLabels(entry.labels.map(::parseLabel))
    val authors = inferAuthorRoles(entry.authors, hasScored = rating != null)
    val hasAny = rating != null || favorite || types.isNotEmpty() || labels.isNotEmpty() || authors.isNotEmpty()
    if (!hasAny) return null
    return SourceTags(entry.source, scale, rating, favorite, types, labels, authors)
}

fun parseAlbumReception(cr: CriticalReception?, albumDynamicRange: Float?): ReceptionTags {
    val measured = if (isPositiveFinite(albumDynamicRange)) {
        DrInfo(albumDynamicRange!!, drQuality(albumDynamicRange), DrSource.MEASURED)
    } else null
    val amgRaw = cr?.amgDr?.takeIf { isPositiveFinite(it) }?.let {
        DrInfo(it, drQuality(it), DrSource.AMG)
    }
    val dr = measured ?: amgRaw
    val amgDr = if (measured != null && amgRaw != null &&
        round(amgRaw.value) != round(measured.value)
    ) {
        AmgDrInfo(amgRaw.value, drQuality(amgRaw.value))
    } else null

    val sources = cr?.sources.orEmpty()
    val amg = sources.firstOrNull { it.source == "AMG" }?.let(::parseSource)
    val tps = sources.firstOrNull { it.source == "TPS" }?.let(::parseSource)
    return ReceptionTags(dr, amgDr, amg, tps)
}

/** Mirrors the web `formatScore`: integers render with one decimal (4 -> "4.0"). */
fun formatScore(n: Float): String {
    val rounded = round(n * 10f) / 10f
    val whole = rounded.toInt()
    val dec = round((rounded - whole) * 10f).toInt()
    return "$whole.$dec"
}

/** DR value: drop the decimal when whole (12.0 -> "12"), else one decimal. */
fun formatDr(value: Float): String {
    val rounded = round(value * 10f) / 10f
    return if (rounded % 1f == 0f) rounded.toInt().toString() else formatScore(rounded)
}
