package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource

@DisplayName("TopologyParamsValidator")
class TopologyParamsValidatorTest {

    private val invalidIpError = "Target IP must be a valid IPv4 address"
    private val blankIpError = "Target IP must not be blank"
    private val communityError = "Community string must not be blank for SNMP v1/v2c"
    private val usernameError = "Username must not be blank for SNMP v3"

    private fun validate(
        targetIp: String = "192.168.1.1",
        version: SnmpVersion = SnmpVersion.V2C,
        community: String = "public",
        username: String? = null
    ) = TopologyParamsValidator.validate(
        TopologyParams(
            targetIp = targetIp,
            snmpVersion = version,
            communityString = community,
            v3Username = username
        )
    )

    @Nested
    @DisplayName("target IP")
    inner class TargetIp {

        @ParameterizedTest
        @ValueSource(strings = ["192.168.1.1", "0.0.0.0", "255.255.255.255", "8.8.8.8"])
        @DisplayName("accepts well-formed IPv4 addresses")
        fun acceptsValid(ip: String) {
            val result = validate(targetIp = ip)
            assertTrue(result.isValid, "expected $ip to be accepted: ${result.errors}")
            assertTrue(result.errors.isEmpty())
        }

        @Test
        @DisplayName("trims surrounding whitespace before validating")
        fun trimsWhitespace() {
            assertTrue(validate(targetIp = "  10.0.0.1  ").isValid)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", "\t"])
        @DisplayName("rejects a blank target as blank, not malformed")
        fun rejectsBlank(ip: String) {
            val result = validate(targetIp = ip)
            assertFalse(result.isValid)
            assertEquals(listOf(blankIpError), result.errors)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "192.168.1",
                "192.168.1.1.1",
                "192.168.1.a",
                "router.local",
                "1234.1.1.1",
                "::1",
                "192.168.1.-1"
            ]
        )
        @DisplayName("rejects malformed addresses")
        fun rejectsMalformed(ip: String) {
            val result = validate(targetIp = ip)
            assertFalse(result.isValid, "expected $ip to be rejected")
            assertEquals(listOf(invalidIpError), result.errors)
        }

        @ParameterizedTest
        @ValueSource(strings = ["256.1.1.1", "1.256.1.1", "1.1.256.1", "1.1.1.256", "999.999.999.999"])
        @DisplayName("rejects octets above 255 even when the shape is right")
        fun rejectsOutOfRangeOctets(ip: String) {
            val result = validate(targetIp = ip)
            assertFalse(result.isValid, "expected $ip to be rejected")
            assertEquals(listOf(invalidIpError), result.errors)
        }
    }

    @Nested
    @DisplayName("SNMP credentials")
    inner class Credentials {

        @ParameterizedTest
        @EnumSource(value = SnmpVersion::class, names = ["V1", "V2C"])
        @DisplayName("requires a community string for v1 and v2c")
        fun requiresCommunity(version: SnmpVersion) {
            val result = validate(version = version, community = " ")
            assertFalse(result.isValid)
            assertEquals(listOf(communityError), result.errors)
        }

        @ParameterizedTest
        @EnumSource(value = SnmpVersion::class, names = ["V1", "V2C"])
        @DisplayName("accepts a non-blank community string for v1 and v2c")
        fun acceptsCommunity(version: SnmpVersion) {
            assertTrue(validate(version = version, community = "private").isValid)
        }

        @Test
        @DisplayName("ignores a blank community string for v3")
        fun v3IgnoresCommunity() {
            assertTrue(validate(version = SnmpVersion.V3, community = "", username = "admin").isValid)
        }

        @Test
        @DisplayName("requires a username for v3 when it is null")
        fun v3RequiresUsernameNotNull() {
            val result = validate(version = SnmpVersion.V3, username = null)
            assertFalse(result.isValid)
            assertEquals(listOf(usernameError), result.errors)
        }

        @Test
        @DisplayName("requires a username for v3 when it is blank")
        fun v3RequiresUsernameNotBlank() {
            val result = validate(version = SnmpVersion.V3, username = "  ")
            assertFalse(result.isValid)
            assertEquals(listOf(usernameError), result.errors)
        }
    }

    @Nested
    @DisplayName("error aggregation")
    inner class Aggregation {

        @Test
        @DisplayName("reports every problem rather than stopping at the first")
        fun reportsAllErrors() {
            val result = validate(targetIp = "", version = SnmpVersion.V2C, community = "")
            assertFalse(result.isValid)
            assertEquals(listOf(blankIpError, communityError), result.errors)
        }

        @Test
        @DisplayName("combines a malformed IP with a missing v3 username")
        fun malformedIpAndMissingUsername() {
            val result = validate(targetIp = "nope", version = SnmpVersion.V3, username = "")
            assertEquals(listOf(invalidIpError, usernameError), result.errors)
        }
    }

    @Nested
    @DisplayName("ValidationResult")
    inner class Result {

        @Test
        @DisplayName("compares by value")
        fun valueSemantics() {
            val a = ValidationResult(isValid = true, errors = emptyList())
            val b = ValidationResult(isValid = true, errors = emptyList())
            assertEquals(a, b)
            assertEquals(a.hashCode(), b.hashCode())
            assertTrue(a.toString().contains("isValid=true"))
            assertFalse(a.copy(isValid = false).isValid)
            assertTrue(a.component1())
            assertTrue(a.component2().isEmpty())
        }
    }
}
