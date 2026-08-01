# Album Reception Filter Sheet Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A live reception filter (DR / AMG / TPS ratings + accolade labels + favorite/untagged flags + ALL/ANY match) on the album library list.

**Architecture:** A pure `ReceptionFilter` model maps to `music/albums/library_items` request args (all params already exist server-side). `Request.Album.listLibrary` merges those args; `ItemListViewModel` holds the filter in state and re-queries reactively on change (live apply); a Material 3 `ModalBottomSheet` (`ReceptionFilterSheet`) edits it, opened from an album-only filter button in the list top bar. Display/query-only — no server or data-model changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material 3), kotlinx.serialization, kotlin.test, Compose Resources.

**Conventions:**
- Unit tests: `kotlin.test`, run `./gradlew :composeApp:testAndroidHostTest` (the AGP-KMP host unit-test task; supports `--tests "*Name*"`). UI tasks verified by compile (the same task compiles commonMain) + `@Preview`s. Static analysis (CI-enforced): `./gradlew detektAll` (autoCorrect may reformat — keep those changes). Final task also runs `./gradlew :androidApp:assembleDebug`.
- Gradle is pinned to the Android Studio JBR (JDK 21) via `~/.gradle/gradle.properties`; SDK via gitignored `local.properties`. Do NOT touch/commit either. Gradle is SLOW (1–4 min; assembleDebug several): run via Bash `timeout: 600000` or `run_in_background: true` + poll; NEVER run two gradle invocations at once.
- All work on branch `enhanced`, committed directly. Reuse the prior slices' types in package `io.music_assistant.client.data.model.client`.

**Reference (do not import; reimplement):** `../../../frontend/src/components/album/ReviewFiltersPanel.vue`.

---

### Task 1: `ReceptionFilter` model + request-args mapper

**Files:**
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/ReceptionFilter.kt`
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/ReceptionFilterTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ReceptionFilterTest.kt`:

```kotlin
package io.music_assistant.client.data.model.client

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReceptionFilterTest {

    @Test fun emptyFilterIsInactiveWithNoArgs() {
        val f = ReceptionFilter()
        assertFalse(f.isActive)
        assertEquals(0, f.activeCount)
        assertTrue(f.toRequestArgs().isEmpty())
    }

    @Test fun drBucketsSerializeSortedArray() {
        val args = ReceptionFilter(drBuckets = setOf("good", "excellent")).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("excellent"), JsonPrimitive("good"))),
            args["dr_buckets"],
        )
    }

    @Test fun ratingsSerializeSortedIntArrays() {
        val args = ReceptionFilter(
            amgRatings = setOf(5, 3),
            tpsRatings = setOf(9, 1),
        ).toRequestArgs()
        assertEquals(listOf(3, 5), (args["amg_ratings"] as JsonArray).map { it.toString().toInt() })
        assertEquals(listOf(1, 9), (args["tps_ratings"] as JsonArray).map { it.toString().toInt() })
    }

    @Test fun labelsSerializeSortedArrays() {
        val args = ReceptionFilter(
            amgLabels = setOf("record_of_the_month", "aoty"),
            tpsLabels = setOf("aotm"),
        ).toRequestArgs()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("aoty"), JsonPrimitive("record_of_the_month"))),
            args["amg_labels"],
        )
        assertEquals(JsonArray(listOf(JsonPrimitive("aotm"))), args["tps_labels"])
    }

    @Test fun flagsEmittedOnlyWhenTrue() {
        val args = ReceptionFilter(
            amgFavorite = true,
            tpsUntagged = true,
        ).toRequestArgs()
        assertEquals(JsonPrimitive(true), args["amg_favorite"])
        assertEquals(JsonPrimitive(true), args["tps_untagged"])
        assertNull(args["amg_untagged"])
        assertNull(args["tps_favorite"])
    }

    @Test fun matchEmittedOnlyForAny() {
        assertNull(ReceptionFilter(matchAny = false).toRequestArgs()["critical_reception_match"])
        assertEquals(
            JsonPrimitive("any"),
            ReceptionFilter(matchAny = true, drBuckets = setOf("good")).toRequestArgs()["critical_reception_match"],
        )
    }

    @Test fun activeCountSumsSelectionsAndFlagsNotMatch() {
        val f = ReceptionFilter(
            drBuckets = setOf("good", "fair"),     // 2
            amgRatings = setOf(4),                 // 1
            amgLabels = setOf("aoty"),             // 1
            amgFavorite = true,                    // 1
            tpsUntagged = true,                    // 1
            matchAny = true,                       // not counted
        )
        assertTrue(f.isActive)
        assertEquals(6, f.activeCount)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ReceptionFilterTest*"`
