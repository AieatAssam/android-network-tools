package net.aieat.netswissknife.app.ui.screens.whois

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisProtocol
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WhoisViewModel query and result kept across leaving and returning")
class WhoisSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var whoisLookupUseCase: WhoisLookupUseCase
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<WhoisViewModel>()

    private val stubHop =
        WhoisHop(
            server = WhoisServer("whois.iana.org", WhoisServerRole.IANA),
            rawResponse = "Domain: example.com",
            queryTimeMs = 10L,
            referral = null,
        )
    private val stubResult =
        WhoisResult(
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
            totalQueryTimeMs = 10L,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whoisLookupUseCase = mockk()
        every { whoisLookupUseCase.hopProgress } returns MutableSharedFlow()
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

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): WhoisViewModel {
        val viewModel =
            WhoisViewModel(
                whoisLookupUseCase = whoisLookupUseCase,
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: WhoisViewModel) {
        ViewModelStore().apply {
            put("whois", viewModel)
            clear()
        }
    }

    @Test
    fun `the query, protocol and finished result are shown again after leaving and returning`() =
        runTest {
            coEvery { whoisLookupUseCase(any(), any()) } returns NetworkResult.Success(stubResult)
            val first = newViewModel()
            first.onQueryChange("example.com")
            first.onProtocolChange(WhoisProtocol.RDAP)
            first.lookup()
            assertEquals(stubResult, first.uiState.value.result)
            leave(first)

            val returned = newViewModel()

            assertEquals("example.com", returned.uiState.value.query)
            assertEquals(WhoisProtocol.RDAP, returned.uiState.value.protocol)
            assertEquals(stubResult, returned.uiState.value.result)
        }

    @Test
    fun `a lookup still running when the tool is left is not restored as running`() =
        runTest {
            coEvery { whoisLookupUseCase(any(), any()) } coAnswers { awaitCancellation() }
            val first = newViewModel()
            first.onQueryChange("example.com")
            first.lookup()
            assertTrue(first.uiState.value.isLoading)
            leave(first)

            val returned = newViewModel()

            assertTrue(!returned.uiState.value.isLoading)
            assertNull(returned.uiState.value.result)
            assertEquals("example.com", returned.uiState.value.query)
        }

    @Test
    fun `a result is not shown for a query the user has since changed`() =
        runTest {
            coEvery { whoisLookupUseCase(any(), any()) } returns NetworkResult.Success(stubResult)
            val first = newViewModel()
            first.onQueryChange("example.com")
            first.lookup()
            leave(first)

            val returned = newViewModel(handle = SavedStateHandle(mapOf("whoisQuery" to "other.org")))

            assertEquals("other.org", returned.uiState.value.query)
            assertNull(returned.uiState.value.result)
        }
}
