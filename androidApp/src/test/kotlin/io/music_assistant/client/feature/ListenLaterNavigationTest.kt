package io.music_assistant.client.feature

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.nav.NavigationDrawerSemantics
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.get
import io.music_assistant.client.support.isTab
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.clickNavBarItem
import io.music_assistant.client.support.rules.createTestRuleChain
import io.music_assistant.client.support.withinTag
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.cd_listen_later
import musicassistantclient.composeapp.generated.resources.listen_later_title
import musicassistantclient.composeapp.generated.resources.nav_menu
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

/**
 * Listen Later is a fork screen whose only entry point is one item in the nav drawer's Library
 * section. It used to be a top-bar button on `LibraryScreen.kt`, and upstream deleted that file
 * when it moved Library into the drawer (HW-246). A lost entry point still compiles, so this
 * test asserts the wiring: the drawer offers Listen Later and it opens the screen.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class ListenLaterNavigationTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `nav drawer opens Listen Later`() {
        val page = launchLoggedInApp(composeTestRule, serviceClient)
        page.clickNavBarItem(Res.string.nav_menu.get())

        composeTestRule
            .onNode(withinTag(NavigationDrawerSemantics.TAG).and(isTab(Res.string.cd_listen_later.get())))
            .performClick()

        composeTestRule.waitUntil("Listen Later screen is displayed") {
            composeTestRule
                .onAllNodes(hasText(Res.string.listen_later_title.get()).and(!withinTag(NavigationDrawerSemantics.TAG)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNode(hasText(Res.string.listen_later_title.get()).and(!withinTag(NavigationDrawerSemantics.TAG)))
            .assertIsDisplayed()
    }
}
