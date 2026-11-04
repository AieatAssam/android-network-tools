package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.subnet.SubnetAddressRole
import net.aieat.netswissknife.core.network.subnet.SubnetCalculatorRepository
import net.aieat.netswissknife.core.network.subnet.SubnetMembership
import net.aieat.netswissknife.core.network.subnet.SubnetSplit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SubnetCalculatorOperationsUseCaseTest {
    private val repository = mockk<SubnetCalculatorRepository>()
    private val useCase = SubnetCalculatorUseCase(repository)

    @Test
    fun `checkMembership delegates to repository`() {
        runTest {
            val membership =
                SubnetMembership(
                    address = "10.0.0.5",
                    cidrNotation = "10.0.0.0/24",
                    networkAddress = "10.0.0.0",
                    broadcastAddress = "10.0.0.255",
                    role = SubnetAddressRole.USABLE_HOST,
                )
            coEvery { repository.checkMembership("10.0.0.5", "10.0.0.0/24") } returns NetworkResult.Success(membership)

            val result = useCase.checkMembership("10.0.0.5", "10.0.0.0/24")

            assertEquals(NetworkResult.Success(membership), result)
            coVerify { repository.checkMembership("10.0.0.5", "10.0.0.0/24") }
        }
    }

    @Test
    fun `splitIntoPrefix and splitIntoCount delegate to repository`() {
        runTest {
            val split = SubnetSplit("10.0.0.0/24", 26, null, 4, emptyList())
            val counted = SubnetSplit("10.0.0.0/24", 26, 3, 4, emptyList())
            coEvery { repository.splitIntoPrefix("10.0.0.0/24", 26) } returns NetworkResult.Success(split)
            coEvery { repository.splitIntoCount("10.0.0.0/24", 3L) } returns NetworkResult.Success(counted)

            assertEquals(NetworkResult.Success(split), useCase.splitIntoPrefix("10.0.0.0/24", 26))
            assertEquals(NetworkResult.Success(counted), useCase.splitIntoCount("10.0.0.0/24", 3L))
        }
    }
}
