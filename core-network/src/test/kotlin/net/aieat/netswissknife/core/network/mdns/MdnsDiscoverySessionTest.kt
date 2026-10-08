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

    @Test
    fun `adversarial PTR burst stays within type instance and query budgets`() {
        val limits = MdnsDiscoveryLimits(
            maxServiceTypes = 2,
            maxInstances = 3,
            maxAddressesPerService = 1,
            maxTxtBytesPerService = 8,
            maxQueries = 5,
        )
        val session = MdnsDiscoverySession(limits)
        val records = buildList {
            repeat(1_000) { index ->
                add(MdnsSessionRecord.Ptr("_services._dns-sd._udp.local.", "_svc$index._tcp.local."))
                add(MdnsSessionRecord.Ptr("_svc$index._tcp.local.", "Instance$index._svc$index._tcp.local."))
            }
        }

        val result = session.process(records)

        assertEquals(2, session.serviceTypes.size)
        assertEquals(3, session.instanceCount)
        assertTrue(result.queries.size <= limits.maxQueries)
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.SERVICE_TYPE_LIMIT))
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.INSTANCE_LIMIT))
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.QUERY_LIMIT))
    }

    @Test
    fun `repeated SRV TXT and address records do not amplify queries or retained bytes`() {
        val limits = MdnsDiscoveryLimits(
            maxServiceTypes = 2,
            maxInstances = 2,
            maxAddressesPerService = 2,
            maxTxtBytesPerService = 8,
            maxQueries = 3,
        )
        val session = MdnsDiscoverySession(limits)
        val instance = "Printer._http._tcp.local."
        session.process(listOf(MdnsSessionRecord.Ptr("_http._tcp.local.", instance)))

        val result = session.process(buildList {
            repeat(100) {
                add(MdnsSessionRecord.Srv(instance, "printer.local.", 8080))
                add(MdnsSessionRecord.Txt(instance, listOf("a=1234", "b=5678", "c=long-value")))
                add(MdnsSessionRecord.Address("printer.local.", "192.0.2.$it"))
            }
        })

        assertTrue(result.queries.size <= limits.maxQueries)
        assertTrue(session.retainedAddressCount <= limits.maxAddressesPerService)
        assertTrue(session.retainedAddressBytes <= limits.maxAddressBytesPerService)
        assertTrue(session.retainedTxtBytes <= limits.maxTxtBytesPerService)
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.ADDRESS_LIMIT))
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.TXT_BYTES_LIMIT))
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.QUERY_LIMIT))
    }

    @Test
    fun `oversized textual address is dropped before retention`() {
        val session = MdnsDiscoverySession(MdnsDiscoveryLimits(maxAddressBytesPerService = 64))
        val instance = "Printer._http._tcp.local."
        session.process(listOf(
            MdnsSessionRecord.Ptr("_http._tcp.local.", instance),
            MdnsSessionRecord.Srv(instance, "printer.local.", 8080),
        ))

        session.process(listOf(MdnsSessionRecord.Address("printer.local.", "x".repeat(65))))

        assertEquals(0, session.retainedAddressCount)
        assertEquals(0, session.retainedAddressBytes)
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.ADDRESS_LIMIT))
    }

    @Test
    fun `valid IPv6 address exactly at byte ceiling is retained`() {
        val ipv6Address = java.net.InetAddress.getByName("2001:db8::1").hostAddress
        val addressBytes = ipv6Address.toByteArray(Charsets.UTF_8).size
        val session = MdnsDiscoverySession(
            MdnsDiscoveryLimits(maxAddressesPerService = 1, maxAddressBytesPerService = addressBytes),
        )
        val instance = "Printer._http._tcp.local."
        session.process(listOf(
            MdnsSessionRecord.Ptr("_http._tcp.local.", instance),
            MdnsSessionRecord.Srv(instance, "printer.local.", 8080),
        ))

        session.process(listOf(MdnsSessionRecord.Address("printer.local.", ipv6Address)))

        assertEquals(1, session.retainedAddressCount)
        assertEquals(addressBytes, session.retainedAddressBytes)
        assertTrue(session.truncationReasons.isEmpty())
    }

    @Test
    fun `duplicate TXT keys remain last wins while oversized entries are truncated`() {
        val session = MdnsDiscoverySession(MdnsDiscoveryLimits(maxTxtBytesPerService = 5))
        val instance = "Printer._http._tcp.local."
        session.process(listOf(
            MdnsSessionRecord.Ptr("_http._tcp.local.", instance),
            MdnsSessionRecord.Srv(instance, "printer.local.", 8080),
        ))

        val result = session.process(listOf(
            MdnsSessionRecord.Txt(instance, listOf("k=one", "k=two", "oversized=value")),
        ))

        assertEquals("two", result.services.single().txtRecords["k"])
        assertEquals(5, session.retainedTxtBytes, "the exact UTF-8 size of k=two includes its delimiter")
        assertTrue(session.truncationReasons.contains(MdnsTruncationReason.TXT_BYTES_LIMIT))
    }

    @Test
    fun `duplicate PTR and SRV packets schedule each follow up only once`() {
        val session = MdnsDiscoverySession()
        val instance = "Printer._http._tcp.local."

        repeat(500) {
            session.process(listOf(MdnsSessionRecord.Ptr("_http._tcp.local.", instance)))
            session.process(listOf(MdnsSessionRecord.Srv(instance, "printer.local.", 8080)))
        }

        assertEquals(1, session.instanceCount)
        assertTrue(session.scheduledQueryCount <= 4)
    }
}
