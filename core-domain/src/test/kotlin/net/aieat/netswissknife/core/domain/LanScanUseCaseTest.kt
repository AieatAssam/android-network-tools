package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.lan.HostChecker
import net.aieat.netswissknife.core.network.lan.LanScanRepositoryImpl
import net.aieat.netswissknife.core.network.lan.PortChecker
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("LanScanUseCase")
class LanScanUseCaseTest {

    private val aliveChecker: HostChecker = { _, _ -> 5L }
    private val noPortChecker: PortChecker = { _, _, _ -> false }

    private fun makeUseCase(hostChecker: HostChecker = { _, _ -> null }): LanScanUseCase =
        LanScanUseCase(LanScanRepositoryImpl(
            hostChecker = hostChecker,
            arpTableReader = { "" },
            portChecker = noPortChecker
        ))

    @Nested
    @DisplayName("subnet validation")
    inner class SubnetValidation {

        @Test
        fun `blank subnet emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "  ")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `empty subnet emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `non-CIDR string emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "192.168.1.1")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `invalid CIDR emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "not-a-cidr/24")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `prefix below 16 emits ValidationError (too large)`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "10.0.0.0/15")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `prefix 31 emits ValidationError (too small)`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "192.168.1.0/31")).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `valid slash-24 subnet is accepted`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "192.168.1.0/24")).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }

        @Test
        fun `valid slash-16 subnet is accepted`() = runTest {
            // Use .first() to avoid collecting all 65 534 host results from a /16 scan.
            // If validation fails the first emission is ValidationError; otherwise validation passed.
            val first = makeUseCase().invoke(LanScanParams(subnet = "10.0.0.0/16")).first()
            assertTrue(first !is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `valid slash-30 subnet is accepted`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "192.168.1.0/30")).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }

        @Test
        fun `subnet is trimmed before validation`() = runTest {
            val results = makeUseCase().invoke(LanScanParams(subnet = "  192.168.1.0/24  ")).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }

        @Test
        fun `validation error message is not blank`() = runTest {
            val result = makeUseCase().invoke(LanScanParams(subnet = "")).toList().first()
            assertTrue(result is LanScanFlowResult.ValidationError)
            assertTrue((result as LanScanFlowResult.ValidationError).message.isNotBlank())
        }
    }

    @Nested
    @DisplayName("timeout validation")
    inner class TimeoutValidation {

        @Test
        fun `timeout below 100 emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", timeoutMs = 99)
            ).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `timeout above 10000 emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", timeoutMs = 10001)
            ).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `timeout of 100 is valid`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", timeoutMs = 100)
            ).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }

        @Test
        fun `timeout of 10000 is valid`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", timeoutMs = 10000)
            ).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }
    }

    @Nested
    @DisplayName("concurrency validation")
    inner class ConcurrencyValidation {

        @Test
        fun `concurrency of 0 emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", concurrency = 0)
            ).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `concurrency above 500 emits ValidationError`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", concurrency = 501)
            ).toList()
            assertTrue(results.first() is LanScanFlowResult.ValidationError)
        }

        @Test
        fun `concurrency of 1 is valid`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", concurrency = 1)
            ).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }

        @Test
        fun `concurrency of 500 is valid`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30", concurrency = 500)
            ).toList()
            assertTrue(results.none { it is LanScanFlowResult.ValidationError })
        }
    }

    @Nested
    @DisplayName("happy path")
    inner class HappyPath {

        @Test
        fun `scan emits ScanComplete as last event`() = runTest {
            val results = makeUseCase().invoke(
                LanScanParams(subnet = "192.168.1.0/30")
            ).toList()
            assertTrue(results.last() is LanScanFlowResult.ScanComplete)
        }

        @Test
        fun `alive host produces HostFound event`() = runTest {
            val useCase = makeUseCase(hostChecker = aliveChecker)
            val found = useCase.invoke(LanScanParams(subnet = "192.168.1.0/30"))
                .filterIsInstance<LanScanFlowResult.HostFound>()
                .toList()
            assertEquals(2, found.size) // /30 has 2 hosts, both alive
        }

        @Test
        fun `repository not called when validation fails`() = runTest {
            var called = false
            val useCase = LanScanUseCase(LanScanRepositoryImpl(
                hostChecker = { _, _ -> called = true; null },
                arpTableReader = { "" },
                portChecker = noPortChecker
            ))
            useCase.invoke(LanScanParams(subnet = "")).toList()
            assertTrue(!called)
        }
    }
}
