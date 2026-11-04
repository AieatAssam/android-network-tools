package net.aieat.netswissknife.core.network.subnet

import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.NetworkResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SubnetInputAndOperationsTest {
    private val repo = SubnetCalculatorRepositoryImpl()

    private fun success(result: NetworkResult<SubnetInfo>): SubnetInfo {
        assertTrue(result is NetworkResult.Success, "expected success but was $result")
        return (result as NetworkResult.Success).data
    }

    private fun errorCode(result: NetworkResult<*>): ErrorCode {
        assertTrue(result is NetworkResult.Error, "expected error but was $result")
        return (result as NetworkResult.Error).info!!.code
    }

    // ── SUB-01 /31 and /32 ─────────────────────────────────────────────────────

    @Test
    fun `slash 32 is a single usable host whose first and last equal the address`() {
        val info = success(repo.calculate("10.0.0.5/32"))
        assertEquals(1L, info.usableHosts)
        assertEquals("10.0.0.5", info.firstHost)
        assertEquals("10.0.0.5", info.lastHost)
    }

    @Test
    fun `slash 31 reports two usable hosts consistent with first and last`() {
        val info = success(repo.calculate("10.0.0.0/31"))
        assertEquals(2L, info.usableHosts)
        assertEquals("10.0.0.0", info.firstHost)
        assertEquals("10.0.0.1", info.lastHost)
    }

    @Test
    fun `range that narrows to slash 31 reports two usable hosts`() {
        val info = success(repo.calculateRange("10.0.0.0", "10.0.0.1"))
        assertEquals(31, info.prefixLength)
        assertEquals(2L, info.usableHosts)
    }

    // ── SUB-14 leading zeros ───────────────────────────────────────────────────

    @Test
    fun `leading zero in prefix is rejected with its own code`() {
        assertEquals(ErrorCode.SUBNET_LEADING_ZERO, errorCode(repo.calculate("1.2.3.4/08")))
    }

    @Test
    fun `leading zero in an octet is rejected`() {
        assertEquals(ErrorCode.SUBNET_LEADING_ZERO, errorCode(repo.calculate("010.1.1.1/8")))
    }

    @Test
    fun `single zero prefix is accepted`() {
        assertEquals(0, success(repo.calculate("0.0.0.0/0")).prefixLength)
    }

    // ── SUB-11 missing prefix ──────────────────────────────────────────────────

    @Test
    fun `empty prefix after slash has a dedicated code`() {
        assertEquals(ErrorCode.SUBNET_PREFIX_MISSING, errorCode(repo.calculate("192.168.1.0/")))
    }

    @Test
    fun `out of range prefix is a prefix error`() {
        assertEquals(ErrorCode.SUBNET_PREFIX_INVALID, errorCode(repo.calculate("192.168.1.0/33")))
    }

    @Test
    fun `non numeric prefix is a prefix error`() {
        assertEquals(ErrorCode.SUBNET_PREFIX_INVALID, errorCode(repo.calculate("192.168.1.0/+8")))
    }

    @Test
    fun `octet above 255 is an address error with the bad address`() {
        val error = repo.calculate("256.0.0.1/24") as NetworkResult.Error
        assertEquals(ErrorCode.SUBNET_ADDRESS_INVALID, error.info?.code)
        assertEquals(listOf("256.0.0.1"), error.info?.args)
    }

    @Test
    fun `ipv6 input is reported as unsupported`() {
        assertEquals(ErrorCode.SUBNET_IPV6_UNSUPPORTED, errorCode(repo.calculate("2001:db8::/32")))
    }

    @Test
    fun `non contiguous dotted mask has its own code`() {
        assertEquals(ErrorCode.SUBNET_MASK_NON_CONTIGUOUS, errorCode(repo.calculate("10.0.0.0/255.0.255.0")))
    }

    // ── SUB-08 scope (whole network range, RFC 6890 / 6598 / 3927 etc.) ───────

    @Test
    fun `cgnat 100 64 slash 10 is shared not public`() {
        val info = success(repo.calculate("100.64.0.0/10"))
        assertEquals(Ipv4Scope.SHARED, info.scope)
        assertFalse(info.isPrivate)
    }

    @Test
    fun `default route is mixed rather than public`() {
        assertEquals(Ipv4Scope.MIXED, success(repo.calculate("0.0.0.0/0")).scope)
    }

    @Test
    fun `subnet that spans private and public space is mixed`() {
        assertEquals(Ipv4Scope.MIXED, success(repo.calculate("10.0.0.0/7")).scope)
    }

    @Test
    fun `rfc1918 blocks are private`() {
        assertEquals(Ipv4Scope.PRIVATE, success(repo.calculate("10.0.0.0/8")).scope)
        assertEquals(Ipv4Scope.PRIVATE, success(repo.calculate("172.16.0.0/12")).scope)
        val lan = success(repo.calculate("192.168.1.0/24"))
        assertEquals(Ipv4Scope.PRIVATE, lan.scope)
        assertTrue(lan.isPrivate)
    }

    @Test
    fun `special purpose blocks have their own scope`() {
        assertEquals(Ipv4Scope.LOOPBACK, success(repo.calculate("127.0.0.1/32")).scope)
        assertEquals(Ipv4Scope.LINK_LOCAL, success(repo.calculate("169.254.1.0/24")).scope)
        assertEquals(Ipv4Scope.THIS_NETWORK, success(repo.calculate("0.0.0.0/8")).scope)
        assertEquals(Ipv4Scope.DOCUMENTATION, success(repo.calculate("192.0.2.0/24")).scope)
        assertEquals(Ipv4Scope.BENCHMARK, success(repo.calculate("198.18.0.0/15")).scope)
        assertEquals(Ipv4Scope.MULTICAST, success(repo.calculate("224.0.0.0/4")).scope)
        assertEquals(Ipv4Scope.RESERVED, success(repo.calculate("240.0.0.0/4")).scope)
    }

    @Test
    fun `ordinary internet space is public`() {
        assertEquals(Ipv4Scope.PUBLIC, success(repo.calculate("8.8.8.0/24")).scope)
    }

    // ── Membership ("Is IP in subnet?") ────────────────────────────────────────

    @Test
    fun `address inside a slash 24 is a usable host`() {
        val m = success2(repo.checkMembership("192.168.1.50", "192.168.1.0/24"))
        assertTrue(m.inSubnet)
        assertEquals(SubnetAddressRole.USABLE_HOST, m.role)
    }

    @Test
    fun `network and broadcast addresses are inside but not usable`() {
        val network = success2(repo.checkMembership("192.168.1.0", "192.168.1.0/24"))
        assertEquals(SubnetAddressRole.NETWORK_ADDRESS, network.role)
        val broadcast = success2(repo.checkMembership("192.168.1.255", "192.168.1.0/24"))
        assertEquals(SubnetAddressRole.BROADCAST_ADDRESS, broadcast.role)
    }

    @Test
    fun `address outside the subnet is reported with the subnet range`() {
        val m = success2(repo.checkMembership("192.168.2.1", "192.168.1.0/24"))
        assertFalse(m.inSubnet)
        assertEquals(SubnetAddressRole.OUTSIDE, m.role)
        assertEquals("192.168.1.0", m.networkAddress)
        assertEquals("192.168.1.255", m.broadcastAddress)
    }

    @Test
    fun `slash 31 and slash 32 have no reserved addresses`() {
        assertEquals(SubnetAddressRole.USABLE_HOST, success2(repo.checkMembership("10.0.0.0", "10.0.0.0/31")).role)
        assertEquals(SubnetAddressRole.USABLE_HOST, success2(repo.checkMembership("10.0.0.1", "10.0.0.0/31")).role)
        assertEquals(SubnetAddressRole.USABLE_HOST, success2(repo.checkMembership("10.0.0.5", "10.0.0.5/32")).role)
    }

    @Test
    fun `invalid membership address is an address error`() {
        assertEquals(ErrorCode.SUBNET_ADDRESS_INVALID, errorCode(repo.checkMembership("999.1.1.1", "10.0.0.0/8")))
    }

    // ── Split (equal subdivision) ──────────────────────────────────────────────

    @Test
    fun `split a slash 24 into slash 26 lists four networks`() {
        val split = success2(repo.splitIntoPrefix("10.0.0.0/24", 26))
        assertEquals(4L, split.subnetCount)
        assertEquals(
            listOf("10.0.0.0/26", "10.0.0.64/26", "10.0.0.128/26", "10.0.0.192/26"),
            split.rows.map { it.cidr },
        )
        assertEquals("10.0.0.192", split.rows.last().firstAddress)
        assertEquals("10.0.0.255", split.rows.last().lastAddress)
        assertFalse(split.truncated)
    }

    @Test
    fun `split count rounds up to a power of two and reports the request`() {
        val split = success2(repo.splitIntoCount("10.0.0.0/24", 3))
        assertEquals(3L, split.requestedCount)
        assertEquals(4L, split.subnetCount)
        assertEquals(26, split.newPrefix)
    }

    @Test
    fun `split count of one keeps the same network`() {
        val split = success2(repo.splitIntoCount("10.0.0.0/24", 1))
        assertEquals(24, split.newPrefix)
        assertEquals(listOf("10.0.0.0/24"), split.rows.map { it.cidr })
    }

    @Test
    fun `split into thirty two gives 256 rows and reports truncation`() {
        val split = success2(repo.splitIntoPrefix("10.0.0.0/8", 32))
        assertEquals(16_777_216L, split.subnetCount)
        assertEquals(256, split.rows.size)
        assertEquals("10.0.0.0/32", split.rows.first().cidr)
        assertEquals("10.0.0.255/32", split.rows.last().cidr)
        assertTrue(split.truncated)
    }

    @Test
    fun `split prefix must be longer than the current one`() {
        assertEquals(ErrorCode.SUBNET_SPLIT_PREFIX_INVALID, errorCode(repo.splitIntoPrefix("10.0.0.0/24", 24)))
        assertEquals(ErrorCode.SUBNET_SPLIT_PREFIX_INVALID, errorCode(repo.splitIntoPrefix("10.0.0.0/24", 33)))
    }

    @Test
    fun `split count larger than the block is rejected`() {
        assertEquals(ErrorCode.SUBNET_SPLIT_COUNT_INVALID, errorCode(repo.splitIntoCount("10.0.0.0/24", 257)))
        assertEquals(ErrorCode.SUBNET_SPLIT_COUNT_INVALID, errorCode(repo.splitIntoCount("10.0.0.0/24", 0)))
    }

    private fun <T> success2(result: NetworkResult<T>): T {
        assertTrue(result is NetworkResult.Success, "expected success but was $result")
        return (result as NetworkResult.Success).data
    }
}
