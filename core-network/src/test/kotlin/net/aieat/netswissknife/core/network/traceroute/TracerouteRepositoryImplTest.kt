package net.aieat.netswissknife.core.network.traceroute

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [GeoIpRepositoryImpl].
 *
 * The old binary-based [TracerouteRepositoryImpl] has been replaced by
 * [net.aieat.netswissknife.app.traceroute.IcmpEnginTracerouteRepositoryImpl] which lives in
 * the :app module and depends on the icmpenguin JNI library — not unit-testable here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@DisplayName("GeoIpRepositoryImpl – private IP detection")
class TracerouteRepositoryImplTest {

    private val geoRepo = GeoIpRepositoryImpl()

    @Nested
    @DisplayName("private / reserved addresses are skipped")
    inner class PrivateIpTest {

        @Test
        @DisplayName("lookup returns null for 192.168 range")
        fun lookupReturnsNullForRfc1918Class192() = runTest {
            assertNull(geoRepo.lookup("192.168.1.100"))
        }

        @Test
        @DisplayName("lookup returns null for 10.x range")
        fun lookupReturnsNullForRfc1918Class10() = runTest {
            assertNull(geoRepo.lookup("10.0.0.1"))
        }

        @Test
        @DisplayName("lookup returns null for 172.16 range")
        fun lookupReturnsNullForRfc1918Class172() = runTest {
            assertNull(geoRepo.lookup("172.16.5.5"))
        }

        @Test
        @DisplayName("lookup returns null for loopback")
        fun lookupReturnsNullForLoopback() = runTest {
            assertNull(geoRepo.lookup("127.0.0.1"))
        }

        @Test
        @DisplayName("lookup returns null for link-local")
        fun lookupReturnsNullForLinkLocal() = runTest {
            assertNull(geoRepo.lookup("169.254.1.1"))
        }
    }
}
