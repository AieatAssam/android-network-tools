package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepository
import net.aieat.netswissknife.core.network.subnet.SubnetInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubnetCalculatorUseCaseTest {

    private val repository = mockk<SubnetCalculatorRepository>()
    private val useCase = SubnetCalculatorUseCase(repository)

    @Test
    fun `invoke delegates to repository and returns result`() = runTest {
        val expectedInfo = SubnetInfo(
            inputIp = "192.168.1.100",
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
            binaryIpAddress = "11000000.10101000.00000001.01100100",
            totalHosts = 256L,
            usableHosts = 254L,
            hostBits = 8,
            ipClass = "C",
            isPrivate = true,
            cidrNotation = "192.168.1.0/24",
            inputIsAligned = false,
        )
        coEvery { repository.calculate("192.168.1.100/24") } returns NetworkResult.Success(expectedInfo)

        val result = useCase("192.168.1.100/24")

        coVerify(exactly = 1) { repository.calculate("192.168.1.100/24") }
        assertEquals(NetworkResult.Success(expectedInfo), result)
    }

    @Test
    fun `invoke returns error when repository fails`() = runTest {
        coEvery { repository.calculate("bad-input") } returns NetworkResult.Error("Invalid IP address")

        val result = useCase("bad-input")

        assertEquals(NetworkResult.Error("Invalid IP address"), result)
    }
}