Expected: FAIL — `ReceptionFilter` unresolved.

- [ ] **Step 3: Implement `ReceptionFilter.kt`**

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ReceptionFilterTest*"`
Expected: PASS.

- [ ] **Step 5: detekt + commit**

Run: `./gradlew detektAll` (Expected: BUILD SUCCESSFUL).
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/ReceptionFilter.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/ReceptionFilterTest.kt
git commit -m "feat(filter): ReceptionFilter model + request-args mapper

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Extend `Request.Album.listLibrary` with the filter

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/api/Request.kt` (`object Album` → `fun listLibrary`, ~line 463)
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/api/AlbumLibraryRequestTest.kt`

- [ ] **Step 1: Write the failing test**

Create `AlbumLibraryRequestTest.kt`:

```kotlin
package io.music_assistant.client.api

import io.music_assistant.client.data.model.client.ReceptionFilter
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AlbumLibraryRequestTest {

    @Test
    fun listLibraryMergesFilterArgs() {
        val req = Request.Album.listLibrary(
            orderBy = "dr_desc",
            receptionFilter = ReceptionFilter(
                drBuckets = setOf("excellent"),
                amgRatings = setOf(4, 5),
                amgFavorite = true,
                matchAny = true,
            ),
        )
        val args = req.args
        assertEquals(JsonPrimitive("dr_desc"), args["order_by"])
        assertEquals(JsonArray(listOf(JsonPrimitive("excellent"))), args["dr_buckets"])
        assertEquals(listOf(4, 5), (args["amg_ratings"] as JsonArray).map { it.toString().toInt() })
        assertEquals(JsonPrimitive(true), args["amg_favorite"])
        assertEquals(JsonPrimitive("any"), args["critical_reception_match"])
    }

    @Test
    fun listLibraryWithoutFilterHasNoFilterArgs() {
        val args = Request.Album.listLibrary(orderBy = "sort_name").args
        assertNull(args["dr_buckets"])
        assertNull(args["critical_reception_match"])
    }
}
```
(`Request.args` is the public `JsonObject` on the `Request` data class — confirm the property name is `args`; if it differs, use the actual accessor.)

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumLibraryRequestTest*"`
Expected: FAIL — `listLibrary` has no `receptionFilter` param.

- [ ] **Step 3: Add the param + merge args**

In `Request.kt`, the album `listLibrary` (~line 463) currently is:
```kotlin
        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
        ) = Request(
            command = APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
            },
        )
```
Change it to:
```kotlin
        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
            receptionFilter: ReceptionFilter = ReceptionFilter(),
        ) = Request(
            command = APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
                receptionFilter.toRequestArgs().forEach { (k, v) -> put(k, v) }
            },
        )
```
Add the import near the other imports: `import io.music_assistant.client.data.model.client.ReceptionFilter`.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*AlbumLibraryRequestTest*"`
Expected: PASS.

- [ ] **Step 5: detekt + commit**

Run: `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/api/Request.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/api/AlbumLibraryRequestTest.kt
git commit -m "feat(filter): merge reception filter args into album library_items request

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Wire `ItemListViewModel` (state + reactive reload + request)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ItemListViewModel.kt`

No new unit test (this is integration wiring); verified by compile + the full suite staying green.

- [ ] **Step 1: Add the state field**

In `data class State` (~line 318) add a field after `onlyFavorites`:
```kotlin
        val onlyFavorites: Boolean = false,
        val receptionFilter: ReceptionFilter = ReceptionFilter(),
```
Add import: `import io.music_assistant.client.data.model.client.ReceptionFilter`.

- [ ] **Step 2: Add `receptionFilter` to the reactive reload key**

