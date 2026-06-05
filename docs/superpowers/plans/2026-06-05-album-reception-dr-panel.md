# Album Reception & DR Panel Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show an album's critical-reception (AMG 5-pt / TPS 10-pt ratings, reviews) and dynamic-range (DR) metadata in a Material 3 "Reception" panel on the mobile album detail screen.

**Architecture:** Extend the wire model (`ServerMetadata`) and client model (`Metadata`) with `dynamic_range` + `critical_reception`, mapped in `MediaItemFactory` (blank-source entries dropped at the boundary). A pure, unit-tested parser (`AlbumReception.kt`, a Kotlin port of the web's `album_tags.ts`) turns that data into a `ReceptionTags` view model. A new `ReceptionPanel` composable renders it, mounted as part of the album detail's hero slot.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material 3), kotlinx.serialization, kotlin.test, Compose Resources (strings).

**Conventions:**
- Test framework: `kotlin.test` (`@Test`, `assertEquals`, `assertNull`, `assertTrue`). JSON instance: `io.music_assistant.client.utils.myJson`. Factory tests use `MediaItemFactory(FakeClient())` (see existing `ServerMediaItemSerializationTest.kt`).
- Run common unit tests: `./gradlew :composeApp:testAndroidHostTest` (this AGP-9 KMP-library module names the host unit-test task `testAndroidHostTest`, per CI — NOT `testDebugUnitTest`). Supports `--tests "*Name*"` filtering. Compile/build check: `./gradlew :androidApp:assembleDebug` (authoritative full build used by CI; composeApp compiles as part of it). Static analysis (CI-enforced): `./gradlew detektAll`.
- Toolchain note: CLI Gradle is pinned to the Android Studio JBR (JDK 21) via `~/.gradle/gradle.properties` (`org.gradle.java.home`); the system PATH `java` is a headless JRE. `local.properties` (gitignored) points at the Android SDK. Do NOT commit either of those files.
- Commit per task (frequent commits). All work on the current `enhanced` branch.

**Reference (do not import; reimplement):** `../../../frontend/src/helpers/album_tags.ts`, `../../../frontend/src/components/album/CriticalReception.vue`.

---

### Task 1: Wire model — `ServerMetadata` gains `dynamic_range` + `critical_reception`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/server/ServerMediaItem.kt`
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/server/CriticalReceptionSerializationTest.kt`

- [ ] **Step 1: Write the failing test**

Create `CriticalReceptionSerializationTest.kt`:

```kotlin
package io.music_assistant.client.data.model.server

