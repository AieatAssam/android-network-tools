package net.aieat.netswissknife.app.ui.screens.traceroute

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.app.platform.NetworkStatus
import net.aieat.netswissknife.app.platform.NetworkStatusProvider
import net.aieat.netswissknife.app.platform.Transport
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("TracerouteViewModel form kept across leaving and returning")
class TracerouteSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var tracerouteUseCase: TracerouteUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<TracerouteViewModel>()

    private val wifiStatus = NetworkStatus(hasInternet = true, hasLocalNetwork = true, transport = Transport.WIFI)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        tracerouteUseCase = mockk()
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

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): TracerouteViewModel {
        val viewModel =
            TracerouteViewModel(
                tracerouteUseCase = tracerouteUseCase,
                recentHostsRepository = recentHostsRepository,
                networkStatusProvider =
                    object : NetworkStatusProvider {
                        override val status = MutableStateFlow(wifiStatus)
                    },
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: TracerouteViewModel) {
        ViewModelStore().apply {
            put("traceroute", viewModel)
            clear()
        }
    }

    @Test
    fun `the host and the route options are shown again after leaving and returning`() =
        runTest {
            val first = newViewModel()
            first.onHostChange("example.com")
            first.onMaxHopsChange(12)
            first.onTimeoutChange(4_000)
            first.onProbesPerHopChange(3)
            first.onProbeTypeChange(TracerouteProbeType.UDP)
            first.onPacketSizeChange(128)
            leave(first)

            val returned = newViewModel()

            assertEquals("example.com", returned.host.value)
            assertEquals(12, returned.maxHops.value)
            assertEquals(4_000, returned.timeoutMs.value)
            assertEquals(3, returned.probesPerHop.value)
            assertEquals(TracerouteProbeType.UDP, returned.probeType.value)
            assertEquals(128, returned.packetSize.value)
            assertTrue(returned.uiState.value is TracerouteUiState.Idle)
        }

    @Test
    fun `a trace still running when the tool is left is not restored as running`() =
        runTest {
            coEvery { tracerouteUseCase(any(), any()) } returns flow { awaitCancellation() }
            val first = newViewModel()
            first.onHostChange("example.com")
            first.startTrace()
            assertTrue(first.uiState.value is TracerouteUiState.Running, "state was ${first.uiState.value}")
            leave(first)

            val returned = newViewModel()

            // Leaving cancels the trace; the stopped state may be kept, but never as Running or Canceling.
            val restored = returned.uiState.value
            val kept = restored !is TracerouteUiState.Running && restored !is TracerouteUiState.Canceling
            assertTrue(kept, "state was $restored")
            assertEquals("example.com", returned.host.value)
        }

    @Test
    fun `a host from a route argument wins over the host kept when the tool was left`() =
        runTest {
            val first = newViewModel()
            first.onHostChange("example.com")
            leave(first)

            val returned = newViewModel(handle = SavedStateHandle(mapOf("traceHost" to "192.0.2.8")))

            assertEquals("192.0.2.8", returned.host.value)
            assertTrue(returned.uiState.value is TracerouteUiState.Idle)
        }
}
