package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TopologyParamsValidationTest {

    private fun validV2cParams() = TopologyParams(
        targetIp = "192.168.1.1",
        snmpVersion = SnmpVersion.V2C,
        communityString = "public",
        maxHops = 3,
        timeoutMs = 3000,
        retries = 1
    )

    @Test
    fun `blank targetIp returns validation error`() {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(targetIp = ""))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("IP", ignoreCase = true) })
    }

    @Test
    fun `invalid IP format returns validation error`() {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(targetIp = "not an ip"))
        assertFalse(result.isValid)
    }

    @Test
    fun `blank community on V2C returns validation error`() {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(communityString = ""))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("community", ignoreCase = true) })
    }

    @Test
    fun `blank community on V1 returns validation error`() {
        val result = TopologyParamsValidator.validate(
            validV2cParams().copy(snmpVersion = SnmpVersion.V1, communityString = "")
        )
        assertFalse(result.isValid)
    }

    @Test
    fun `blank v3Username on V3 returns validation error`() {
        val result = TopologyParamsValidator.validate(
            TopologyParams(
                targetIp = "10.0.0.1",
                snmpVersion = SnmpVersion.V3,
                communityString = "",
                v3Username = "",
                maxHops = 3,
                timeoutMs = 3000,
                retries = 1
            )
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("username", ignoreCase = true) })
    }

    @Test
    fun `valid V2C params with default community passes`() {
        val result = TopologyParamsValidator.validate(validV2cParams())
        assertTrue(result.isValid)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `valid V3 params passes`() {
        val result = TopologyParamsValidator.validate(
            TopologyParams(
                targetIp = "10.0.0.1",
                snmpVersion = SnmpVersion.V3,
                communityString = "",
                v3Username = "admin",
                v3AuthPassword = "authpass",
                v3PrivPassword = "privpass",
                v3AuthProtocol = V3AuthProtocol.SHA,
                v3PrivProtocol = V3PrivProtocol.AES128,
                maxHops = 3,
                timeoutMs = 3000,
                retries = 1
            )
        )
        assertTrue(result.isValid)
    }

    @Test
    fun `accepts minimum topology values and rejects settings above the operation ceiling`() {
        val minimums = validV2cParams().copy(
            maxHops = TopologyParamsValidator.MIN_MAX_HOPS,
            timeoutMs = TopologyParamsValidator.MIN_TIMEOUT_MS,
            retries = TopologyParamsValidator.MIN_RETRIES
        )
        val maximums = validV2cParams().copy(
            maxHops = TopologyParamsValidator.MAX_MAX_HOPS,
            timeoutMs = TopologyParamsValidator.MAX_TIMEOUT_MS,
            retries = TopologyParamsValidator.MAX_RETRIES
        )

        assertTrue(TopologyParamsValidator.validate(minimums).isValid)
        val maximumResult = TopologyParamsValidator.validate(maximums)
        assertFalse(maximumResult.isValid)
        assertTrue(maximumResult.errors.any { it.contains("10 minute limit") })
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 11, Int.MIN_VALUE, Int.MAX_VALUE])
    fun `rejects maxHops outside supported range`(maxHops: Int) {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(maxHops = maxHops))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Max hops") })
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, 499, 30_001, Int.MIN_VALUE, Int.MAX_VALUE])
    fun `rejects timeout outside supported range`(timeoutMs: Int) {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(timeoutMs = timeoutMs))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Timeout") })
    }

    @ParameterizedTest
    @ValueSource(ints = [-1, 6, Int.MIN_VALUE, Int.MAX_VALUE])
    fun `rejects retries outside supported range`(retries: Int) {
        val result = TopologyParamsValidator.validate(validV2cParams().copy(retries = retries))

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Retries") })
    }

    @Test
    fun `V3 privacy requires authentication and a privacy password`() {
        val result = TopologyParamsValidator.validate(
            TopologyParams(
                targetIp = "10.0.0.1",
                snmpVersion = SnmpVersion.V3,
                v3Username = "admin",
                v3PrivProtocol = V3PrivProtocol.AES128
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("privacy requires", ignoreCase = true) })
        assertTrue(result.errors.any { it.contains("Privacy password", ignoreCase = true) })
    }

    @Test
    fun `V3 authentication requires an authentication password`() {
        val result = TopologyParamsValidator.validate(
            TopologyParams(
                targetIp = "10.0.0.1",
                snmpVersion = SnmpVersion.V3,
                v3Username = "admin",
                v3AuthProtocol = V3AuthProtocol.SHA
            )
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.contains("Authentication password", ignoreCase = true) })
    }
}
