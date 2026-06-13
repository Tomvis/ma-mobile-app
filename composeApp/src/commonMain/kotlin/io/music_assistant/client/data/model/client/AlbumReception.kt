package io.music_assistant.client.data.model.client

import kotlin.math.round

enum class DrQuality { EXCELLENT, GOOD, FAIR, POOR }
enum class DrSource { MEASURED, AMG }

// Normalized accolade categories (TAG_SCHEMA_VERSION 3.2.0+). Dated awards (AOTY /
// RECORD_OF_THE_MONTH / HONORABLE_MENTION) inline their date in the display string;
// the rest are exact review-column / honor strings. UNKNOWN carries through anything
// we don't model. AOTM is gone — it was always the same concept as RECORD_OF_THE_MONTH.
// Declaration order IS display priority: sortAccolades and legacyAccolades both derive
// their ordering from `ordinal`, so keep UNKNOWN last (sorts after everything else).
enum class AccoladeKind {
    AOTY, RECORD_OF_THE_MONTH, HONORABLE_MENTION, SCORE_REVISED,
    LIT, RFU, TYMHM, SITF, YMIO, REVIEW, UNKNOWN,
}
enum class AuthorRole { CANONICAL, SECONDARY, LIST_PICK }

/**
 * Review source identity. [serverKey] is the raw `source` string the server sends;
 * [scale] is the rating denominator (AMG /5, the rest /10). [alwaysShowsStar] drives the
 * compact-pill star policy: AMG always shows it, the others only when there is no rating.
 * Unknown sources resolve to [OTHER].
 */
enum class ReviewSourceKind(val serverKey: String, val scale: Int, val alwaysShowsStar: Boolean) {
    AMG("AMG", 5, alwaysShowsStar = true),
    TPS("TPS", 10, alwaysShowsStar = false),
    OTHER("", 10, alwaysShowsStar = false),
    ;

    companion object {
        fun fromServerKey(key: String): ReviewSourceKind =
            entries.firstOrNull { it != OTHER && it.serverKey == key } ?: OTHER
    }
}

data class DrInfo(val value: Float, val quality: DrQuality, val source: DrSource)
data class AmgDrInfo(val value: Float, val quality: DrQuality)

data class ParsedAccolade(
    val raw: String,           // value as stored/received (3.2.0 display string, or legacy token in transit)
    val kind: AccoladeKind,
    val display: String,       // human-readable string to render (date inlined); == raw for 3.2.0 data
    val year: Int? = null,
    val month: Int? = null,
    val isAward: Boolean = false,  // dated editorial honors get the trophy/accolade styling
)

data class AuthorWithRole(val name: String, val role: AuthorRole)