import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CriticalReceptionSerializationTest {

    private val metadataJson = """
        {
          "dynamic_range": 12.0,
          "critical_reception": {
            "amg_dr": 11.0,
            "sources": [
              {"source": "AMG", "rating": 4.5, "types": ["Review", "TYMHM"],
               "labels": ["AOTY-2024"], "authors": ["J. Smith"]},
              {"source": "TPS", "rating": 8.4},
              {"source": null, "rating": 9.9}
            ]
          },
          "last_refresh": null
        }
    """.trimIndent()

    @Test
    fun decodesDynamicRangeAndCriticalReception() {
        val md = myJson.decodeFromString<ServerMetadata>(metadataJson)

        assertEquals(12.0, md.dynamicRange)
        assertEquals(11.0, md.criticalReception?.amgDr)
        assertEquals(3, md.criticalReception?.sources?.size)
        assertEquals("AMG", md.criticalReception?.sources?.get(0)?.source)
        assertEquals(4.5, md.criticalReception?.sources?.get(0)?.rating)
        assertEquals(listOf("Review", "TYMHM"), md.criticalReception?.sources?.get(0)?.types)
        // null source decodes to "" (default), not a crash
        assertEquals("", md.criticalReception?.sources?.get(2)?.source)
    }

    @Test
    fun decodesMetadataWithoutNewFields() {
        val md = myJson.decodeFromString<ServerMetadata>("""{"last_refresh": null}""")
        assertNull(md.dynamicRange)
        assertNull(md.criticalReception)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*CriticalReceptionSerializationTest*"`
Expected: FAIL — `ServerMetadata` has no `dynamicRange`/`criticalReception`; `ServerCriticalReception` unresolved.

- [ ] **Step 3: Add the wire fields and types**

In `ServerMediaItem.kt`, add two fields to `ServerMetadata` (next to the other metadata fields, before `lastRefresh`):

```kotlin
    @SerialName("dynamic_range") val dynamicRange: Double? = null,
    @SerialName("critical_reception") val criticalReception: ServerCriticalReception? = null,
```

Add these new types in the same file (after `ServerMetadata`):

```kotlin
@Serializable
data class ServerCriticalReception(
    @SerialName("amg_dr") val amgDr: Double? = null,
    @SerialName("sources") val sources: List<ServerReviewSourceEntry>? = null,
)

@Serializable
data class ServerReviewSourceEntry(
    // Defaults to "" so a null/missing source still decodes (mashumaro coerces
    // wire null to "None" server-side; we drop unusable entries in the mapper).
    @SerialName("source") val source: String = "",
    @SerialName("rating") val rating: Double? = null,
    @SerialName("favorite") val favorite: Boolean? = null,
    @SerialName("types") val types: List<String>? = null,
    @SerialName("labels") val labels: List<String>? = null,
    @SerialName("authors") val authors: List<String>? = null,
)
```

Note: `myJson` must tolerate the explicit `"source": null` → default. Confirm `myJson` is configured with `coerceInputValues = true` (it ignores unknown keys already). If the null-source assertion fails because kotlinx doesn't coerce explicit null to default, change the test's third source to omit the key (`{"rating": 9.9}`) and rely on the mapper's blank-drop in Task 2 — but prefer enabling `coerceInputValues = true` on `myJson` in `utils/` if not already set.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*CriticalReceptionSerializationTest*"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/server/ServerMediaItem.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/server/CriticalReceptionSerializationTest.kt
git commit -m "feat(data): decode album dynamic_range + critical_reception from server"
```

---

### Task 2: Client model + mapper — `Metadata` carries reception; blank sources dropped

**Files:**
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/CriticalReception.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/Metadata.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/factory/MediaItemFactory.kt` (`createMetadata`, ~line 207)
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/AlbumMetadataMappingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AlbumMetadataMappingTest.kt`:

```kotlin
package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.FakeClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumMetadataMappingTest {
    private val factory = MediaItemFactory(FakeClient())

    private fun albumJson() = """
        {"item_id":"a1","provider":"library","name":"Album",
         "media_type":"${MediaType.ALBUM.serverValue}",
         "metadata":{
           "dynamic_range": 12.0,
           "critical_reception": {
             "amg_dr": 11.0,
             "sources": [
               {"source":"AMG","rating":4.5},
               {"source":"","rating":9.9}
             ]
           },
           "last_refresh": null
         }}
    """.trimIndent()

    @Test
    fun mapsReceptionAndDropsBlankSource() {
        val album = factory.create(
            myJson.decodeFromString<ServerMediaItem>(albumJson()),
        ) as Album

        assertEquals(12f, album.metadata?.dynamicRange)
        assertEquals(11f, album.metadata?.criticalReception?.amgDr)
        // blank-source entry dropped; only AMG survives
        assertEquals(1, album.metadata?.criticalReception?.sources?.size)
        assertEquals("AMG", album.metadata?.criticalReception?.sources?.get(0)?.source)
        assertEquals(4.5f, album.metadata?.criticalReception?.sources?.get(0)?.rating)
    }

    @Test
    fun nullMetadataMapsNull() {
        val album = factory.create(
            myJson.decodeFromString<ServerMediaItem>(
                """{"item_id":"a2","provider":"library","name":"A","media_type":"${MediaType.ALBUM.serverValue}"}""",
            ),
        ) as Album
        assertNull(album.metadata)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumMetadataMappingTest*"`
Expected: FAIL — `Metadata` has no `dynamicRange`/`criticalReception`; `CriticalReception` client type unresolved.

- [ ] **Step 3: Add client models**

Create `CriticalReception.kt`:

```kotlin
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
```

Extend `Metadata` (add two fields):

```kotlin
data class Metadata(
    val explicit: Boolean,
    val images: List<ImageInfo>,
    val releaseDate: String?,
    val chapters: List<Chapter>,
    val dynamicRange: Float?,
    val criticalReception: CriticalReception?,
)
```

- [ ] **Step 4: Map the new fields in `MediaItemFactory.createMetadata`**

Replace `createMetadata` (~line 207):

```kotlin
    private fun createMetadata(server: ServerMetadata?): Metadata? = server?.let {
        Metadata(
            explicit = it.explicit == true,
            images = it.images?.map(::createImageInfo).orEmpty(),
            releaseDate = it.releaseDate,
            chapters = it.chapters?.map(::createChapter).orEmpty(),
            dynamicRange = it.dynamicRange?.toFloat(),
            criticalReception = createCriticalReception(it.criticalReception),
        )
    }

    private fun createCriticalReception(server: ServerCriticalReception?): CriticalReception? {
        if (server == null) return null
        val sources = server.sources
            ?.filter { it.source.isNotBlank() && it.source != "None" }
            ?.map { entry ->
                ReviewSource(
                    source = entry.source,
                    rating = entry.rating?.toFloat(),
                    favorite = entry.favorite,
                    types = entry.types.orEmpty(),
                    labels = entry.labels.orEmpty(),
                    authors = entry.authors.orEmpty(),
                )
            }
            .orEmpty()
        if (server.amgDr == null && sources.isEmpty()) return null
        return CriticalReception(amgDr = server.amgDr?.toFloat(), sources = sources)
    }
```

Add imports to `MediaItemFactory.kt`:
```kotlin
import io.music_assistant.client.data.model.client.CriticalReception
import io.music_assistant.client.data.model.client.ReviewSource
import io.music_assistant.client.data.model.server.ServerCriticalReception
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumMetadataMappingTest*"`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/CriticalReception.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/Metadata.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/factory/MediaItemFactory.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/AlbumMetadataMappingTest.kt
git commit -m "feat(data): map dynamic_range + critical_reception into client Metadata"
```

---

### Task 3: Parsing logic — `AlbumReception.kt` (port of `album_tags.ts`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/AlbumReception.kt`
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/AlbumReceptionTest.kt`

- [ ] **Step 1: Write the failing tests**

Create `AlbumReceptionTest.kt`:

```kotlin
package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class AlbumReceptionTest {

    private fun cr(
        amgDr: Float? = null,
        sources: List<ReviewSource> = emptyList(),
    ) = CriticalReception(amgDr, sources)

    private fun src(
        source: String,
        rating: Float? = null,
        favorite: Boolean? = null,
        types: List<String> = emptyList(),
        labels: List<String> = emptyList(),
        authors: List<String> = emptyList(),
    ) = ReviewSource(source, rating, favorite, types, labels, authors)

    @Test fun drBands() {
        assertEquals(DrQuality.EXCELLENT, drQuality(14f))
        assertEquals(DrQuality.GOOD, drQuality(13.9f))
        assertEquals(DrQuality.GOOD, drQuality(10f))
        assertEquals(DrQuality.FAIR, drQuality(9.9f))
        assertEquals(DrQuality.FAIR, drQuality(7f))
        assertEquals(DrQuality.POOR, drQuality(6.9f))
    }

    @Test fun measuredDrWins() {
        val t = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = 12f)
        assertEquals(12f, t.dr?.value)
        assertEquals(DrSource.MEASURED, t.dr?.source)
    }

    @Test fun amgDrFallbackWhenNoMeasured() {
        val t = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = null)
        assertEquals(8f, t.dr?.value)
        assertEquals(DrSource.AMG, t.dr?.source)
    }

    @Test fun divergenceOnlyWhenBothAndRoundsDiffer() {
        // both present, round differs (12 vs 8) -> amgDr caption present
        val diverge = parseAlbumReception(cr(amgDr = 8f), albumDynamicRange = 12f)
        assertEquals(8f, diverge.amgDr?.value)
        // both present, round equal (12 vs 11.6 -> 12) -> no caption
        val agree = parseAlbumReception(cr(amgDr = 11.6f), albumDynamicRange = 12f)
        assertNull(agree.amgDr)
        // only one present -> no caption
        assertNull(parseAlbumReception(cr(amgDr = 8f), null).amgDr)
    }

    @Test fun amgScale5_tps Scale10() {
        val t = parseAlbumReception(
            cr(sources = listOf(src("AMG", rating = 4.5f), src("TPS", rating = 8.4f))),
            null,
        )
        assertEquals(5, t.amg?.scale)
        assertEquals(4.5f, t.amg?.rating)
        assertEquals(10, t.tps?.scale)
        assertEquals(8.4f, t.tps?.rating)
    }

    @Test fun ratingWinsOverFavorite() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG", rating = 4f, favorite = true))), null)
        assertEquals(4f, t.amg?.rating)
        assertFalse(t.amg?.favorite ?: true)
    }

    @Test fun favoriteWhenNoRating() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG", favorite = true))), null)
        assertNull(t.amg?.rating)
        assertTrue(t.amg?.favorite ?: false)
    }

    @Test fun emptySourceDropped() {
        val t = parseAlbumReception(cr(sources = listOf(src("AMG"))), null)
        assertNull(t.amg)
        assertFalse(t.hasAny)
    }

    @Test fun labelParsing() {
        assertEquals(LabelKind.AOTY, parseLabel("AOTY-2024").kind)
        assertEquals(2024, parseLabel("AOTY-2024").year)
        assertEquals(LabelKind.AOTM, parseLabel("AOTM-2024-03").kind)
        assertEquals(3, parseLabel("AOTM-2024-03").month)
        assertEquals(LabelKind.HONORABLE_MENTION, parseLabel("HONORABLE_MENTION-2023").kind)
        assertEquals(LabelKind.RECORD_OF_THE_MONTH, parseLabel("RECORD_OF_THE_MONTH").kind)
        assertEquals(LabelKind.TYMHM, parseLabel("TYMHM").kind)
        assertEquals(LabelKind.UNKNOWN, parseLabel("WHATEVER").kind)
    }

    @Test fun labelSortPriority() {
        val sorted = sortLabels(listOf(parseLabel("TYMHM"), parseLabel("AOTY-2024")))
        assertEquals(LabelKind.AOTY, sorted[0].kind)
        assertEquals(LabelKind.TYMHM, sorted[1].kind)
    }

    @Test fun authorRolesScoredVsUnscored() {
        val scored = inferAuthorRoles(listOf("A", "B", "C"), hasScored = true)
        assertEquals(AuthorRole.CANONICAL, scored[0].role)
        assertEquals(AuthorRole.SECONDARY, scored[1].role)
        assertEquals(AuthorRole.LIST_PICK, scored[2].role)
        val unscored = inferAuthorRoles(listOf("A"), hasScored = false)
        assertEquals(AuthorRole.LIST_PICK, unscored[0].role)
    }

    @Test fun formatScoreMatchesWeb() {
        assertEquals("4.0", formatScore(4f))
        assertEquals("4.5", formatScore(4.5f))
        assertEquals("8.4", formatScore(8.4f))
    }

    @Test fun hasAnyFalseForEmpty() {
        assertFalse(parseAlbumReception(null, null).hasAny)
    }
}
```

(Rename the test method `amgScale5_tps Scale10` to `amgScale5TpsScale10` — no spaces — when typing; shown spaced only to avoid a copy error.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumReceptionTest*"`
Expected: FAIL — `AlbumReception.kt` symbols unresolved.

