package net.aieat.netswissknife.app.ui.screens.topology

import androidx.lifecycle.ViewModelStore
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.TopologyDiscoveryUseCase
import net.aieat.netswissknife.core.network.topology.TopologyDiscoveryRepository
import net.aieat.netswissknife.core.network.topology.TopologyParams
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Topology discovery state kept across leaving and returning")
class TopologySessionRestoreTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var memory: ToolSessionMemory
    private lateinit var recents: RecentHostsRepository
    private lateinit var useCase: TopologyDiscoveryUseCase

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        memory = ToolSessionMemory()
        recents = mockk(relaxed = true)
        every { recents.getRecents(any()) } returns flowOf(emptyList())
        // The real use case: an invalid seed is rejected by its own validation, so the repository
        // is never reached and the terminal state is a genuine one.
        useCase = TopologyDiscoveryUseCase(mockk<TopologyDiscoveryRepository>())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newViewModel() = TopologyDiscoveryViewModel(useCase, recents, sessionMemory = memory)

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: TopologyDiscoveryViewModel) {
        ViewModelStore().apply {
            put("topology", viewModel)
            clear()
        }
    }

    @Test
    fun `the last finished state and its seed are shown again after leaving and returning`() {
        val first = newViewModel()
        first.startDiscovery(TopologyParams(targetIp = "bad host!!"))
        val before = first.uiState.value
        assertFalse(before is TopologyUiState.Discovering)

        leave(first)

        val returned = newViewModel()
        assertEquals(before, returned.uiState.value)
        assertEquals("bad host!!", returned.restoredSeed)
    }

    @Test
    fun `a fresh tool with nothing kept starts idle with an empty seed`() {
        val fresh = newViewModel()

        assertEquals(TopologyUiState.Idle, fresh.uiState.value)
        assertEquals(null, fresh.restoredSeed)
    }
}
