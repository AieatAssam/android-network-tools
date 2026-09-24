package net.aieat.netswissknife.app.ui.screens.whois

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.AppPreferenceKeys
import net.aieat.netswissknife.app.data.RecentHostsRepository
import net.aieat.netswissknife.core.domain.WhoisLookupUseCase
import net.aieat.netswissknife.core.domain.WhoisParams
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.whois.WhoisHop
import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import net.aieat.netswissknife.core.network.whois.WhoisServer
import net.aieat.netswissknife.core.network.whois.WhoisServerRole
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
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
            coEvery { whoisLookupUseCase(any(), any()) } returns NetworkResult.Success(stubResult)
            viewModel.onQueryChange("example.com")
            viewModel.lookup()
            val state = viewModel.uiState.value
            assertNotNull(state.result)
            assertFalse(state.isLoading)
        }

        @Test
        fun `error sets error message`() = runTest {
            coEvery { whoisLookupUseCase(any(), any()) } returns NetworkResult.Error("lookup failed")
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

        @Test
        fun `a second lookup's result is not overwritten by a stale first lookup`() = runTest {
            val firstResultReady = CompletableDeferred<Unit>()
            coEvery { whoisLookupUseCase(WhoisParams(query = "first.com"), any()) } coAnswers {
                firstResultReady.await()
                NetworkResult.Success(stubResult.copy(domainName = "first.com"))
            }
            coEvery { whoisLookupUseCase(WhoisParams(query = "second.com"), any()) } returns
                NetworkResult.Success(stubResult.copy(domainName = "second.com"))

            viewModel.onQueryChange("first.com")
            viewModel.lookup()

            viewModel.onQueryChange("second.com")
            viewModel.lookup()

            assertEquals("second.com", viewModel.uiState.value.result?.domainName)

            // The stale first lookup's use-case call finally resolves late — it must
            // not be allowed to overwrite the second (newer) lookup's result.
            firstResultReady.complete(Unit)
            runCurrent()

            assertEquals("second.com", viewModel.uiState.value.result?.domainName)
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
        coEvery { whoisLookupUseCase(any(), any()) } returns NetworkResult.Success(stubResult)
        viewModel.onQueryChange("example.com")
        viewModel.lookup()
        coVerify { recentHostsRepository.addRecent(AppPreferenceKeys.RECENT_WHOIS_HOSTS, "example.com") }
    }

    @Test
    fun `replacement lookup ignores delayed hop from previous operation`() = runTest {
        val progress = MutableSharedFlow<WhoisHop>(extraBufferCapacity = 4)
        every { whoisLookupUseCase.hopProgress } returns progress
        val sessions = mutableMapOf<String, OperationSession>()
        coEvery { whoisLookupUseCase(any(), any()) } coAnswers {
            sessions[firstArg<WhoisParams>().query] = secondArg()
            kotlinx.coroutines.awaitCancellation()
        }

        try {
            viewModel.onQueryChange("first.com")
            viewModel.lookup()
            runCurrent()
            val firstSession = checkNotNull(sessions["first.com"])
            val firstHop = stubHop.copy(
                server = WhoisServer("whois.first.test", WhoisServerRole.IANA),
                operationId = firstSession.budget.operationId,
            )
            progress.emit(firstHop)
            runCurrent()
            assertEquals(listOf(firstHop.server), viewModel.uiState.value.hopStates.map { it.server })

            viewModel.onQueryChange("second.com")
            viewModel.lookup()
            runCurrent()
            val secondSession = checkNotNull(sessions["second.com"])
            assertEquals(CancellationReason.USER_STOP, firstSession.cancellationReason)
            assertTrue(viewModel.uiState.value.hopStates.isEmpty(), "replacement starts with a fresh hop list")

            progress.emit(firstHop.copy(server = WhoisServer("late.first.test", WhoisServerRole.REGISTRY)))
            runCurrent()
            assertTrue(viewModel.uiState.value.hopStates.isEmpty(), "a delayed old-operation hop must be ignored")

            val secondHop = stubHop.copy(
                server = WhoisServer("whois.second.test", WhoisServerRole.IANA),
                operationId = secondSession.budget.operationId,
            )
            progress.emit(secondHop)
            runCurrent()
            assertEquals(listOf(secondHop.server), viewModel.uiState.value.hopStates.map { it.server })
        } finally {
            viewModel.stopLookup()
            runCurrent()
        }
    }

    @Test
    fun `independent view models filter shared progress by their operation ids`() = runTest {
        val progress = MutableSharedFlow<WhoisHop>(extraBufferCapacity = 4)
        every { whoisLookupUseCase.hopProgress } returns progress
        val sessions = mutableMapOf<String, OperationSession>()
        coEvery { whoisLookupUseCase(any(), any()) } coAnswers {
            sessions[firstArg<WhoisParams>().query] = secondArg()
            kotlinx.coroutines.awaitCancellation()
        }
        val secondViewModel = WhoisViewModel(whoisLookupUseCase, recentHostsRepository)

        try {
            viewModel.onQueryChange("first.com")
            viewModel.lookup()
            secondViewModel.onQueryChange("second.com")
            secondViewModel.lookup()
            runCurrent()
            assertEquals(2, sessions.size)
            val firstSession = checkNotNull(sessions["first.com"])
            val secondSession = checkNotNull(sessions["second.com"])

            val firstHop = stubHop.copy(
                server = WhoisServer("whois.first.test", WhoisServerRole.IANA),
                operationId = firstSession.budget.operationId,
            )
            val secondHop = stubHop.copy(
                server = WhoisServer("whois.second.test", WhoisServerRole.IANA),
                operationId = secondSession.budget.operationId,
            )

            progress.emit(firstHop)
            runCurrent()
            assertEquals(listOf(firstHop.server), viewModel.uiState.value.hopStates.map { it.server })
            assertTrue(secondViewModel.uiState.value.hopStates.isEmpty())

            progress.emit(secondHop)
            runCurrent()
            assertEquals(listOf(firstHop.server), viewModel.uiState.value.hopStates.map { it.server })
            assertEquals(listOf(secondHop.server), secondViewModel.uiState.value.hopStates.map { it.server })
        } finally {
            viewModel.stopLookup()
            secondViewModel.stopLookup()
            runCurrent()
        }
    }

    @Test
    fun `stop cancels caller-owned session with USER_STOP and ignores late progress`() = runTest {
        val progress = MutableSharedFlow<WhoisHop>(extraBufferCapacity = 4)
        every { whoisLookupUseCase.hopProgress } returns progress
        var receivedSession: OperationSession? = null
        coEvery { whoisLookupUseCase(any(), any()) } coAnswers {
            receivedSession = secondArg()
            kotlinx.coroutines.awaitCancellation()
        }

        viewModel.onQueryChange("example.com")
        viewModel.lookup()
        runCurrent()
        assertNotNull(receivedSession)
        val session = checkNotNull(receivedSession)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.stopLookup()
        runCurrent()

        assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Lookup stopped. Retry when ready.", viewModel.uiState.value.error)
        progress.tryEmit(stubHop.copy(operationId = session.budget.operationId))
        runCurrent()
        assertTrue(viewModel.uiState.value.hopStates.isEmpty(), "stopped lookup must ignore late progress")
    }

    @Test
    fun `lifecycle pause cancels caller-owned session with LIFECYCLE_PAUSE`() = runTest {
        var receivedSession: OperationSession? = null
        coEvery { whoisLookupUseCase(any(), any()) } coAnswers {
            receivedSession = secondArg()
            kotlinx.coroutines.awaitCancellation()
        }
        viewModel.onQueryChange("example.com")
        viewModel.lookup()
        runCurrent()
        assertNotNull(receivedSession)
        val session = checkNotNull(receivedSession)

        viewModel.onLifecyclePause()
        runCurrent()

        assertEquals(CancellationReason.LIFECYCLE_PAUSE, session.cancellationReason)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.error.orEmpty().contains("left the foreground"))
    }

    @Test
    fun `late result after Stop cannot overwrite stopped state`() = runTest {
        val delayedResult = CompletableDeferred<NetworkResult<WhoisResult>>()
        coEvery { whoisLookupUseCase(any(), any()) } coAnswers {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) { delayedResult.await() }
            } catch (_: kotlinx.coroutines.CancellationException) {
                // Model a legacy adapter that returns after cancellation; the ViewModel's
                // session identity guard must still prevent that result from being applied.
            }
            delayedResult.getCompleted()
        }
        viewModel.onQueryChange("example.com")
        viewModel.lookup()
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.stopLookup()
        runCurrent()
        delayedResult.complete(NetworkResult.Success(stubResult.copy(domainName = "late.example.com")))
        runCurrent()

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.result)
        assertEquals("Lookup stopped. Retry when ready.", viewModel.uiState.value.error)
    }
}
