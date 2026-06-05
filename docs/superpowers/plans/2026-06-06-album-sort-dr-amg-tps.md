# Album Sort by DR / AMG / TPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Dynamic Range / AMG rating / TPS rating sort options to the album library sort menu, defaulting to descending (best first).

**Architecture:** Three new `SortField` enum values (whose `serverKey` + `toServerString()` already produce the server's `order_by` keys) are added to the album sort field list and the `SortChip` label map; a new `defaultDescending` flag makes them sort descending on first pick. Pure config/UI — the album library already sorts server-side via `order_by`; no server, request, or ViewModel changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material 3), kotlin.test, Compose Resources.

**Conventions:**
- Unit tests: `kotlin.test`, run `./gradlew :composeApp:testAndroidHostTest` (supports `--tests "*Name*"`). Static analysis: `./gradlew detektAll` (autoCorrect may reformat — keep it; if a run reports BUILD FAILED right after autocorrecting, run it again). Final step also runs `./gradlew :androidApp:assembleDebug`.
- Gradle pinned to the Android Studio JBR (JDK 21) via `~/.gradle/gradle.properties`; SDK via gitignored `local.properties` — don't touch/commit either. Gradle is SLOW (1–4 min; assembleDebug several): run via Bash `timeout: 600000` or `run_in_background: true` + poll; never two gradle invocations at once.
- All work on branch `enhanced`, committed directly.

**Why one task:** `SortChip.localizedName()` is an *exhaustive* `when (this)` over `SortField`. The moment the new enum values exist, that `when` stops compiling until its cases are added. So the enum values, the label cases, and the strings must land together to keep the build green — this is a single atomic change.

---

### Task 1: Add DR / AMG / TPS album sort options (descending-first)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/SortOption.kt` (`SortField` enum + `SortConfig.fieldsFor(MediaType.ALBUM)`)
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/SortChip.kt` (`localizedName()` cases + the dropdown new-field branch)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/SortOptionTest.kt`

- [ ] **Step 1: Write the failing test**

Create `SortOptionTest.kt`:
```kotlin
package io.music_assistant.client.data.model.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SortOptionTest {

    @Test fun receptionFieldsProduceServerKeys() {
        assertEquals("dr", SortOption(SortField.DR).toServerString())
        assertEquals("dr_desc", SortOption(SortField.DR, descending = true).toServerString())
        assertEquals("amg_rating", SortOption(SortField.AMG_RATING).toServerString())
        assertEquals(
            "amg_rating_desc",
            SortOption(SortField.AMG_RATING, descending = true).toServerString(),
        )
        assertEquals("tps_rating", SortOption(SortField.TPS_RATING).toServerString())
        assertEquals(
            "tps_rating_desc",
            SortOption(SortField.TPS_RATING, descending = true).toServerString(),
        )
    }

    @Test fun albumFieldsIncludeReceptionSorts() {
        val fields = SortConfig.fieldsFor(MediaType.ALBUM)
        assertTrue(SortField.DR in fields)
        assertTrue(SortField.AMG_RATING in fields)
        assertTrue(SortField.TPS_RATING in fields)
    }

    @Test fun receptionFieldsDefaultDescendingExistingFieldsDoNot() {
        assertTrue(SortField.DR.defaultDescending)
        assertTrue(SortField.AMG_RATING.defaultDescending)
        assertTrue(SortField.TPS_RATING.defaultDescending)
        assertFalse(SortField.NAME.defaultDescending)
        assertFalse(SortField.YEAR.defaultDescending)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*SortOptionTest*"`
Expected: FAIL — `SortField.DR` / `defaultDescending` unresolved (compile error).

- [ ] **Step 3: Add the enum values + `defaultDescending` flag**

In `SortOption.kt`, change the `SortField` enum header to add the optional flag and append the three values. The enum currently is:
```kotlin
enum class SortField(val serverKey: String, val displayName: String) {
    ORIGINAL("original", "Original"),
    NAME("sort_name", "Name"),
    DURATION("duration", "Duration"),
    DATE_ADDED("timestamp_added", "Date added"),
    DATE_MODIFIED("timestamp_modified", "Date modified"),
    LAST_PLAYED("last_played", "Last played"),
    PLAY_COUNT("play_count", "Play count"),
    YEAR("year", "Year"),
    POSITION("position", "Position"),
    ARTIST_NAME("artist_name", "Artist"),
    RELEASE_DATE("release_date", "Release date"),
}
```
Change it to:
```kotlin
enum class SortField(
    val serverKey: String,
    val displayName: String,
    val defaultDescending: Boolean = false,
) {
    ORIGINAL("original", "Original"),
    NAME("sort_name", "Name"),
    DURATION("duration", "Duration"),
    DATE_ADDED("timestamp_added", "Date added"),
    DATE_MODIFIED("timestamp_modified", "Date modified"),
    LAST_PLAYED("last_played", "Last played"),
    PLAY_COUNT("play_count", "Play count"),
    YEAR("year", "Year"),
    POSITION("position", "Position"),
    ARTIST_NAME("artist_name", "Artist"),
    RELEASE_DATE("release_date", "Release date"),
    DR("dr", "DR", defaultDescending = true),
    AMG_RATING("amg_rating", "AMG", defaultDescending = true),
    TPS_RATING("tps_rating", "TPS", defaultDescending = true),
}
```
(`toServerString()` is unchanged — it already appends `_desc` when descending.)

- [ ] **Step 4: Add the three fields to the album sort list**

In the same file, `SortConfig.fieldsFor(MediaType.ALBUM)` currently is:
```kotlin
        MediaType.ALBUM -> listOf(
            SortField.NAME,
            SortField.ARTIST_NAME,
            SortField.YEAR,
            SortField.DATE_ADDED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
        )
```
Change it to append the three:
```kotlin
        MediaType.ALBUM -> listOf(
            SortField.NAME,
            SortField.ARTIST_NAME,
            SortField.YEAR,
            SortField.DATE_ADDED,
            SortField.LAST_PLAYED,
            SortField.PLAY_COUNT,
            SortField.DR,
            SortField.AMG_RATING,
            SortField.TPS_RATING,
        )
```
(Do NOT change `fieldsFor(SubItemContext...)` or any other media type.)

- [ ] **Step 5: Add the label cases + the descending-first new-field branch in `SortChip.kt`**

`SortField.localizedName()` is an exhaustive `when`. Add three cases before its closing brace:
```kotlin
    SortField.RELEASE_DATE -> stringResource(Res.string.sort_release_date)
    SortField.DR -> stringResource(Res.string.sort_dr)
    SortField.AMG_RATING -> stringResource(Res.string.sort_amg)
    SortField.TPS_RATING -> stringResource(Res.string.sort_tps)
}
```
In the dropdown's field-tap handler, the `else` branch (a *different* field was picked) currently is:
```kotlin
                    if (field == SortField.ORIGINAL) {
                        onSortChanged(SortOption(field))
                    } else if (field == currentSort.field) {
                        onSortChanged(SortOption(field, !currentSort.descending))
                    } else {
                        onSortChanged(SortOption(field))
                    }
```
Change ONLY the final `else` branch to honor the field's default direction:
```kotlin
                    if (field == SortField.ORIGINAL) {
                        onSortChanged(SortOption(field))
                    } else if (field == currentSort.field) {
                        onSortChanged(SortOption(field, !currentSort.descending))
                    } else {
                        onSortChanged(SortOption(field, descending = field.defaultDescending))
                    }
```
(The ORIGINAL branch and the same-field toggle branch are unchanged. Existing fields have `defaultDescending = false`, so picking them still defaults to ascending — no behavior change.)

- [ ] **Step 6: Add the strings**

Append inside `<resources>` in `strings.xml`:
```xml
  <string name="sort_dr">DR</string>
  <string name="sort_amg">AMG</string>
  <string name="sort_tps">TPS</string>
```

- [ ] **Step 7: Run the test to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*SortOptionTest*"`
Expected: PASS (3 tests). Then run the full suite once to confirm no regressions:
Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL (169 tests — the prior 166 + 3 new).

- [ ] **Step 8: detekt + full app build**

Run: `./gradlew detektAll` (Expected BUILD SUCCESSFUL; if it reports FAILED right after autocorrecting a file, run it again and keep the autocorrect changes).
Run: `./gradlew :androidApp:assembleDebug` (Expected BUILD SUCCESSFUL).

- [ ] **Step 9: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/SortOption.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/SortChip.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/SortOptionTest.kt
git commit -m "feat(sort): album sort by DR / AMG / TPS (descending-first)

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 10: Live verification (manual)**

Open the album library, tap the sort chip, and confirm DR / AMG / TPS appear in the menu; picking one re-sorts the list (server-side) and defaults to descending (down-arrow); a second tap on the same field flips to ascending. Confirm other media types' sort menus are unchanged.
```bash
./gradlew :androidApp:installDebug   # in-place update if a device is connected
```

---

## Self-Review

**Spec coverage:**
- `SortField` + `DR`/`AMG_RATING`/`TPS_RATING` + `defaultDescending` → Step 3. ✓
- `fieldsFor(MediaType.ALBUM)` includes them → Step 4. ✓
- `localizedName()` cases + descending-first new-field branch → Step 5. ✓
- strings → Step 6. ✓
- No server/request/VM/clientSorted changes → respected (only SortField config + SortChip). ✓
- Testing (server keys, album fields, defaultDescending) + compile + detekt + assembleDebug + live → Steps 1–2, 7–10. ✓
- Existing fields unchanged (defaultDescending defaults false) → Step 3 (default value) + the `receptionFieldsDefaultDescendingExistingFieldsDoNot` test. ✓

**Type consistency:** `SortField.DR/AMG_RATING/TPS_RATING` (serverKeys `dr`/`amg_rating`/`tps_rating`), `SortField.defaultDescending: Boolean`, `SortOption.toServerString()`, `SortConfig.fieldsFor(MediaType.ALBUM)`, `localizedName()` cases, `Res.string.sort_dr/sort_amg/sort_tps` — all used consistently across the steps and the test.

**Placeholders:** none — every step shows the exact before/after code; the `else`-branch change is disambiguated from the identical `ORIGINAL`-branch line by surrounding context.
