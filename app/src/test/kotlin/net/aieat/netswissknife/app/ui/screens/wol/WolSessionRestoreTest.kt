package net.aieat.netswissknife.app.ui.screens.wol

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("WakeOnLanViewModel form, device name and last send kept across leaving and returning")
class WolSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: WakeOnLanUseCase
    private lateinit var memory: ToolSessionMemory
    private val created = mutableListOf<WakeOnLanViewModel>()

    private val mac = "AA:BB:CC:DD:EE:FF"
    private val report =
        WolSendReport(
            macAddress = mac,
            broadcastAddress = "192.168.1.255",
            port = 7,
            packetsSent = 1,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
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

    private fun newViewModel(handle: SavedStateHandle = SavedStateHandle()): WakeOnLanViewModel {
        val viewModel = WakeOnLanViewModel(useCase, savedStateHandle = handle, sessionMemory = memory)
        created += viewModel
        return viewModel
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: WakeOnLanViewModel) {
        ViewModelStore().apply {
            put("wol", viewModel)
            clear()
        }
    }

    @Test
    fun `the MAC, broadcast address, port and last successful send are shown again after returning`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(report)
            val first = newViewModel()
            first.onMacAddressChange(mac)
            first.onBroadcastAddressChange("192.168.1.255")
            first.onPortChange("7")
            first.send()
            leave(first)

            val returned = newViewModel()

            assertEquals(mac, returned.macAddress.value)
            assertEquals("192.168.1.255", returned.broadcastAddress.value)
            assertEquals("7", returned.port.value)
            assertEquals(WolUiState.Success(report), returned.uiState.value)
        }

    @Test
    fun `a send still running when the tool is left is not restored as running`() =
        runTest {
            coEvery { useCase(any(), any()) } coAnswers { awaitCancellation() }
            val first = newViewModel()
            first.onMacAddressChange(mac)
            first.send()
            assertEquals(WolUiState.Sending, first.uiState.value)
            leave(first)

            val returned = newViewModel()

            assertEquals(WolUiState.Idle, returned.uiState.value)
            assertEquals(mac, returned.macAddress.value)
        }

    @Test
    fun `the saved device name being typed is kept after returning`() =
        runTest {
            val first = newViewModel()
            first.onMacAddressChange(mac)
            first.savedDeviceEditor.onDeviceNameChange("Office PC")
            leave(first)

            val returned = newViewModel()

            assertEquals("Office PC", returned.deviceName.value)
        }

    @Test
    fun `a result for a MAC from a new handoff is not shown with the old send`() =
        runTest {
            coEvery { useCase(any(), any()) } returns NetworkResult.Success(report)
            val first = newViewModel()
            first.onMacAddressChange(mac)
            first.send()
            leave(first)

            val returned = newViewModel(handle = SavedStateHandle(mapOf("editedWolMac" to "11:22:33:44:55:66")))

            assertEquals("11:22:33:44:55:66", returned.macAddress.value)
            assertTrue(returned.uiState.value is WolUiState.Idle)
        }
}
