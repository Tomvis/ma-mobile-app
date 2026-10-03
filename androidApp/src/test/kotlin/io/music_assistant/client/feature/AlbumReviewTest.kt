package io.music_assistant.client.feature

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import io.music_assistant.client.support.get
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.rules.createTestRuleChain
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.review_about_title
import musicassistantclient.composeapp.generated.resources.review_title
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

    @Test
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

        composeTestRule.onNodeWithText(Res.string.review_title.get()).assertExists()
        composeTestRule.onNodeWithText("Violin is a major feature.\n\nSo is the descent.").assertExists()
        composeTestRule.onNodeWithText("— Doug, The Progressive Subway").assertHasClickAction()
        composeTestRule.onNodeWithText("The label's blurb.").assertDoesNotExist()
    }

    @Test
    fun `album without a review shows its description`() {
        val album = ServerMediaItemFixtures.album(
            metadata = ServerMetadata(description = "The label's blurb."),
        )
        serviceClient.addItems(album)

        launchLoggedInApp(composeTestRule, serviceClient).clickOnMedia(album)

        composeTestRule.onNodeWithText(Res.string.review_about_title.get()).assertExists()
        composeTestRule.onNodeWithText("The label's blurb.").assertExists()
    }
}
