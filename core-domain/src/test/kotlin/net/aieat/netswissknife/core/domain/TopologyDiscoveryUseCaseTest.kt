package net.aieat.netswissknife.core.domain

import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.topology.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TopologyDiscoveryUseCaseTest {

    private lateinit var repository: TopologyDiscoveryRepository
    private lateinit var useCase: TopologyDiscoveryUseCase

    private val validParams = TopologyParams(
        targetIp = "192.168.1.1",
        snmpVersion = SnmpVersion.V2C,
        communityString = "public",
        maxHops = 3,
        timeoutMs = 3000,
        retries = 1
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = TopologyDiscoveryUseCase(repository)
    }

    @Test
    fun `valid params delegates to repository and returns its Flow`() = runTest {
        val mockGraph = TopologyGraph(
            nodes = emptyList(),
            links = emptyList(),
            seedIp = "192.168.1.1",
            queriedAt = System.currentTimeMillis()
        )
        every { repository.discover(validParams) } returns flowOf(
            TopologyDiscoveryEvent.Complete(mockGraph)
        )

        val events = useCase.invoke(validParams).toList()

        verify(exactly = 1) { repository.discover(validParams) }
        assertEquals(1, events.size)
        assertTrue(events[0] is TopologyDiscoveryEvent.Complete)
    }

    @Test
    fun `invalid params emits Error without calling repository`() = runTest {
        val invalidParams = validParams.copy(targetIp = "")

        val events = useCase.invoke(invalidParams).toList()

        verify(exactly = 0) { repository.discover(any()) }
        assertEquals(1, events.size)
        assertTrue(events[0] is TopologyDiscoveryEvent.Error)
    }
}