- [ ] **Step 3: Implement `AlbumReception.kt`**

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumReceptionTest*"`
Expected: PASS (all cases).

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/AlbumReception.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/AlbumReceptionTest.kt
git commit -m "feat(reception): pure parser for DR + AMG/TPS critical reception"
```

---

### Task 4: i18n strings + `ReceptionPanel` composable + fixtures/previews

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/item/ReceptionPanel.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/AppMediaItemFixtures.kt`

- [ ] **Step 1: Add string resources**

Append inside `<resources>` in `strings.xml`:

```xml
  <string name="reception_title">Reception</string>
  <string name="reception_dynamic_range">Dynamic Range</string>
  <string name="reception_dr_excellent">Excellent</string>
  <string name="reception_dr_good">Good</string>
  <string name="reception_dr_fair">Fair</string>
  <string name="reception_dr_poor">Poor</string>
  <string name="reception_amg_dr_fallback">AMG</string>
  <string name="reception_amg_dr_reported">AMG reported %1$s</string>
  <string name="reception_personal_pick">Personal pick</string>
  <string name="reception_score_with_max">%1$s / %2$d</string>
  <string name="reception_type_review">Review</string>
  <string name="reception_type_tymhm">They Might Have Missed</string>
  <string name="reception_type_sitf">Stuck in the Future</string>
  <string name="reception_type_ymio">You Missed It Originally</string>
  <string name="reception_type_lit">Lost in Time</string>
  <string name="reception_type_rfu">Recommended For You</string>
  <string name="reception_label_aoty">Album of the Year %1$d</string>
  <string name="reception_label_aotm">Album of the Month %1$d-%2$d</string>
  <string name="reception_label_honorable_mention">Honorable Mention %1$d</string>
  <string name="reception_label_record_of_the_month">Record of the Month</string>
  <string name="reception_label_score_revised">Score Revised</string>
```

