# Album Reception & DR panel — mobile (Compose Multiplatform)

**Date:** 2026-06-05
**Repo:** `mobile-app` (music-assistant/mobile-app), branch `enhanced`
**Status:** Design approved, pending spec review

## Goal

Bring the **enhanced** branch's album *critical reception* and *dynamic range* (DR)
metadata to the mobile app. On the album detail screen, show a "Reception" panel
with the AMG (5-pt) and TPS (10-pt) ratings/reviews and the album's DR value —
the same information the web frontend renders via `components/album/CriticalReception.vue`,
re-implemented natively in **Material 3** (the app's design language).

This is the **first slice** of a larger "bring enhanced features to mobile" effort.
Later slices (Listen Later, filter & order-by, list/grid DR badges, track-level DR)
are explicitly out of scope here but reuse this slice's data + parsing layer.

## Context & source of truth

The data already exists end-to-end on the enhanced branches; the mobile client is a
blank slate for it. No server/models changes are required for this slice.

- **models** (`music_assistant_models/media_items/metadata.py`):
  - `MediaItemMetadata.dynamic_range: float | None` — canonical measured DR.
    On an Album it's the album-scope mean of measured track DRs (rounded); on a
    Track it's the per-track DR14.
  - `MediaItemMetadata.critical_reception: CriticalReception | None`.
  - `CriticalReception(amg_dr: float | None, sources: list[ReviewSourceEntry] | None)`.
    `amg_dr` is AMG's review-reported DR (secondary/fallback only).
  - `ReviewSourceEntry(source: str = "", rating: float | None, favorite: bool | None,
    types: list[str] | None, labels: list[str] | None, authors: list[str] | None)`.
    Blank/`"None"`-sourced entries are dropped (forward-compat; mirrors the enum
    `_missing_` convention).
- **server**: populates these from file tags (`helpers/tags.py`: `dynamic_range`,
  `album_dynamic_range`, `critical_reception`) and exposes them on the normal album
  wire payload (`metadata.dynamic_range`, `metadata.critical_reception`). The album
  detail / `music/albums/library_items` responses already carry them.
- **frontend reference** (the experience we mirror, not the code):
  - `components/album/CriticalReception.vue` — the detail-screen strip.
  - `helpers/album_tags.ts` — the parsing/threshold logic (ported below).
  - `composables/useAlbumTags.ts`, `useAlbumBadgeLabels.ts` — shared label/score logic.

## Design

### 1. Data layer (wire → client)

**Wire** (`data/model/server/ServerMediaItem.kt`):

Extend `ServerMetadata`:
```kotlin
@SerialName("dynamic_range") val dynamicRange: Double? = null,
@SerialName("critical_reception") val criticalReception: ServerCriticalReception? = null,
```

New serializable types (same file):
```kotlin
@Serializable
data class ServerCriticalReception(
    @SerialName("amg_dr") val amgDr: Double? = null,
    @SerialName("sources") val sources: List<ServerReviewSourceEntry>? = null,
)

@Serializable
data class ServerReviewSourceEntry(
    @SerialName("source") val source: String = "",   // default "" so null/missing decodes
    @SerialName("rating") val rating: Double? = null,
    @SerialName("favorite") val favorite: Boolean? = null,
    @SerialName("types") val types: List<String>? = null,
    @SerialName("labels") val labels: List<String>? = null,
    @SerialName("authors") val authors: List<String>? = null,
)
```
Forward-compat: a `null`/missing `source` decodes to `""`; entries with a blank
source are dropped at the mapping boundary (kotlinx.serialization has no
`__post_init__`, so we filter in the mapper rather than the model).

**Client** (`data/model/client/Metadata.kt`): extend `Metadata` with the parsed,
UI-facing shape (carry only what the UI reads, per the app's mapping convention):
```kotlin
val dynamicRange: Float?,
val criticalReception: CriticalReception?,   // client model: amgDr + List<ReviewSource>
```
`CriticalReception` / `ReviewSource` client models live alongside `Metadata`
(`data/model/client/`). The `ServerMediaItem → AppMediaItem` mapper (where
`Metadata(...)` is constructed) populates them, dropping blank-source entries.

`Album` already exposes `metadata: Metadata?` via the `AppMediaItem` sealed class,
so the panel reads `(item as Album).metadata?.criticalReception` / `?.dynamicRange`.

### 2. Parsing logic (`helpers/AlbumReception.kt`, pure & testable)

A direct Kotlin port of `album_tags.ts`. Pure functions, no Compose/Android deps,
fully unit-testable in `commonTest`.

- Thresholds: `excellent = 14, good = 10, fair = 7`; `drQuality(v): DrQuality`
  → `EXCELLENT (>=14) / GOOD (>=10) / FAIR (>=7) / POOR (<7)`.
- Primary DR: prefer measured `dynamicRange`; fall back to `criticalReception.amgDr`
  (tagged `source = AMG`) so older scanned albums still show a value. `source`
  distinguishes `MEASURED` vs `AMG` so the UI can label a fallback honestly.
- Divergence: expose `amgDr` separately only when **both** measured and AMG values
  exist and their rounded integers differ (detail-view "AMG reported N" caption).
- `parseSource(entry)`: scale = 5 for `AMG`, 10 for `TPS`. Rating wins over favorite
  (rating only if finite and > 0; favorite only when no rating). `types` passthrough.
  `labels` parsed + priority-sorted. `authors` → role inference. Returns null when the
  entry carries no usable signal.
- Label parsing (`parseLabel`): exact matches `RECORD_OF_THE_MONTH`, `SCORE_REVISED`,
  `TYMHM`, `SITF`, `YMIO`, `LIT`, `RFU`; regex `AOTY-YYYY`, `AOTM-YYYY-M(M)`,
  `HONORABLE_MENTION-YYYY` (capture year/month); else `unknown`.
- Label sort priority: `aoty(0) < record_of_the_month(1) < aotm(2) <
  honorable_mention(3) < score_revised(4) < lit(5) < rfu(6) < tymhm(7) < sitf(8) <
  ymio(9) < unknown(100)`; ties break by year desc then month desc.
- Author roles: when scored, index 0 = `CANONICAL`, index 1 = `SECONDARY`, rest =
  `LIST_PICK`; when unscored, all = `LIST_PICK`. (Mobile renders the names; roles are
  retained in the model but not shown as tooltips — see Open decisions.)
- Output: `ReceptionTags(dr, amgDr, amg: SourceTags?, tps: SourceTags?, hasAny)`.

### 3. Compose UI (`ui/compose/item/ReceptionPanel.kt`, Material 3)

Mounted as a **full-span item in `ItemDetailsScreen`'s `LazyVerticalGrid`, directly
after `ItemHeader(...)` and before the tabbed tracks**, rendered **only** when
`item is Album` and `ReceptionTags.hasAny`.

A tonal `Card` (`MaterialTheme.colorScheme.surfaceContainer`), padded, titled
**"Reception"** (`titleMedium`). Vertical sections, each shown only when present:

- **Dynamic Range**: row with `"Dynamic Range"` label, the value (`titleLarge`,
  tabular), a `LinearProgressIndicator` (progress = `value / 20f`), and the verdict
  word (`labelLarge`, `onSurfaceVariant`). When DR is the AMG fallback, a small
  `AssistChip`/caption "AMG"; on divergence, a `bodySmall` caption "AMG reported N".
- **AMG** and **TPS**: each a row with `source`, `rating/scale` (e.g. `4.5 / 5`,
  `8.4 / 10`, `Number.isInteger ? toFixed(1)`), and a `LinearProgressIndicator`
  (`rating / scale`). When the source has only `favorite` (no rating): a "Personal
  pick" row with a `Star`/`AutoAwesome` icon instead.
- **Chips**: accolade labels + type labels as read-only `AssistChip`s, reusing the
  app's chip conventions; accolades get a leading `EmojiEvents`/trophy icon. Label
  display is localized (e.g. "AOTY 2024", "Record of the Month", "Honorable Mention
  2023", "Review", "TYMHM").
- **Bylines**: authors as a `bodySmall` `onSurfaceVariant` caption ("— J. Smith, A. Jones").

Colors default to **theme roles only** (bars use `primary`) per the chosen
"Material 3 native" direction. See Open decisions for the optional DR semantic tint.

### 4. i18n (Compose string resources)

Add keys to `composeResources/values/strings.xml` (English; `values-nl-rNL` falls
back to English until translated):
- `reception_title`, `reception_dynamic_range`, `reception_amg`, `reception_tps`
- `reception_dr_excellent|good|fair|poor` (verdict words)
- `reception_personal_pick`
- `reception_amg_dr_fallback`, `reception_amg_dr_reported` (arg: value)
- type labels: `reception_type_review|tymhm|sitf|ymio|lit|rfu` (unknown → raw)
- label displays: `reception_label_aoty` (year), `reception_label_aotm`
  (year, month), `reception_label_honorable_mention` (year),
  `reception_label_record_of_the_month`, `reception_label_score_revised`
- `reception_score_with_max` (score, max)

### 5. Testing

- `commonTest` unit tests for `AlbumReception` parsing, porting the meaningful cases
  from `helpers/album_tags.test.ts`:
  - DR band boundaries (13/14, 9/10, 6/7), measured-vs-AMG fallback, divergence
    present only when both exist and round differently.
  - `parseSource`: AMG scale 5 / TPS scale 10; rating-over-favorite; rating-less
    favorite → favorite; entirely empty entry → dropped.
  - Label parse (AOTY/AOTM/HONORABLE_MENTION/fixed/unknown) + priority sort.
  - Author role inference (scored vs unscored).
  - Blank/`"None"`-source entries dropped.
- A kotlinx.serialization decode test: a sample album JSON with
  `metadata.dynamic_range` + `metadata.critical_reception` (incl. a blank-source
  entry) decodes into `ServerMetadata` correctly.
- Compose `@Preview`s: extend `AppMediaItemFixtures.album(...)` to accept reception/DR
  so previews cover full/partial/favorite-only/fallback/empty states.

## Open decisions (resolve at spec review)

1. **DR semantic tint** — default is theme-only (`primary`). The web colors DR by
   quality (green→blue→amber→red), which carries real at-a-glance meaning for an
   audiophile feature. Opt-in: tint only the DR bar + verdict by quality band, leave
   everything else theme-driven. *Default: off.*
2. **Author roles** — retained in the model but not surfaced (no hover on mobile).
   Could show role as a tiny caption suffix later; default is names only.
3. **Long-form `review` prose** — `MediaItemMetadata.review` (free text) is *not*
   shown in this panel (matches the web `CriticalReception.vue`, which shows
   structured data only). Out of scope unless requested.

## Not in scope (future slices)

- Listen Later (save-for-later flag + dedicated list + `music/albums/listen_later_*`).
- Filter & order-by (DR / rating / listen-later) on library album lists.
- DR / rating badges on list rows and album grid tiles (web `AlbumListBadges.vue`).
- Track-level DR display in the album track list.

## Build sequence

1. Wire models (`ServerMetadata` + new types) + client `Metadata` + mapper changes.
2. `AlbumReception.kt` parsing + `commonTest` unit tests.
3. `ReceptionPanel.kt` Compose component + string resources + previews.
4. Mount in `ItemDetailsScreen` (full-span, albums only, `hasAny`).
5. Serialization decode test; verify against a live enhanced-server album response.
