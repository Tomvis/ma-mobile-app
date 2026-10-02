package io.music_assistant.client.support.pages

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import io.music_assistant.client.support.get
import musicassistantclient.composeapp.generated.resources.Res
import musicassistantclient.composeapp.generated.resources.settings_connect
import musicassistantclient.composeapp.generated.resources.settings_connect_saved
import kotlin.math.sign

class ConnectPage(private val composeTestRule: ComposeTestRule, private val savedCredentials: Boolean = false) : Page {
    override fun assert() {
        composeTestRule.onNodeWithText("Connection Method").assertIsDisplayed()
    }

    fun connect(): AuthenticatePage {
        clickConnect()
        return AuthenticatePage(composeTestRule).assertOnPage()
    }

    fun <T : Page> connect(destination: T): T {
        clickConnect()
        return destination.assertOnPage()
    }

    fun connectWithError(message: String): ConnectPage {
        clickConnect()
        composeTestRule.onNodeWithText(message)
            .swipeIntoView()
            .assertIsDisplayed()
        return this.assertOnPage()
    }

    private fun clickConnect() {
        val label = if (savedCredentials) Res.string.settings_connect_saved else Res.string.settings_connect
        composeTestRule.onNodeWithText(label.get())
            .swipeIntoView()
            .assertIsDisplayed()
            .performClick()
    }

    // Not performScrollTo(): here each of its ScrollBy steps also collapses or re-expands the
    // enterAlways top bar (TopBarLayout), which moves the viewport it aimed at, so it can loop
    // forever (OOM). Real swipes, bounded, until the node is fully inside the scroll viewport.
    private fun SemanticsNodeInteraction.swipeIntoView(): SemanticsNodeInteraction {
        repeat(MAX_SWIPES) {
            val node = fetchSemanticsNode()
            val viewport = node.scrollParent().boundsInRoot
            val top = node.positionInRoot.y
            val bottom = top + node.size.height
            val dy = when {
                bottom > viewport.bottom -> bottom - viewport.bottom
                top < viewport.top -> top - viewport.top
                else -> return this
            }
            // The margin covers touch slop, which a swipe loses before it scrolls.
            val distance = (dy + sign(dy) * SWIPE_MARGIN).coerceIn(-viewport.height / 2, viewport.height / 2)
            val scrollParentId = node.scrollParent().id
            composeTestRule.onNode(SemanticsMatcher("scroll parent") { it.id == scrollParentId })
                .performTouchInput {
                    // Slow, so the fling after it stays small.
                    swipe(center, center - Offset(0f, distance), durationMillis = 1_000)
                }
        }
        throw AssertionError("Not scrolled into view after $MAX_SWIPES swipes")
    }

    private fun SemanticsNode.scrollParent(): SemanticsNode =
        generateSequence(parent) { it.parent }.first { SemanticsActions.ScrollBy in it.config }

    private companion object {
        const val MAX_SWIPES = 10
        const val SWIPE_MARGIN = 48f
    }
}