In `init` (~line 48), change the tracked tuple so a filter change triggers `loadFirstPage()`:
```kotlin
            _state.map { listOf(it.searchQuery, it.sortOption, it.onlyFavorites, it.receptionFilter) }
                .distinctUntilChanged()
                .debounce { Timings.INPUT_DEBOUNCE }
                .collect { loadFirstPage() }
```
(Replaces the `Triple(...)` map. `List<Any>` structural equality drives `distinctUntilChanged`; `SortOption` and `ReceptionFilter` are data classes with value equality.)

- [ ] **Step 3: Add the change handler**

Next to `onSortChanged` (~line 108):
```kotlin
    fun onReceptionFilterChanged(filter: ReceptionFilter) {
        _state.update { it.copy(receptionFilter = filter) }
    }
```

- [ ] **Step 4: Thread the filter into the request**

Change `getRequest` (~line 161) to accept the filter and pass it on the album branch. Update its signature:
```kotlin
    private fun getRequest(
        mediaType: MediaType,
        offset: Int,
        orderBy: String,
        searchQuery: String?,
        onlyFavorites: Boolean,
        receptionFilter: ReceptionFilter,
    ): Request {
```
and the `MediaType.ALBUM` branch:
```kotlin
            MediaType.ALBUM -> Request.Album.listLibrary(
                limit = PAGE_SIZE,
                offset = offset,
                search = searchQuery,
                orderBy = orderBy,
                favorite = favorites,
                receptionFilter = receptionFilter,
            )
```
Then pass `state.value.receptionFilter` / `currentState.receptionFilter` at the two call sites:
- In `loadFirstPage` (~line 268), the `getRequest(...)` call gains a final arg `state.value.receptionFilter`.
- In `loadMore` (~line 131), the `getRequest(...)` call gains a final arg `currentState.receptionFilter`.

- [ ] **Step 5: Compile + full suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, all prior tests green (158 with Tasks 1–2 added).

- [ ] **Step 6: detekt + commit**

Run: `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ItemListViewModel.kt
git commit -m "feat(filter): album list re-queries live on reception filter change

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: `ReceptionFilterSheet` composable + strings + previews

**Files:**
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ReceptionFilterSheet.kt`

- [ ] **Step 1: Add strings**

Append inside `<resources>` in `strings.xml`:
```xml
  <string name="filter_title">Reception filters</string>
  <string name="filter_match">Match</string>
  <string name="filter_match_all">All</string>
  <string name="filter_match_any">Any</string>
  <string name="filter_dr">Dynamic Range</string>
  <string name="filter_amg">AMG</string>
  <string name="filter_tps">TPS</string>
  <string name="filter_personal_pick">Personal pick</string>
  <string name="filter_untagged">Untagged</string>
  <string name="filter_clear_all">Clear all</string>
  <string name="filter_dr_untagged">Untagged</string>
  <string name="filter_label_aoty">Album of the Year</string>
  <string name="filter_label_aotm">Album of the Month</string>
  <string name="filter_label_honorable_mention">Honorable Mention</string>
  <string name="filter_label_record_of_the_month">Record of the Month</string>
  <string name="cd_reception_filter">Reception filters</string>
```

- [ ] **Step 2: Create `ReceptionFilterSheet.kt`**

