package io.music_assistant.client.feature

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.ServerCriticalReception
import io.music_assistant.client.data.model.server.ServerMetadata
import io.music_assistant.client.data.model.server.ServerReviewLink
import io.music_assistant.client.data.model.server.ServerReviewSourceEntry
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.ServerMediaItemFixtures
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config

/**
 * The album review is a fork feature drawn from one block in the album hero, so it guards its
 * call site on the real screen: an upstream refactor of the details screen must not drop it.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class AlbumReviewTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    private val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    // Upstream's header text block (#1277) plus the critic panel push the tabs below a medium phone's fold.
    @Test
    @Config(qualifiers = "w412dp-h1400dp")
    fun `album page shows the critic review, signed with a link to the post`() {
        val album = ServerMediaItemFixtures.album(
            metadata = ServerMetadata(
                description = "The label's blurb.",
                criticalReception = ServerCriticalReception(
                    sources = listOf(
                        ServerReviewSourceEntry(
                            source = "TPS",
                            rating = 8.0,
                            links = listOf(ServerReviewLink("Review", "https://tps.example/review/")),
                            authors = listOf("Doug"),
                            review = "Violin is a major feature.\n\nSo is the descent.",
                        ),
                    ),
                ),
            ),
        )
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)

        composeTestRule.onNodeWithText("Violin is a major feature.\n\nSo is the descent.").assertExists()
        composeTestRule.onNodeWithText("— Doug, The Progressive Subway").assertHasClickAction()
        // The description is upstream's header text block (#1277), not a second review.
        composeTestRule.onAllNodesWithText("The label's blurb.").assertCountEquals(1)
    }

    @Test
    fun `album without a critic review shows its description once, in the header`() {
        val album = ServerMediaItemFixtures.album(
            metadata = ServerMetadata(description = "The label's blurb."),
        )
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)

        // Once: upstream's header block shows it, and the critic panel does not repeat it.
        composeTestRule.onAllNodesWithText("The label's blurb.").assertCountEquals(1)
    }
}
