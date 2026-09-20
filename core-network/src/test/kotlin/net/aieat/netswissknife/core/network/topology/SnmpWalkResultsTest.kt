package net.aieat.netswissknife.core.network.topology

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString
import org.snmp4j.smi.VariableBinding
import org.snmp4j.util.TreeEvent

class SnmpWalkResultsTest {
    @Test
    fun `walk retains rows received before a TreeEvent error`() {
        val events = listOf(
            mockk<TreeEvent> {
                every { isError } returns false
                every { variableBindings } returns arrayOf(
                    VariableBinding(OID("1.3.6.1.2.1.2.2.1.2.1"), OctetString("eth0"))
                )
            },
            mockk<TreeEvent> {
                every { isError } returns true
                every { variableBindings } returns null
            }
        )

        assertEquals(
            mapOf("1.3.6.1.2.1.2.2.1.2.1" to "eth0"),
            collectWalkResults(events)
        )
    }
}
