package com.example.netswissknife.core.domain

import com.example.netswissknife.core.network.portscan.PortConnectChecker
import com.example.netswissknife.core.network.portscan.PortConnectResult
import com.example.netswissknife.core.network.portscan.PortScanRepository
import com.example.netswissknife.core.network.portscan.PortScanRepositoryImpl
import com.example.netswissknife.core.network.portscan.PortStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("PortScanUseCase")
class PortScanUseCaseTest {

    private val openChecker: PortConnectChecker = { _, _ ->
        PortConnectResult(PortStatus.OPEN, 5L, null)
    }

    private fun makeUseCase(checker: PortConnectChecker = openChecker): PortScanUseCase =
        PortScanUseCase(PortScanRepositoryImpl(checker = checker))

    @Nested
    @DisplayName("host validation")
    inner class HostValidation {

        @Test
        fun `blank host emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(PortScanParams(host = "   ")).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `empty host emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(PortScanParams(host = "")).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `invalid host emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(PortScanParams(host = "not a valid host!!")).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `valid hostname is accepted`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "example.com", preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `valid IPv4 is accepted`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "192.168.1.1", preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `host is trimmed before validation`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "  8.8.8.8  ", preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.none { it.isError })
        }
    }

    @Nested
    @DisplayName("timeout validation")
    inner class TimeoutValidation {

        @Test
        fun `timeout below 100 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", timeoutMs = 99)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `timeout above 30000 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", timeoutMs = 30001)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `timeout of 100 is valid`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", timeoutMs = 100, preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `timeout of 30000 is valid`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", timeoutMs = 30000, preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.none { it.isError })
        }
    }

    @Nested
    @DisplayName("concurrency validation")
    inner class ConcurrencyValidation {

        @Test
        fun `concurrency of 0 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", concurrency = 0)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `concurrency above 500 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", concurrency = 501)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }
    }

    @Nested
    @DisplayName("custom port range validation")
    inner class CustomRangeValidation {

        @Test
        fun `startPort of 0 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 0, endPort = 100)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `endPort above 65535 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 1, endPort = 65536)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `startPort greater than endPort emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 100, endPort = 80)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `range larger than 10000 emits error`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 1, endPort = 10001)
            ).toList()
            assertTrue(results.first() is PortScanFlowResult.ValidationError)
        }

        @Test
        fun `valid custom range of exactly 1 is accepted`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 80, endPort = 80)
            ).toList()
            assertTrue(results.none { it.isError })
        }

        @Test
        fun `valid custom range of 10000 is accepted`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.CUSTOM, startPort = 1, endPort = 10000)
            ).toList()
            assertTrue(results.none { it.isError })
        }
    }

    @Nested
    @DisplayName("preset scanning")
    inner class PresetScanning {

        @Test
        fun `web preset emits PortScanned for each web port`() = runTest {
            val portResults = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.WEB)
            )
                .filterIsInstance<PortScanFlowResult.PortScanned>()
                .toList()
            assertEquals(PortScanPreset.WEB.ports.size, portResults.size)
        }

        @Test
        fun `scan emits ScanComplete as last event`() = runTest {
            val results = makeUseCase().invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.WEB)
            ).toList()
            assertTrue(results.last() is PortScanFlowResult.ScanComplete)
        }

        @Test
        fun `summary open count is correct`() = runTest {
            val allOpen: PortConnectChecker = { _, _ ->
                PortConnectResult(PortStatus.OPEN, 5L, null)
            }
            val complete = makeUseCase(allOpen).invoke(
                PortScanParams(host = "8.8.8.8", preset = PortScanPreset.WEB)
            )
                .filterIsInstance<PortScanFlowResult.ScanComplete>()
                .toList()
                .first()
            assertEquals(PortScanPreset.WEB.ports.size, complete.summary.openPorts)
        }
    }
}
