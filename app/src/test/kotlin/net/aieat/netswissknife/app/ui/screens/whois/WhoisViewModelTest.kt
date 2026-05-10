package net.aieat.netswissknife.app.ui.screens.whois

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WhoisViewModel")
class WhoisViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var whoisLookupUseCase: WhoisLookupUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var viewModel: WhoisViewModel

    private val stubServer = WhoisServer("whois.iana.org", WhoisServerRole.IANA)
    private val stubHop = WhoisHop(
        server = stubServer,
        rawResponse = "Domain: example.com",
        queryTimeMs = 10L,
        referral = null
    )
    private val stubResult = WhoisResult(
        query = "example.com",
        queryType = WhoisQueryType.DOMAIN,
        hops = listOf(stubHop),
        domainName = "example.com",
        registrar = "IANA",
        registrarUrl = null,
        registeredOn = null,
        expiresOn = null,
        updatedOn = null,
        nameServers = listOf("a.iana-servers.net"),
        statusCodes = listOf("clientDeleteProhibited"),
        registrantOrg = null,
        registrantCountry = null,
        dnssec = null,
        netName = null,
        netRange = null,
        orgName = null,
        country = null,
        totalQueryTimeMs = 10L
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whoisLookupUseCase = mockk()
        every { whoisLookupUseCase.hopProgress } returns MutableSharedFlow()
        recentHostsRepository = mockk(relaxed = true) {
            every { getRecents(any()) } returns flowOf(emptyList())
        }
        viewModel = WhoisViewModel(whoisLookupUseCase, recentHostsRepository)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has empty query and no result`() {
        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertNull(state.result)
        assertFalse(state.isLoading)
    }

    @Nested
    @DisplayName("lookup state transitions")
    inner class LookupStateTransitions {

        @Test
        fun `success sets result`() = runTest {
            coEvery { whoisLookupUseCase(any()) } returns NetworkResult.Success(stubResult)
            viewModel.onQueryChange("example.com")
            viewModel.lookup()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertFalse(state.isLoading)
        }

        @Test
        fun `error sets error message`() = runTest {
            coEvery { whoisLookupUseCase(any()) } returns NetworkResult.Error("lookup failed")
            viewModel.onQueryChange("example.com")
            viewModel.lookup()
            val state = viewModel.uiState.value
            assertNull(state.result)
            assertEquals("lookup failed", state.error)
        }

        @Test
        fun `blank query does not trigger lookup`() = runTest {
            viewModel.onQueryChange("  ")
            viewModel.lookup()
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    @Test
    fun `onToggleRawResponse toggles showRawResponse`() {
        assertFalse(viewModel.uiState.value.showRawResponse)
        viewModel.onToggleRawResponse()
        assertTrue(viewModel.uiState.value.showRawResponse)
        viewModel.onToggleRawResponse()
        assertFalse(viewModel.uiState.value.showRawResponse)
    }

    @Test
    fun `addRecent is called on lookup`() = runTest {
        coEvery { whoisLookupUseCase(any()) } returns NetworkResult.Success(stubResult)
        viewModel.onQueryChange("example.com")
        viewModel.lookup()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_WHOIS_HOSTS, "example.com") }
    }
}