```kotlin
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.ReceptionFilter
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.filter_amg
import musicassistantclient.composeapp.generated.resources.filter_clear_all
import musicassistantclient.composeapp.generated.resources.filter_dr
import musicassistantclient.composeapp.generated.resources.filter_dr_untagged
import musicassistantclient.composeapp.generated.resources.filter_label_aotm
import musicassistantclient.composeapp.generated.resources.filter_label_aoty
import musicassistantclient.composeapp.generated.resources.filter_label_honorable_mention
import musicassistantclient.composeapp.generated.resources.filter_label_record_of_the_month
import musicassistantclient.composeapp.generated.resources.filter_match
import musicassistantclient.composeapp.generated.resources.filter_match_all
import musicassistantclient.composeapp.generated.resources.filter_match_any
import musicassistantclient.composeapp.generated.resources.filter_personal_pick
import musicassistantclient.composeapp.generated.resources.filter_title
import musicassistantclient.composeapp.generated.resources.filter_tps
import musicassistantclient.composeapp.generated.resources.filter_untagged
import musicassistantclient.composeapp.generated.resources.reception_dr_excellent
import musicassistantclient.composeapp.generated.resources.reception_dr_fair
import musicassistantclient.composeapp.generated.resources.reception_dr_good
import musicassistantclient.composeapp.generated.resources.reception_dr_poor
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

@Composable
fun ReceptionFilterSheet(
    filter: ReceptionFilter,
    onChange: (ReceptionFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(Res.string.filter_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (filter.isActive) {
                    TextButton(onClick = { onChange(ReceptionFilter()) }) {
                        Text(stringResource(Res.string.filter_clear_all))
                    }
                }
            }

            SectionLabel(stringResource(Res.string.filter_match))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = !filter.matchAny,
                    onClick = { onChange(filter.copy(matchAny = false)) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text(stringResource(Res.string.filter_match_all)) }
                SegmentedButton(
                    selected = filter.matchAny,
                    onClick = { onChange(filter.copy(matchAny = true)) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text(stringResource(Res.string.filter_match_any)) }
            }

            SectionLabel(stringResource(Res.string.filter_dr))
            ChipRow {
                ReceptionFilter.DR_BUCKETS.forEach { b ->
                    FilterChip(
                        selected = b in filter.drBuckets,
                        onClick = { onChange(filter.copy(drBuckets = filter.drBuckets.toggle(b))) },
                        label = { Text(drBucketLabel(b)) },
                    )
                }
            }

            SectionLabel(stringResource(Res.string.filter_amg))
            ChipRow {
                ReceptionFilter.AMG_RATINGS.forEach { n ->
                    FilterChip(
                        selected = n in filter.amgRatings,
                        onClick = { onChange(filter.copy(amgRatings = filter.amgRatings.toggle(n))) },
                        label = { Text("$n★") },
                    )
                }
            }
            ChipRow {
                ReceptionFilter.ACCOLADE_LABELS.forEach { l ->
                    FilterChip(
                        selected = l in filter.amgLabels,
                        onClick = { onChange(filter.copy(amgLabels = filter.amgLabels.toggle(l))) },
                        label = { Text(accoladeLabel(l)) },
                    )
                }
                FilterChip(
                    selected = filter.amgFavorite,
                    onClick = { onChange(filter.copy(amgFavorite = !filter.amgFavorite)) },
                    label = { Text(stringResource(Res.string.filter_personal_pick)) },
                )
                FilterChip(
                    selected = filter.amgUntagged,
                    onClick = { onChange(filter.copy(amgUntagged = !filter.amgUntagged)) },
                    label = { Text(stringResource(Res.string.filter_untagged)) },
                )
            }

            SectionLabel(stringResource(Res.string.filter_tps))
            ChipRow {
                ReceptionFilter.TPS_BANDS.forEach { lo ->
                    FilterChip(
                        selected = lo in filter.tpsRatings,
                        onClick = { onChange(filter.copy(tpsRatings = filter.tpsRatings.toggle(lo))) },
                        label = { Text(tpsBandLabel(lo)) },
                    )
                }
            }
            ChipRow {
                ReceptionFilter.ACCOLADE_LABELS.forEach { l ->
                    FilterChip(
                        selected = l in filter.tpsLabels,
                        onClick = { onChange(filter.copy(tpsLabels = filter.tpsLabels.toggle(l))) },
                        label = { Text(accoladeLabel(l)) },
                    )
                }
                FilterChip(
                    selected = filter.tpsFavorite,
                    onClick = { onChange(filter.copy(tpsFavorite = !filter.tpsFavorite)) },
                    label = { Text(stringResource(Res.string.filter_personal_pick)) },
                )
                FilterChip(
                    selected = filter.tpsUntagged,
                    onClick = { onChange(filter.copy(tpsUntagged = !filter.tpsUntagged)) },
                    label = { Text(stringResource(Res.string.filter_untagged)) },
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChipRow(content: @Composable FlowRow.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

@Composable
private fun drBucketLabel(bucket: String): String {
    val res: StringResource = when (bucket) {
        "excellent" -> Res.string.reception_dr_excellent
        "good" -> Res.string.reception_dr_good
        "fair" -> Res.string.reception_dr_fair
        "poor" -> Res.string.reception_dr_poor
        else -> Res.string.filter_dr_untagged
    }
    return stringResource(res)
}

@Composable
private fun accoladeLabel(label: String): String {
    val res: StringResource = when (label) {
        "aoty" -> Res.string.filter_label_aoty
        "aotm" -> Res.string.filter_label_aotm
        "honorable_mention" -> Res.string.filter_label_honorable_mention
        else -> Res.string.filter_label_record_of_the_month
    }
    return stringResource(res)
}

// TPS bands: selector `lo` covers [lo, lo+2) on the /10 scale; 9 is the open top band.
private fun tpsBandLabel(lo: Int): String = if (lo >= 9) "9+" else "$lo–${lo + 2}"

@Preview
@Composable
private fun ReceptionFilterSheetEmptyPreview() {
    ReceptionFilterSheet(filter = ReceptionFilter(), onChange = {}, onDismiss = {})
}

@Preview
@Composable
private fun ReceptionFilterSheetPopulatedPreview() {
    ReceptionFilterSheet(
        filter = ReceptionFilter(
            drBuckets = setOf("excellent", "good"),
            amgRatings = setOf(4, 5),
            tpsRatings = setOf(7),
            amgLabels = setOf("aoty"),
            amgFavorite = true,
            matchAny = true,
        ),
        onChange = {},
        onDismiss = {},
    )
}
```
Note on `TextAlign` import: it's listed for safety but only used if needed; if detekt flags it as unused, remove it.

