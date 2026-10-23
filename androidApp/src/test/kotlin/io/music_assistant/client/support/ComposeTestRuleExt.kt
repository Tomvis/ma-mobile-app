package io.music_assistant.client.support

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.ComposeTestRule
import io.music_assistant.client.ui.compose.nav.NavigationBarSemantics
import io.music_assistant.client.ui.compose.nav.NavigationDrawerSemantics

fun ComposeTestRule.onNodeInContent(matcher: SemanticsMatcher): SemanticsNodeInteraction {
    return onNode(
        withinTag(NavigationBarSemantics.TAG).not()
            .and(withinTag(NavigationDrawerSemantics.TAG).not())
            .and(matcher),
    )
}
