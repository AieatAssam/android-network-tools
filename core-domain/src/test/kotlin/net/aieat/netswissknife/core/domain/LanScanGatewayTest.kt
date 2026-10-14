package net.aieat.netswissknife.core.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.lan.LanScanRepository
import net.aieat.netswissknife.core.network.lan.LanScanRequest
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LanScanGatewayTest {
    @Test
    fun `gateway inside subnet is forwarded`() = runTest {
        val repository = mockk<LanScanRepository>()
        every { repository.scan(any<LanScanRequest>()) } returns emptyFlow()

        LanScanUseCase(repository)(
            LanScanParams("192.168.1.0/24", gatewayIp = "192.168.1.254"),
        ).collect {}

        verify {
            repository.scan(match { it.gatewayIp == "192.168.1.254" })
        }
    }

    @Test
    fun `gateway outside subnet is discarded`() = runTest {
        val repository = mockk<LanScanRepository>()
        every { repository.scan(any<LanScanRequest>()) } returns emptyFlow()

        LanScanUseCase(repository)(
            LanScanParams("192.168.1.0/24", gatewayIp = "192.168.2.1"),
        ).collect {}

        verify {
            repository.scan(match { it.gatewayIp == null })
        }
    }

    @Test
    fun `caller operation session is forwarded to repository`() = runTest {
        val repository = mockk<LanScanRepository>()
        val session = OperationSession(
            OperationBudget.start(requirement = OperationRequirement.LOCAL_NETWORK),
        )
        every { repository.scan(any<LanScanRequest>(), any()) } returns emptyFlow()

        LanScanUseCase(repository)(
            LanScanParams("192.168.1.0/24"),
            session,
        ).collect {}

        verify { repository.scan(any<LanScanRequest>(), session) }
    }
}
