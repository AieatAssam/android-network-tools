package net.aieat.netswissknife.app.ui.screens.wol

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.WakeOnLanUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

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
    fun `isMacInvalid true for partial input, false when valid or blank`() {
        viewModel.onMacAddressChange("AA:BB")
        assertTrue(viewModel.isMacInvalid)

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        assertFalse(viewModel.isMacInvalid)

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
        coEvery { useCase(any()) } returns NetworkResult.Success(stubReport)

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        assertEquals(WolUiState.Success(stubReport), viewModel.uiState.value)
        coVerify { useCase(any()) }
    }

    @Test
    fun `send transitions to Error on use case failure`() = runTest {
        coEvery { useCase(any()) } returns NetworkResult.Error("boom")

        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        assertEquals(WolUiState.Error("boom"), viewModel.uiState.value)
    }

    @Test
    fun `send is a no-op while input is invalid`() = runTest {
        viewModel.onMacAddressChange("not-a-mac")
        viewModel.send()

        assertEquals(WolUiState.Idle, viewModel.uiState.value)
        coVerify(exactly = 0) { useCase(any()) }
    }

    @Test
    fun `reset returns to Idle`() = runTest {
        coEvery { useCase(any()) } returns NetworkResult.Success(stubReport)
        viewModel.onMacAddressChange("AA:BB:CC:DD:EE:FF")
        viewModel.send()

        viewModel.reset()

        assertEquals(WolUiState.Idle, viewModel.uiState.value)
    }
}
