package net.aieat.netswissknife.app.ui.screens.subnet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.app.data.ToolSessionMemory
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.Ipv4Scope
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SubnetCalculatorViewModel inputs and result kept across leaving and returning")
class SubnetSessionRestoreTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: SubnetCalculatorUseCase
    private lateinit var memory: ToolSessionMemory

    private val sampleSubnetInfo =
        SubnetInfo(
            inputIp = "192.168.1.0",
            prefixLength = 24,
            networkAddress = "192.168.1.0",
            broadcastAddress = "192.168.1.255",
            firstHost = "192.168.1.1",
            lastHost = "192.168.1.254",
            subnetMask = "255.255.255.0",
            wildcardMask = "0.0.0.255",
            hexMask = "0xFFFFFF00",
            binaryMask = "11111111.11111111.11111111.00000000",
            binaryNetworkAddress = "11000000.10101000.00000001.00000000",
            binaryIpAddress = "11000000.10101000.00000001.00000000",
            totalHosts = 256,
            usableHosts = 254,
            hostBits = 8,
            ipClass = "C",
            isPrivate = true,
            scope = Ipv4Scope.PRIVATE,
            cidrNotation = "192.168.1.0/24",
            inputIsAligned = true,
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        every { useCase.cidrInputError(any()) } returns null
        memory = ToolSessionMemory()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Leaving the tool clears its ViewModel, as popping its navigation entry does. */
    private fun leave(viewModel: SubnetCalculatorViewModel) {
        ViewModelStore().apply {
            put("subnet", viewModel)
            clear()
        }
    }

    @Test
    fun `the input and its calculated result are shown again after leaving and returning`() {
        coEvery { useCase(any()) } returns NetworkResult.Success(sampleSubnetInfo)
        val first = SubnetCalculatorViewModel(useCase, sessionMemory = memory)
        first.onInputChange("192.168.1.0/24")
        first.calculate()
        leave(first)

        val returned = SubnetCalculatorViewModel(useCase, sessionMemory = memory)

        assertEquals("192.168.1.0/24", returned.uiState.value.input)
        assertEquals(sampleSubnetInfo, returned.uiState.value.result)
    }

    @Test
    fun `an edited input drops the result, so the result is not kept for the old input`() {
        coEvery { useCase(any()) } returns NetworkResult.Success(sampleSubnetInfo)
        val first = SubnetCalculatorViewModel(useCase, sessionMemory = memory)
        first.onInputChange("192.168.1.0/24")
        first.calculate()
        first.onInputChange("10.0.0.0/8")
        leave(first)

        val returned = SubnetCalculatorViewModel(useCase, sessionMemory = memory)

        assertEquals("10.0.0.0/8", returned.uiState.value.input)
        assertNull(returned.uiState.value.result)
    }

    @Test
    fun `the calculator input is seeded into the saved state when it is restored from memory`() {
        coEvery { useCase(any()) } returns NetworkResult.Success(sampleSubnetInfo)
        val first = SubnetCalculatorViewModel(useCase, sessionMemory = memory)
        first.onInputChange("192.168.1.0/24")
        leave(first)

        val handle = SavedStateHandle()
        SubnetCalculatorViewModel(useCase, savedStateHandle = handle, sessionMemory = memory)

        assertEquals("192.168.1.0/24", handle.get<String>("subnetInput"))
    }
}
