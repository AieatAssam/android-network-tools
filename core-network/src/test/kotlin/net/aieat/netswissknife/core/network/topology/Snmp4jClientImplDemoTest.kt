package net.aieat.netswissknife.core.network.topology

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfSystemProperty

/**
 * Opt-in live interoperability check. Ordinary unit/CI runs remain offline;
 * run with -Dnetswissknife.snmp.demo=true when public network access is
 * intentionally available.
 */
class Snmp4jClientImplDemoTest {

    @Test
    @EnabledIfSystemProperty(named = "netswissknife.snmp.demo", matches = "true")
    fun `standard v2c GET works against the public pysnmp demo`() = runTest {
        val params = TopologyParams(
            targetIp = "demo.pysnmp.com",
            snmpVersion = SnmpVersion.V2C,
            communityString = "public",
            timeoutMs = 2_000,
            retries = 0
        )

        Snmp4jClientImpl(params).use { client ->
            val value = client.get(
                SnmpTarget(params.targetIp, 161, params),
                "1.3.6.1.2.1.1.1.0"
            )
            assertFalse(value.isNullOrBlank(), "demo.pysnmp.com returned no sysDescr")
        }
    }
}
