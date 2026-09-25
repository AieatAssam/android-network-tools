package net.aieat.netswissknife.app.platform

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LiteralDestinationClassifierTest {
    @Test
    fun `classifies local and public IPv4 and IPv6 literals without resolving names`() {
        val cases = listOf(
            "192.168.1.1" to OperationTargetClass.LOCAL_ADDRESS,
            "10.20.30.40" to OperationTargetClass.LOCAL_ADDRESS,
            "172.16.0.1" to OperationTargetClass.LOCAL_ADDRESS,
            "172.31.255.254" to OperationTargetClass.LOCAL_ADDRESS,
            "127.0.0.1" to OperationTargetClass.LOCAL_ADDRESS,
            "169.254.10.20" to OperationTargetClass.LOCAL_ADDRESS,
            "8.8.8.8" to OperationTargetClass.INTERNET_ADDRESS,
            "192.0.0.9" to OperationTargetClass.INTERNET_ADDRESS,
            "::1" to OperationTargetClass.LOCAL_ADDRESS,
            "::" to OperationTargetClass.UNKNOWN,
            "fe80::1" to OperationTargetClass.LOCAL_ADDRESS,
            "fd00::1" to OperationTargetClass.LOCAL_ADDRESS,
            "[fe80::1%wlan0]" to OperationTargetClass.LOCAL_ADDRESS,
            "fd00::1%wlan0" to OperationTargetClass.LOCAL_ADDRESS,
            "2001:4860:4860::8888" to OperationTargetClass.INTERNET_ADDRESS,
            "::ffff:192.168.1.1" to OperationTargetClass.LOCAL_ADDRESS,
            "100.64.0.1" to OperationTargetClass.UNKNOWN, // CGNAT shared address space
            "192.0.2.1" to OperationTargetClass.UNKNOWN, // Documentation
            "198.18.0.1" to OperationTargetClass.UNKNOWN, // Benchmarking
            "224.0.0.1" to OperationTargetClass.UNKNOWN, // Multicast
            "0.0.0.0" to OperationTargetClass.UNKNOWN, // Unspecified/reserved
            "2001:db8::1" to OperationTargetClass.UNKNOWN, // IPv6 documentation
            "ff02::1" to OperationTargetClass.LOCAL_ADDRESS, // Link-local multicast
            "ff05::1" to OperationTargetClass.UNKNOWN, // Site-local multicast is not a local-subnet route.
            "4000::1" to OperationTargetClass.UNKNOWN, // Reserved outside global unicast
            "example.com" to OperationTargetClass.UNKNOWN,
            "localhost" to OperationTargetClass.UNKNOWN,
            "999.1.1.1" to OperationTargetClass.UNKNOWN,
            "01.2.3.4" to OperationTargetClass.INTERNET_ADDRESS,
            "not-an-address" to OperationTargetClass.UNKNOWN,
            "fe80::1%" to OperationTargetClass.UNKNOWN,
            "fe80::1%wlan/0" to OperationTargetClass.UNKNOWN,
            "fe80::1%wlan0%extra" to OperationTargetClass.UNKNOWN,
            "2001:4860::1%wlan0" to OperationTargetClass.UNKNOWN,
            "010.0.0.1" to OperationTargetClass.LOCAL_ADDRESS,
            "001.002.003.004" to OperationTargetClass.INTERNET_ADDRESS,
            "008.008.008.008" to OperationTargetClass.INTERNET_ADDRESS,
            "ff01::1" to OperationTargetClass.LOCAL_ADDRESS,
            "ff02::1%wlan0" to OperationTargetClass.LOCAL_ADDRESS,
            "[8.8.8.8]" to OperationTargetClass.UNKNOWN,
            "[8.8.8.8" to OperationTargetClass.UNKNOWN,
            "8.8.8.8]" to OperationTargetClass.UNKNOWN,
        )

        assertAll(cases.map { (input, expected) ->
            org.junit.jupiter.api.function.Executable {
                assertEquals(expected, LiteralDestinationClassifier.classify(input), input)
            }
        })
    }
}
