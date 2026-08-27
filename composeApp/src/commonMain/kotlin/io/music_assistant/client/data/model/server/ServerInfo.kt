package io.music_assistant.client.data.model.server

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Highest server schema_version this client is built and tested against.
 *
 * Fork note: upstream ships 53 while the server this app is deployed against
 * (2.10.0) advertises 63, which would make the SERVER_AHEAD dialog fire on
 * every single connect. Each of 55..63 lands on a type this client does not
 * model -- external-source queue retention, task reports, source loudness and
 * crossfade modes, external-source audio quality, source shuffle/repeat, the
 * playlog event, pairing-code config entries, Sendspin one-click approve, and
 * the Spotify Connect/AirPlay player settings. `myJson` sets
 * `ignoreUnknownKeys = true`, so the added fields are read straight past
 * rather than failing, and the new PLAYLOG_UPDATED event is dropped by
 * `parseEventType` while `media_item_played` still fires as before.
 *
 * So 63 is accurate in the sense the constant means: the client is built and
 * verified against a 63 server. Drop this back to upstream's value on the
 * merge where upstream bumps it themselves.
 */
const val LOCAL_SCHEMA_VERSION = 63

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
