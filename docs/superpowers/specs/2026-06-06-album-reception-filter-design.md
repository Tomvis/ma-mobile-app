# Album reception filter sheet — mobile (Compose Multiplatform)

**Date:** 2026-06-06
**Repo:** `mobile-app` (music-assistant/mobile-app), branch `enhanced`
**Status:** Design approved, pending spec review

## Goal

Add a **reception filter** to the album library list: filter albums by Dynamic Range,
AMG/TPS ratings, accolade labels, and source favorite/untagged flags, combined with an
ALL/ANY match mode. Mirrors the enhanced web app's
`components/album/ReviewFiltersPanel.vue`, re-implemented as a native Material 3
bottom sheet. Filters apply **live** (each change re-queries the library).

This is a later slice of bringing the enhanced album metadata to mobile. The data,
parser, detail panel, and list/grid badges already landed. The server already supports
every filter param; **no server or model changes**. Sorting by DR/AMG/TPS is a separate
(B1) slice, not part of this one.

## Context

`music/albums/library_items` (server `controllers/media/albums.py`) already accepts:
- `dr_buckets: list[str]` — `excellent` (≥14) / `good` (10–14) / `fair` (7–10) /
  `poor` (<7) / `untagged`.
- `amg_ratings: list[int]` — 1..5; matches `floor(AMG rating) == N`.
- `tps_ratings: list[int]` — band selectors `1,3,5,7,9`; each covers `[N, N+2)` on the
  /10 scale (i.e. 1–3, 3–5, 5–7, 7–9, 9–11).
- `amg_favorite` / `tps_favorite: bool` — source flagged as a favorite/personal pick.
- `amg_labels` / `tps_labels: list[str]` — accolade kinds; server matches exactly
  `aoty`, `aotm`, `honorable_mention`, `record_of_the_month`.
- `amg_untagged` / `tps_untagged: bool` — albums missing that source entirely.
- `critical_reception_match: str` — `"all"` (default; AND all clauses) or `"any"` (OR).
- (`favorite: bool` favorites-only already exists and keeps its own existing chip — NOT
  part of this sheet.)

Mobile plumbing this slice extends:
- `ui/compose/library/ItemListViewModel.kt` — drives the album list. Its `init` reloads
  `loadFirstPage()` whenever `searchQuery` / `sortOption` / `onlyFavorites` change
  (reactive `_state.map { … }.distinctUntilChanged().collect { loadFirstPage() }`).
  `getRequest(MediaType.ALBUM, …)` → `Request.Album.listLibrary(...)`.
- `api/Request.kt` — `Request.Album.listLibrary(favorite, search, limit, offset, orderBy)`;
  lists are serialized as JSON arrays via
  `myJson.decodeFromString<JsonArray>(myJson.encodeToString(list))` (existing pattern).
- `ui/compose/library/ItemListScreen.kt` — control row in `TwoRowTopAppBar` already has
  a favorites `FilterChip`, a `SortChip`, and a view-mode `IconButton`.

## Design

### 1. Filter model — `data/model/client/ReceptionFilter.kt`

```kotlin
data class ReceptionFilter(
    val drBuckets: Set<String> = emptySet(),   // excellent/good/fair/poor/untagged
    val amgRatings: Set<Int> = emptySet(),      // 1..5
    val amgLabels: Set<String> = emptySet(),    // aoty/aotm/honorable_mention/record_of_the_month
    val amgFavorite: Boolean = false,
    val amgUntagged: Boolean = false,
    val tpsRatings: Set<Int> = emptySet(),      // 1,3,5,7,9
    val tpsLabels: Set<String> = emptySet(),
    val tpsFavorite: Boolean = false,
    val tpsUntagged: Boolean = false,
    val matchAny: Boolean = false,              // false = "all", true = "any"
) {
    val isActive: Boolean get() = /* any set non-empty or any flag true */
    val activeCount: Int get() = /* sum of selected chips + true flags (match mode not counted) */
}
```
Constants for the option sets (DR bucket ids, AMG ratings 1..5, TPS band selectors
1/3/5/7/9, accolade label ids) live alongside the model.

### 2. Request args mapper

A pure function (e.g. `ReceptionFilter.toRequestArgs(): Map<String, JsonElement>` or a
helper) producing only the keys that are active:
- `dr_buckets`, `amg_ratings`, `amg_labels`, `tps_ratings`, `tps_labels` → `JsonArray`
  (omitted when empty).
- `amg_favorite`, `amg_untagged`, `tps_favorite`, `tps_untagged` → `JsonPrimitive(true)`
  (omitted when false).
- `critical_reception_match` → `"any"` only when `matchAny` (omitted otherwise; server
  defaults to `"all"`).
`Request.Album.listLibrary` gains a `receptionFilter: ReceptionFilter = ReceptionFilter()`
param and merges these args into its `buildJsonObject`. This mapper is the key
**unit-tested** unit.

