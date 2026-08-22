package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Highest server schema_version this client is built and tested against.
 *
 * Fork note: upstream ships 53 while the server this app is deployed against
 * (2.10.0rc4) advertises 54, which made the SERVER_AHEAD dialog fire on every
 * single connect. Schema 54 is one feature -- Spotify Connect session queue
 * delegation (server #5880) -- which adds `queue_owner` on the queue and
 * `queue_capabilities` on AudioSource. This client models neither, and
 * `myJson` sets `ignoreUnknownKeys = true`, so both are read straight past
 * rather than failing; nothing in 54 is reachable without a Spotify Connect
 * provider, which this deployment does not run.
 *
 * So 54 is accurate here in the sense the constant means: the client is built
 * and verified against a 54 server. Drop this back to upstream's value on the
 * merge where upstream bumps it themselves.
 */
const val LOCAL_SCHEMA_VERSION = 54

@Serializable
data class ServerInfo(
    @SerialName("server_id") var serverId: String,
    @SerialName("server_version") var serverVersion: String? = null,
    @SerialName("schema_version") var schemaVersion: Int? = null,
    @SerialName("min_supported_schema_version") var minSupportedSchemaVersion: Int? = null,
    @SerialName("base_url") var baseUrl: String? = null,
    // @SerialName("homeassistant_addon") var homeassistantAddon: Boolean? = null,
    // @SerialName("onboard_done") var onboardDone: Boolean? = null
)
