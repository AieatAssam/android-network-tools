package net.aieat.netswissknife.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsRepository
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class MdnsDiscoveryUseCaseTest {

    private val repository = mockk<MdnsRepository>()
    private val useCase = MdnsDiscoveryUseCase(repository)

    private val fakeService = DiscoveredService(
        serviceType = "_http._tcp",
        instanceName = "My Server._http._tcp.local.",
        displayName = "My Server",
        hostname = "myserver.local",
        port = 80
    )

    @Test
    fun `emits ServiceFound events from repository`() = runTest {
        every { repository.discover(any()) } returns flowOf(
            MdnsUpdate.ServiceFound(fakeService),
            MdnsUpdate.DiscoveryComplete(1)
        )

        val results = useCase(5_000L).toList()

        assertEquals(2, results.size)
        assertInstanceOf(MdnsUpdate.ServiceFound::class.java, results[0])
        assertEquals(fakeService, (results[0] as MdnsUpdate.ServiceFound).service)
    }

    @Test
    fun `emits DiscoveryComplete at end`() = runTest {
        every { repository.discover(any()) } returns flowOf(
            MdnsUpdate.DiscoveryComplete(0)
        )

        val results = useCase(5_000L).toList()

        assertEquals(1, results.size)
        assertInstanceOf(MdnsUpdate.DiscoveryComplete::class.java, results[0])
        assertEquals(0, (results[0] as MdnsUpdate.DiscoveryComplete).totalFound)
    }

    @Test
    fun `passes timeout to repository`() = runTest {
        every { repository.discover(3_000L) } returns flowOf(MdnsUpdate.DiscoveryComplete(0))

        useCase(3_000L).toList()

        verify { repository.discover(3_000L) }
    }
}
