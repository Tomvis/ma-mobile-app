package io.music_assistant.client.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.clickLibrary
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.ui.compose.library.ReceptionFilterSemantics
import io.music_assistant.client.ui.compose.support.inScrollable
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_reception_filter
import musicassistantclient.composeapp.generated.resources.filter_amg
import musicassistantclient.composeapp.generated.resources.filter_dr
import musicassistantclient.composeapp.generated.resources.filter_tps
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

/**
 * The album critical-reception filter is a fork feature, and its only call site is one block
 * in the library list's top bar. That is exactly the shape that an upstream merge deletes
 * silently: the composable, the ViewModel handler and the request args all keep compiling on
 * their own, so nothing fails — the button just stops being drawn. It happened once already
 * (upstream replaced `library/ItemListScreen.kt` with `LibraryListScreen.kt` and the call
 * site went with the old file), and the whole feature was invisible in the app for weeks.
 *
 * So this test asserts the wiring, not the sheet's internals: the button is reachable from
 * the album library, it opens a sheet, and the sheet carries all three reception sections.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class AlbumReceptionFilterTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `album library offers the reception filter`() {
        val album = ServerMediaItemFixtures.album()
        serviceClient.addItems(album)
        serviceClient.addToLibrary(album)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickAlbums()

        composeTestRule.onNodeWithContentDescription(Res.string.cd_reception_filter.get())
            .assertIsDisplayed()
            .performClick()

        // The sheet scrolls: DR, AMG and TPS do not all fit a medium phone at once.
        composeTestRule.inScrollable(ReceptionFilterSemantics.CONTENT_TAG) {
            onNode(hasText(Res.string.filter_dr.get())).assertIsDisplayed()
            onNode(hasText(Res.string.filter_amg.get())).assertIsDisplayed()
            onNode(hasText(Res.string.filter_tps.get())).assertIsDisplayed()
        }
    }

    @Test
    fun `non-album libraries do not offer the reception filter`() {
        val track = ServerMediaItemFixtures.track()
        serviceClient.addItems(track)
        serviceClient.addToLibrary(track)

        launchLoggedInApp(composeTestRule, serviceClient)
            .clickLibrary()
            .clickTracks()

        // Critical reception is an album-level tag, so the action is album-only. Offering it
        // on tracks would be a control that can only ever filter to nothing.
        composeTestRule.onNodeWithContentDescription(Res.string.cd_reception_filter.get())
            .assertDoesNotExist()
    }
}
