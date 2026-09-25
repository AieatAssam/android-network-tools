package net.aieat.netswissknife.core.domain

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationSession
import net.aieat.netswissknife.core.network.wol.WakeOnLanRepository
import net.aieat.netswissknife.core.network.wol.WolSendReport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WakeOnLanUseCaseTest {

    private val repository: WakeOnLanRepository = mockk()
    private val useCase = WakeOnLanUseCase(repository)

    @Test
    fun `delegates to repository for a valid MAC`() = runBlocking {
        val report = WolSendReport("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9, 3)
        coEvery { repository.sendMagicPacket(any(), any(), any(), any()) } returns
            NetworkResult.Success(report)

        val result = useCase(WakeOnLanParams(macAddress = "aa:bb:cc:dd:ee:ff"))

        assertTrue(result is NetworkResult.Success)
        assertEquals(report, (result as NetworkResult.Success).data)
        coVerify { repository.sendMagicPacket("aa:bb:cc:dd:ee:ff", "255.255.255.255", 9) }
    }

    @Test
    fun `forwards caller owned operation session`() = runBlocking {
        val report = WolSendReport("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9, 3)
        val session = OperationSession(OperationBudget.start(timeoutMillis = 5_000))
        coEvery {
            repository.sendMagicPacket(any(), any(), any(), any(), any())
        } returns NetworkResult.Success(report)

        val result = useCase(WakeOnLanParams(macAddress = "aa:bb:cc:dd:ee:ff"), session)

        assertEquals(NetworkResult.Success(report), result)
        coVerify {
            repository.sendMagicPacket("aa:bb:cc:dd:ee:ff", "255.255.255.255", 9, 3, session)
        }
    }

    @Test
    fun `trims whitespace before validating`() = runBlocking {
        coEvery { repository.sendMagicPacket(any(), any(), any(), any()) } returns
            NetworkResult.Success(WolSendReport("AA:BB:CC:DD:EE:FF", "192.168.1.255", 7, 3))

        val result = useCase(
            WakeOnLanParams(macAddress = " AA:BB:CC:DD:EE:FF ", broadcastAddress = " 192.168.1.255 ", port = 7)
        )

        assertTrue(result is NetworkResult.Success)
        coVerify { repository.sendMagicPacket("AA:BB:CC:DD:EE:FF", "192.168.1.255", 7) }
    }

    @Test
    fun `rejects blank MAC without touching repository`() = runBlocking {
        val result = useCase(WakeOnLanParams(macAddress = "   "))
        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }

    @Test
    fun `rejects malformed MAC`() = runBlocking {
        val result = useCase(WakeOnLanParams(macAddress = "12:34:56"))
        assertTrue(result is NetworkResult.Error)
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }

    @Test
    fun `rejects reserved zero and out-of-range ports`() = runBlocking {
        for (port in listOf(0, 65_536)) {
            val result = useCase(WakeOnLanParams(macAddress = "AA:BB:CC:DD:EE:FF", port = port))
            assertTrue(result is NetworkResult.Error, "Expected port $port to be rejected")
            assertEquals(ErrorCode.PORT_OUT_OF_RANGE, (result as NetworkResult.Error).info?.code)
        }
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }

    @Test
    fun `accepts both UDP destination port boundaries`() = runBlocking {
        coEvery { repository.sendMagicPacket(any(), any(), any(), any()) } coAnswers {
            NetworkResult.Success(
                WolSendReport(
                    macAddress = firstArg(),
                    broadcastAddress = secondArg(),
                    port = thirdArg(),
                    packetsSent = 3,
                ),
            )
        }

        for (port in listOf(1, 65_535)) {
            val result = useCase(WakeOnLanParams(macAddress = "AA:BB:CC:DD:EE:FF", port = port))
            assertEquals(port, (result as NetworkResult.Success).data.port)
            coVerify { repository.sendMagicPacket("AA:BB:CC:DD:EE:FF", "255.255.255.255", port, 3) }
        }
    }
}
