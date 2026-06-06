# Listen Later (save-for-later) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Roon-style save-for-later for albums: a save/remove toggle in the album overflow menu plus a dedicated Listen Later screen (saved albums, newest-first), reached from a bookmark button in the Library top bar.

**Architecture:** The `listen_later` flag (already on every server item payload) is wired into the wire + client album models; a `Request.Album` add/remove pair drives an `ActionsViewModel.onListenLaterClick` toggle (mirroring favorite); a new `ListenLaterScreen`/`ViewModel` queries `library_items(listen_later=true, order_by=listen_later_added_at_desc)` and reuses `AdaptiveMediaGrid`; a new `MainNav.ListenLater` destination is reached from the Library top bar. No server/models changes.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform (Material 3), kotlinx.serialization, kotlin.test, Compose Resources, Koin, androidx.navigation3.

**Conventions:**
- Unit tests: `kotlin.test`; run `./gradlew :composeApp:testAndroidHostTest` (supports `--tests "*Name*"`). `myJson` is `io.music_assistant.client.utils.myJson`; factory tests use `MediaItemFactory(FakeClient())`. Static analysis: `./gradlew detektAll` (autoCorrect may reformat — keep it; if a run reports BUILD FAILED right after autocorrecting, run it again). The final task runs `./gradlew :androidApp:assembleDebug`.
- Gradle pinned to the Android Studio JBR (JDK 21) via `~/.gradle/gradle.properties`; SDK via gitignored `local.properties` — don't touch/commit either. Gradle is SLOW (1–4 min; assembleDebug several): run via Bash `timeout: 600000` or `run_in_background: true` + poll; never two gradle invocations at once.
- All work on branch `enhanced`, committed directly. UI files use `@file:Suppress("MagicNumber")` for dp/size tokens (codebase convention).

---

### Task 1: Data — `listen_later` on the album model

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/server/ServerMediaItem.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/items/AppMediaItem.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/items/Album.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/data/factory/MediaItemFactory.kt`
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/ListenLaterMappingTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ListenLaterMappingTest.kt`:
```kotlin
package io.music_assistant.client.data.model.client

import io.music_assistant.client.data.factory.MediaItemFactory
import io.music_assistant.client.data.model.server.FakeClient
import io.music_assistant.client.data.model.server.ServerMediaItem
import io.music_assistant.client.data.model.client.items.Album
import io.music_assistant.client.utils.myJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenLaterMappingTest {
    private val factory = MediaItemFactory(FakeClient())

    private fun albumJson(listenLater: Boolean) = """
        {"item_id":"a1","provider":"library","name":"Album",
         "media_type":"${MediaType.ALBUM.serverValue}","listen_later":$listenLater}
    """.trimIndent()

    @Test fun mapsListenLaterTrue() {
        val album = factory.create(myJson.decodeFromString<ServerMediaItem>(albumJson(true))) as Album
        assertTrue(album.listenLater)
    }

    @Test fun defaultsListenLaterFalseWhenAbsent() {
        val json = """{"item_id":"a2","provider":"library","name":"A","media_type":"${MediaType.ALBUM.serverValue}"}"""
        val album = factory.create(myJson.decodeFromString<ServerMediaItem>(json)) as Album
        assertFalse(album.listenLater)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterMappingTest*"`
Expected: FAIL — `Album.listenLater` unresolved.

- [ ] **Step 3: Wire model — `ServerMediaItem`**

