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
            val error = result as NetworkResult.Error
            assertEquals(ErrorCode.PORT_OUT_OF_RANGE, error.info?.code)
            assertEquals(listOf(port, WakeOnLanParams.MIN_PORT, WakeOnLanParams.MAX_PORT), error.info?.args)
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

    @Test
    fun `rejects multicast, broadcast and all-zero MACs without touching repository`() {
        runBlocking {
            for (mac in listOf("01:00:5E:00:00:01", "FF:FF:FF:FF:FF:FF", "00:00:00:00:00:00")) {
                val result = useCase(WakeOnLanParams(macAddress = mac))
                assertTrue(result is NetworkResult.Error, mac)
                assertEquals(ErrorCode.MAC_INVALID, (result as NetworkResult.Error).info?.code, mac)
            }
        }
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }

    @Test
    fun `rejects mixed separator styles and accepts dotted and bare notations`() {
        runBlocking {
            val mixed = useCase(WakeOnLanParams(macAddress = "AA:BB-CC:DD:EE:FF"))
            assertEquals(ErrorCode.MAC_INVALID, (mixed as NetworkResult.Error).info?.code)
            coEvery { repository.sendMagicPacket(any(), any(), any(), any()) } returns
                NetworkResult.Success(WolSendReport("AA:BB:CC:DD:EE:FF", "255.255.255.255", 9, 3))

            assertTrue(useCase(WakeOnLanParams(macAddress = "AABB.CCDD.EEFF")) is NetworkResult.Success)
            assertTrue(useCase(WakeOnLanParams(macAddress = "aabbccddeeff")) is NetworkResult.Success)
        }
    }

    @Test
    fun `rejects invalid broadcast addresses with BROADCAST_INVALID and never sends`() {
        runBlocking {
            for (address in listOf("example.com", "256.0.0.1", "192.168.1", "fe80::1")) {
                val result = useCase(WakeOnLanParams(macAddress = "AA:BB:CC:DD:EE:FF", broadcastAddress = address))
                assertTrue(result is NetworkResult.Error, address)
                assertEquals(ErrorCode.BROADCAST_INVALID, (result as NetworkResult.Error).info?.code, address)
            }
        }
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }

    @Test
    fun `accepts limited, subnet-directed and unicast IPv4 targets`() {
        runBlocking {
            coEvery { repository.sendMagicPacket(any(), any(), any(), any()) } returns
                NetworkResult.Success(WolSendReport("AA:BB:CC:DD:EE:FF", "192.168.1.255", 9, 3))

            for (address in listOf("255.255.255.255", "192.168.1.255", "10.0.0.7")) {
                val params = WakeOnLanParams("AA:BB:CC:DD:EE:FF", broadcastAddress = address)
                assertTrue(useCase(params) is NetworkResult.Success, address)
            }
        }
    }

    @Test
    fun `blank broadcast address is BROADCAST_BLANK`() {
        runBlocking {
            val result = useCase(WakeOnLanParams(macAddress = "AA:BB:CC:DD:EE:FF", broadcastAddress = "  "))
            assertEquals(ErrorCode.BROADCAST_BLANK, (result as NetworkResult.Error).info?.code)
        }
        coVerify(exactly = 0) { repository.sendMagicPacket(any(), any(), any(), any()) }
    }
}
