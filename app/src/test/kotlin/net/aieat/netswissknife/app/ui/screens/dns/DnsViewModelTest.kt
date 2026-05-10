package net.aieat.netswissknife.app.ui.screens.dns

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.util.SystemDnsAddressProvider
import net.aieat.netswissknife.core.domain.DnsLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.dns.DnsRecord
import net.aieat.netswissknife.core.network.dns.DnsRecordType
import net.aieat.netswissknife.core.network.dns.DnsResult
import net.aieat.netswissknife.core.network.dns.DnsServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DnsViewModel")
class DnsViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: DnsLookupUseCase
    private lateinit var systemDnsProvider: SystemDnsAddressProvider
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: DnsViewModel

    private val stubResult = DnsResult(
        domain = "example.com",
        recordType = DnsRecordType.A,
        server = DnsServer.Google,
        records = listOf(DnsRecord(DnsRecordType.A, "example.com", "93.184.216.34", 300L, "raw")),
        queryTimeMs = 42L,
        rawResponse = ";; raw"
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        systemDnsProvider = mockk { every { getAddresses() } returns emptyList() }
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = DnsViewModel(useCase, systemDnsProvider, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("custom server address editing")
    inner class CustomServerEditing {

        @Test
        fun `typing custom address when preset is active switches selectedServer to Custom`() {
            // starts on System (default preset)
            assertTrue(viewModel.selectedServer.value is DnsServer.System)

            viewModel.onCustomServerAddressChange("1.2.3.4")

            assertTrue(
                viewModel.selectedServer.value is DnsServer.Custom,
                "Expected Custom but got ${viewModel.selectedServer.value}"
            )
        }

        @Test
        fun `typed address is stored in selectedServer`() {
            viewModel.onCustomServerAddressChange("9.9.9.9")

            assertEquals("9.9.9.9", (viewModel.selectedServer.value as DnsServer.Custom).address)
        }

        @Test
        fun `customServerAddress state reflects typed value`() {
            viewModel.onCustomServerAddressChange("1.1.1.1")

            assertEquals("1.1.1.1", viewModel.customServerAddress.value)
        }

        @Test
        fun `selecting Custom from dropdown (empty address) switches to Custom`() {
            // simulates clicking "Custom…" in the dropdown which passes an empty string
            viewModel.onCustomServerAddressChange("")

            assertTrue(viewModel.selectedServer.value is DnsServer.Custom)
        }

        @Test
        fun `updating address while already in Custom mode updates the server address`() {
            viewModel.onCustomServerAddressChange("8.8.8.8")
            viewModel.onCustomServerAddressChange("1.0.0.1")

            assertEquals("1.0.0.1", (viewModel.selectedServer.value as DnsServer.Custom).address)
        }
    }

    @Nested
    @DisplayName("preset server selection")
    inner class PresetSelection {

        @Test
        fun `selecting a preset server stores it`() {
            viewModel.onServerChange(DnsServer.Google)

            assertEquals(DnsServer.Google, viewModel.selectedServer.value)
        }

        @Test
        fun `selecting preset after custom clears custom mode`() {
            viewModel.onCustomServerAddressChange("1.2.3.4")
            viewModel.onServerChange(DnsServer.Cloudflare)

            assertEquals(DnsServer.Cloudflare, viewModel.selectedServer.value)
        }
    }

    @Nested
    @DisplayName("lookup uses custom server address")
    inner class LookupWithCustomServer {

        @Test
        fun `performLookup passes custom address to use case`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)

            viewModel.onDomainChange("example.com")
            viewModel.onCustomServerAddressChange("192.168.1.1")
            viewModel.performLookup()

            coEvery { useCase(match { it.server == DnsServer.Custom("192.168.1.1") }) } returns NetworkResult.Success(stubResult)
            // Verify state transitioned to Success (not Error)
            assertTrue(viewModel.uiState.value is DnsUiState.Success)
        }
    }

    @Nested
    @DisplayName("state transitions")
    inner class StateTransitions {

        @Test
        fun `initial state is Idle`() {
            assertTrue(viewModel.uiState.value is DnsUiState.Idle)
        }

        @Test
        fun `success transitions to Success`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onDomainChange("example.com")
            viewModel.performLookup()
            assertTrue(viewModel.uiState.value is DnsUiState.Success)
        }

        @Test
        fun `error transitions to Error with message`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Error("nxdomain")
            viewModel.onDomainChange("nonexistent.invalid")
            viewModel.performLookup()
            val state = viewModel.uiState.value
            assertTrue(state is DnsUiState.Error)
            assertEquals("nxdomain", (state as DnsUiState.Error).message)
        }

        @Test
        fun `onClearResults resets to Idle`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onDomainChange("example.com")
            viewModel.performLookup()
            viewModel.onClearResults()
            assertTrue(viewModel.uiState.value is DnsUiState.Idle)
        }

        @Test
        fun `addRecent is called on performLookup`() = runTest {
            coEvery { useCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onDomainChange("example.com")
            viewModel.performLookup()
            coVerify { recentHostsRepository.addRecent(
                net.aieat.netswissknife.app.data.AppPreferenceKeys.RECENT_DNS_HOSTS,
                "example.com"
            ) }
        }
    }
}
