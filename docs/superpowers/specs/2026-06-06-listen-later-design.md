# Listen Later (save-for-later) — mobile (Compose Multiplatform)

**Date:** 2026-06-06
**Repo:** `mobile-app` (music-assistant/mobile-app), branch `enhanced`
**Status:** Design approved, pending spec review

## Goal

Bring the enhanced server's Roon-style **save-for-later** for albums to the mobile app:
a toggle action (save / remove) plus a dedicated **Listen Later** screen listing saved
albums newest-first. No server or models changes — the API, the `listen_later` flag, and
the sort/filter keys already exist.

## Context

- **models** (`media_items/media_item.py`): `MediaItem.listen_later: bool` and
  `listen_later_added_at: int | None` (epoch seconds when set) ride on every item payload;
  only albums surface UI today.
- **server** (`controllers/music.py`):
  - `music/albums/listen_later_add(item)` — `item` is an MA URI or Album; marks the album
    save-for-later WITHOUT adding it to the regular library (no `in_library` flip). It is
    **deliberately excluded from the regular Albums view** — only `/listen-later` shows it.
    Returns the `Album`.
  - `music/albums/listen_later_remove(library_item_id)` — clears the flag (and deletes the
    orphan row if it has no other anchor). No-op if not currently saved.
  - `music/albums/library_items(..., listen_later=true, order_by="listen_later_added_at_desc")`
    returns the saved albums, newest-first. (`listen_later_added_at` / `_desc` are valid
    `order_by` keys.)
- **mobile** insertion points (already explored):
  - `data/model/server/ServerMediaItem.kt` (the wire mirror) + `data/factory/MediaItemFactory.kt`
    (maps to client `Album`); `AppMediaItem` is the client sealed base.
  - `api/APICommands.kt` + `api/Request.kt` (`object Album`); `Request.Album.listLibrary`
    already takes favorite/search/limit/offset/orderBy/receptionFilter.
  - `ui/compose/common/items/ItemAction.kt` (the `ItemAction` sealed class + `title()`/`icon()`),
    `ItemActionResolver.kt` (builds the overflow actions), `ItemActions.kt`
    (`LibraryActions` interface), `ui/compose/common/viewmodel/ActionsViewModel.kt`
    (implements `LibraryActions`; `onFavoriteClick` = optimistic `dataSource` override +
    `Request.Library.addFavorite/removeFavorite` — the pattern to mirror).
  - Navigation (`ui/compose/home/MainNavRoot.kt`): a `MainNav` sealed interface (`Landing`,
    `Library`, `ItemList(mediaType)`, `ItemDetails`, `Search`) registered via
    `entryProvider { entry<MainNav.X> { ... } }`; ViewModels created via Koin
    `parametersOf(...)`. `ui/compose/library/ItemListScreen.kt` renders an album list via
    `AdaptiveMediaGrid` — reusable for the new screen.
  - `ui/compose/library/LibraryScreen.kt` has a `TopAppBar` with an actions slot (currently
    the `Tune` customize button) — the entry-point home for the bookmark button.

## Design

### 1. Data layer

- `ServerMediaItem` (wire): add `@SerialName("listen_later") val listenLater: Boolean? = null`
  and `@SerialName("listen_later_added_at") val listenLaterAddedAt: Long? = null` (top-level,
  not metadata — they ride on the base item).
- `AppMediaItem` (client sealed base): add `val listenLater: Boolean` (default `false`) so
  albums carry it; `Album` populates it from the wire value.
- `MediaItemFactory`: map `listenLater = server.listenLater == true` (and `listenLaterAddedAt`
  if the dedicated screen needs the timestamp; the screen sorts server-side, so the value is
  optional on the client — include it only if used).

### 2. API

- `APICommands`: `MUSIC_ALBUMS_LISTEN_LATER_ADD = "music/albums/listen_later_add"`,
  `MUSIC_ALBUMS_LISTEN_LATER_REMOVE = "music/albums/listen_later_remove"`.
- `Request.Album`:
  - `listenLaterAdd(uri: String)` → args `{ "item": uri }`.
  - `listenLaterRemove(libraryItemId: String)` → args `{ "library_item_id": libraryItemId }`.
  - extend `listLibrary(...)` with `listenLater: Boolean? = null` → emits `listen_later=true`
    when set (used by the Listen Later screen).

### 3. Toggle action

