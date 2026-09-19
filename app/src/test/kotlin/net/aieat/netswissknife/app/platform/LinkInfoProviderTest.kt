package net.aieat.netswissknife.app.platform

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LinkInfoProviderTest {
    @Test
    fun `cidrOf normalises host address`() {
        assertEquals("192.168.1.0/24", LinkInfoMapper.cidrOf("192.168.1.37", 24))
    }

    @Test
    fun `prefix is constrained to supported scanner range`() {
        assertEquals("192.168.0.0/16", LinkInfoMapper.cidrOf("192.168.1.37", 8))
        assertEquals("192.168.1.36/30", LinkInfoMapper.cidrOf("192.168.1.37", 32))
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
