package net.aieat.netswissknife.app.ui.screens.wol

import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import net.aieat.netswissknife.app.platform.NetworkErrorKind
import net.aieat.netswissknife.app.ui.navigation.HostTool
import net.aieat.netswissknife.app.ui.navigation.ToolDestination
import net.aieat.netswissknife.app.ui.navigation.ToolHost
import net.aieat.netswissknife.app.ui.navigation.ToolIntent
import net.aieat.netswissknife.app.ui.navigation.ToolIntentCodec
import net.aieat.netswissknife.app.ui.navigation.ToolMacAddress
import net.aieat.netswissknife.app.ui.navigation.ToolSource
import net.aieat.netswissknife.core.domain.WakeOnLanParams
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.net.LocalNetworkPermissionDeniedException
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WakeOnLanViewModel")
class WakeOnLanViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var useCase: WakeOnLanUseCase
    private lateinit var viewModel: WakeOnLanViewModel

    private val stubReport = WolSendReport("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9, 3)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        viewModel = WakeOnLanViewModel(useCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Idle with defaults`() {
        assertEquals(WolUiState.Idle, viewModel.uiState.value)
        assertEquals("", viewModel.macAddress.value)
        assertEquals("255.255.255.255", viewModel.broadcastAddress.value)
        assertEquals("9", viewModel.port.value)
        assertFalse(viewModel.canSend)
        assertFalse(viewModel.isMacInvalid)
    }

    @Test
    fun `typed LAN WOL handoff pre-fills normalized MAC without sending`() {
        val mac = requireNotNull(ToolMacAddress.parse("02-23-45-67-89-ab"))
        val intent = ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.LAN)
        val handoff = WakeOnLanViewModel(
            useCase,
            savedStateHandle = SavedStateHandle(
                mapOf("intent" to ToolIntentCodec.encode(intent), "mac" to mac.value),
            ),
        )

        assertEquals("02:23:45:67:89:AB", handoff.macAddress.value)
        assertEquals(ToolSource.LAN, handoff.sourceContext)
        assertFalse(handoff.hasInvalidHandoff.value)
        assertEquals(WolUiState.Idle, handoff.uiState.value)
        assertTrue(handoff.canSend)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `edited handoff MAC survives recreation and invalid payload is recoverable`() {
        val mac = requireNotNull(ToolMacAddress.parse("02:23:45:67:89:AB"))
        val intent = ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.LAN)
        val routeArgs = mapOf("intent" to ToolIntentCodec.encode(intent), "mac" to mac.value)
        val savedState = SavedStateHandle(routeArgs)
        val first = WakeOnLanViewModel(useCase, savedStateHandle = savedState)
        first.onMacAddressChange("02:23:45:67:89:AC")
        assertEquals("02:23:45:67:89:AC", savedState.get<String>("editedWolMac"))

        val restored = WakeOnLanViewModel(
            useCase,
            savedStateHandle = SavedStateHandle(routeArgs + ("editedWolMac" to "02:23:45:67:89:AC")),
        )
        assertEquals("02:23:45:67:89:AC", restored.macAddress.value)
        assertFalse(restored.hasInvalidHandoff.value)

        val invalidState = SavedStateHandle(mapOf("intent" to "ti1.invalid", "mac" to mac.value))
        val invalid = WakeOnLanViewModel(useCase, savedStateHandle = invalidState)
        assertTrue(invalid.hasInvalidHandoff.value)
        assertEquals("", invalid.macAddress.value)
        assertNull(invalid.sourceContext)

        listOf("01:23:45:67:89:AB", "FF:FF:FF:FF:FF:FF", "00:00:00:00:00:00").forEach { rejectedMac ->
            invalid.onMacAddressChange(rejectedMac)
            assertTrue(invalid.hasInvalidHandoff.value, "$rejectedMac must not dismiss invalid handoff")
            assertTrue(invalid.isMacInvalid, "$rejectedMac must fail unicast validation")
            assertFalse(invalid.canSend, "$rejectedMac must not enable Send")
        }

        invalid.onMacAddressChange("02:23:45:67:89:AD")
        assertFalse(invalid.hasInvalidHandoff.value)
        assertEquals(true, invalidState.get<Boolean>("wolHandoffRecovered"))
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `typed WOL route rejects wrong destination mismatched and incomplete arguments`() {
        val mac = requireNotNull(ToolMacAddress.parse("02:23:45:67:89:AB"))
        val wrongDestination = ToolIntent(
            ToolDestination.HostTarget(HostTool.PING, requireNotNull(ToolHost.parse("192.0.2.8"))),
            ToolSource.LAN,
        )
        val wrongSource = ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.MDNS)
        val missingSource = ToolIntent(ToolDestination.WakeOnLan(mac))
        listOf(
            mapOf("intent" to "ti1.invalid", "mac" to mac.value),
            mapOf("intent" to ToolIntentCodec.encode(ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.LAN)), "mac" to "02:23:45:67:89:AC"),
            mapOf("intent" to ToolIntentCodec.encode(wrongDestination), "mac" to mac.value),
            mapOf("intent" to ToolIntentCodec.encode(wrongSource), "mac" to mac.value),
            mapOf("intent" to ToolIntentCodec.encode(missingSource), "mac" to mac.value),
            mapOf("intent" to ToolIntentCodec.encode(ToolIntent(ToolDestination.WakeOnLan(mac), ToolSource.LAN))),
        ).forEach { arguments ->
            val invalid = WakeOnLanViewModel(useCase, savedStateHandle = SavedStateHandle(arguments))
            assertTrue(invalid.hasInvalidHandoff.value)
            assertEquals("", invalid.macAddress.value)
            assertNull(invalid.sourceContext)
            assertFalse(invalid.canSend)
        }
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `isMacInvalid rejects partial and non-unicast input, accepts valid unicast or blank`() {
        viewModel.onMacAddressChange("AA:BB")
        assertTrue(viewModel.isMacInvalid)

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        assertFalse(viewModel.isMacInvalid)
        assertTrue(viewModel.canSend)

        viewModel.onMacAddressChange("01:23:45:67:89:AB")
        assertTrue(viewModel.isMacInvalid)
        assertFalse(viewModel.canSend)

        viewModel.onMacAddressChange("")
        assertFalse(viewModel.isMacInvalid)
    }

    @Test
    fun `onPortChange rejects non-numeric and over-length input`() {
        viewModel.onPortChange("abc")
        assertEquals("9", viewModel.port.value)

        viewModel.onPortChange("123456")
        assertEquals("9", viewModel.port.value)

        viewModel.onPortChange("7")
        assertEquals("7", viewModel.port.value)

        viewModel.onPortChange("")
        assertEquals("", viewModel.port.value)
    }

    @Test
    fun `send transitions to Success on use case success`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubReport)

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        assertEquals(WolUiState.Success(stubReport), viewModel.uiState.value)
        coVerify { useCase(any(), any()) }
    }

    @Test
    fun `send transitions to Error on use case failure`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Error("boom")

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        assertEquals(WolUiState.Error("boom"), viewModel.uiState.value)
    }

    @Test
    fun `send marks local permission denial distinctly from general failure`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Error(
            "permission denied",
            LocalNetworkPermissionDeniedException(SecurityException("denied")),
        )

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        val denied = viewModel.uiState.value as WolUiState.Error
        assertEquals(NetworkErrorKind.LOCAL_NETWORK_PERMISSION_DENIED, denied.networkErrorKind)

        coEvery { useCase(any(), any()) } returns NetworkResult.Error("boom")
        viewModel.send()
        val generic = viewModel.uiState.value as WolUiState.Error
        assertEquals(NetworkErrorKind.GENERAL, generic.networkErrorKind)
    }

    @Test
    fun `send is a no-op while input is invalid`() = runTest {
        viewModel.onMacAddressChange("not-a-mac")
        viewModel.send()

        assertEquals(WolUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `rejects UDP destination port zero and values above the maximum`() = runTest {
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")

        for (port in listOf("0", "65536")) {
            viewModel.onPortChange(port)
            assertFalse(viewModel.canSend, "Expected port $port to disable Send")
            viewModel.send()
        }

        coVerify(exactly = 0) { useCase(any(), any()) }
    }

    @Test
    fun `accepts UDP destination port boundaries`() = runTest {
        coEvery { useCase(any(), any()) } coAnswers {
            val params = firstArg<WakeOnLanParams>()
            NetworkResult.Success(stubReport.copy(port = params.port))
        }
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")

        for (port in listOf("1", "65535")) {
            viewModel.onPortChange(port)
            assertTrue(viewModel.canSend)
            viewModel.send()
            coVerify { useCase(match { it.port == port.toInt() }, any()) }
        }
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        coEvery { useCase(any(), any()) } returns NetworkResult.Success(stubReport)
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        viewModel.reset()

        assertEquals(WolUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `stop sending cancels the caller session with user stop reason`() = runTest {
        val session = slot<OperationSession>()
        coEvery { useCase(any(), capture(session)) } coAnswers { awaitCancellation() }
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")

        viewModel.send()
        assertEquals(WolUiState.Sending, viewModel.uiState.value)
        viewModel.stopSending()

        assertEquals(CancellationReason.USER_STOP, session.captured.cancellationReason)
        assertEquals(WolUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `late completion after stop cannot restore a stale success`() = runTest {
        var completion: Continuation<NetworkResult<WolSendReport>>? = null
        coEvery { useCase(any(), any()) } coAnswers {
            suspendCoroutine { continuation -> completion = continuation }
        }
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")

        viewModel.send()
        assertEquals(WolUiState.Sending, viewModel.uiState.value)
        viewModel.stopSending()
        completion!!.resume(NetworkResult.Success(stubReport))

        assertEquals(WolUiState.Idle, viewModel.uiState.value)
    }
}
