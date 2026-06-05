# Album-View Reception Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show compact DR / AMG / TPS reception chips on album grid tiles and list rows, wherever albums appear.

**Architecture:** A new `AlbumReceptionBadges` composable reuses the existing `parseAlbumReception` view-model (from the detail-panel slice) and renders up to three small pills (DR tinted by quality band; AMG/TPS neutral tonal). It mounts as a bottom-start overlay on `AlbumGridItem` (tile) and, via a new optional `subtitleAccessory` slot on the shared `RowItem`, under the subtitle on `AlbumRowItem` (row). Display-only — no data, model, or server changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material 3), Compose Resources.

**Conventions:**
- This is UI work: the codebase has no automated screen tests. Each task is verified by **compiling + the existing unit suite staying green** (`./gradlew :composeApp:testAndroidHostTest`), `@Preview`s for visual check, and `./gradlew detektAll` (CI-enforced). The final task also runs `./gradlew :androidApp:assembleDebug`.
- Gradle is pinned to the Android Studio JBR (JDK 21) via `~/.gradle/gradle.properties`; Android SDK via gitignored `local.properties`. Do NOT touch/commit either. Gradle is SLOW (1–4 min) — run via Bash `timeout: 600000` or `run_in_background: true` + poll; never run two gradle invocations at once.
- All work on branch `enhanced`, committed directly. detekt runs `autoCorrect` locally and may reformat the new files on the `detektAll` step — that's expected; include those formatting changes in the same commit.
- Reuse from the prior slice (package `io.music_assistant.client.data.model.client`): `parseAlbumReception(cr, dr): ReceptionTags`; `ReceptionTags(dr: DrInfo?, amg: SourceTags?, tps: SourceTags?, …, hasAny)`; `DrInfo(value: Float, quality: DrQuality, source)`; `DrQuality{EXCELLENT,GOOD,FAIR,POOR}`; `SourceTags(source: String, scale: Int, rating: Float?, favorite: Boolean, …)`; `formatScore(Float)`; `formatDr(Float)`.

**Reference (do not import; reimplement):** `../../../frontend/src/components/album/AlbumListBadges.vue` (row) and `AlbumPanelBadges.vue` (tile).

---

### Task 1: `AlbumReceptionBadges` composable + previews

**Files:**
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/AlbumReceptionBadges.kt`

This task creates the self-contained component with `@Preview`s; it is not mounted anywhere yet (Tasks 2–3 mount it).

- [ ] **Step 1: Create `AlbumReceptionBadges.kt`**

```kotlin
@file:Suppress("MagicNumber")

package io.music_assistant.client.ui.compose.common.items

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import io.music_assistant.client.data.model.client.DrInfo
import io.music_assistant.client.data.model.client.DrQuality
import io.music_assistant.client.data.model.client.SourceTags
import io.music_assistant.client.data.model.client.formatDr
import io.music_assistant.client.data.model.client.formatScore
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.data.model.client.parseAlbumReception

enum class ReceptionBadgeStyle { Tile, Row }

/**
 * Compact DR / AMG / TPS reception chips for album tiles and rows. Renders nothing
 * when the album has no reception data. DR is tinted by quality band (scannability);
 * AMG/TPS are neutral tonal. Mirrors the web AlbumPanelBadges / AlbumListBadges.
 */
