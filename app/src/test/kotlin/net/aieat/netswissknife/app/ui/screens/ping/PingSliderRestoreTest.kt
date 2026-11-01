package net.aieat.netswissknife.app.ui.screens.ping

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.ContinuousPingUseCase
import net.aieat.netswissknife.core.domain.PingUseCase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("PingViewModel advanced sliders kept across leaving and returning")
class PingSliderRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var pingUseCase: PingUseCase
    private lateinit var continuousPingUseCase: ContinuousPingUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<PingViewModel>()

    /** DataStore defaults that differ from the built-in ones, so an overwrite is visible. */
    private val defaults =
        mutablePreferencesOf(
            AppPreferenceKeys.DEFAULT_PING_COUNT to 40,
            AppPreferenceKeys.DEFAULT_TIMEOUT_MS to 5_000,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        pingUseCase = mockk()
        continuousPingUseCase = mockk()
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        memory = ToolSessionMemory()
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
            Dispatchers.resetMain()
        }

    private fun newViewModel(
        handle: SavedStateHandle = SavedStateHandle(),
        prefs: Preferences = emptyPreferences(),
    ): PingViewModel {
        val dataStore: DataStore<Preferences> =
            mockk {
                every { data } returns flowOf(prefs)
            }
        val viewModel =
            PingViewModel(
                pingUseCase = pingUseCase,
                continuousPingUseCase = continuousPingUseCase,
                dataStore = dataStore,
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: PingViewModel) {
        ViewModelStore().apply {
            put("ping", viewModel)
            clear()
        }
    }

    @Test
    fun `the sliders are shown again with their values after leaving and returning`() {
        val first = newViewModel(prefs = defaults)
        first.onCountChange(25)
        first.onTimeoutChange(4_000)
        first.onPayloadSizeChange(120)
        first.onTtlChange(32)
        first.onIntervalChange(500)
        leave(first)

        val returned = newViewModel(prefs = defaults)

        assertEquals(25, returned.count.value)
        assertEquals(4_000, returned.timeoutMs.value)
        assertEquals(120, returned.payloadBytes.value)
        assertEquals(32, returned.ttl.value)
        assertEquals(500, returned.intervalMs.value)
    }

    @Test
    fun `a slider value restored from saved state beats the DataStore default after process death`() {
        val handle = SavedStateHandle(mapOf("pingCount" to 7))

        val restored = newViewModel(handle = handle, prefs = defaults)

        assertEquals(7, restored.count.value)
    }

    @Test
    fun `a fresh tool still uses the DataStore defaults for count and timeout`() {
        val fresh = newViewModel(prefs = defaults)

        assertEquals(40, fresh.count.value)
        assertEquals(5_000, fresh.timeoutMs.value)
    }

    @Test
    fun `resetting the advanced options is kept after returning`() {
        val first = newViewModel(prefs = defaults)
        first.onPayloadSizeChange(300)
        first.onResetAdvancedOptions()
        leave(first)

        val returned = newViewModel(prefs = defaults)

        assertEquals(56, returned.payloadBytes.value)
        assertEquals(64, returned.ttl.value)
        assertEquals(1_000, returned.intervalMs.value)
    }
}