- [ ] **Step 2: Extend fixtures for previews**

In `AppMediaItemFixtures.album(...)`, add parameters and pass through to `metadata` (default keeps `null`):

```kotlin
    fun album(
        itemId: String = uniqueIdGenerator.nextInt().toString(),
        name: String = "Album $itemId",
        artist: Artist? = artist(),
        version: String? = null,
        metadata: Metadata? = null,
    ): Album {
        return Album(
            // ...unchanged fields...
            metadata = metadata,
            // ...unchanged fields...
        )
    }

    fun receptionMetadata(): Metadata = Metadata(
        explicit = false,
        images = emptyList(),
        releaseDate = null,
        chapters = emptyList(),
        dynamicRange = 12f,
        criticalReception = CriticalReception(
            amgDr = 11f,
            sources = listOf(
                ReviewSource("AMG", rating = 4.5f, favorite = null,
                    types = listOf("Review", "TYMHM"), labels = listOf("AOTY-2024"), authors = listOf("J. Smith")),
                ReviewSource("TPS", rating = 8.4f, favorite = null,
                    types = emptyList(), labels = listOf("RECORD_OF_THE_MONTH"), authors = listOf("A. Jones")),
            ),
        ),
    )
```

Add imports in `AppMediaItemFixtures.kt`: `CriticalReception`, `ReviewSource` (same `client` package, so no import needed).

