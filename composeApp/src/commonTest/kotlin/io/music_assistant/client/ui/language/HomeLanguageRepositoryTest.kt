package io.music_assistant.client.ui.language

import com.russhwolf.settings.MapSettings
import io.music_assistant.client.api.Answer
import io.music_assistant.client.api.Request
import io.music_assistant.client.data.model.server.StubServiceClient
import io.music_assistant.client.utils.myJson
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** HW-191: the app's language follows the person's home language from MA's home_theme provider. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeLanguageRepositoryTest {
    private val shipped = listOf("de", "en", "he", "ru", "sk-SK", "zh-CN")

    private class FakeHost(private val tags: List<String>) : AppLanguageHost {
        var applied: String? = "unset"
        override val supported = true
        override fun shipped() = tags
        override fun applied() = applied
        override fun apply(tag: String?) { applied = tag }
        override fun device() = "de-DE"
        override fun nativeName(tag: String) = tag
    }

    private class FakeClient(private val state: String, private val invalidTimes: Int = 0) : StubServiceClient() {
        val requests = mutableListOf<Request>()
        override val isReadyForCommands: StateFlow<Boolean> = MutableStateFlow(true)
        override val foregroundEvents: Flow<Unit> = emptyFlow()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            requests += request
            val json = when {
                requests.size <= invalidTimes ->
                    """{"message_id":"m","error_code":12,"details":"Invalid command: home_language/get"}"""
                request.command == "home_language/choose" -> {
                    val language = request.args?.get("language")?.jsonPrimitive?.content
                    val choice = language?.let { """{"language":"$it","basis":"follow"}""" } ?: "null"
                    """{"message_id":"m","result":{"claim":{"language":"ru_RU","override":"follow"},"choice":$choice}}"""
                }
                else -> """{"message_id":"m","result":$state}"""
            }
            return Result.success(Answer(myJson.decodeFromString(JsonObject.serializer(), json)))
        }
    }

    /** advanceUntilIdle() ignores backgroundScope, where the repository runs. */
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    private val lera = """{"claim":{"language":"ru_RU","override":"follow"}}"""

    @Test
    fun appliesTheClaimMatchedToAShippedLanguage() = runTest {
        val host = FakeHost(shipped)
        val repo = HomeLanguageRepository(FakeClient(lera), MapSettings(), backgroundScope, host)
        settle()
        assertEquals(EffectiveLanguage("ru", LanguageSource.Home), repo.effective.value)
        assertEquals("ru", host.applied)
    }

    @Test
    fun noProviderLeavesTheAppLanguageAlone() = runTest {
        val host = FakeHost(shipped)
        val client = FakeClient(lera, invalidTimes = Int.MAX_VALUE)
        val repo = HomeLanguageRepository(client, MapSettings(), backgroundScope, host)
        settle()
        assertNull(repo.state.value)
        assertEquals("unset", host.applied)
    }

    @Test
    fun retriesWhileTheProviderLoads() = runTest {
        val host = FakeHost(shipped)
        HomeLanguageRepository(FakeClient(lera, invalidTimes = 2), MapSettings(), backgroundScope, host)
        settle()
        assertEquals("ru", host.applied)
    }

    @Test
    fun aPickIsSentInTheWebUisLocaleCode() = runTest {
        val host = FakeHost(shipped)
        val client = FakeClient(lera)
        val repo = HomeLanguageRepository(client, MapSettings(), backgroundScope, host)
        settle()
        repo.choose(LanguagePick.Language("ru"))
        settle()
        assertEquals("ru_RU", client.requests.last().args?.get("language")?.jsonPrimitive?.content)
        repo.choose(LanguagePick.Language("he"))
        settle()
        assertEquals(EffectiveLanguage("he", LanguageSource.App), repo.effective.value)
        assertEquals("he", host.applied)
    }

    @Test
    fun matchDeviceStaysOnThisDeviceUntilTheOverrideChanges() = runTest {
        val host = FakeHost(shipped)
        val settings = MapSettings()
        val client = FakeClient(lera)
        val repo = HomeLanguageRepository(client, settings, backgroundScope, host)
        settle()
        val sent = client.requests.size
        repo.choose(LanguagePick.Device)
        settle()
        assertEquals(sent, client.requests.size)
        assertNull(host.applied)
        assertEquals(LanguageSource.Device, repo.effective.value?.source)

        // A new per-app override in authentik wins over the device pick, as over any in-app choice.
        val overridden = HomeLanguageState(HomeLanguageClaim("he", override = "he"))
        assertEquals(EffectiveLanguage("he", LanguageSource.Home), resolveHomeLanguage(overridden, "follow", shipped))
    }

    @Test
    fun followClearsTheDevicePickAndTheChoice() = runTest {
        val host = FakeHost(shipped)
        val client = FakeClient(lera)
        val repo = HomeLanguageRepository(client, MapSettings(), backgroundScope, host)
        settle()
        repo.choose(LanguagePick.Device)
        repo.choose(LanguagePick.Follow)
        settle()
        assertEquals(EffectiveLanguage("ru", LanguageSource.Home), repo.effective.value)
        assertNull(client.requests.last().args?.get("language"))
    }

    @Test
    fun aStaleChoiceLosesToTheClaim() {
        val state = HomeLanguageState(
            claim = HomeLanguageClaim("ru_RU", override = "ru"),
            choice = HomeLanguageChoice("he", basis = "follow"),
        )
        assertEquals(EffectiveLanguage("ru", LanguageSource.Home), resolveHomeLanguage(state, null, shipped))
    }

    @Test
    fun matchesWebAndBcp47Codes() {
        assertEquals("ru", matchShipped("ru_RU", shipped))
        assertEquals("zh-CN", matchShipped("zh_CN", shipped))
        assertEquals("he", matchShipped("iw", shipped))
        assertEquals("en", matchShipped("pt_BR", shipped))
        assertEquals("ru_RU", webLocale("ru"))
        assertEquals("zh_CN", webLocale("zh-CN"))
        assertEquals("he", webLocale("he"))
    }
}
