# Album-view reception badges — mobile (Compose Multiplatform)

**Date:** 2026-06-05
**Repo:** `mobile-app` (music-assistant/mobile-app), branch `enhanced`
**Status:** Design approved, pending spec review

## Goal

Show compact **DR / AMG / TPS** reception chips on album **grid tiles** and **list
rows**, wherever albums appear (Library, artist-albums, search). This mirrors the
enhanced web app's `components/album/AlbumListBadges.vue` (list rows) and
`AlbumPanelBadges.vue` (grid/panel tiles), re-implemented natively.

This is the **second slice** of bringing the enhanced album metadata to mobile. The
first slice (album detail Reception panel) already landed the data + parser layers;
this slice is **display-only** and reuses them. No server or model changes.

## Scope

- **In:** DR chip, AMG chip (★ + score), TPS chip (score), on album grid tiles and
  list rows, gated on `parseAlbumReception(...).hasAny`.
- **Out (this slice):**
  - The **listen-later bookmark** chip the web shows alongside these — `listen_later`
    is not wired into the mobile data layer yet; it belongs to the future Listen Later
    slice.
  - **Filter & Order-by** (DR/AMG/TPS sort + reception filter sheet) — the next slice.

## Context / reuse

The data and logic already exist from the detail-panel slice:
- `Album.metadata?.criticalReception` (client `CriticalReception`/`ReviewSource`) and
  `Album.metadata?.dynamicRange` are populated by `MediaItemFactory`.
- `parseAlbumReception(cr, dr): ReceptionTags` exposes `dr` (`DrInfo` with `value`,
  `quality`, `source`), `amg`/`tps` (`SourceTags` with `rating`, `scale`, `favorite`),
  `hasAny`, plus `formatScore`/`formatDr`.

Injection points (both in `ui/compose/common/items/`):
- `MediaItems.kt` → `AlbumGridItem` renders `Box { AlbumImage(item); Badges(item, ...) }`
  (a `BoxScope.Badges` overlay: favorite/provider at bottom-end, explicit at top-end).
- `MediaItems.kt` → `AlbumRowItem` renders a generic `RowItem(name, subtitle, imageContent, …)`
  (a private composable: image Box + a Column of name + subtitle).
- `AlbumWithMenu` (`BrowsableItemWithMenu.kt`) already switches `AlbumGridItem` vs
  `AlbumRowItem` by `ViewMode`. No change needed there.

## Design

### 1. New composable: `ui/compose/common/items/AlbumReceptionBadges.kt`

```kotlin
enum class ReceptionBadgeStyle { Tile, Row }

@Composable
fun AlbumReceptionBadges(album: Album, style: ReceptionBadgeStyle, modifier: Modifier = Modifier)
```
- Computes `parseAlbumReception(album.metadata?.criticalReception, album.metadata?.dynamicRange)`;
  returns (emits nothing) when `!tags.hasAny`.
- Renders a horizontal strip of up to three small pills, in order **DR · AMG · TPS**,
  each shown only when present:
  - **DR** pill: text `DR ${formatDr(value)}` (e.g. `DR 12`).
  - **AMG** pill: a small filled `Star` glyph + `formatScore(rating)` (e.g. `★ 4.5`);
    when AMG has no rating but `favorite`, show just the star.
  - **TPS** pill: `formatScore(rating)` (e.g. `8.4`); star when favorite-only.
- `style` controls chrome only (see Styling): `Tile` pills carry a translucent scrim
  for legibility over album art; `Row` pills are flat.
- Each pill has a `contentDescription` for a11y (e.g. "Dynamic Range 12, Good",
  "AMG 4.5 of 5", "TPS 8.4 of 10") via the reception string resources.

### 2. Placement

- **Tile** — in `AlbumGridItem`, inside the existing cover `Box`, add
  `AlbumReceptionBadges(item, style = Tile, modifier = Modifier.align(Alignment.BottomStart).padding(4.dp))`.
  Bottom-start is free (bottom-end = favorite/provider, top-end = explicit/progress).
- **Row** — extend the private `RowItem` with an optional slot and pass the badges from
  `AlbumRowItem`:
  ```kotlin
  private fun RowItem(
      …,
      subtitleAccessory: (@Composable () -> Unit)? = null,
  )
  ```
  Render `subtitleAccessory?.invoke()` inside `RowItem`'s text `Column`, after the
  subtitle `Text` (the Column already uses `Arrangement.spacedBy(8.dp)`). `AlbumRowItem`
  passes `subtitleAccessory = { AlbumReceptionBadges(item, style = Row) }`. All other
  `RowItem` callers omit it (default `null`) — no behavior change for non-albums.

### 3. Styling

Consistent with the detail panel's Material-3-tonal language: small rounded pills
(`MaterialTheme.typography.labelSmall`, tabular numerals), tonal container.

- **DR pill — semantic tint (default ON).** The DR pill's container/text is tinted by
  `DrQuality` band (excellent/good → greenish/blue, fair → amber, poor/compressed →
  red), because at-a-glance scannability is the entire purpose of a list badge. A
  restrained, theme-aware tint (not loud). *This is the one open decision — see below.*
- **AMG / TPS pills — neutral tonal** (`surfaceVariant`/`secondaryContainer`), AMG with
  a small star glyph. No per-source accent (unlike the web's rose/sky), to stay within
  the Material-native direction chosen for the panel.
- **Tile variant:** pills sit on a translucent scrim (`scrim`/black at low alpha with
  rounded corners) so they read over arbitrary album art; kept small to avoid crowding
  small tiles.
- **Row variant:** flat pills on the row background.

### 4. i18n

Reuse existing `reception_*` strings where possible; add only content-description
strings if needed (e.g. `reception_badge_dr_cd` "Dynamic Range %1$s, %2$s",
`reception_badge_amg_cd` "AMG %1$s of 5", `reception_badge_tps_cd` "TPS %1$s of 10").
English now; `values-nl-rNL` falls back.

### 5. Testing

- `parseAlbumReception` / `formatScore` / `formatDr` are already unit-tested — no new
  parser tests needed.
- Compose `@Preview`s in `AlbumReceptionBadges.kt`: Tile and Row variants using the
  existing `AppMediaItemFixtures.receptionMetadata()` (full data) and a plain
  `AppMediaItemFixtures.album()` (no data → renders nothing). A row preview exercises
  the new `RowItem.subtitleAccessory` slot.
- Gates: `:composeApp:testAndroidHostTest` (compile + existing suite green),
  `:androidApp:assembleDebug`, `detektAll`.

## Open decision (resolve at spec review)

1. **DR semantic tint** — default **ON** for the DR pill only (AMG/TPS stay neutral).
   The detail panel stayed theme-neutral; badges differ because scanning a list is
   their job. Flip to all-neutral if preferred.

## Build sequence

1. `AlbumReceptionBadges.kt` (composable + previews) + any content-description strings.
2. Extend `RowItem` with the `subtitleAccessory` slot; wire `AlbumRowItem`.
3. Mount in `AlbumGridItem` (tile overlay).
4. Gates: testAndroidHostTest → assembleDebug → detektAll.
5. Live check on device against the enhanced server (albums list + grid).
