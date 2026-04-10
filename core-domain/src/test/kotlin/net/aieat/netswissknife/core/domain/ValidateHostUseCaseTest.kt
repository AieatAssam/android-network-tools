package net.aieat.netswissknife.core.domain

import net.aieat.netswissknife.core.network.NetworkResult
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ValidateHostUseCaseTest {

    private val useCase = ValidateHostUseCase()

    @Test
    fun `valid hostname returns Success`() = runTest {
        val result = useCase("google.com")
        assertTrue(result is NetworkResult.Success)
        assertEquals("google.com", (result as NetworkResult.Success).data)
    }

    @Test
    fun `valid IP returns Success`() = runTest {
        val result = useCase("8.8.8.8")
        assertTrue(result is NetworkResult.Success)
    }

    @Test
    fun `blank host returns Error`() = runTest {
        val result = useCase("")
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `invalid host returns Error with descriptive message`() = runTest {
        val result = useCase("not a host!!")
        assertTrue(result is NetworkResult.Error)
        assertTrue((result as NetworkResult.Error).message.contains("not a host!!"))
    }
}