- [ ] **Step 3: Implement `ReceptionPanel.kt`**

```kotlin
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.item

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AmgDrInfo
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.DrInfo
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.DrSource
import io.music_assistant.client.data.model.client.LabelKind
import io.music_assistant.client.data.model.client.ParsedLabel
import io.music_assistant.client.data.model.client.ReceptionTags
import io.music_assistant.client.data.model.client.SourceTags
import io.music_assistant.client.data.model.client.formatDr
import io.music_assistant.client.data.model.client.formatScore
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.parseAlbumReception
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_fallback
import musicassistantclient.composeapp.generated.resources.reception_amg_dr_reported
import musicassistantclient.composeapp.generated.resources.reception_dr_excellent
import musicassistantclient.composeapp.generated.resources.reception_dr_fair
import musicassistantclient.composeapp.generated.resources.reception_dr_good
import musicassistantclient.composeapp.generated.resources.reception_dr_poor
import musicassistantclient.composeapp.generated.resources.reception_dynamic_range
import musicassistantclient.composeapp.generated.resources.reception_label_aotm
import musicassistantclient.composeapp.generated.resources.reception_label_aoty
import musicassistantclient.composeapp.generated.resources.reception_label_honorable_mention
import musicassistantclient.composeapp.generated.resources.reception_label_record_of_the_month
import musicassistantclient.composeapp.generated.resources.reception_label_score_revised
import musicassistantclient.composeapp.generated.resources.reception_personal_pick
import musicassistantclient.composeapp.generated.resources.reception_score_with_max
import musicassistantclient.composeapp.generated.resources.reception_title
import musicassistantclient.composeapp.generated.resources.reception_type_lit
import musicassistantclient.composeapp.generated.resources.reception_type_review
import musicassistantclient.composeapp.generated.resources.reception_type_rfu
import musicassistantclient.composeapp.generated.resources.reception_type_sitf
import musicassistantclient.composeapp.generated.resources.reception_type_tymhm
import musicassistantclient.composeapp.generated.resources.reception_type_ymio
import org.jetbrains.compose.resources.stringResource

@Composable
fun AlbumReceptionPanel(album: Album, modifier: Modifier = Modifier) {
    val tags = parseAlbumReception(
        album.metadata?.criticalReception,
        album.metadata?.dynamicRange,
    )
    if (!tags.hasAny) return
    ReceptionPanel(tags, modifier)
}

@Composable
private fun ReceptionPanel(tags: ReceptionTags, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(Res.string.reception_title),
                style = MaterialTheme.typography.titleMedium,
            )
            tags.dr?.let { DrRow(it, tags.amgDr) }
            tags.amg?.let { SourceRow(it) }
            tags.tps?.let { SourceRow(it) }
        }
    }
}

@Composable
private fun drVerdict(quality: DrQuality): String = stringResource(
    when (quality) {
        DrQuality.EXCELLENT -> Res.string.reception_dr_excellent
        DrQuality.GOOD -> Res.string.reception_dr_good
        DrQuality.FAIR -> Res.string.reception_dr_fair
        DrQuality.POOR -> Res.string.reception_dr_poor
    },
)

@Composable
private fun DrRow(dr: DrInfo, amgDr: AmgDrInfo?) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(Res.string.reception_dynamic_range),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (dr.source == DrSource.AMG) {
                Text(
                    text = stringResource(Res.string.reception_amg_dr_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = formatDr(dr.value), style = MaterialTheme.typography.headlineSmall)
            LinearProgressIndicator(
                progress = { (dr.value / 20f).coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f),
            )
            Text(text = drVerdict(dr.quality), style = MaterialTheme.typography.labelLarge)
        }
        amgDr?.let {
            Text(
                text = stringResource(Res.string.reception_amg_dr_reported, formatDr(it.value)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(s: SourceTags) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = s.source,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp),
            )
            if (s.rating != null) {
                Text(
                    text = stringResource(Res.string.reception_score_with_max, formatScore(s.rating), s.scale),
                    style = MaterialTheme.typography.titleMedium,
                )
                LinearProgressIndicator(
                    progress = { (s.rating / s.scale).coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                )
            } else if (s.favorite) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                    modifier = Modifier, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.reception_personal_pick), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (s.labels.isNotEmpty() || s.types.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.labels.forEach { LabelChip(it) }
                s.types.forEach { TypeChip(it) }
            }
        }
        if (s.authors.isNotEmpty()) {
            Text(
                text = "— " + s.authors.joinToString(", ") { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun labelText(label: ParsedLabel): String = when (label.kind) {
    LabelKind.AOTY -> stringResource(Res.string.reception_label_aoty, label.year ?: 0)
    LabelKind.AOTM -> stringResource(Res.string.reception_label_aotm, label.year ?: 0, label.month ?: 0)
    LabelKind.HONORABLE_MENTION -> stringResource(Res.string.reception_label_honorable_mention, label.year ?: 0)
    LabelKind.RECORD_OF_THE_MONTH -> stringResource(Res.string.reception_label_record_of_the_month)
    LabelKind.SCORE_REVISED -> stringResource(Res.string.reception_label_score_revised)
    LabelKind.TYMHM -> stringResource(Res.string.reception_type_tymhm)
    LabelKind.SITF -> stringResource(Res.string.reception_type_sitf)
    LabelKind.YMIO -> stringResource(Res.string.reception_type_ymio)
    LabelKind.LIT -> stringResource(Res.string.reception_type_lit)
    LabelKind.RFU -> stringResource(Res.string.reception_type_rfu)
    LabelKind.UNKNOWN -> label.raw
}

private fun isAccolade(kind: LabelKind): Boolean = kind == LabelKind.AOTY ||
    kind == LabelKind.AOTM || kind == LabelKind.RECORD_OF_THE_MONTH || kind == LabelKind.HONORABLE_MENTION

@Composable
private fun LabelChip(label: ParsedLabel) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(labelText(label)) },
        leadingIcon = if (isAccolade(label.kind)) {
            { Icon(Icons.Default.EmojiEvents, contentDescription = null,
                modifier = Modifier, tint = AssistChipDefaults.assistChipColors().labelColor) }
        } else null,
    )
}

@Composable
private fun TypeChip(type: String) {
    val text = when (type) {
        "Review" -> stringResource(Res.string.reception_type_review)
        "TYMHM" -> stringResource(Res.string.reception_type_tymhm)
        "SITF" -> stringResource(Res.string.reception_type_sitf)
        "YMIO" -> stringResource(Res.string.reception_type_ymio)
        "LIT" -> stringResource(Res.string.reception_type_lit)
        "RFU" -> stringResource(Res.string.reception_type_rfu)
        else -> type
    }
    AssistChip(onClick = {}, enabled = false, label = { Text(text) })
}

@Preview
@Composable
private fun ReceptionPanelPreview() {
    AlbumReceptionPanel(AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()))
}

@Preview
@Composable
private fun ReceptionPanelEmptyPreview() {
    // Renders nothing (hasAny == false) — verifies the early return.
    AlbumReceptionPanel(AppMediaItemFixtures.album())
}
```