@Composable
fun AlbumReceptionBadges(
    album: Album,
    style: ReceptionBadgeStyle,
    modifier: Modifier = Modifier,
) {
    val tags = parseAlbumReception(
        album.metadata?.criticalReception,
        album.metadata?.dynamicRange,
    )
    if (!tags.hasAny) return

    val contentDesc = buildList {
        tags.dr?.let { add("DR ${formatDr(it.value)}") }
        tags.amg?.let { s -> add("AMG" + (s.rating?.let { " ${formatScore(it)}" } ?: "")) }
        tags.tps?.let { s -> add("TPS" + (s.rating?.let { " ${formatScore(it)}" } ?: "")) }
    }.joinToString(", ")

    val container = if (style == ReceptionBadgeStyle.Tile) {
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(horizontal = 3.dp, vertical = 2.dp)
    } else {
        modifier
    }

    Row(
        modifier = container.semantics(mergeDescendants = true) {
            contentDescription = contentDesc
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tags.dr?.let { DrPill(it) }
        tags.amg?.let { SourcePill(it) }
        tags.tps?.let { SourcePill(it) }
    }
}

// Restrained semantic hues for the DR pill, matching the web DR palette. Used as a
// low-alpha container with the same hue as the (more saturated) text, so it reads on
// both light and dark themes and over the tile scrim.
private fun drHue(quality: DrQuality): Color = when (quality) {
    DrQuality.EXCELLENT -> Color(0xFF22C55E)
    DrQuality.GOOD -> Color(0xFF3B82F6)
    DrQuality.FAIR -> Color(0xFFEAB308)
    DrQuality.POOR -> Color(0xFFEF4444)
}

@Composable
private fun DrPill(dr: DrInfo) {
    val hue = drHue(dr.quality)
    Pill(
        text = "DR ${formatDr(dr.value)}",
        container = hue.copy(alpha = 0.22f),
        content = hue,
    )
}

@Composable
private fun SourcePill(s: SourceTags) {
    // AMG always shows the star; TPS shows the star only when there is no rating.
    val showStar = s.source == "AMG" || s.rating == null
    Pill(
        text = s.rating?.let { formatScore(it) },
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
        leadingStar = showStar,
    )
}

@Composable
private fun Pill(
    text: String?,
    container: Color,
    content: Color,
    leadingStar: Boolean = false,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingStar) {
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(11.dp),
            )
        }
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = content,
            )
        }
    }
}

@Preview
@Composable
private fun BadgesRowPreview() {
    AlbumReceptionBadges(
        AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()),
        style = ReceptionBadgeStyle.Row,
    )
}

@Preview
@Composable
private fun BadgesTilePreview() {
    Box {
        AlbumReceptionBadges(
            AppMediaItemFixtures.album(metadata = AppMediaItemFixtures.receptionMetadata()),
            style = ReceptionBadgeStyle.Tile,
        )
    }
}

@Preview
@Composable
private fun BadgesEmptyPreview() {
    // No metadata -> renders nothing.
    AlbumReceptionBadges(AppMediaItemFixtures.album(), style = ReceptionBadgeStyle.Row)
}
```

- [ ] **Step 2: Compile + regression suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, 156 tests still green (no new tests; this verifies the new composable compiles and nothing regressed).

If `Icons.Filled.Star` fails to resolve, the app bundles `material-icons-extended`, so it should; if not, use `androidx.compose.material.icons.filled.Star` (same import) — it is a core icon and is available.

- [ ] **Step 3: detekt**

Run: `./gradlew detektAll`
Expected: BUILD SUCCESSFUL (autoCorrect may reformat the new file; keep those changes).

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/AlbumReceptionBadges.kt
git commit -m "feat(ui): AlbumReceptionBadges composable (DR/AMG/TPS chips) + previews

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Row badges — `RowItem` accessory slot + `AlbumRowItem`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/MediaItems.kt` (`RowItem` ~line 1210, `AlbumRowItem` ~line 940)

- [ ] **Step 1: Add an optional `subtitleAccessory` slot to `RowItem`**

`RowItem` currently is:
```kotlin
@Composable
private fun RowItem(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String?,
    imageContent: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(rowImageSize())) { imageContent() }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
```

Change it to add a defaulted `subtitleAccessory` parameter and render it at the end of the text `Column`:
```kotlin
@Composable
private fun RowItem(
    modifier: Modifier = Modifier,
    name: String,
    subtitle: String?,
    imageContent: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    subtitleAccessory: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(rowImageSize())) { imageContent() }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            subtitleAccessory?.invoke()
        }
    }
}
```
All other `RowItem` callers omit the new parameter (default `null`) — unchanged behavior for non-albums.

- [ ] **Step 2: Pass row badges from `AlbumRowItem`**

