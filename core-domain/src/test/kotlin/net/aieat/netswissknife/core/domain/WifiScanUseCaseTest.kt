package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.wifi.WifiBand
import net.aieat.netswissknife.core.network.wifi.WifiScanRepository
import net.aieat.netswissknife.core.network.wifi.WifiScanResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("WifiScanUseCase")
class WifiScanUseCaseTest {

    private lateinit var repository: WifiScanRepository
    private lateinit var useCase: WifiScanUseCase

    private val emptyResult = WifiScanResult(
        accessPoints = emptyList(),
        channels = emptyList(),
        connectedNetwork = null,
        scanTimestampMs = 1_000L,
        isWifiEnabled = true
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = WifiScanUseCase(repository)
    }

    @Nested
    @DisplayName("isSupported")
    inner class IsSupportedTest {
        @Test
        fun `delegates to repository`() {
            every { repository.isSupported } returns true
            assertTrue(useCase.isSupported)
        }

        @Test
        fun `returns false when repository is not supported`() {
            every { repository.isSupported } returns false
            assertFalse(useCase.isSupported)
        }
    }

    @Nested
    @DisplayName("invoke")
    inner class InvokeTest {
        @Test
        fun `throws WifiNotSupportedException when not supported`() = runTest {
            every { repository.isSupported } returns false
            assertThrows(WifiNotSupportedException::class.java) {
                kotlinx.coroutines.runBlocking { useCase() }
            }
        }

        @Test
        fun `delegates to repository when supported`() = runTest {
            every { repository.isSupported } returns true
            coEvery { repository.scan() } returns emptyResult
            val result = useCase()
            assertEquals(emptyResult, result)
            coVerify(exactly = 1) { repository.scan() }
        }

        @Test
        fun `propagates repository exceptions`() = runTest {
            every { repository.isSupported } returns true
            coEvery { repository.scan() } throws SecurityException("Location permission denied")
            val thrown = assertThrows(SecurityException::class.java) {
                kotlinx.coroutines.runBlocking { useCase() }
            }
            assertEquals("Location permission denied", thrown.message)
        }

        @Test
        fun `result contains correct scan timestamp`() = runTest {
            every { repository.isSupported } returns true
            val timestamped = emptyResult.copy(scanTimestampMs = 99_000L)
            coEvery { repository.scan() } returns timestamped
            val result = useCase()
            assertEquals(99_000L, result.scanTimestampMs)
        }

        @Test
        fun `access points are passed through unchanged`() = runTest {
            every { repository.isSupported } returns true
            val resultWith3Aps = emptyResult.copy(
                accessPoints = listOf(
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true)
                )
            )
            coEvery { repository.scan() } returns resultWith3Aps
            val result = useCase()
            assertEquals(3, result.accessPoints.size)
        }
    }
}
