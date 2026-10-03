package io.music_assistant.client.ui.theme

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import home.theme.HomeThemes
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * The signed-in person's home theme (HW-65), fork-only. Same model as the web UI (HW-64):
 * the server's `home_theme` provider keeps, per MA user, the *claim* that home-monitoring's
 * theme_sync pushes from authentik and an in-app *choice*, which wins while its basis is the
 * claim's current override. Re-read on every sign-in and app foreground; the provider's events
 * are not modelled here, so a change made elsewhere shows on the next foreground.
 */
class HomeThemeRepository(
    private val apiClient: ServiceClient,
    private val settings: Settings,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<HomeThemeState?>(null)

    /** The server's answer; null until known, or when the server has no home_theme provider. */
    val state: StateFlow<HomeThemeState?> = _state.asStateFlow()

    private val _effective = MutableStateFlow(readLast())

    /** What to paint; before the server answered, this device's last theme; null = upstream's own setting. */
    val effective: StateFlow<HomeEffective?> = _effective.asStateFlow()

    init {
        _effective.value?.let { homeThemeId.value = it.theme }
        scope.launch {
            merge(apiClient.isReadyForCommands.filter { it }.map { }, apiClient.foregroundEvents)
                .collect { refresh() }
        }
    }

    suspend fun refresh() {
        if (!apiClient.isReadyForCommands.value) return
        apiClient.sendRequest(Request(command = GET))
            .map { it.resultAs<HomeThemeState>() }
            .onSuccess { update(it) }
            .onFailure { logger.i { "home_theme/get failed, keeping the last theme: ${it.message}" } }
    }

    /** An in-app choice; a null [theme] is "Follow home theme". */
    fun choose(theme: String?, mode: HomeMode) {
        // Optimistic, so the tap shows at once (and offline); the server's answer settles it.
        val current = _state.value ?: HomeThemeState()
        update(
            current.copy(
                choice = theme?.let {
                    HomeThemeChoice(it, mode.wire, current.claim?.override ?: FOLLOW)
                },
            ),
        )
        scope.launch {
            val args = buildJsonObject {
                theme?.let {
                    put("theme", JsonPrimitive(it))
                    put("mode", JsonPrimitive(mode.wire))
                }
            }
            apiClient.sendRequest(Request(command = CHOOSE, args = args))
                .map { it.resultAs<HomeThemeState>() }
                .onSuccess { update(it) }
                .onFailure { logger.w(it) { "home_theme/choose failed" } }
        }
    }

    private fun update(state: HomeThemeState?) {
        _state.value = state
        val effective = state?.let(::resolveHomeTheme) ?: return
        _effective.value = effective
        homeThemeId.value = effective.theme
        settings.putString(LAST_KEY, "${effective.theme}/${effective.mode.wire}")
    }

    private fun readLast(): HomeEffective? =
        settings.getStringOrNull(LAST_KEY)?.split("/")?.takeIf { it.size == 2 }?.let { (theme, mode) ->
            HomeMode.fromWire(mode)?.let { HomeEffective(HomeThemes.byId(theme).id, it, HomeSource.Default) }
        }

    private companion object {
        const val GET = "home_theme/get"
        const val CHOOSE = "home_theme/choose"
        const val LAST_KEY = "home_theme.last"
        val logger = Logger.withTag("HomeTheme")
    }
}

enum class HomeMode(val wire: String) {
    Automatic("automatic"), Light("light"), Dark("dark");

    companion object {
        fun fromWire(value: String?): HomeMode? = entries.firstOrNull { it.wire == value }
    }
}

enum class HomeSource { App, Home, Default }

data class HomeEffective(val theme: String, val mode: HomeMode, val source: HomeSource)

@Serializable
data class HomeThemeClaim(val theme: String, val mode: String, val override: String = FOLLOW)

@Serializable
data class HomeThemeChoice(val theme: String, val mode: String, val basis: String = FOLLOW)

@Serializable
data class HomeThemeState(val claim: HomeThemeClaim? = null, val choice: HomeThemeChoice? = null)

const val FOLLOW = "follow"

/** The theme to show: the in-app choice while still current, else the claim, else the default. */
fun resolveHomeTheme(state: HomeThemeState): HomeEffective {
    val known = { id: String -> HomeThemes.byId(id).id }
    val mode = { wire: String -> HomeMode.fromWire(wire) ?: HomeMode.Automatic }
    val claim = state.claim
    val choice = state.choice?.takeIf { it.basis == (claim?.override ?: FOLLOW) }
    return choice?.let { HomeEffective(known(it.theme), mode(it.mode), HomeSource.App) }
        ?: claim?.let { HomeEffective(known(it.theme), mode(it.mode), HomeSource.Home) }
        ?: HomeEffective(HomeThemes.DEFAULT, HomeMode.Automatic, HomeSource.Default)
}

/** Upstream's light/dark/system setting for a home mode. */
fun HomeMode.toThemeSetting(): ThemeSetting = when (this) {
    HomeMode.Automatic -> ThemeSetting.FollowSystem
    HomeMode.Light -> ThemeSetting.Light
    HomeMode.Dark -> ThemeSetting.Dark
}

fun ThemeSetting.toHomeMode(): HomeMode = when (this) {
    ThemeSetting.FollowSystem -> HomeMode.Automatic
    ThemeSetting.Light -> HomeMode.Light
    ThemeSetting.Dark -> HomeMode.Dark
}