- `ItemAction`: add `data object SaveForLater` and `data object RemoveFromLater` (Kind.OTHER);
  `title()` → new strings; `icon()` → a bookmark glyph (Tabler `Bookmark`/`BookmarkOff` or a
  Material bookmark — match the icon library already used in `ItemAction.kt`).
- `LibraryActions` interface: add `fun onListenLaterClick(item: AppMediaItem)`.
- `ActionsViewModel.onListenLaterClick`: `if (item.listenLater) sendRequest(Request.Album.listenLaterRemove(item.itemId)) else sendRequest(Request.Album.listenLaterAdd(item.uri))`.
  Reflect the change by refetch / the existing `mediaItemRepository.itemChanges` flow. An
  optimistic local override (mirroring `setFavoriteOverride`) is optional polish — not required.
- `ItemActionResolver` (`resolveDetailOverflowActions` and any list-row action builder): include
  `SaveForLater`/`RemoveFromLater` (based on `item.listenLater`) **for `Album` items only**.

### 4. Dedicated Listen Later screen

- `MainNav.ListenLater` (data object) + `entry<MainNav.ListenLater> { ListenLaterScreen(...) }`
  in `MainNavRoot`.
- `ListenLaterViewModel` (Koin): loads
  `Request.Album.listLibrary(listenLater = true, orderBy = "listen_later_added_at_desc")` via
  `MediaItemRepository`; exposes a `DataState<List<AppMediaItem>>`. Supports remove-in-place
  (the album overflow's RemoveFromLater drops the row / triggers a reload).
- `ListenLaterScreen` reuses `AdaptiveMediaGrid` + the album item menu for rendering (play,
  open, remove-from-later). A `Screen { TopAppBar(title = "Listen Later", back) }` shell.

### 5. Entry point

A bookmark `IconButton` in `LibraryScreen`'s `TopAppBar` actions (next to the `Tune` customize
button) that navigates to `MainNav.ListenLater`. (Wiring: `LibraryScreen` gains an
`onListenLaterClick: () -> Unit`; `MainNavRoot`'s `Library` entry pushes `MainNav.ListenLater`.)

### 6. i18n

`action_save_for_later` ("Save for later"), `action_remove_from_later` ("Remove from Listen
Later"), `listen_later_title` ("Listen Later"), `cd_listen_later` (entry-button description).

## Testing

- `commonTest` decode test: a `ServerMediaItem` with `listen_later: true` /
  `listen_later_added_at` decodes; `MediaItemFactory` maps an album's `listenLater`.
- `Request.Album` builder tests: `listenLaterAdd(uri).args` = `{item}`,
  `listenLaterRemove(id).args` = `{library_item_id}`, `listLibrary(listenLater=true)` emits
  `listen_later=true` (and omits it when null).
- `ItemActionResolver` test: an `Album` with `listenLater=false` yields `SaveForLater`; with
  `listenLater=true` yields `RemoveFromLater`; non-albums yield neither.
- `ListenLaterScreen`/`ViewModel` via preview + the live device check (save an album from
  search/detail → appears in Listen Later newest-first → remove drops it).
- Gates: `:composeApp:testAndroidHostTest`, `detektAll`, `:androidApp:assembleDebug`.

## Open decisions (resolve at spec review)

1. **Optimistic vs refetch** for the toggle — default: rely on refetch / `itemChanges`
   (simplest); an optimistic `listenLater` override is optional follow-up.
2. **`listen_later_added_at` on the client** — included only if the screen needs it; the sort
   is server-side, so it can be omitted from the client model. Default: omit unless used.

## Not in scope

- Bookmark badge on album rows/tiles (the web shows one; deferred — the dedicated screen is
  the primary surface).
- Listen-later for non-album media types (server is album-only).
- The `critical_reception` payload on `listen_later_add` (a file-scan/external-client concern).
- A library *category* for Listen Later (chosen approach is a separate screen).

## Build sequence

1. Data: `ServerMediaItem` + `AppMediaItem`/`Album` + `MediaItemFactory` (+ decode test).
2. API: `APICommands` + `Request.Album` add/remove/`listLibrary` listenLater (+ builder tests).
3. Action: `ItemAction` + `LibraryActions` + `ActionsViewModel` + `ItemActionResolver`
   (+ resolver test); wire the overflow menu.
4. Screen: `ListenLaterViewModel` + `ListenLaterScreen` + `MainNav.ListenLater` entry + Koin.
5. Entry point: bookmark button in `LibraryScreen` top bar + nav wiring + strings.
6. Gates (testAndroidHostTest → detektAll → assembleDebug) + live device check.