data class SourceTags(
    val source: String,             // "AMG" | "TPS" | other
    val kind: ReviewSourceKind,     // resolved identity (drives scale + star policy)
    val scale: Int,                 // 5 (AMG) or 10 (TPS)
    val rating: Float?,
    val favorite: Boolean,
    val accolades: List<ParsedAccolade>,
    val links: List<ReviewLink>,    // labeled post links (3.3.0+); label mirrors an accolade
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

private object DrThresholds {
    const val EXCELLENT = 14f
    const val GOOD = 10f
    const val FAIR = 7f
}

fun drQuality(value: Float): DrQuality = when {
    value >= DrThresholds.EXCELLENT -> DrQuality.EXCELLENT
    value >= DrThresholds.GOOD -> DrQuality.GOOD
    value >= DrThresholds.FAIR -> DrQuality.FAIR
    else -> DrQuality.POOR
}

private fun isPositiveFinite(n: Float?): Boolean = n != null && n.isFinite() && n > 0f

private val AWARD_KINDS = setOf(
    AccoladeKind.AOTY, AccoladeKind.RECORD_OF_THE_MONTH, AccoladeKind.HONORABLE_MENTION,
)

private val MONTH_ABBR = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

// Exact, undated accolades — both the 3.2.0 display form and the legacy token map here.
private val EXACT_ACCOLADES: Map<String, Pair<AccoladeKind, String>> = mapOf(
    "Review" to (AccoladeKind.REVIEW to "Review"),
    "TYMHM" to (AccoladeKind.TYMHM to "TYMHM"),
    "SITF" to (AccoladeKind.SITF to "SITF"),
    "YMIO" to (AccoladeKind.YMIO to "YMIO"),
    "Lost in Time" to (AccoladeKind.LIT to "Lost in Time"),
    "LIT" to (AccoladeKind.LIT to "Lost in Time"),
    "RFU" to (AccoladeKind.RFU to "RFU"),
    "Score Revised" to (AccoladeKind.SCORE_REVISED to "Score Revised"),
    "SCORE_REVISED" to (AccoladeKind.SCORE_REVISED to "Score Revised"),
    "Contrite" to (AccoladeKind.SCORE_REVISED to "Score Revised"),
)

private val AOTY_NEW = Regex("""^Album of the Year \((\d{4})\)$""")
private val ROTM_NEW = Regex("""^Record of the Month \(([A-Za-z]{3}) (\d{4})\)$""")
private val HM_NEW = Regex("""^Honorable Mention \((\d{4})\)$""")
private val AOTY_OLD = Regex("""^AOTY-(\d{4})$""")
private val AOTM_OLD = Regex("""^AOTM-(\d{4})-(\d{1,2})$""")
private val HM_OLD = Regex("""^HONORABLE_MENTION-(\d{4})$""")

private fun monthFromAbbr(abbr: String): Int? =
    MONTH_ABBR.indexOf(abbr).let { if (it < 0) null else it + 1 }

// Re-attaches the parsed "(year)" / "(Mon year)" date facet to a (possibly localized)
// accolade name. Shared by parseAccolade's legacy folding and the reception UI, so both
// the inlined display string and the localized chip render the same facet.
internal fun formatDated(name: String, year: Int?, month: Int?): String = when {
    year == null -> name
    month != null && month in 1..12 -> "$name (${MONTH_ABBR[month - 1]} $year)"
    else -> "$name ($year)"
}

private fun mkAccolade(
    kind: AccoladeKind,
    display: String,
    raw: String,
    year: Int? = null,
    month: Int? = null,
): ParsedAccolade = ParsedAccolade(raw, kind, display, year, month, isAward = kind in AWARD_KINDS)

/**
 * Parse one accolade string into its kind + display form. Recognizes the 3.2.0
 * human-readable display strings ("Album of the Year (2024)") and, defensively
 * during the transition, the deprecated machine tokens ("AOTY-2024", …).
 * Unrecognized values pass through as an opaque display label.
 */
fun parseAccolade(raw: String): ParsedAccolade {
    val text = raw.trim()
    EXACT_ACCOLADES[text]?.let { return mkAccolade(it.first, it.second, raw) }

    AOTY_NEW.find(text)?.let {
        return mkAccolade(AccoladeKind.AOTY, text, raw, it.groupValues[1].toInt())
    }
    ROTM_NEW.find(text)?.let {
        return mkAccolade(
            AccoladeKind.RECORD_OF_THE_MONTH, text, raw,
            it.groupValues[2].toInt(), monthFromAbbr(it.groupValues[1]),
        )
    }
    if (text == "Record of the Month") return mkAccolade(AccoladeKind.RECORD_OF_THE_MONTH, text, raw)
    HM_NEW.find(text)?.let {
        return mkAccolade(AccoladeKind.HONORABLE_MENTION, text, raw, it.groupValues[1].toInt())
    }

    // legacy machine tokens (folded to a display form for rendering)
    AOTY_OLD.find(text)?.let {
        val year = it.groupValues[1].toInt()
        return mkAccolade(AccoladeKind.AOTY, formatDated("Album of the Year", year, null), raw, year)
    }
    if (text == "AOTY") return mkAccolade(AccoladeKind.AOTY, "Album of the Year", raw)
    AOTM_OLD.find(text)?.let {
        val year = it.groupValues[1].toInt()
        val month = it.groupValues[2].toInt()
        return mkAccolade(
            AccoladeKind.RECORD_OF_THE_MONTH,
            formatDated("Record of the Month", year, month), raw, year, month,
        )
    }
    if (text == "AOTM" || text == "RECORD_OF_THE_MONTH") {
        return mkAccolade(AccoladeKind.RECORD_OF_THE_MONTH, "Record of the Month", raw)
    }
    HM_OLD.find(text)?.let {
        val year = it.groupValues[1].toInt()
        return mkAccolade(
            AccoladeKind.HONORABLE_MENTION, formatDated("Honorable Mention", year, null), raw, year,
        )
    }
    if (text == "HONORABLE_MENTION") return mkAccolade(AccoladeKind.HONORABLE_MENTION, "Honorable Mention", raw)

    return mkAccolade(AccoladeKind.UNKNOWN, text, raw)
}

fun sortAccolades(accolades: List<ParsedAccolade>): List<ParsedAccolade> = accolades.sortedWith(
    compareBy<ParsedAccolade> { it.kind.ordinal }
        .thenByDescending { it.year ?: 0 }
        .thenByDescending { it.month ?: 0 },
)

/**
 * Fold deprecated split `types` + `labels` tokens into the 3.2.0 `accolades` display
 * list, collapsing the old triple-encoding to one entry per concept (preferring the
 * dated variant). Unrecognized tokens pass through verbatim. Mirrors the server's
 * `legacy_accolades`; used by the mapping boundary for pre-3.2.0 payloads.
 */
fun legacyAccolades(types: List<String>, labels: List<String>): List<String> {
    val byKind = LinkedHashMap<AccoladeKind, ParsedAccolade>()
    val extras = mutableListOf<String>()
    fun dateScore(a: ParsedAccolade) = (if (a.year != null) 2 else 0) + (if (a.month != null) 1 else 0)
    for (token in types + labels) {
        val parsed = parseAccolade(token)
        if (parsed.kind == AccoladeKind.UNKNOWN) {
            if (parsed.display.isNotBlank() && parsed.display !in extras) extras.add(parsed.display)
            continue
        }
        val cur = byKind[parsed.kind]
        if (cur == null || dateScore(parsed) > dateScore(cur)) byKind[parsed.kind] = parsed
    }
    return AccoladeKind.entries.filter { it != AccoladeKind.UNKNOWN }
        .mapNotNull { byKind[it]?.display } + extras
}

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
    val kind = ReviewSourceKind.fromServerKey(entry.source)
    val rating = if (isPositiveFinite(entry.rating)) entry.rating else null
    val favorite = rating == null && entry.favorite == true
    val accolades = sortAccolades(entry.accolades.map(::parseAccolade))
    val links = entry.links.filter { it.url.isNotBlank() }
    val authors = inferAuthorRoles(entry.authors, hasScored = rating != null)
    val hasAny = rating != null || favorite || accolades.isNotEmpty() ||
        links.isNotEmpty() || authors.isNotEmpty()
    if (!hasAny) return null
    return SourceTags(entry.source, kind, kind.scale, rating, favorite, accolades, links, authors)
}

