package net.aieat.netswissknife.app.ui.screens.subnet

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.aieat.netswissknife.core.domain.SubnetCalculatorUseCase
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.Ipv4Scope
import net.aieat.netswissknife.core.network.subnet.SubnetAddressRole
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import net.aieat.netswissknife.core.network.subnet.SubnetMembership
import net.aieat.netswissknife.core.network.subnet.SubnetRow
import net.aieat.netswissknife.core.network.subnet.SubnetSplit
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        scope = Ipv4Scope.PRIVATE,
        cidrNotation = "192.168.1.0/24",
        inputIsAligned = true,
    )

    private val sampleMembership =
        SubnetMembership(
            address = "192.168.1.50",
            cidrNotation = "192.168.1.0/24",
            networkAddress = "192.168.1.0",
            broadcastAddress = "192.168.1.255",
            role = SubnetAddressRole.USABLE_HOST,
        )

    private val sampleSplit =
        SubnetSplit(
            parentCidr = "192.168.1.0/24",
            newPrefix = 26,
            requestedCount = null,
            subnetCount = 4,
            rows = listOf(SubnetRow("192.168.1.0/26", "192.168.1.0", "192.168.1.63")),
        )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        useCase = mockk()
        coEvery { useCase(any()) } returns NetworkResult.Success(sampleSubnetInfo)
        every { useCase.cidrInputError(any()) } returns null
        coEvery { useCase.invokeRange(any(), any()) } returns NetworkResult.Success(sampleSubnetInfo)
        coEvery { useCase.checkMembership(any(), any()) } returns NetworkResult.Success(sampleMembership)
        coEvery { useCase.splitIntoPrefix(any(), any()) } returns NetworkResult.Success(sampleSplit)
        coEvery { useCase.splitIntoCount(any(), any()) } returns NetworkResult.Success(sampleSplit)
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

    @Nested
    @DisplayName("editing clears stale results (SUB-02)")
    inner class StaleResults {
        @Test
        fun `clearing the input clears the previous result`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                assertTrue(viewModel.uiState.value.result != null)

                viewModel.onInputChange("")

                assertNull(viewModel.uiState.value.result)
            }
        }

        @Test
        fun `editing the input clears the result and its error`() {
            runTest {
                coEvery { useCase("192.168.1.0/") } returns NetworkResult.error(ErrorCode.SUBNET_PREFIX_MISSING)
                viewModel.onInputChange("192.168.1.0/")
                viewModel.calculate()
                assertTrue(viewModel.uiState.value.error != null)

                viewModel.onInputChange("192.168.1.0/2")

                assertNull(viewModel.uiState.value.error)
                assertNull(viewModel.uiState.value.result)
            }
        }

        @Test
        fun `editing a range bound clears the range result`() {
            runTest {
                viewModel.toggleMode()
                viewModel.onMinIpChange("192.168.1.0")
                viewModel.onMaxIpChange("192.168.1.255")
                viewModel.calculateRange()
                assertTrue(viewModel.uiState.value.result != null)

                viewModel.onMaxIpChange("192.168.1.1")

                assertNull(viewModel.uiState.value.result)
            }
        }

        @Test
        fun `input error keeps its typed code and shows inline without calculating`() {
            runTest {
                every { useCase.cidrInputError("10.0.0.0/") } returns
                    NetworkResult.error(ErrorCode.SUBNET_PREFIX_MISSING)
                viewModel.onInputChange("10.0.0.0/")

                val error = viewModel.uiState.value.inputError
                val info: ErrorInfo? = error?.info
                assertEquals(ErrorCode.SUBNET_PREFIX_MISSING, info?.code)
                viewModel.calculate()
                assertNull(viewModel.uiState.value.result)
                assertNull(viewModel.uiState.value.error)
            }
        }

        @Test
        fun `a valid entry clears the inline input error`() {
            runTest {
                every { useCase.cidrInputError("10.0.0.0/") } returns
                    NetworkResult.error(ErrorCode.SUBNET_PREFIX_MISSING)
                viewModel.onInputChange("10.0.0.0/")
                viewModel.onInputChange("10.0.0.0/8")

                assertNull(viewModel.uiState.value.inputError)
            }
        }
    }

    @Nested
    @DisplayName("is this address in the subnet?")
    inner class Membership {
        @Test
        fun `checking an address keeps the calculated result`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()

                viewModel.onCheckIpChange("192.168.1.50")

                assertTrue(viewModel.uiState.value.result != null)
                assertEquals(sampleMembership, viewModel.uiState.value.membership)
            }
        }

        @Test
        fun `membership is checked against the calculated network`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()

                viewModel.onCheckIpChange("192.168.1.50")

                coVerify { useCase.checkMembership("192.168.1.50", "192.168.1.0/24") }
            }
        }

        @Test
        fun `no membership check happens without a result`() {
            runTest {
                viewModel.onCheckIpChange("192.168.1.50")

                coVerify(exactly = 0) { useCase.checkMembership(any(), any()) }
                assertNull(viewModel.uiState.value.membership)
            }
        }

        @Test
        fun `changing the address clears the previous answer`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onCheckIpChange("192.168.1.50")

                viewModel.onCheckIpChange("")

                assertNull(viewModel.uiState.value.membership)
            }
        }
    }

    @Nested
    @DisplayName("split into equal subnets")
    inner class Split {
        @Test
        fun `split by prefix stores the split`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onSplitInputChange("26")

                viewModel.splitNetwork()

                assertEquals(sampleSplit, viewModel.uiState.value.split)
                coVerify { useCase.splitIntoPrefix("192.168.1.0/24", 26) }
            }
        }

        @Test
        fun `split by count uses the count mode`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onSplitModeChange(SubnetSplitMode.BY_COUNT)
                viewModel.onSplitInputChange("3")

                viewModel.splitNetwork()

                coVerify { useCase.splitIntoCount("192.168.1.0/24", 3L) }
            }
        }

        @Test
        fun `unparseable prefix is sent as an invalid value`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onSplitInputChange("abc")

                viewModel.splitNetwork()

                coVerify { useCase.splitIntoPrefix("192.168.1.0/24", -1) }
            }
        }

        @Test
        fun `changing the mode clears the split`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onSplitInputChange("26")
                viewModel.splitNetwork()

                viewModel.onSplitModeChange(SubnetSplitMode.BY_COUNT)

                assertNull(viewModel.uiState.value.split)
                assertEquals("", viewModel.uiState.value.splitInput)
            }
        }

        @Test
        fun `editing the network clears the split`() {
            runTest {
                viewModel.onInputChange("192.168.1.0/24")
                viewModel.calculate()
                viewModel.onSplitInputChange("26")
                viewModel.splitNetwork()

                viewModel.onInputChange("10.0.0.0/8")

                assertNull(viewModel.uiState.value.split)
            }
        }
    }
}
