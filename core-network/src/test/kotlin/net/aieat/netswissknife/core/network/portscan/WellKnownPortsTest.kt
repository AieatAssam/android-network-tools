package net.aieat.netswissknife.core.network.portscan

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("WellKnownPorts")
class WellKnownPortsTest {

    @Nested
    @DisplayName("getInfo")
    inner class GetInfo {

        @ParameterizedTest
        @CsvSource(
            "22, SSH",
            "80, HTTP",
            "443, HTTPS",
            "3306, MySQL"
        )
        @DisplayName("resolves registered ports to their service name")
        fun resolvesKnownPorts(port: Int, serviceName: String) {
            assertEquals(serviceName, WellKnownPorts.getInfo(port)?.serviceName)
        }

        @Test
        @DisplayName("defaults the protocol to TCP")
        fun defaultsToTcp() {
            assertEquals("TCP", WellKnownPorts.getInfo(22)?.protocol)
        }

        @Test
        @DisplayName("marks datagram services as UDP")
        fun udpServices() {
            assertEquals("UDP", WellKnownPorts.getInfo(161)?.protocol)
            assertEquals("UDP", WellKnownPorts.getInfo(123)?.protocol)
        }

        @Test
        @DisplayName("gives every registered port a non-blank description")
        fun descriptionsPresent() {
            (0..65535).mapNotNull { WellKnownPorts.getInfo(it) }.forEach {
                assertTrue(it.serviceName.isNotBlank())
                assertTrue(it.description.isNotBlank())
                assertTrue(it.protocol.isNotBlank())
            }
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 7777, 65535, -1])
        @DisplayName("returns null for unregistered ports")
        fun unknownPorts(port: Int) {
            assertNull(WellKnownPorts.getInfo(port))
        }
    }

    @Nested
    @DisplayName("getServiceName")
    inner class GetServiceName {

        @Test
        @DisplayName("prefers the registered name over the range label")
        fun prefersRegisteredName() {
            assertEquals("SSH", WellKnownPorts.getServiceName(22))
        }

        @ParameterizedTest
        @ValueSource(ints = [1, 1023])
        @DisplayName("labels unregistered system ports as Well-known")
        fun wellKnownRange(port: Int) {
            assertEquals("Well-known", WellKnownPorts.getServiceName(port))
        }

        @ParameterizedTest
        @ValueSource(ints = [1024, 49151])
        @DisplayName("labels unregistered user ports as Registered")
        fun registeredRange(port: Int) {
            assertEquals("Registered", WellKnownPorts.getServiceName(port))
        }

        @ParameterizedTest
        @ValueSource(ints = [0, 49152, 65535])
        @DisplayName("labels everything else as Dynamic/Private")
        fun dynamicRange(port: Int) {
            assertEquals("Dynamic/Private", WellKnownPorts.getServiceName(port))
        }

        @Test
        @DisplayName("never returns a blank label for any valid port")
        fun neverBlank() {
            (0..65535).forEach {
                assertTrue(WellKnownPorts.getServiceName(it).isNotBlank(), "blank label for port $it")
            }
        }
    }

    @Nested
    @DisplayName("presets")
    inner class Presets {

        private val presets: Map<String, List<Int>> = mapOf(
            "COMMON_PORTS" to WellKnownPorts.COMMON_PORTS,
            "WEB_PORTS" to WellKnownPorts.WEB_PORTS,
            "DATABASE_PORTS" to WellKnownPorts.DATABASE_PORTS,
            "MAIL_PORTS" to WellKnownPorts.MAIL_PORTS,
            "REMOTE_ACCESS_PORTS" to WellKnownPorts.REMOTE_ACCESS_PORTS
        )

        @Test
        @DisplayName("hold only valid, non-duplicated port numbers")
        fun validAndUnique() {
            presets.forEach { (name, ports) ->
                assertTrue(ports.isNotEmpty(), "$name is empty")
                assertTrue(ports.all { it in 1..65535 }, "$name has an out-of-range port")
                assertEquals(ports.size, ports.toSet().size, "$name has duplicates")
            }
        }

        @Test
        @DisplayName("cover the services each preset advertises")
        fun coverExpectedServices() {
            assertTrue(WellKnownPorts.COMMON_PORTS.containsAll(listOf(22, 80, 443)))
            assertTrue(WellKnownPorts.WEB_PORTS.containsAll(listOf(80, 443, 8080)))
            assertTrue(WellKnownPorts.DATABASE_PORTS.containsAll(listOf(3306, 5432, 27017)))
            assertTrue(WellKnownPorts.MAIL_PORTS.containsAll(listOf(25, 143, 993)))
            assertTrue(WellKnownPorts.REMOTE_ACCESS_PORTS.containsAll(listOf(22, 3389, 5900)))
        }
    }

    @Nested
    @DisplayName("PortInfo")
    inner class PortInfoValue {

        @Test
        @DisplayName("compares by value")
        fun valueSemantics() {
            val a = PortInfo("SSH", "Secure Shell")
            assertEquals(a, PortInfo("SSH", "Secure Shell", "TCP"))
            assertEquals(a.hashCode(), PortInfo("SSH", "Secure Shell").hashCode())
            assertTrue(a.toString().contains("SSH"))
            assertEquals("UDP", a.copy(protocol = "UDP").protocol)
            assertEquals("SSH", a.component1())
            assertEquals("Secure Shell", a.component2())
            assertEquals("TCP", a.component3())
            assertNotNull(a)
        }
    }
}
