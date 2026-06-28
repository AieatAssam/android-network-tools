package net.aieat.netswissknife.app.ui.screens.subnet

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("SubnetCalculatorViewModel")
class SubnetCalculatorViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var useCase: SubnetCalculatorUseCase
    private lateinit var viewModel: SubnetCalculatorViewModel

    private val sampleSubnetInfo = SubnetInfo(
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
        cidrNotation = "192.168.1.0/24",
        inputIsAligned = true,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        coEvery { useCase(any()) } returns NetworkResult.Success(sampleSubnetInfo)
        coEvery { useCase.invokeRange(any(), any()) } returns NetworkResult.Success(sampleSubnetInfo)
        viewModel = SubnetCalculatorViewModel(useCase)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    @DisplayName("toggleMode – CIDR → Range")
    inner class CidrToRange {

        @Test
        fun `with no prior result switches to range mode with blank fields`() = runTest {
            viewModel.onInputChange("192.168.1.0/24")
            viewModel.toggleMode()

            assertTrue(viewModel.uiState.value.isRangeMode)
            assertEquals("192.168.1.0", viewModel.uiState.value.minIpInput)
            assertEquals("", viewModel.uiState.value.maxIpInput)
        }

        @Test
        fun `with calculated result pre-fills network and broadcast addresses`() = runTest {
            viewModel.onInputChange("192.168.1.0/24")
            viewModel.calculate()
            viewModel.toggleMode()

            assertTrue(viewModel.uiState.value.isRangeMode)
            assertEquals("192.168.1.0", viewModel.uiState.value.minIpInput)
            assertEquals("192.168.1.255", viewModel.uiState.value.maxIpInput)
        }

        @Test
        fun `clears result on toggle`() = runTest {
            viewModel.onInputChange("192.168.1.0/24")
            viewModel.calculate()
            viewModel.toggleMode()

            assertFalse(viewModel.uiState.value.result != null)
        }
    }

    @Nested
    @DisplayName("toggleMode – Range → CIDR")
    inner class RangeToCidr {

        @Test
        fun `switches to CIDR mode and copies minIp into CIDR input`() = runTest {
            viewModel.toggleMode() // switch to range first
            viewModel.onMinIpChange("10.0.0.1")
            viewModel.onMaxIpChange("10.0.0.100")
            viewModel.toggleMode() // switch back to CIDR

            assertFalse(viewModel.uiState.value.isRangeMode)
            assertEquals("10.0.0.1", viewModel.uiState.value.input)
        }

        @Test
        fun `does not clear CIDR input when min IP is blank`() = runTest {
            viewModel.onInputChange("192.168.1.0/24")
            viewModel.toggleMode() // to range — minIpInput should be "192.168.1.0" now
            viewModel.onMinIpChange("") // clear min IP
            viewModel.toggleMode() // back to CIDR

            assertFalse(viewModel.uiState.value.isRangeMode)
            // input is preserved from before
        }
    }
}