Note: `AssistChip` with `enabled = false` gives a read-only, non-interactive chip (the app has no existing read-only chip; this is the lightest Material 3 option). If the disabled styling reads as greyed-out in review, switch to `SuggestionChip(onClick = {})` — keep the swap minimal.

- [ ] **Step 4: Compile to verify the UI builds**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL (generated string accessors resolve; composable compiles).

If a `LinearProgressIndicator(progress = {...})` overload error appears, the project's Compose version may expect `progress = Float` (non-lambda). In that case use `progress = (value).coerceIn(0f,1f)` without the lambda. Verify against the Material 3 version in `gradle/libs.versions.toml`.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/item/ReceptionPanel.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/AppMediaItemFixtures.kt
git commit -m "feat(ui): Material 3 album Reception & DR panel + previews"
```

---

### Task 5: Mount the panel in the album detail hero slot

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/item/ItemDetailsScreen.kt` (the `heroSlot` definition, ~line 360)

- [ ] **Step 1: Add the panel to `heroSlot`**

Replace the `heroSlot` definition:

```kotlin
    val heroSlot: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth()) {
            ItemHeader(
                item = item,
                providerIconFetcher = providerIconFetcher,
                onPlayClick = onPlayItemClick,
            )
            (item as? Album)?.let { AlbumReceptionPanel(it, modifier = Modifier.fillMaxWidth()) }
        }
    }
```

