package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

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
        val result = TopologyParamsValidator.validate(validV2cParams().copy(targetIp = "not.an.ip.address.at.all"))
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
}
