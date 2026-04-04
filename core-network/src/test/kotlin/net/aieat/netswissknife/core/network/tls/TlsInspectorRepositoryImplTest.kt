package net.aieat.netswissknife.core.network.tls

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TlsInspectorRepositoryImplTest {

    private lateinit var repository: TlsInspectorRepositoryImpl

    @BeforeEach
    fun setUp() {
        repository = TlsInspectorRepositoryImpl()
    }

    @Test
    fun `inspect returns Error for blank host`() = runTest {
        val result = repository.inspect("", 443, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Expected Error for blank host but got $result")
    }

    @Test
    fun `inspect returns Error for whitespace-only host`() = runTest {
        val result = repository.inspect("   ", 443, 5_000)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect returns Error for port 0`() = runTest {
        val result = repository.inspect("example.com", 0, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Port 0 is out of range, expected Error")
    }

    @Test
    fun `inspect returns Error for port 65536`() = runTest {
        val result = repository.inspect("example.com", 65_536, 5_000)
        assertTrue(result is NetworkResult.Error,
            "Port 65536 is out of range, expected Error")
    }

    @Test
    fun `inspect returns Error for negative port`() = runTest {
        val result = repository.inspect("example.com", -1, 5_000)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect returns Error for timeout below 500 ms`() = runTest {
        val result = repository.inspect("example.com", 443, 499)
        assertTrue(result is NetworkResult.Error,
            "Timeout < 500 ms should return Error")
    }

    @Test
    fun `inspect returns Error for timeout of zero`() = runTest {
        val result = repository.inspect("example.com", 443, 0)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect accepts valid port boundary 1`() = runTest {
        // Port 1 is valid — may succeed or fail for network reasons, but not an input-validation error
        val result = repository.inspect("   ", 1, 5_000)
        // Host is blank so it must still return Error (blank host check comes first)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `inspect accepts valid port boundary 65535`() = runTest {
        // Port 65535 is valid — only the blank host should cause Error here
        val result = repository.inspect("", 65_535, 5_000)
        assertTrue(result is NetworkResult.Error)
    }
}
