package net.aieat.netswissknife.app.ui.screens.mdns

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.MdnsDiscoveryUseCase
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.core.network.mdns.DiscoveredService
import net.aieat.netswissknife.core.network.mdns.MdnsUpdate
import net.aieat.netswissknife.core.network.mdns.MdnsTruncationReason
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("MdnsDiscoveryViewModel")
class MdnsDiscoveryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: MdnsDiscoveryUseCase
    private lateinit var viewModel: MdnsDiscoveryViewModel

    private fun stubService(name: String = "printer", type: String = "_ipp._tcp.local.") =
        DiscoveredService(
            serviceType = type,
            instanceName = name,
            displayName = name,
            hostname = "$name.local",
            port = 631,
            ipAddresses = listOf("192.168.1.50")
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        viewModel = MdnsDiscoveryViewModel(useCase)
    }

    @AfterEach
    fun tearDown() = runBlocking {
        // startScan() also runs a while(true){delay(100)} timer job in viewModelScope;
        // drain it before resetMain() so it never touches Main afterwards.
        viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("initial state")
    inner class InitialState {

        @Test
        fun `starts idle with no services`() {
            val state = viewModel.uiState.value
            assertTrue(!state.isScanning)
            assertTrue(state.services.isEmpty())
            assertTrue(!state.scanComplete)
        }
    }

    @Nested
    @DisplayName("startScan")
    inner class StartScan {

        @Test
        fun `sets isScanning true immediately`() {
            every { useCase(any(), any()) } returns flow { awaitCancellation() }

            viewModel.startScan()

            assertTrue(viewModel.uiState.value.isScanning)
        }

        @Test
        fun `marks permission denial distinctly and keeps generic discovery errors general`() = runTest {
            every { useCase(any(), any()) } returns flow { throw LocalNetworkPermissionDeniedException(SecurityException("denied")) }

            viewModel.startScan()

            assertEquals(NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED, viewModel.uiState.value.networkErrorKind)

            viewModel.reset()
            every { useCase(any(), any()) } returns flow { throw IllegalStateException("timeout") }
            viewModel.startScan()
            assertEquals(NetworkErrorKind.GENERAL, viewModel.uiState.value.networkErrorKind)
        }

        @Test
        fun `accumulates discovered services grouped by type`() = runTest {
            val svc = stubService()
            every { useCase(any(), any()) } returns flowOf(
                MdnsUpdate.ServiceFound(svc),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals(1, state.services.size)
            assertEquals(listOf(svc), state.servicesByType[svc.serviceType])
            assertTrue(state.scanComplete)
            assertTrue(!state.isScanning)
            assertEquals(1, state.totalFound)
        }

        @Test
        fun `retains explicit truncation reasons with partial services`() = runTest {
            val svc = stubService()
            every { useCase(any(), any()) } returns flowOf(
                MdnsUpdate.ServiceFound(svc),
                MdnsUpdate.DiscoveryComplete(
                    totalFound = 1,
                    truncationReasons = setOf(MdnsTruncationReason.QUERY_LIMIT),
                ),
            )

            viewModel.startScan()

            assertEquals(listOf(svc), viewModel.uiState.value.services)
            assertEquals(setOf(MdnsTruncationReason.QUERY_LIMIT), viewModel.uiState.value.truncationReasons)
        }

        @Test
        fun `re-discovering the same instance replaces it instead of duplicating`() = runTest {
            val first = stubService()
            val updated = first.copy(hostname = "printer2.local")
            every { useCase(any(), any()) } returns flowOf(
                MdnsUpdate.ServiceFound(first),
                MdnsUpdate.ServiceFound(updated),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals(1, state.services.size)
            assertEquals("printer2.local", state.services.single().hostname)
        }

        @Test
        fun `is a no-op while already scanning`() {
            every { useCase(any(), any()) } returns flow { awaitCancellation() }
            viewModel.startScan()

            viewModel.startScan()

            verify(exactly = 1) { useCase(any(), any()) }
        }

        @Test
        fun `sets error state and stops scanning on failure`() = runTest {
            every { useCase(any(), any()) } returns flow { throw RuntimeException("mdns failed") }

            viewModel.startScan()

            val state = viewModel.uiState.value
            assertEquals("mdns failed", state.error)
            assertTrue(!state.isScanning)
        }
    }

    @Nested
    @DisplayName("stopScan and reset")
    inner class StopAndReset {

        @Test
        fun `stopScan sets isScanning false`() {
            every { useCase(any(), any()) } returns flow { awaitCancellation() }
            viewModel.startScan()

            viewModel.stopScan()

            assertTrue(!viewModel.uiState.value.isScanning)
        }

        @Test
        fun `cancellation keeps partial results and does not become a discovery error`() {
            val partial = stubService()
            val sessionSlot = slot<OperationSession>()
            every { useCase(any(), capture(sessionSlot)) } returns flow {
                emit(MdnsUpdate.ServiceFound(partial))
                awaitCancellation()
            }
            viewModel.startScan()

            viewModel.stopScan()

            assertEquals(listOf(partial), viewModel.uiState.value.services)
            assertTrue(!viewModel.uiState.value.isScanning)
            assertEquals(null, viewModel.uiState.value.error)
            assertTrue(!viewModel.uiState.value.scanComplete)
            assertTrue(viewModel.uiState.value.scanCanceled)
            assertEquals(CancellationReason.USER_STOP, sessionSlot.captured.cancellationReason)
            assertEquals(OperationRequirement.LOCAL_NETWORK, sessionSlot.captured.budget.requirement)
            assertEquals(1, sessionSlot.captured.budget.maxConcurrentProbes)
            assertEquals(65_536L, sessionSlot.captured.budget.maxResponseBytes)
            assertEquals(5_000_000_000L, sessionSlot.captured.budget.deadline.timeoutNanos)
        }

        @Test
        fun `empty user stop becomes canceled empty and repeated stop is harmless`() {
            every { useCase(any(), any()) } returns flow { awaitCancellation() }
            viewModel.startScan()

            viewModel.stopScan()
            viewModel.stopScan()

            val state = viewModel.uiState.value
            assertTrue(!state.isScanning)
            assertTrue(state.scanCanceled)
            assertTrue(state.services.isEmpty())
            assertEquals(null, state.error)
        }

        @Test
        fun `scan remains canceling until cleanup finishes then enables retry`() = runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            every { useCase(any(), any()) } returns flow {
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) { delay(100) }
                }
            }

            viewModel.startScan()
            testScheduler.runCurrent()
            viewModel.stopScan()

            assertTrue(viewModel.uiState.value.isScanning)
            assertTrue(viewModel.uiState.value.isCanceling)
            viewModel.startScan()
            verify(exactly = 1) { useCase(any(), any()) }

            testScheduler.advanceTimeBy(100)
            testScheduler.runCurrent()

            assertTrue(!viewModel.uiState.value.isScanning)
            assertTrue(viewModel.uiState.value.scanCanceled)
            viewModel.startScan()
            testScheduler.runCurrent()
            verify(exactly = 2) { useCase(any(), any()) }
            viewModel.stopScan()
            testScheduler.runCurrent()
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }

        @Test
        fun `late old run error cannot replace a restarted scan state`() = runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            every { useCase(any(), any()) } returnsMany listOf(
                flow {
                    try {
                        awaitCancellation()
                    } finally {
                        withContext(NonCancellable) { throw IllegalStateException("late old failure") }
                    }
                },
                flow { awaitCancellation() },
            )

            viewModel.startScan()
            testScheduler.runCurrent()
            viewModel.reset()
            viewModel.startScan()
            testScheduler.runCurrent()

            val state = viewModel.uiState.value
            assertTrue(state.isScanning)
            assertEquals(null, state.error)
            assertTrue(state.services.isEmpty())

            viewModel.stopScan()
            testScheduler.runCurrent()
            viewModel.viewModelScope.coroutineContext.job.cancelAndJoin()
        }

        @Test
        fun `clearing view model records lifecycle pause`() {
            val sessionSlot = slot<OperationSession>()
            every { useCase(any(), capture(sessionSlot)) } returns flow { awaitCancellation() }
            val store = ViewModelStore()
            store.put("mdns", viewModel)

            viewModel.startScan()
            store.clear()

            assertEquals(CancellationReason.LIFECYCLE_PAUSE, sessionSlot.captured.cancellationReason)
        }

        @Test
        fun `reset clears back to default state`() = runTest {
            every { useCase(any(), any()) } returns flowOf(
                MdnsUpdate.ServiceFound(stubService()),
                MdnsUpdate.DiscoveryComplete(totalFound = 1)
            )
            viewModel.startScan()

            viewModel.reset()

            assertEquals(MdnsDiscoveryUiState(), viewModel.uiState.value)
        }
    }
}