- [ ] **Step 3: Compile + suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL (generated string accessors resolve; sheet compiles), suite green.

KNOWN-RISK fallbacks (only if the specific error appears):
- If `FlowRow(content = content)` with `@Composable FlowRow.() -> Unit` mismatches, change `ChipRow` to take `@Composable () -> Unit` and call `FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }`.
- If `SegmentedButtonDefaults.itemShape` signature differs in this Material3 version, use `SegmentedButtonDefaults.itemShape(index = 0, count = 2)` (named) or the baseShape; keep the change minimal and report it.

- [ ] **Step 4: detekt + commit**

Run: `./gradlew detektAll` (keep any autocorrect).
```bash
git add composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ReceptionFilterSheet.kt
git commit -m "feat(filter): Material 3 reception filter bottom sheet + previews

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Album-only filter button entry point + final gates

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ItemListScreen.kt`

- [ ] **Step 1: Thread filter state through to the top bar**

In the `ItemListTopBar(...)` call (~line 123) add two arguments after `onToggleFavorites = ...`:
```kotlin
                onToggleFavorites = itemListViewModel::toggleFavorites,
                receptionFilter = state.receptionFilter,
                onReceptionFilterChanged = itemListViewModel::onReceptionFilterChanged,
```

- [ ] **Step 2: Add the params to `ItemListTopBar`**

Add to the `private fun ItemListTopBar(...)` signature (~line 165, after `onToggleFavorites`):
```kotlin
    onToggleFavorites: () -> Unit,
    receptionFilter: ReceptionFilter,
    onReceptionFilterChanged: (ReceptionFilter) -> Unit,
```
Add imports to `ItemListScreen.kt`:
```kotlin
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.music_assistant.client.data.model.client.ReceptionFilter
```
(`Icons`, `IconButton`, `Icon`, `MediaType`, `Text` are already imported in this file.)

- [ ] **Step 3: Add the album-only filter button + sheet**

In the `secondRow` (~line 295), the inner `Row { SortChip(...); IconButton(viewMode) }` becomes — insert the filter control as the first child of that inner `Row`:
```kotlin
                    Row {
                        if (mediaType == MediaType.ALBUM) {
                            var showFilter by rememberSaveable { mutableStateOf(false) }
                            BadgedBox(
                                badge = {
                                    if (receptionFilter.isActive) {
                                        Badge { Text("${receptionFilter.activeCount}") }
                                    }
                                },
                            ) {
                                IconButton(onClick = { showFilter = true }) {
                                    Icon(
                                        imageVector = Icons.Default.FilterList,
                                        contentDescription = stringResource(Res.string.cd_reception_filter),
                                    )
                                }
                            }
                            if (showFilter) {
                                ReceptionFilterSheet(
                                    filter = receptionFilter,
                                    onChange = onReceptionFilterChanged,
                                    onDismiss = { showFilter = false },
                                )
                            }
                        }
                        SortChip(
                            currentSort = sortOption,
                            availableFields = SortConfig.fieldsFor(mediaType),
                            onSortChanged = { onSortChanged(it) },
                        )
                        IconButton(onClick = onToggleViewMode) {
                            Icon(
                                imageVector = when (viewMode) {
                                    ViewMode.LIST -> Icons.Default.GridView
                                    ViewMode.GRID -> Icons.AutoMirrored.Filled.ViewList
                                },
                                contentDescription = stringResource(Res.string.cd_toggle_view_mode),
                            )
                        }
                    }
```
`ReceptionFilterSheet` is in the same package (`ui.compose.library`) — no import needed.

