package net.aieat.netswissknife.core.network.mdns

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MdnsDiscoverySessionTest {
    @Test
    fun `meta PTR registers type and requests its instances`() {
        val session = MdnsDiscoverySession()

        val result = session.process(listOf(
            MdnsSessionRecord.Ptr(
                owner = "_services._dns-sd._udp.local.",
                target = "_http._tcp.local."
            )
        ))

        assertEquals(setOf("_http._tcp"), session.serviceTypes)
        assertEquals(listOf(MdnsSessionQuery("_http._tcp.local.", MdnsQueryType.PTR)), result.queries)
        assertTrue(result.services.isEmpty())
    }

    @Test
    fun `instance PTR requests SRV and TXT`() {
        val session = MdnsDiscoverySession()

        val result = session.process(listOf(
            MdnsSessionRecord.Ptr(
                owner = "_http._tcp.local.",
                target = "Printer._http._tcp.local."
            )
        ))

        assertEquals(
            listOf(
                MdnsSessionQuery("Printer._http._tcp.local.", MdnsQueryType.SRV),
                MdnsSessionQuery("Printer._http._tcp.local.", MdnsQueryType.TXT)
            ),
            result.queries
        )
        assertTrue(result.services.isEmpty())
    }

    @Test
    fun `SRV and address records in one packet emit one complete service`() {
        val session = MdnsDiscoverySession()
        session.process(listOf(
            MdnsSessionRecord.Ptr("_http._tcp.local.", "Printer._http._tcp.local.")
        ))

        val result = session.process(listOf(
            MdnsSessionRecord.Srv("Printer._http._tcp.local.", "printer.local.", 8080),
            MdnsSessionRecord.Address("printer.local.", "192.168.1.24"),
            MdnsSessionRecord.Address("printer.local.", "192.168.1.24")
        ))

        assertEquals(listOf(MdnsQueryType.A, MdnsQueryType.AAAA), result.queries.map { it.type })
        assertEquals(1, result.services.size)
        assertEquals("printer.local", result.services.single().hostname)
        assertEquals(8080, result.services.single().port)
        assertEquals(listOf("192.168.1.24"), result.services.single().ipAddresses)
        assertTrue(session.finish().isEmpty())
        assertEquals(1, session.totalFound)
    }

    @Test
    fun `finish emits a partial service with a hostname once`() {
        val session = MdnsDiscoverySession()
        session.process(listOf(
            MdnsSessionRecord.Ptr("_http._tcp.local.", "Printer._http._tcp.local."),
            MdnsSessionRecord.Srv("Printer._http._tcp.local.", "printer.local.", 0)
        ))

        val services = session.finish()

        assertEquals(1, services.size)
        assertEquals("printer.local", services.single().hostname)
        assertEquals(0, services.single().port)
        assertTrue(session.finish().isEmpty())
        assertEquals(1, session.totalFound)
    }
}
