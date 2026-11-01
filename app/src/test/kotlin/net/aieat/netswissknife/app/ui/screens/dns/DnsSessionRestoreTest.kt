package net.aieat.netswissknife.app.ui.screens.dns

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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.app.data.ToolSessionMemory
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
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("DnsViewModel form and result kept across leaving and returning")
class DnsSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: DnsLookupUseCase
    private lateinit var systemDnsProvider: SystemDnsAddressProvider
    private lateinit var recentHostsRepository: RecentHostsRepository
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<DnsViewModel>()

    private val stubResult =
        DnsResult(
            domain = "example.com",
            recordType = DnsRecordType.A,
            server = DnsServer.Google,
            records = listOf(DnsRecord(DnsRecordType.A, "example.com", "93.184.216.34", 300L, "raw")),
            queryTimeMs = 42L,
            rawResponse = ";; raw",
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        systemDnsProvider = mockk { every { getAddresses() } returns emptyList() }
        recentHostsRepository =
            mockk(relaxed = true) {
                every { getRecents(any()) } returns flowOf(emptyList())
            }
        memory = ToolSessionMemory()
    }

    @AfterEach
    fun tearDown() =
        runBlocking {
            // Every ViewModel must finish its Main-dispatched work before resetMain(), or the
            // leaked coroutine fails the next test class.
            created.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
            Dispatchers.resetMain()
        }

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): DnsViewModel {
        val viewModel =
            DnsViewModel(
                dnsLookupUseCase = useCase,
                systemDnsAddressProvider = systemDnsProvider,
                recentHostsRepository = recentHostsRepository,
                savedStateHandle = handle,
                sessionMemory = memory,
            )
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: DnsViewModel) {
        ViewModelStore().apply {
            put("dns", viewModel)
            clear()
        }
    }

    @Test
    fun `a finished lookup and its form are shown again after leaving and returning`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
            val first = newViewModel()
            first.onDomainChange("example.com")
            first.onRecordTypeChange(DnsRecordType.MX)
            first.performLookup()
            assertTrue(first.uiState.value is DnsUiState.Success)
            leave(first)

            val returned = newViewModel()

            assertEquals(stubResult, (returned.uiState.value as DnsUiState.Success).result)
            assertEquals("example.com", returned.domain.value)
            assertEquals(DnsRecordType.MX, returned.recordType.value)
        }

    @Test
    fun `a lookup still running when the tool is left is not restored as running`() =
        runTest {
            coEvery { useCase(any(), any()) } coAnswers { awaitCancellation() }
            val first = newViewModel()
            first.onDomainChange("example.com")
            first.performLookup()
            assertTrue(first.uiState.value is DnsUiState.Loading)
            leave(first)

            val returned = newViewModel()

            assertTrue(returned.uiState.value is DnsUiState.Idle)
            assertEquals("example.com", returned.domain.value)
        }

    @Test
    fun `a custom resolver is kept with its address and the system resolver is re-read, not stored`() =
        runTest {
            val first = newViewModel()
            first.onCustomServerAddressChange("192.0.2.53")
            leave(first)

            val returned = newViewModel()

            assertEquals(DnsServer.Custom("192.0.2.53"), returned.selectedServer.value)
            assertEquals("192.0.2.53", returned.customServerAddress.value)
        }

    @Test
    fun `a cleared result does not come back after leaving`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubResult)
            val first = newViewModel()
            first.onDomainChange("example.com")
            first.performLookup()
            first.onClearResults()
            leave(first)

            val returned = newViewModel()

            assertTrue(returned.uiState.value is DnsUiState.Idle)
            assertEquals("example.com", returned.domain.value)
        }
}
