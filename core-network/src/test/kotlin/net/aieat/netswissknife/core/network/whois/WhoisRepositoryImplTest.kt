package net.aieat.netswissknife.core.network.whois

import kotlinx.coroutines.test.runTest
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("WhoisRepositoryImpl – validation")
class WhoisRepositoryImplTest {

    private val repo = WhoisRepositoryImpl()

    @Test
    @DisplayName("lookup returns Error for blank query")
    fun `lookup returns Error for blank query`() = runTest {
        val result = repo.lookup("   ", 5_000)
        assertTrue(result is NetworkResult.Error)
        val error = result as NetworkResult.Error
        assertTrue(error.message.contains("blank", ignoreCase = true))
    }

    @Test
    @DisplayName("lookup returns Error for timeout below 500 ms")
    fun `lookup returns Error for timeout below 500 ms`() = runTest {
        val result = repo.lookup("example.com", 499)
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    @DisplayName("lookup returns Error for timeout above 30000 ms")
    fun `lookup returns Error for timeout above 30000 ms`() = runTest {
        val result = repo.lookup("example.com", 30_001)
        assertTrue(result is NetworkResult.Error)
    }
}