fun parseAlbumReception(cr: CriticalReception?, albumDynamicRange: Float?): ReceptionTags {
    val measured = if (isPositiveFinite(albumDynamicRange)) {
        DrInfo(albumDynamicRange!!, drQuality(albumDynamicRange), DrSource.MEASURED)
    } else {
        null
    }
    val amgRaw = cr?.amgDr?.takeIf { isPositiveFinite(it) }?.let {
        DrInfo(it, drQuality(it), DrSource.AMG)
    }
    val dr = measured ?: amgRaw
    val amgDr = if (measured != null && amgRaw != null &&
        round(amgRaw.value) != round(measured.value)
    ) {
        AmgDrInfo(amgRaw.value, drQuality(amgRaw.value))
    } else {
        null
    }

    val sources = cr?.sources.orEmpty()
    // First *usable* entry per source (a source row with no signal parses to null),
    // mirroring the web's `.map(parseSource).find(defined)`.
    val amg = sources.filter { it.source == ReviewSourceKind.AMG.serverKey }
        .firstNotNullOfOrNull(::parseSource)
    val tps = sources.filter { it.source == ReviewSourceKind.TPS.serverKey }
        .firstNotNullOfOrNull(::parseSource)
    return ReceptionTags(dr, amgDr, amg, tps)
}

/** Links whose label matches an accolade chip (legacy-folded chips keep their raw token). */
fun linksForAccolade(source: SourceTags, accolade: ParsedAccolade): List<ReviewLink> =
    source.links.filter { it.label == accolade.raw || it.label == accolade.display }

/** URL for the review score: the post labeled "Review" (the canonical review). */
fun reviewLink(source: SourceTags): ReviewLink? = source.links.firstOrNull { it.label == "Review" }

/** Formats a rating to exactly one decimal place for display (4f -> "4.0", 8.4f -> "8.4"). */
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