Ensure these imports exist in `ItemDetailsScreen.kt` (add if missing):
```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import io.music_assistant.client.data.model.client.items.Album
```
(`AlbumReceptionPanel` is in the same package `ui.compose.item`, so no import is needed for it.)

- [ ] **Step 2: Compile**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Full build + run the regression test suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: PASS (Tasks 1–3 tests green, nothing else broken).

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Live verification against the enhanced server**

Install on a device/emulator and open an album known to have reception/DR tags
(scanned via the enhanced server). Confirm the Reception card shows under the
album header with DR meter + verdict, AMG/TPS scores + bars, accolade/type chips,
and author bylines. Open an album with **no** reception data and confirm the card
does not appear.

```bash
./gradlew :androidApp:installDebug
```

If the card never appears even for tagged albums, capture the raw WS album payload
and confirm `metadata.dynamic_range` / `metadata.critical_reception` are present —
i.e. the app is pointed at the **enhanced** server, not stock.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/item/ItemDetailsScreen.kt
git commit -m "feat(ui): mount Reception panel under album detail header"
```

---

## Self-Review

**Spec coverage:**
- Data layer (wire + client + mapper, blank-source drop) → Tasks 1, 2. ✓
- Parsing logic (DR bands, fallback/divergence, source/label/author parsing) → Task 3. ✓
- Compose Material 3 panel (DR meter, AMG/TPS, chips, bylines, favorite, fallback/divergence captions) → Task 4. ✓
- i18n strings → Task 4. ✓
- Mount (full-span hero, albums only, hasAny gate) → Task 5. ✓ (gate is inside `AlbumReceptionPanel` via early return; albums-only via `as? Album`.)
- Testing (parser units, serialization decode, previews) → Tasks 1, 2, 3, 4. ✓
- Open decisions: DR semantic tint left **off** (theme-only bars) per the spec default; author roles retained in model, names-only in UI; long-form `review` not shown. ✓

**Type consistency:** `ReceptionTags`/`SourceTags`/`DrInfo`/`AmgDrInfo`/`ParsedLabel`/`AuthorWithRole`, the enums (`DrQuality`/`DrSource`/`LabelKind`/`AuthorRole`), and the functions (`parseAlbumReception`, `drQuality`, `parseLabel`, `sortLabels`, `inferAuthorRoles`, `formatScore`, `formatDr`) are defined in Task 3 and used identically in Tasks 3–4. Client `CriticalReception`/`ReviewSource` defined in Task 2, used in Tasks 2–4. Wire `ServerCriticalReception`/`ServerReviewSourceEntry` defined in Task 1, used in Tasks 1–2. ✓

**Known verification points (flagged inline, not placeholders):**
- `myJson` may need `coerceInputValues = true` for the explicit-null-source test (Task 1, Step 3).
- `LinearProgressIndicator` lambda-vs-Float `progress` overload depends on the Material 3 version (Task 4, Step 4).
- `AssistChip(enabled=false)` vs `SuggestionChip` for read-only chips — visual call at review (Task 4, Step 3).