### 3. ViewModel wiring

- `ItemListViewModel.State` gains `receptionFilter: ReceptionFilter = ReceptionFilter()`.
- Add `receptionFilter` to the reactive reload key in `init` (extend the tracked tuple),
  so any filter change auto-reloads page 0 — this *is* the live-apply behavior.
- `onReceptionFilterChanged(filter: ReceptionFilter)` updates state.
- `getRequest`'s `MediaType.ALBUM` branch passes `receptionFilter` to
  `Request.Album.listLibrary`. Other media types are unchanged (no filter param).

### 4. UI — `ReceptionFilterSheet` + entry point

New `ui/compose/library/ReceptionFilterSheet.kt`: a Material 3 `ModalBottomSheet` taking
`filter: ReceptionFilter` + `onChange: (ReceptionFilter) -> Unit` + `onDismiss`. Sections:
- **Header**: title + active count + **Clear all** (resets to `ReceptionFilter()`).
- **Match**: `SingleChoiceSegmentedButtonRow` ALL / ANY (`matchAny`).
- **DR**: 5 `FilterChip`s — Excellent / Good / Fair / Poor / Untagged (toggle `drBuckets`).
- **AMG**: 5 rating `FilterChip`s (1★…5★ → `amgRatings`), 4 label chips (AOTY / AOTM /
  Honorable Mention / Record of the Month → `amgLabels`), and two toggles "Personal pick"
  (`amgFavorite`) / "Untagged" (`amgUntagged`).
- **TPS**: 5 band chips (1–3 / 3–5 / 5–7 / 7–9 / 9+ → `tpsRatings`), 4 label chips
  (`tpsLabels`), "Personal pick" (`tpsFavorite`) / "Untagged" (`tpsUntagged`).
Every toggle emits an updated `ReceptionFilter` via `onChange` (live).

Entry point in `ItemListScreen`'s control row: a filter `IconButton` (e.g.
`Icons.Default.FilterList`/`Tune`) shown **only when `mediaType == MediaType.ALBUM`**,
with a small active-count badge when `filter.isActive`; tapping opens the sheet
(`rememberModalBottomSheetState`). Wire `onChange` → `itemListViewModel.onReceptionFilterChanged`.

### 5. i18n

Add filter strings to `composeResources/values/strings.xml` (reuse existing
`reception_*` verdict/label strings where they fit): section headers (`filter_title`,
`filter_match`, `filter_match_all`, `filter_match_any`, `filter_dr`, `filter_amg`,
`filter_tps`, `filter_rating`, `filter_labels`, `filter_personal_pick`,
`filter_untagged`, `filter_clear_all`, `filter_active_count`), DR bucket names (reuse
`reception_dr_*`; add `filter_dr_untagged`), and TPS band labels (e.g. `1–3`, computed).
English now; `values-nl-rNL` falls back.

### 6. Testing

- `ReceptionFilterTest` (`commonTest`): `isActive`/`activeCount` for empty + various
  combinations; the request-args mapper — empty filter → no keys; each
  bucket/rating/label set → correct array key+contents; each flag → bool key only when
  true; `critical_reception_match` present only when `matchAny`.
- A `Request.Album.listLibrary` arg-serialization test (decode the built args, assert the
  filter keys/arrays match), alongside the existing request/serialization tests.
- `ReceptionFilterSheet` `@Preview`s (empty + a populated filter).
- Gates: `:composeApp:testAndroidHostTest`, `detektAll`, `:androidApp:assembleDebug`,
  plus a live device check.

## Open decisions (resolve at spec review)

1. **Filter button icon / placement** — default `Icons.Default.FilterList` with an
   active-count badge, in the existing control row, album-only. (Cosmetic.)
2. **TPS band chip labels** — shown as numeric ranges `1–3 … 9+`; could instead be
   `≥7` style. Default: ranges.

## Not in scope

- Sort by DR/AMG/TPS (separate B1 slice).
- Listen-later filter (data not wired on mobile yet).
- Persisting the filter across app restarts (session-only for v1, like the current sort
  which is not persisted per-list either — confirm at review if persistence is wanted).
- Applying reception filters anywhere other than the album library list (e.g.
  artist-albums) — album library only for v1.

## Build sequence

1. `ReceptionFilter` model + constants + request-args mapper + `commonTest` unit tests.
2. Extend `Request.Album.listLibrary` with the filter param + serialization test.
3. Wire `ItemListViewModel` (state field, reactive reload key, `onReceptionFilterChanged`,
   `getRequest` album branch).
4. `ReceptionFilterSheet` composable + strings + previews.
5. Filter `IconButton` entry point (album-only, active badge) in `ItemListScreen` + open
   sheet + wire to the ViewModel.
6. Gates (testAndroidHostTest → detektAll → assembleDebug) + live device check.
