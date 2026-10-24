package io.music_assistant.client.ui.compose.auth

import io.music_assistant.client.data.model.server.AuthProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SignInOptionTest {
    @Test
    fun `providers keep server order and unknown types are dropped before drawing`() {
        val options = SignInOption.from(
            listOf(
                BUILTIN,
                AuthProvider(id = "future", type = "future", requiresRedirect = true),
                HA,
                OIDC,
            ),
        )

        assertEquals(listOf("builtin", "homeassistant", "oidc"), options.map { it.provider.id })
        assertIs<SignInOption.Credentials>(options[0])
        assertIs<SignInOption.HomeAssistant>(options[1])
        assertIs<SignInOption.OpenIdConnect>(options[2])
    }

    @Test
    fun `OpenID Connect is labelled with the name the server admin configured`() {
        assertEquals("Authentik", SignInOption.from(listOf(OIDC)).single().tabLabel)
        assertEquals(
            SignInOption.DEFAULT_OIDC_NAME,
            SignInOption.from(listOf(OIDC.copy(name = null))).single().tabLabel,
        )
        assertEquals(
            SignInOption.DEFAULT_OIDC_NAME,
            SignInOption.from(listOf(OIDC.copy(name = "  "))).single().tabLabel,
        )
    }

    @Test
    fun `an OpenID Connect provider without the redirect flow is not offered`() {
        assertEquals(emptyList<SignInOption>(), SignInOption.from(listOf(OIDC.copy(requiresRedirect = false))))
    }

    private companion object {
        val BUILTIN = AuthProvider(id = "builtin", type = "builtin", requiresRedirect = false)
        val HA = AuthProvider(id = "homeassistant", type = "homeassistant", requiresRedirect = true)
        val OIDC = AuthProvider(id = "oidc", type = "oidc", requiresRedirect = true, name = "Authentik")
    }
}