- [ ] **Step 4: Compile + suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, suite green (158 tests).

- [ ] **Step 5: detekt**

Run: `./gradlew detektAll`
Expected: BUILD SUCCESSFUL (fix/keep autocorrect on the touched files; if detekt flags new findings in feature files, fix per the codebase convention — e.g. `@file:Suppress("MagicNumber")` for design tokens).

- [ ] **Step 6: Full app build**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ItemListScreen.kt
git commit -m "feat(filter): album-only reception filter button + sheet in library top bar

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 8: Live verification (manual, enhanced server)**

Install and open the album library; tap the filter icon (albums only). Toggle DR/AMG/TPS chips, labels, favorite/untagged, and ALL/ANY — the list should re-query live and the icon should show an active-count badge. Confirm the button is absent on non-album lists (Artists/Tracks/etc.).
```bash
./gradlew :androidApp:installDebug   # in-place update (same debug signing as the installed build)
```

---

## Self-Review

**Spec coverage:**
- Filter model + constants + request-args mapper → Task 1. ✓
- `Request.Album.listLibrary` extension + serialization test → Task 2. ✓
- ViewModel state + reactive live reload + `onReceptionFilterChanged` + getRequest album branch → Task 3. ✓
- `ReceptionFilterSheet` (Match ALL/ANY, DR, AMG ratings+labels+favorite+untagged, TPS bands+labels+favorite+untagged, clear-all) + strings + previews → Task 4. ✓
- Album-only filter `IconButton` with active-count badge + open sheet + wiring → Task 5. ✓
- i18n strings → Task 4. ✓
- Testing (mapper units, request serialization, previews, gates, live check) → Tasks 1, 2, 4, 5. ✓
- Live apply (re-query per change) → Task 3 reactive key. ✓
- No server/model changes; album-only; favorites-only stays separate → respected (only album branch sends params; the existing `onlyFavorites` chip untouched). ✓
- Persistence intentionally out (session-only) → no persistence code added. ✓

**Type consistency:** `ReceptionFilter` (fields, `isActive`, `activeCount`, `toRequestArgs`, companion `DR_BUCKETS`/`AMG_RATINGS`/`TPS_BANDS`/`ACCOLADE_LABELS`) defined in Task 1; used identically in Tasks 2 (request), 3 (state/VM), 4 (sheet), 5 (top bar). `Request.Album.listLibrary(..., receptionFilter)` defined Task 2, called Task 3. `onReceptionFilterChanged(ReceptionFilter)` defined Task 3, wired Task 5. `ReceptionFilterSheet(filter, onChange, onDismiss)` defined Task 4, used Task 5.

**Placeholder scan:** every code step shows complete code; the two known-risk fallbacks (FlowRow content lambda, `SegmentedButtonDefaults.itemShape`) and the `Request.args` accessor caveat are concrete checks, not vague instructions.

## Superseded (2026-08-01)

Historical record of the v1 build — the tasks above are kept as executed. Since then:

- **Task 4's `ReceptionFilterSheet.kt` was replaced.** The filter is now
  `ui/compose/library/ReceptionFilterAction.kt` (icon button + sheet body) on the shared
  `FilterAction` / `SettingsSheet` chrome, not the hand-rolled `ModalBottomSheet` shown in
  the Task 4 code block. The `ReceptionFilterSheet(filter, onChange, onDismiss)` signature
  and its previews are gone; the entry point wiring described in Task 5 now lives inside
  `ReceptionFilterAction`.
- **Task 3's live apply became commit-on-Apply.** Edits accumulate in a sheet-local
  working copy and reach `ItemListViewModel.onReceptionFilterChanged` only on "Apply",
  which drops a commit equal to the current filter instead of re-querying. The "re-query
  live on every change" behaviour in the goal, Task 3 and the manual-check steps no longer
  describes the app.
