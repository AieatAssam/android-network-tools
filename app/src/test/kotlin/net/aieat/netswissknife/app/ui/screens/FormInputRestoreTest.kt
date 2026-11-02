package net.aieat.netswissknife.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.ui.screens.dns.DnsViewModel
import net.aieat.netswissknife.app.ui.screens.subnet.SubnetCalculatorViewModel
import net.aieat.netswissknife.app.ui.screens.subnet.SubnetSplitMode
import net.aieat.netswissknife.app.ui.screens.traceroute.TracerouteViewModel
import net.aieat.netswissknife.app.ui.screens.whois.WhoisViewModel
import net.aieat.netswissknife.app.ui.screens.wol.WakeOnLanViewModel
import net.aieat.netswissknife.app.util.SystemDnsAddressProvider
import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.domain.TracerouteUseCase
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsServer
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.whois.WhoisProtocol
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Form inputs are written to SavedStateHandle as they change and read back when the ViewModel is
 * recreated (process death or Back and return). Results are not restored here by design.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("Tool form inputs kept in SavedStateHandle")
class FormInputRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var recentHostsRepository: RecentHostsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `DNS writes its name, record type and resolver and restores them`() {
        val handle = SavedStateHandle()
        val first =
            DnsViewModel(
                mockk<DnsLookupUseCase>(),
                mockk<SystemDnsAddressProvider>(relaxed = true),
                recentHostsRepository,
                savedStateHandle = handle,
            )
        first.onDomainChange("example.com")
        first.onRecordTypeChange(DnsRecordType.MX)
        first.onCustomServerAddressChange("10.0.0.53")

        assertEquals("example.com", handle.get<String>("dnsDomain"))

        val returned =
            DnsViewModel(
                mockk<DnsLookupUseCase>(),
                mockk<SystemDnsAddressProvider>(relaxed = true),
                recentHostsRepository,
                savedStateHandle =
                    SavedStateHandle(
                        mapOf(
                            "dnsDomain" to "example.com",
                            "dnsRecordType" to DnsRecordType.MX.name,
                            "dnsServer" to "custom",
                            "dnsCustomServer" to "10.0.0.53",
                        ),
                    ),
            )
        assertEquals("example.com", returned.domain.value)
        assertEquals(DnsRecordType.MX, returned.recordType.value)
        assertEquals(DnsServer.Custom("10.0.0.53"), returned.selectedServer.value)
        assertEquals("10.0.0.53", returned.customServerAddress.value)
    }

    @Test
    fun `Traceroute writes its settings and restores them with clamped values`() {
        val handle = SavedStateHandle()
        val first = TracerouteViewModel(mockk<TracerouteUseCase>(), recentHostsRepository, savedStateHandle = handle)
        first.onHostChange("example.org")
        first.onMaxHopsChange(99)
        first.onProbeTypeChange(TracerouteProbeType.UDP)

        assertEquals("example.org", handle.get<String>("traceHost"))
        assertEquals(64, handle.get<Int>("traceMaxHops"))

        val returned =
            TracerouteViewModel(
                mockk<TracerouteUseCase>(),
                recentHostsRepository,
                savedStateHandle =
                    SavedStateHandle(
                        mapOf(
                            "traceHost" to "example.org",
                            "traceMaxHops" to 12,
                            "traceProbeType" to TracerouteProbeType.UDP.name,
                        ),
                    ),
            )
        assertEquals("example.org", returned.host.value)
        assertEquals(12, returned.maxHops.value)
        assertEquals(TracerouteProbeType.UDP, returned.probeType.value)
    }

    @Test
    fun `WHOIS restores its query and protocol`() {
        val returned =
            WhoisViewModel(
                mockk<WhoisLookupUseCase>(),
                recentHostsRepository,
                savedStateHandle =
                    SavedStateHandle(mapOf("whoisQuery" to "example.com", "whoisProtocol" to WhoisProtocol.RDAP.name)),
            )
        assertEquals("example.com", returned.uiState.value.query)
        assertEquals(WhoisProtocol.RDAP, returned.uiState.value.protocol)
        assertNull(returned.uiState.value.result)
    }

    @Test
    fun `Wake-on-LAN restores its broadcast address and port`() {
        val returned =
            WakeOnLanViewModel(
                mockk<WakeOnLanUseCase>(),
                savedStateHandle = SavedStateHandle(mapOf("wolBroadcastAddress" to "192.168.1.255", "wolPort" to "7")),
            )
        assertEquals("192.168.1.255", returned.broadcastAddress.value)
        assertEquals("7", returned.port.value)
    }

    @Test
    fun `Subnet restores its inputs but not a derived result`() {
        val handle = SavedStateHandle()
        val first = SubnetCalculatorViewModel(subnetUseCase(), savedStateHandle = handle)
        first.onSplitModeChange(SubnetSplitMode.BY_COUNT)
        first.onSplitInputChange("4")
        first.onInputChange("192.168.1.0/24")

        val returned =
            SubnetCalculatorViewModel(
                subnetUseCase(),
                savedStateHandle = SavedStateHandle(handle.keys().associateWith { handle.get<Any>(it) }),
            )
        assertEquals("192.168.1.0/24", returned.uiState.value.input)
        assertEquals(SubnetSplitMode.BY_COUNT, returned.uiState.value.splitMode)
        assertEquals("4", returned.uiState.value.splitInput)
        assertNull(returned.uiState.value.result)
    }

    /** Subnet inputs are checked as they are typed, so the use case answers that check with no error. */
    private fun subnetUseCase(): SubnetCalculatorUseCase {
        val useCase = mockk<SubnetCalculatorUseCase>()
        every { useCase.cidrInputError(any()) } returns null
        return useCase
    }
}
