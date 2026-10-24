package io.music_assistant.client.feature

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.data.model.server.AuthProvider
import io.music_assistant.client.support.FakeServiceClient
import io.music_assistant.client.support.Qualifiers
import io.music_assistant.client.support.launchApp
import io.music_assistant.client.support.launchLoggedInApp
import io.music_assistant.client.support.pages.clickSettings
import io.music_assistant.client.support.rules.createTestRuleChain
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.java.KoinJavaComponent.inject
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = Qualifiers.MEDIUM_PHONE)
class LoginTest {
    @get:Rule
    val testRuleChain = createTestRuleChain()

    @get:Rule
    val composeTestRule = createComposeRule()

    val serviceClient: FakeServiceClient by inject(ServiceClient::class.java)

    @Test
    fun `entering incorrect username or password shows server error`() {
        launchApp(composeTestRule)
            .connect()
            .loginWithError("wrong", serviceClient.password, "Invalid username or password")
            .loginWithError(serviceClient.username, "wrong", "Invalid username or password")
    }

    @Test
    fun `OpenID Connect sign-in starts the server redirect flow with the app return URL`() {
        // An unknown provider type before it must not shift the tabs off their providers.
        serviceClient.addAuthProvider(AuthProvider(id = "future", type = "future", requiresRedirect = true))
        serviceClient.addAuthProvider(
            AuthProvider(id = "oidc", type = "oidc", requiresRedirect = true, name = "Authentik"),
        )
        launchApp(composeTestRule)
            .connect()
            .signInWith("Authentik")

        assertEquals(
            listOf<Pair<String, String?>>("oidc" to "musicassistant://auth/callback"),
            serviceClient.authorizationUrlRequests,
        )
    }

    @Test
    fun `password login stays the default tab when OpenID Connect is offered`() {
        serviceClient.addAuthProvider(
            AuthProvider(id = "oidc", type = "oidc", requiresRedirect = true, name = "Authentik"),
        )
        launchApp(composeTestRule)
            .connect()
            .login(serviceClient.username, serviceClient.password)
    }

    @Test
    fun `can logout`() {
        launchLoggedInApp(composeTestRule, serviceClient)
            .clickSettings()
            .logout()
    }
}