In `ServerMediaItem.kt`, add to the `ServerMediaItem` data class (near `favorite`):
```kotlin
    @SerialName("listen_later") val listenLater: Boolean? = null,
```
(Do not add `listen_later_added_at` — the screen sorts server-side; the client doesn't need the timestamp.)

- [ ] **Step 4: Client base — `AppMediaItem`**

In `AppMediaItem.kt`, add an open default property to the `sealed class AppMediaItem` (after `abstract val favorite: Boolean?`):
```kotlin
    open val listenLater: Boolean get() = false
```
(Open with a default so only `Album` overrides it; other subtypes are untouched.)

- [ ] **Step 5: `Album` overrides it**

In `Album.kt`, add a constructor property (defaulted, so existing construction sites / fixtures keep compiling) and mark it `override`. The `Album` data class currently starts:
```kotlin
data class Album(
    override val itemId: String,
    ...
    val artists: List<Artist>,
) : AppMediaItem() {
```
Add `override val listenLater: Boolean = false,` as the LAST constructor parameter (after `artists`):
```kotlin
data class Album(
    override val itemId: String,
    // ...unchanged...
    val artists: List<Artist>,
    override val listenLater: Boolean = false,
) : AppMediaItem() {
```

- [ ] **Step 6: Map it in `MediaItemFactory`**

In `MediaItemFactory.kt`, the `MediaType.ALBUM -> Album(...)` branch — add `listenLater` to the constructor call (after `artists = ...`):
```kotlin
            MediaType.ALBUM -> Album(
                // ...unchanged args...
                artists = artists?.mapNotNull { create(it) as? Artist } ?: emptyList(),
                listenLater = listenLater == true,
            )
```
(`listenLater` here is `server.listenLater` from the `with(server)` receiver.)

- [ ] **Step 7: Run to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterMappingTest*"` → PASS (2 tests).
Then `./gradlew :composeApp:testAndroidHostTest` → BUILD SUCCESSFUL, full suite green.

- [ ] **Step 8: detekt + commit**

Run `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/server/ServerMediaItem.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/items/AppMediaItem.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/items/Album.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/factory/MediaItemFactory.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/data/model/client/ListenLaterMappingTest.kt
git commit -m "feat(listen-later): wire listen_later flag onto the album model

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: API — `Request.Album` add / remove / listLibrary filter

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/api/APICommands.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/api/Request.kt` (`object Album`)
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/api/ListenLaterRequestTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ListenLaterRequestTest.kt`:
```kotlin
package io.music_assistant.client.api

import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListenLaterRequestTest {

    @Test fun addUsesItemUri() {
        val req = Request.Album.listenLaterAdd("library://album/5")
        assertEquals("music/albums/listen_later_add", req.command)
        assertEquals(JsonPrimitive("library://album/5"), req.args!!["item"])
    }

    @Test fun removeUsesLibraryItemId() {
        val req = Request.Album.listenLaterRemove("5")
        assertEquals("music/albums/listen_later_remove", req.command)
        assertEquals(JsonPrimitive("5"), req.args!!["library_item_id"])
    }

    @Test fun listLibraryEmitsListenLaterWhenSet() {
        assertEquals(
            JsonPrimitive(true),
            Request.Album.listLibrary(listenLater = true).args!!["listen_later"],
        )
        assertNull(Request.Album.listLibrary().args!!["listen_later"])
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterRequestTest*"`
Expected: FAIL — `listenLaterAdd`/`listenLaterRemove` unresolved; `listLibrary` has no `listenLater`.

- [ ] **Step 3: APICommands**

In `APICommands.kt`, in the Album commands section (near `MUSIC_ALBUMS_LIBRARY_ITEMS`), add:
```kotlin
    const val MUSIC_ALBUMS_LISTEN_LATER_ADD = "music/albums/listen_later_add"
    const val MUSIC_ALBUMS_LISTEN_LATER_REMOVE = "music/albums/listen_later_remove"
```

- [ ] **Step 4: Request.Album builders**

In `Request.kt`, inside `object Album`, extend `listLibrary` with a `listenLater` param and add the two builders. The current `listLibrary` is:
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
Change it to add `listenLater: Boolean? = null` and emit it, and add the two new builders right after:
```kotlin
        fun listLibrary(
            favorite: Boolean? = null,
            search: String? = null,
            limit: Int = Int.MAX_VALUE,
            offset: Int = 0,
            orderBy: String? = null,
            receptionFilter: ReceptionFilter = ReceptionFilter(),
            listenLater: Boolean? = null,
        ) = Request(
            command = APICommands.MUSIC_ALBUMS_LIBRARY_ITEMS,
            args = buildJsonObject {
                favorite?.let { put("favorite", JsonPrimitive(it)) }
                search?.let { put("search", JsonPrimitive(it)) }
                put("limit", JsonPrimitive(limit))
                put("offset", JsonPrimitive(offset))
                orderBy?.let { put("order_by", JsonPrimitive(it)) }
                receptionFilter.toRequestArgs().forEach { (k, v) -> put(k, v) }
                listenLater?.let { put("listen_later", JsonPrimitive(it)) }
            },
        )

        fun listenLaterAdd(itemUri: String) = Request(
            command = APICommands.MUSIC_ALBUMS_LISTEN_LATER_ADD,
            args = buildJsonObject { put("item", JsonPrimitive(itemUri)) },
        )

        fun listenLaterRemove(libraryItemId: String) = Request(
            command = APICommands.MUSIC_ALBUMS_LISTEN_LATER_REMOVE,
            args = buildJsonObject { put("library_item_id", JsonPrimitive(libraryItemId)) },
        )
```

- [ ] **Step 5: Run to verify it passes**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterRequestTest*"` → PASS (3 tests).

- [ ] **Step 6: detekt + commit**

Run `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/api/APICommands.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/api/Request.kt \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/api/ListenLaterRequestTest.kt
git commit -m "feat(listen-later): Request.Album add/remove + listen_later library filter

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Toggle action — resolver, LibraryActions, ActionsViewModel, dispatch

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemAction.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemActionResolver.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemActions.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/viewmodel/ActionsViewModel.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: the action→handler dispatch site(s) (located via grep in Step 7)
- Test: `composeApp/src/commonTest/kotlin/io/music_assistant/client/ui/compose/common/items/ListenLaterActionResolverTest.kt`

- [ ] **Step 1: Write the failing test**

Create `ListenLaterActionResolverTest.kt`:
```kotlin
package io.music_assistant.client.ui.compose.common.items

import io.music_assistant.client.data.model.client.AppMediaItemFixtures
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ListenLaterActionResolverTest {

    @Test fun savedAlbumOffersRemove() {
        val actions = resolveDetailOverflowActions(
            item = AppMediaItemFixtures.album(listenLater = true),
            librarySupported = true,
            canAddToPlaylist = false,
        )
        assertTrue(ItemAction.RemoveFromLater in actions)
        assertFalse(ItemAction.SaveForLater in actions)
    }

    @Test fun unsavedAlbumOffersSave() {
        val actions = resolveDetailOverflowActions(
            item = AppMediaItemFixtures.album(listenLater = false),
            librarySupported = true,
            canAddToPlaylist = false,
        )
        assertTrue(ItemAction.SaveForLater in actions)
        assertFalse(ItemAction.RemoveFromLater in actions)
    }

    @Test fun nonAlbumOffersNeither() {
        val actions = resolveLongClickActions(
            item = AppMediaItemFixtures.artist(),
            librarySupported = true,
            canAddToPlaylist = false,
            canRemoveFromPlaylist = false,
            progressSupported = false,
        )
        assertFalse(ItemAction.SaveForLater in actions)
        assertFalse(ItemAction.RemoveFromLater in actions)
    }
}
```
(NOTE: `AppMediaItemFixtures.album(...)` needs a `listenLater` param — add `listenLater: Boolean = false` to that fixture in Step 4 and pass it to the `Album(...)` it builds.)

- [ ] **Step 2: Run to verify it fails**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterActionResolverTest*"`
Expected: FAIL — `ItemAction.SaveForLater` and the fixture param unresolved.

- [ ] **Step 3: `ItemAction` — new actions + title + icon**

In `ItemAction.kt`:
- Add to the sealed class (next to `Favorite`/`Unfavorite`):
```kotlin
    data object SaveForLater : ItemAction(Kind.OTHER)
    data object RemoveFromLater : ItemAction(Kind.OTHER)
```
- In `fun ItemAction.title()`, add cases:
```kotlin
    ItemAction.SaveForLater -> Res.string.action_save_for_later
    ItemAction.RemoveFromLater -> Res.string.action_remove_from_later
```
- In the `fun ItemAction.icon()` `when`, add cases using a bookmark glyph from the icon set already imported in this file (`compose.icons.tablericons`). Use `TablerIcons.Bookmark` for SaveForLater and `TablerIcons.BookmarkOff` for RemoveFromLater (add the imports `import compose.icons.tablericons.Bookmark` and `import compose.icons.tablericons.BookmarkOff`):
```kotlin
    ItemAction.SaveForLater -> TablerIcons.Bookmark
    ItemAction.RemoveFromLater -> TablerIcons.BookmarkOff
```
(If `BookmarkOff` is not in the Tabler set bundled here, use `TablerIcons.Bookmark` for both and rely on the title to differentiate — verify by compiling; report which you used.)

- [ ] **Step 4: Fixture — add `listenLater` param**

In `data/model/client/AppMediaItemFixtures.kt`, add `listenLater: Boolean = false` to `fun album(...)` and pass it to the `Album(...)` constructor (`listenLater = listenLater`).

- [ ] **Step 5: Resolver — offer the action for albums**

In `ItemActionResolver.kt`, in BOTH `resolveLongClickActions` and `resolveDetailOverflowActions`, add (after the favorite block) an album-only entry:
```kotlin
    if (item is Album) {
        add(if (item.listenLater) ItemAction.RemoveFromLater else ItemAction.SaveForLater)
    }
```
(`Album` is already imported in this file.)

- [ ] **Step 6: `LibraryActions` + `ActionsViewModel`**

In `ItemActions.kt`, add to the `LibraryActions` interface:
```kotlin
    fun onListenLaterClick(item: AppMediaItem)
```
In `ActionsViewModel.kt`, implement it (next to `onFavoriteClick`):
```kotlin
    override fun onListenLaterClick(item: AppMediaItem) {
        viewModelScope.launch {
            if (item.listenLater) {
                apiClient.sendRequest(Request.Album.listenLaterRemove(item.itemId))
            } else {
                item.uri?.let { apiClient.sendRequest(Request.Album.listenLaterAdd(it)) }
            }
        }
    }
```

- [ ] **Step 7: Dispatch — map the new actions to the handler**

Find every place that maps `ItemAction.Favorite`/`Unfavorite` to `libraryActions...onFavoriteClick`:
Run: `grep -rn "onFavoriteClick(item)" composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/item`
At EACH such `when (...)` block (e.g. in `ItemHeader.kt`'s `ItemOverflow`, and the item-menu builders), add a branch alongside the favorite one:
```kotlin
                ItemAction.SaveForLater,
                ItemAction.RemoveFromLater,
                -> libraryActions?.onListenLaterClick(item)
```
(Match the existing nullability/`?.` style at each site. If a site dispatches via a non-null `libraryActions`, drop the `?`.)

- [ ] **Step 8: Strings**

Append inside `<resources>` in `strings.xml`:
```xml
  <string name="action_save_for_later">Save for later</string>
  <string name="action_remove_from_later">Remove from Listen Later</string>
```

- [ ] **Step 9: Run tests + suite**

Run: `./gradlew :composeApp:testAndroidHostTest --tests "*ListenLaterActionResolverTest*"` → PASS (3 tests).
Run: `./gradlew :composeApp:testAndroidHostTest` → full suite green.

- [ ] **Step 10: detekt + commit**

Run `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemAction.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemActionResolver.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/items/ItemActions.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/common/viewmodel/ActionsViewModel.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/data/model/client/AppMediaItemFixtures.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml \
        composeApp/src/commonTest/kotlin/io/music_assistant/client/ui/compose/common/items/ListenLaterActionResolverTest.kt
# plus the dispatch-site file(s) from Step 7:
git add -u
git commit -m "feat(listen-later): save/remove toggle in the album action menu

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Dedicated Listen Later screen + ViewModel + nav destination + DI

**Files:**
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ListenLaterViewModel.kt`
- Create: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ListenLaterScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/di/SharedModule.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/home/MainNavRoot.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: `ListenLaterViewModel`**

Model it on `ItemListViewModel` (loads a page, exposes `DataState`). Create:
```kotlin
package io.music_assistant.client.ui.compose.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.data.repository.MediaItemRepository
import io.music_assistant.client.ui.compose.common.DataState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ListenLaterViewModel(
    private val mediaItemRepository: MediaItemRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DataState<List<AppMediaItem>>>(DataState.Loading())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { DataState.Loading() }
            val result = mediaItemRepository.fetchMediaItems(
                Request.Album.listLibrary(
                    listenLater = true,
                    orderBy = "listen_later_added_at_desc",
                ),
            )
            result.getOrNull()
                ?.let { items -> _state.update { DataState.Data(items) } }
                ?: _state.update { DataState.Error() }
        }
    }
}
```
(Verify the exact `DataState.Loading()/Data()/Error()` constructors against `ui/compose/common/DataState.kt` and `fetchMediaItems` return type against `MediaItemRepository`; adjust to match — these mirror `ItemListViewModel`.)

- [ ] **Step 2: `ListenLaterScreen`**

Reuse `AdaptiveMediaGrid` (it renders an album list with the item menu). Create:
```kotlin
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.music_assistant.client.ui.compose.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.music_assistant.client.data.model.client.QueueOption
import io.music_assistant.client.data.model.client.items.AppMediaItem
import io.music_assistant.client.ui.compose.common.DataState
import io.music_assistant.client.ui.compose.common.items.LibraryActions
import io.music_assistant.client.ui.compose.common.items.PlaylistActions
import io.music_assistant.client.ui.compose.nav.Screen
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.common_back
import musicassistantclient.composeapp.generated.resources.listen_later_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun ListenLaterScreen(
    viewModel: ListenLaterViewModel,
    contentPadding: PaddingValues,
    playlistActions: PlaylistActions,
    libraryActions: LibraryActions,
    onBack: () -> Unit,
    onNavigateClick: (AppMediaItem) -> Unit,
    onPlayClick: (AppMediaItem, QueueOption, Boolean) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Screen(
        topBar = { scrollBehavior ->
            TopAppBar(
                title = { Text(stringResource(Res.string.listen_later_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(Res.string.common_back))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) {
        val items = (state as? DataState.Data)?.data.orEmpty()
        AdaptiveMediaGrid(
            items = items,
            onNavigateClick = onNavigateClick,
            onPlayClick = onPlayClick,
            playlistActions = playlistActions,
            libraryActions = libraryActions,
            contentPadding = contentPadding,
        )
    }
}
```
(Match `AdaptiveMediaGrid`'s exact required params — it takes `items`, `onNavigateClick`, `onPlayClick`, `playlistActions`, `libraryActions`, `contentPadding` and optional `progressActions`/`viewMode`/paging. Pass what's required; omit optional paging since this is a single fetch. Verify against `AdaptiveMediaGrid.kt` and fill any required arg.)

- [ ] **Step 3: DI registration**

In `SharedModule.kt`, near the `ItemListViewModel` factory, register:
```kotlin
        viewModelOf(::ListenLaterViewModel)
```
(or `factory { ListenLaterViewModel(get()) }` matching the file's style — `ListenLaterViewModel` takes only `MediaItemRepository`. Add the import.)

- [ ] **Step 4: Nav destination**

In `MainNavRoot.kt`:
- Add to the `private sealed interface MainNav` (next to `Library`):
```kotlin
    data object ListenLater : MainNav
```
- Add an `entry<MainNav.ListenLater>` to the `entryProvider { ... }` block (model it on the `ItemList` entry; reuse the shared `actionsViewModel` for `playlistActions`/`libraryActions`, and the same `onNavigateClick` push-to-`ItemDetails` logic):
```kotlin
        entry<MainNav.ListenLater> {
            val listenLaterViewModel = koinViewModel<ListenLaterViewModel>()
            ListenLaterScreen(
                viewModel = listenLaterViewModel,
                contentPadding = contentPadding,
                playlistActions = actionsViewModel,
                libraryActions = actionsViewModel,
                onBack = { multiBackStack.removeLastOrNull() },
                onNavigateClick = { item ->
                    multiBackStack.add(
                        MainNav.ItemDetails(
                            itemId = item.itemId,
                            mediaType = item.mediaType,
                            providerId = item.provider,
                        ),
                    )
                },
                onPlayClick = { item, queueOption, replace ->
                    actionsViewModel.onPlayClick(item, queueOption, replace)
                },
            )
        }
```
(Verify `actionsViewModel.onPlayClick` signature against `ActionsViewModel`/`ItemListScreen`'s `itemListViewModel::onPlayClick` usage; match it. Add imports for `ListenLaterScreen`/`ListenLaterViewModel`.)

- [ ] **Step 5: String**

Append inside `<resources>` in `strings.xml`:
```xml
  <string name="listen_later_title">Listen Later</string>
```

- [ ] **Step 6: Compile + suite + detekt + commit**

Run: `./gradlew :composeApp:testAndroidHostTest` → BUILD SUCCESSFUL, suite green.
Run: `./gradlew detektAll`.
```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ListenLaterViewModel.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/ListenLaterScreen.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/di/SharedModule.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/home/MainNavRoot.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(listen-later): dedicated Listen Later screen + nav destination

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Entry point — bookmark button in the Library top bar + final gates

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/LibraryScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/home/MainNavRoot.kt` (the `Library` entry)
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`

- [ ] **Step 1: `LibraryScreen` — add the button + callback**

Add an `onListenLaterClick: () -> Unit` parameter to `LibraryScreen(...)`, and a bookmark `IconButton` in the `TopAppBar` `actions` (before the existing `Tune` customize button):
```kotlin
fun LibraryScreen(
    libraryCategoriesViewModel: LibraryCategoriesViewModel,
    contentPadding: PaddingValues,
    onTypeClick: (MediaType) -> Unit,
    onListenLaterClick: () -> Unit,
) {
```
In the `TopAppBar`'s `actions = { ... }`:
```kotlin
                actions = {
                    IconButton(onClick = onListenLaterClick) {
                        Icon(
                            imageVector = Icons.Default.Bookmarks,
                            contentDescription = stringResource(Res.string.cd_listen_later),
                        )
                    }
                    IconButton(onClick = { showCustomizeDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = stringResource(Res.string.cd_customize_tabs),
                        )
                    }
                },
```
Add imports: `import androidx.compose.material.icons.filled.Bookmarks` and `import musicassistantclient.composeapp.generated.resources.cd_listen_later`. (`Icons.Default.Bookmarks` is in the bundled material-icons-extended.)

- [ ] **Step 2: Wire the nav push in `MainNavRoot`'s `Library` entry**

In the `entry<MainNav.Library>` block, pass the new callback:
```kotlin
            LibraryScreen(
                libraryCategoriesViewModel,
                contentPadding = contentPadding,
                onTypeClick = {
                    multiBackStack.add(MainNav.ItemList(it))
                },
                onListenLaterClick = {
                    multiBackStack.add(MainNav.ListenLater)
                },
            )
```

- [ ] **Step 3: String**

Append inside `<resources>` in `strings.xml`:
```xml
  <string name="cd_listen_later">Listen Later</string>
```

- [ ] **Step 4: Compile + suite + detekt**

Run: `./gradlew :composeApp:testAndroidHostTest` → BUILD SUCCESSFUL, suite green.
Run: `./gradlew detektAll` → BUILD SUCCESSFUL.

- [ ] **Step 5: Full app build**

Run: `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/library/LibraryScreen.kt \
        composeApp/src/commonMain/kotlin/io/music_assistant/client/ui/compose/home/MainNavRoot.kt \
        composeApp/src/commonMain/composeResources/values/strings.xml
git commit -m "feat(listen-later): Library top-bar bookmark entry to Listen Later

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>"
```

- [ ] **Step 7: Live verification (manual, enhanced server)**

Install; from an album's overflow menu (search / detail) tap **Save for later**; open Library → bookmark button → the album appears in Listen Later (newest-first). From there, **Remove from Listen Later** and re-open the screen to confirm it's gone. Confirm the action does not appear on non-album items.
```bash
./gradlew :androidApp:installDebug   # in-place update if a device is connected
```

---

## Self-Review

**Spec coverage:**
- Data (`listen_later` on ServerMediaItem → AppMediaItem/Album → factory) → Task 1. ✓
- API (`listenLaterAdd`/`Remove` + `listLibrary(listenLater)`) → Task 2. ✓
- Toggle action (ItemAction + resolver album-only + LibraryActions + ActionsViewModel + dispatch) → Task 3. ✓
- Dedicated screen (ViewModel + Screen + MainNav.ListenLater + DI) → Task 4. ✓
- Entry point (Library top-bar bookmark + nav) → Task 5. ✓
- i18n strings → Tasks 3, 4, 5. ✓
- Testing (mapping, request builders, resolver) + gates + live → Tasks 1–5. ✓
- Refetch-not-optimistic; `listen_later_added_at` omitted; badge/non-albums out of scope → respected. ✓

**Type consistency:** `Album.listenLater: Boolean` (Task 1) used in resolver/ActionsViewModel/fixture (Task 3) and the screen query (Task 4). `Request.Album.listenLaterAdd/listenLaterRemove/listLibrary(listenLater)` (Task 2) used in ActionsViewModel (Task 3) + ListenLaterViewModel (Task 4). `ItemAction.SaveForLater/RemoveFromLater` (Task 3). `LibraryActions.onListenLaterClick` (Task 3) implemented by `ActionsViewModel`, consumed by dispatch (Task 3) + the screen (Task 4). `MainNav.ListenLater` (Task 4) pushed from `LibraryScreen.onListenLaterClick` (Task 5).

**Verification points flagged inline (not placeholders):** Tabler `Bookmark`/`BookmarkOff` availability (Task 3 Step 3); exact `DataState`/`fetchMediaItems`/`AdaptiveMediaGrid` signatures (Task 4 Steps 1–2); `actionsViewModel.onPlayClick` signature (Task 4 Step 4); the dispatch-site grep (Task 3 Step 7). Each is a concrete "verify against file X and match" check, with the fallback named.
