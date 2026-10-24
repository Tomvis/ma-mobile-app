package io.music_assistant.client.ui.language

import co.touchlab.kermit.Logger
import com.russhwolf.settings.Settings
import io.music_assistant.client.api.Request
import io.music_assistant.client.api.ServiceClient
import io.music_assistant.client.ui.theme.FOLLOW
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * The signed-in person's UI language (HW-191), fork-only. Same model as the web UI (HW-190): the
 * server's `home_theme` provider keeps, per MA user, the *claim* theme_sync pushes from authentik and
 * an in-app *choice*, which wins while its basis is the claim's current override. "Match device" is
 * this device's own choice, kept here with the same basis rule. Re-read on every sign-in and app
 * foreground; with no answer (no provider) the app's language is left alone.
 */
class HomeLanguageRepository(
    private val apiClient: ServiceClient,
    private val settings: Settings,
    private val scope: CoroutineScope,
    private val locales: AppLanguageHost = AppLocales,
) {
    private val _state = MutableStateFlow<HomeLanguageState?>(null)

    /** The server's answer; null until known, or when the server has no home_language commands. */
    val state: StateFlow<HomeLanguageState?> = _state.asStateFlow()

    private val _effective = MutableStateFlow<EffectiveLanguage?>(null)

    /** What the app shows; null = untouched (no answer yet, or no provider). */
    val effective: StateFlow<EffectiveLanguage?> = _effective.asStateFlow()

    private var retry: Job? = null

    init {
        if (locales.supported) {
            scope.launch {
                merge(apiClient.isReadyForCommands.filter { it }.map { }, apiClient.foregroundEvents)
                    .collect { refresh() }
            }
        }
    }

    /** Retries "Invalid command" right after an MA restart, as the home theme does (HW-76). */
    suspend fun refresh() {
        retry?.cancel()
        if (fetch()) return
        retry = scope.launch {
            for (wait in RETRY_DELAYS) {
                delay(wait)
                if (fetch()) return@launch
            }
            logger.i { "home_language/get still unknown to the server, keeping the app language" }
        }
    }

    /** False only when the server doesn't know home_language/get (yet). */
    private suspend fun fetch(): Boolean {
        if (!apiClient.isReadyForCommands.value) return true
        apiClient.sendRequest(Request(command = GET))
            .onSuccess { answer ->
                if (answer.json["error_code"]?.jsonPrimitive?.intOrNull == INVALID_COMMAND) return false
                update(answer.resultAs<HomeLanguageState>())
            }
            .onFailure { logger.i { "home_language/get failed, keeping the app language: ${it.message}" } }
        return true
    }

    /** An in-app pick: [LanguagePick.Follow], [LanguagePick.Device] or a shipped language tag. */
    fun choose(pick: LanguagePick) {
        val current = _state.value ?: return
        val basis = current.claim?.override ?: FOLLOW
        when (pick) {
            LanguagePick.Device -> {
                // This device only: the person's other devices and the web keep their language.
                settings.putString(DEVICE_KEY, basis)
                update(current)
            }
            LanguagePick.Follow, is LanguagePick.Language -> {
                settings.remove(DEVICE_KEY)
                val language = (pick as? LanguagePick.Language)?.let { webLocale(it.tag) }
                // Optimistic, so the tap shows at once; the server's answer settles it.
                update(current.copy(choice = language?.let { HomeLanguageChoice(it, basis) }))
                scope.launch {
                    val args = buildJsonObject { language?.let { put("language", JsonPrimitive(it)) } }
                    apiClient.sendRequest(Request(command = CHOOSE, args = args))
                        .map { it.resultAs<HomeLanguageState>() }
                        .onSuccess { update(it) }
                        .onFailure { logger.w(it) { "home_language/choose failed" } }
                }
            }
        }
    }

    private fun update(state: HomeLanguageState?) {
        _state.value = state ?: return
        val effective = resolveHomeLanguage(state, settings.getStringOrNull(DEVICE_KEY), locales.shipped())
        _effective.value = effective
        locales.apply(effective.tag)
    }

    private companion object {
        const val GET = "home_language/get"
        const val CHOOSE = "home_language/choose"
        const val DEVICE_KEY = "home_language.device_basis"
        const val INVALID_COMMAND = 12 // music_assistant_models.errors.InvalidCommand
        val RETRY_DELAYS = listOf(2.seconds, 5.seconds, 10.seconds)
        val logger = Logger.withTag("HomeLanguage")
    }
}

sealed interface LanguagePick {
    data object Follow : LanguagePick
    data object Device : LanguagePick
    data class Language(val tag: String) : LanguagePick
}

enum class LanguageSource { App, Device, Home }

/** [tag] null = the device's language. */
data class EffectiveLanguage(val tag: String?, val source: LanguageSource)

/** `language` is a web UI locale code: "en", "ru_RU", "he", "sr_Latn". */
@Serializable
data class HomeLanguageClaim(val language: String, val override: String = FOLLOW)

@Serializable
data class HomeLanguageChoice(val language: String, val basis: String = FOLLOW)

@Serializable
data class HomeLanguageState(val claim: HomeLanguageClaim? = null, val choice: HomeLanguageChoice? = null)

/**
 * The language to show: this device's "Match device" while its basis is current, else the in-app
 * choice while current, else the claim; with neither, the device's language.
 */
fun resolveHomeLanguage(state: HomeLanguageState, deviceBasis: String?, shipped: List<String>): EffectiveLanguage {
    val basis = state.claim?.override ?: FOLLOW
    val choice = state.choice?.takeIf { it.basis == basis }
    return when {
        deviceBasis == basis -> EffectiveLanguage(null, LanguageSource.Device)
        choice != null -> EffectiveLanguage(matchShipped(choice.language, shipped), LanguageSource.App)
        state.claim != null -> EffectiveLanguage(matchShipped(state.claim.language, shipped), LanguageSource.Home)
        else -> EffectiveLanguage(null, LanguageSource.Device)
    }
}

/**
 * The shipped tag for a web or BCP-47 code, most specific first ("ru_RU" → "ru", "zh_CN" → "zh-CN"),
 * like the web UI's resolveLocale; a language the app lacks falls back to English (HW-188).
 */
fun matchShipped(requested: String, shipped: List<String>): String {
    val key = { tag: String -> tag.lowercase().replace('_', '-').replace(Regex("^iw(?=-|$)"), "he") }
    val byKey = shipped.associateBy(key)
    val parts = key(requested).split('-')
    return parts.indices.reversed()
        .firstNotNullOfOrNull { byKey[parts.subList(0, it + 1).joinToString("-")] }
        ?: "en"
}

/** The web UI's locale file for a shipped tag, so a pick here also reads right there. */
fun webLocale(tag: String): String = WEB_LOCALES[tag] ?: tag.replace('-', '_')

// Where MA's web locale file names differ from the app's values-* tags.
private val WEB_LOCALES = mapOf("ru" to "ru_RU", "iw" to "he")
