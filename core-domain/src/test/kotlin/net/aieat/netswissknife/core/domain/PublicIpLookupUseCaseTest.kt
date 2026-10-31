package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.netinfo.IpFamily
import net.aieat.netswissknife.core.network.netinfo.PublicIpRepository
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.HopGeoLocation
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PublicIpLookupUseCaseTest {
    private val publicIp = mockk<PublicIpRepository>()
    private val geoIp = mockk<GeoIpRepository>()
    private val reverseDns = mockk<TracerouteReverseDnsRepository>()
    private val location =
        HopGeoLocation("203.0.113.7", "Exampleland", "EX", "Example City", 1.0, 2.0, "Example ISP", "AS64500")

    private lateinit var useCase: PublicIpLookupUseCase

    @BeforeEach
    fun setUp() {
        useCase = PublicIpLookupUseCase(publicIp, geoIp, reverseDns)
    }

    private fun noRoute(): NetworkResult<String> = NetworkResult.error(ErrorCode.NETWORK_REQUEST_FAILED, "no route")

    @Test
    fun `found addresses are enriched with reverse DNS and geo`() =
        runTest {
            coEvery { publicIp.fetch(IpFamily.IPV4) } returns NetworkResult.Success("203.0.113.7")
            coEvery { publicIp.fetch(IpFamily.IPV6) } returns noRoute()
            coEvery { reverseDns.lookup("203.0.113.7", any()) } returns "host.example"
            coEvery { geoIp.lookup("203.0.113.7") } returns location

            val report = useCase(Unit)

            val ipv4 = (report.ipv4 as NetworkResult.Success).data
            assertEquals("203.0.113.7", ipv4.address)
            assertEquals("host.example", ipv4.hostname)
            assertEquals(location, ipv4.geo)
            assertInstanceOf(NetworkResult.Error::class.java, report.ipv6)
        }

    @Test
    fun `a failed IPv6 lookup does not stop IPv4 enrichment`() =
        runTest {
            coEvery { publicIp.fetch(IpFamily.IPV4) } returns NetworkResult.Success("203.0.113.7")
            coEvery { publicIp.fetch(IpFamily.IPV6) } returns NetworkResult.error(ErrorCode.NETWORK_TIMEOUT, "slow")
            coEvery { reverseDns.lookup(any(), any()) } returns "host.example"
            coEvery { geoIp.lookup(any()) } returns null

            val report = useCase(Unit)

            assertEquals("host.example", (report.ipv4 as NetworkResult.Success).data.hostname)
            assertEquals(ErrorCode.NETWORK_TIMEOUT, (report.ipv6 as NetworkResult.Error).info?.code)
        }

    @Test
    fun `enrichment failure keeps the address and drops only the optional detail`() =
        runTest {
            coEvery { publicIp.fetch(IpFamily.IPV4) } returns NetworkResult.Success("203.0.113.7")
            coEvery { publicIp.fetch(IpFamily.IPV6) } returns noRoute()
            coEvery { reverseDns.lookup(any(), any()) } throws IllegalStateException("resolver broke")
            coEvery { geoIp.lookup(any()) } returns location

            val ipv4 = (useCase(Unit).ipv4 as NetworkResult.Success).data

            assertEquals("203.0.113.7", ipv4.address)
            assertNull(ipv4.hostname)
            assertEquals(location, ipv4.geo)
        }

    @Test
    fun `each family is fetched exactly once per invocation`() =
        runTest {
            coEvery { publicIp.fetch(any()) } returns noRoute()

            useCase(Unit)

            coVerify(exactly = 1) { publicIp.fetch(IpFamily.IPV4) }
            coVerify(exactly = 1) { publicIp.fetch(IpFamily.IPV6) }
        }
}
