package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubnetCalculatorRepositoryTest {

    private val repo = SubnetCalculatorRepositoryImpl()

    // ── CIDR notation ──────────────────────────────────────────────────────────

    @Test
    fun `calculate returns correct network for standard class-C CIDR`() {
        val result = repo.calculate("192.168.1.100/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("192.168.1.0", info.networkAddress)
        assertEquals("192.168.1.255", info.broadcastAddress)
        assertEquals("192.168.1.1", info.firstHost)
        assertEquals("192.168.1.254", info.lastHost)
        assertEquals(24, info.prefixLength)
        assertEquals(256L, info.totalHosts)
        assertEquals(254L, info.usableHosts)
        assertEquals("255.255.255.0", info.subnetMask)
        assertEquals("0.0.0.255", info.wildcardMask)
    }

    @Test
    fun `calculate returns correct info for slash 16`() {
        val result = repo.calculate("10.0.0.1/16")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("10.0.0.0", info.networkAddress)
        assertEquals("10.0.255.255", info.broadcastAddress)
        assertEquals(16, info.prefixLength)
        assertEquals(65536L, info.totalHosts)
        assertEquals(65534L, info.usableHosts)
        assertEquals("255.255.0.0", info.subnetMask)
        assertEquals("0.0.255.255", info.wildcardMask)
    }

    @Test
    fun `calculate returns correct info for slash 8`() {
        val result = repo.calculate("10.50.100.200/8")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("10.0.0.0", info.networkAddress)
        assertEquals("10.255.255.255", info.broadcastAddress)
        assertEquals(8, info.prefixLength)
        assertEquals(16777216L, info.totalHosts)
        assertEquals(16777214L, info.usableHosts)
    }

    @Test
    fun `calculate accepts dot-decimal mask notation`() {
        val result = repo.calculate("192.168.1.0/255.255.255.0")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals(24, info.prefixLength)
        assertEquals("192.168.1.0", info.networkAddress)
    }

    @Test
    fun `calculate accepts space-separated mask notation`() {
        val result = repo.calculate("192.168.1.0 255.255.255.0")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals(24, info.prefixLength)
        assertEquals("192.168.1.0", info.networkAddress)
    }

    @Test
    fun `calculate accepts bare IP and defaults to slash 32`() {
        val result = repo.calculate("192.168.1.1")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals(32, info.prefixLength)
        assertEquals("192.168.1.1", info.networkAddress)
        assertEquals("192.168.1.1", info.broadcastAddress)
        assertEquals(1L, info.totalHosts)
        assertEquals(1L, info.usableHosts)
    }

    @Test
    fun `calculate returns error for invalid input`() {
        val result = repo.calculate("not-an-ip")
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `calculate returns error for out-of-range IP`() {
        val result = repo.calculate("256.0.0.1/24")
        assertTrue(result is NetworkResult.Error)
    }

    @Test
    fun `calculate returns error for prefix out of range`() {
        val result = repo.calculate("192.168.1.0/33")
        assertTrue(result is NetworkResult.Error)
    }

    // ── Binary representation ──────────────────────────────────────────────────

    @Test
    fun `calculate produces correct binary mask for slash 24`() {
        val result = repo.calculate("192.168.1.0/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("11111111.11111111.11111111.00000000", info.binaryMask)
    }

    @Test
    fun `calculate produces correct binary network address for slash 24`() {
        val result = repo.calculate("192.168.1.0/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("11000000.10101000.00000001.00000000", info.binaryNetworkAddress)
    }

    // ── IP properties ──────────────────────────────────────────────────────────

    @Test
    fun `calculate identifies private class-C address`() {
        val result = repo.calculate("192.168.1.0/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertTrue(info.isPrivate)
        assertEquals("C", info.ipClass)
    }

    @Test
    fun `calculate identifies private class-A address`() {
        val result = repo.calculate("10.0.0.0/8")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertTrue(info.isPrivate)
        assertEquals("A", info.ipClass)
    }

    @Test
    fun `calculate identifies private class-B address`() {
        val result = repo.calculate("172.16.0.0/12")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertTrue(info.isPrivate)
        assertEquals("B", info.ipClass)
    }

    // ── Edge cases ─────────────────────────────────────────────────────────────

    @Test
    fun `calculate handles slash 31 correctly`() {
        val result = repo.calculate("192.168.1.0/31")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals(2L, info.totalHosts)
        assertEquals(0L, info.usableHosts)
    }

    @Test
    fun `calculate handles slash 0 correctly`() {
        val result = repo.calculate("0.0.0.0/0")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("0.0.0.0", info.networkAddress)
        assertEquals("255.255.255.255", info.broadcastAddress)
        assertEquals(0, info.prefixLength)
    }

    @Test
    fun `calculate populates CIDR notation field`() {
        val result = repo.calculate("192.168.1.100/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("192.168.1.0/24", info.cidrNotation)
    }

    @Test
    fun `calculate populates hex mask field for slash 24`() {
        val result = repo.calculate("192.168.1.0/24")
        assertTrue(result is NetworkResult.Success)
        val info = (result as NetworkResult.Success).data
        assertEquals("0xFFFFFF00", info.hexMask)
    }
}