`AlbumRowItem` (~line 940) currently is:
```kotlin
internal fun AlbumRowItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
    )
}
```
Add the `subtitleAccessory`:
```kotlin
internal fun AlbumRowItem(
    modifier: Modifier = Modifier,
    item: Album,
    onClick: (Album) -> Unit,
    onLongClick: (Album) -> Unit,
    providerIconFetcher: (@Composable (Modifier, String) -> Unit)?,
) {
    RowItem(
        modifier = modifier,
        name = item.displayName,
        subtitle = item.localizedSubtitle(),
        imageContent = {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        },
        onClick = { onClick(item) },
        onLongClick = { onLongClick(item) },
        subtitleAccessory = {
            AlbumReceptionBadges(item, style = ReceptionBadgeStyle.Row)
        },
    )
}
```
`AlbumReceptionBadges` / `ReceptionBadgeStyle` are in the same package — no import needed.

- [ ] **Step 3: Compile + regression suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, 156 tests green.

- [ ] **Step 4: detekt**

Run: `./gradlew detektAll`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/MediaItems.kt
git commit -m "feat(ui): album list-row reception badges via RowItem accessory slot

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Tile badges — overlay on `AlbumGridItem`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/MediaItems.kt` (`AlbumGridItem` ~line 169)

- [ ] **Step 1: Add the tile overlay**

`AlbumGridItem`'s cover `Box` currently is:
```kotlin
        Box {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
        }
```
Add the reception badges pinned to the bottom-start of the cover (bottom-end and top-end are used by `Badges`):
```kotlin
        Box {
            AlbumImage(item)
            Badges(
                item = item,
                providerIconFetcher = providerIconFetcher,
            )
            AlbumReceptionBadges(
                album = item,
                style = ReceptionBadgeStyle.Tile,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp),
            )
        }
```
`Modifier.align(Alignment.BottomStart)` is valid here because the parent is a `Box` (BoxScope). `Alignment` and `Modifier` and `padding` are already imported in `MediaItems.kt` (used elsewhere); if `Alignment` is not imported, add `import androidx.compose.ui.Alignment`.

- [ ] **Step 2: Compile + regression suite**

Run: `./gradlew :composeApp:testAndroidHostTest`
Expected: BUILD SUCCESSFUL, 156 tests green.

- [ ] **Step 3: detekt**

Run: `./gradlew detektAll`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Full app build (authoritative)**

Run: `./gradlew :androidApp:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/MediaItems.kt
git commit -m "feat(ui): album grid-tile reception badge overlay

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 6: Live verification (manual, against the enhanced server)**

Install on the device and open the album library in both grid and list view; albums with reception tags should show the DR (tinted) / AMG (★ + score) / TPS chips, and albums without should show none. Also check artist-album lists and search results render the badges.
```bash
./gradlew :androidApp:installDebug   # if a device is connected (may need uninstall first on signature mismatch)
```

---

## Self-Review

**Spec coverage:**
- `AlbumReceptionBadges` composable (Tile/Row, DR/AMG/TPS, hidden when no data) → Task 1. ✓
- DR semantic tint (default on), AMG/TPS neutral → Task 1 (`drHue` + `DrPill`/`SourcePill`). ✓
- Tile placement (bottom-start overlay) → Task 3. ✓
- Row placement (below subtitle via `RowItem.subtitleAccessory`) → Task 2. ✓
- Reuse `parseAlbumReception`/`formatScore`/`formatDr`, no data/model/server changes → all tasks (display-only). ✓
- a11y content description → Task 1 (merged `contentDescription` on the Row; pills `clearAndSetSemantics`). ✓
- Testing via previews + compile + detekt + assembleDebug → Tasks 1–3. ✓
- Listen-later excluded → not implemented anywhere. ✓

**Type consistency:** `AlbumReceptionBadges(album: Album, style: ReceptionBadgeStyle, modifier)`, `ReceptionBadgeStyle{Tile,Row}`, `DrPill(DrInfo)`, `SourcePill(SourceTags)`, `Pill(text: String?, container, content, leadingStar)`, `drHue(DrQuality): Color` — defined in Task 1 and used identically in Tasks 2–3. Reused symbols (`parseAlbumReception`, `ReceptionTags`, `DrInfo`, `SourceTags`, `DrQuality`, `formatScore`, `formatDr`, `Album.metadata`) match the prior slice's definitions. `RowItem.subtitleAccessory: (@Composable () -> Unit)?` defined in Task 2 Step 1, used in Task 2 Step 2.

**No placeholders:** every code step shows complete code; the two known-risk fallbacks (Star icon import; `Alignment` import) are concrete, not vague.
