package net.aieat.netswissknife.app.platform

import android.net.NetworkCapabilities
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LinkInfoProviderTest {
    @Test
    fun `validated capability is required for an online precheck`() {
        val capabilities = mockk<NetworkCapabilities>()
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } returns true

        assertTrue(LinkInfoMapper.isValidatedNetwork(capabilities))
    }

    @Test
    fun `missing validated capability fails the online precheck`() {
        val capabilities = mockk<NetworkCapabilities>()
        every {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } returns false

        assertFalse(LinkInfoMapper.isValidatedNetwork(capabilities))
        assertFalse(LinkInfoMapper.isValidatedNetwork(null))
    }

    @Test
    fun `network availability can be injected without Android connectivity`() {
        assertTrue(LinkInfoProvider { true }.hasValidatedNetwork())
        assertFalse(LinkInfoProvider { false }.hasValidatedNetwork())
    }

    @Test
    fun `network availability override is fail safe`() {
        assertFalse(LinkInfoProvider { error("connectivity unavailable") }.hasValidatedNetwork())
    }

    @Test
    fun `cidrOf normalises host address`() {
        assertEquals("192.168.1.0/24", LinkInfoMapper.cidrOf("192.168.1.37", 24))
    }

    @Test
    fun `CIDR mapping preserves platform prefixes`() {
        assertEquals("0.0.0.0/0", LinkInfoMapper.cidrOf("192.168.1.37", 0))
        assertEquals("192.0.0.0/8", LinkInfoMapper.cidrOf("192.168.1.37", 8))
        assertEquals("192.168.0.0/16", LinkInfoMapper.cidrOf("192.168.1.37", 16))
        assertEquals("192.168.1.36/30", LinkInfoMapper.cidrOf("192.168.1.37", 30))
        assertEquals("192.168.1.36/31", LinkInfoMapper.cidrOf("192.168.1.37", 31))
        assertEquals("192.168.1.37/32", LinkInfoMapper.cidrOf("192.168.1.37", 32))
    }

    @Test
    fun `CIDR mapping rejects invalid prefixes`() {
        assertNull(LinkInfoMapper.cidrOf("192.168.1.37", -1))
        assertNull(LinkInfoMapper.cidrOf("192.168.1.37", 33))
    }

    @Test
    fun `defaultGateway chooses ipv4 default route`() {
        assertEquals(
            "192.168.1.254",
            LinkInfoMapper.defaultGateway(
                listOf("192.168.1.0/24" to "0.0.0.0", "0.0.0.0/0" to "192.168.1.254"),
            ),
        )
        assertNull(LinkInfoMapper.defaultGateway(listOf("0.0.0.0/0" to "::")))
    }
}
