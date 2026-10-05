package io.music_assistant.client.ui.theme

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
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** HW-76: home_theme/get answered "Invalid command" while MA's provider is still loading. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeThemeRepositoryTest {
    private class FakeClient(private val invalidTimes: Int) : StubServiceClient() {
        var calls = 0
        override val isReadyForCommands: StateFlow<Boolean> = MutableStateFlow(true)
        override val foregroundEvents: Flow<Unit> = emptyFlow()

        override suspend fun sendRequest(request: Request): Result<Answer> {
            calls++
            val json = if (calls <= invalidTimes) {
                """{"message_id":"m","error_code":12,"details":"Invalid command: home_theme/get"}"""
            } else {
                """{"message_id":"m","result":{"claim":{"theme":"dusk","mode":"dark"}}}"""
            }
            return Result.success(Answer(myJson.decodeFromString(JsonObject.serializer(), json)))
        }
    }

    private val dusk = HomeEffective("dusk", HomeMode.Dark, HomeSource.Home)

    /** advanceUntilIdle() ignores backgroundScope, where the repository runs. */
    private fun TestScope.settle() {
        advanceTimeBy(60_000)
        runCurrent()
    }

    @Test
    fun retriesUntilTheProviderIsLoaded() = runTest {
        val client = FakeClient(invalidTimes = 2)
        val repo = HomeThemeRepository(client, MapSettings(), backgroundScope)
        settle()
        assertEquals(3, client.calls)
        assertEquals(dusk, repo.effective.value)
    }

    @Test
    fun givesUpAfterThreeRetriesKeepingTheLastTheme() = runTest {
        val client = FakeClient(invalidTimes = Int.MAX_VALUE)
        val repo = HomeThemeRepository(client, MapSettings(), backgroundScope)
        settle()
        assertEquals(4, client.calls)
        assertNull(repo.effective.value)
    }

    @Test
    fun theNextRefreshCancelsThePendingRetry() = runTest {
        val client = FakeClient(invalidTimes = 1)
        val repo = HomeThemeRepository(client, MapSettings(), backgroundScope)
        advanceTimeBy(1_000)
        backgroundScope.launch { repo.refresh() }
        settle()
        assertEquals(2, client.calls)
        assertEquals(dusk, repo.effective.value)
    }
}
