package io.music_assistant.client.ui.compose.auth

import io.music_assistant.client.data.model.server.AuthProvider

/** How the login panel offers one server auth provider. */
sealed interface SignInOption {
    val provider: AuthProvider

    /** Text of the provider's tab. */
    val tabLabel: String

    /** Username and password, sent to the server. */
    data class Credentials(override val provider: AuthProvider) : SignInOption {
        override val tabLabel = "Music Assistant"
    }

    /** Home Assistant OAuth through the server's redirect flow. */
    data class HomeAssistant(override val provider: AuthProvider) : SignInOption {
        override val tabLabel = "Home Assistant"
    }

    /**
     * OpenID Connect (server 2.11 fork, HW-221): the same server redirect flow as Home
     * Assistant, labelled with the name the server admin gave the identity provider.
     */
    data class OpenIdConnect(override val provider: AuthProvider) : SignInOption {
        override val tabLabel = provider.name?.takeIf { it.isNotBlank() } ?: DEFAULT_OIDC_NAME
    }

    companion object {
        const val DEFAULT_OIDC_NAME = "OpenID Connect"

        /**
         * The providers the panel can draw, in server order. Unknown types are dropped here,
         * not while drawing, so tab indexes always match the provider shown below them.
         */
        fun from(providers: List<AuthProvider>): List<SignInOption> = providers.mapNotNull {
            when (it.type) {
                "builtin" -> Credentials(it)
                "homeassistant" -> HomeAssistant(it)
                "oidc" -> if (it.requiresRedirect) OpenIdConnect(it) else null
                else -> null
            }
        }
    }
}
