# Sort albums by DR / AMG / TPS — mobile (Compose Multiplatform)

**Date:** 2026-06-06
**Repo:** `mobile-app` (music-assistant/mobile-app), branch `enhanced`
**Status:** Design approved, pending spec review

## Goal

Add three sort options — **Dynamic Range**, **AMG rating**, **TPS rating** — to the
album library sort menu, defaulting to **descending** (best first) on first pick. This
is the "order-by" half (B1) of the enhanced web app's album sorting; the filter half
already shipped. No server changes — the `order_by` keys already exist.

## Context

- `data/model/client/SortOption.kt`:
  - `enum class SortField(val serverKey: String, val displayName: String)` — each field's
    `serverKey` plus a (legacy, unused for display) `displayName`.
  - `SortOption(field, descending).toServerString()` = `serverKey` (+ `_desc` when
    descending). For the new fields this yields `dr`/`dr_desc`, `amg_rating`/
    `amg_rating_desc`, `tps_rating`/`tps_rating_desc` — exactly the server keys in
    `server/.../controllers/media/albums.py` `extra_sort_keys`.
  - `SortConfig.fieldsFor(MediaType.ALBUM)` is the field list shown for the album library.
  - `clientSorted` is used ONLY by `ItemDetailsViewModel` sub-lists (artist-albums =
    Name/Year, tracks, episodes); the main album library (`ItemListViewModel`) sorts
    server-side via `order_by`. So these fields, added only to `fieldsFor(MediaType.ALBUM)`,
    never reach `clientSorted` — no change needed there.
- `ui/compose/common/SortChip.kt`:
  - Displays a field via `SortField.localizedName()` — an **exhaustive** `when (this)`
    mapping each field to `stringResource(Res.string.sort_*)` (compiler forces a case for
    every new value).
  - The dropdown's field-tap logic: tapping a *different* field calls
    `onSortChanged(SortOption(field))` (ascending); tapping the *current* field toggles
    direction.

## Design

### 1. `SortField` — add the three values + a default-direction flag

Add an optional `defaultDescending` property (default `false`, so existing fields are
unchanged) and the three new values:
```kotlin
enum class SortField(
    val serverKey: String,
    val displayName: String,
    val defaultDescending: Boolean = false,
) {
    // ...existing entries unchanged...
    DR("dr", "DR", defaultDescending = true),
    AMG_RATING("amg_rating", "AMG", defaultDescending = true),
    TPS_RATING("tps_rating", "TPS", defaultDescending = true),
}
```

### 2. `SortConfig.fieldsFor(MediaType.ALBUM)` — append the three

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

### 3. `SortChip` — labels + descending-first on new-field pick

- In `SortField.localizedName()`, add three `when` cases:
  ```kotlin
  SortField.DR -> stringResource(Res.string.sort_dr)
  SortField.AMG_RATING -> stringResource(Res.string.sort_amg)
  SortField.TPS_RATING -> stringResource(Res.string.sort_tps)
  ```
- In the dropdown's field-tap `else` branch (a *different* field is picked), honor the
  field's default direction:
  ```kotlin
  // was: onSortChanged(SortOption(field))
  onSortChanged(SortOption(field, descending = field.defaultDescending))
  ```
  (The ORIGINAL branch and the same-field toggle branch are unchanged. Existing fields
  have `defaultDescending = false`, so their behavior — ascending on first pick — is
  preserved.)

### 4. i18n

Add to `composeResources/values/strings.xml`: `sort_dr` ("DR"), `sort_amg` ("AMG"),
`sort_tps` ("TPS"). English now; `values-nl-rNL` falls back.

## Testing

- `commonTest` (`SortOptionTest` or similar):
  - `SortOption(SortField.DR, descending = false).toServerString() == "dr"` and
    `descending = true → "dr_desc"`; same for `AMG_RATING` (`amg_rating`/`amg_rating_desc`)
    and `TPS_RATING` (`tps_rating`/`tps_rating_desc`).
  - `SortConfig.fieldsFor(MediaType.ALBUM)` contains `DR`, `AMG_RATING`, `TPS_RATING`.
  - `SortField.DR.defaultDescending` is `true`; `SortField.NAME.defaultDescending` is
    `false` (existing fields unaffected).
- Compile (the exhaustive `localizedName()` `when` guarantees the labels exist) + `detektAll`.
- Live check: open the album library sort menu, pick DR/AMG/TPS, confirm the list
  re-sorts (server-side) and the new fields default to descending; confirm the direction
  arrow toggles on a second tap.

## Not in scope

- Sorting these fields anywhere other than the main album library (artist-albums etc.
  keep Name/Year).
- Changing the default direction of existing fields (Name/Year/etc. unchanged).
- Removing the unused `SortField.displayName` property (out of scope; legacy cleanup).

## Build sequence

1. `SortField` (3 values + `defaultDescending`) + `fieldsFor(MediaType.ALBUM)` + unit tests.
2. `SortChip.localizedName()` cases + `sort_dr`/`sort_amg`/`sort_tps` strings + the
   descending-first `else`-branch change.
3. Gates (testAndroidHostTest → detektAll → assembleDebug) + live check.
