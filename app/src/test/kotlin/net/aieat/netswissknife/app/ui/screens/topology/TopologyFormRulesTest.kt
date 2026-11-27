package net.aieat.netswissknife.app.ui.screens.topology

import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.topology.SnmpVersion
import net.aieat.netswissknife.core.network.topology.TopologyParams
import net.aieat.netswissknife.core.network.topology.V3AuthProtocol
import net.aieat.netswissknife.core.network.topology.V3PrivProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TopologyFormRulesTest {
    private val base =
        TopologyParams(
            targetIp = "10.0.0.1",
            snmpVersion = SnmpVersion.V3,
            communityString = "public",
            v3Username = "monitor",
            v3AuthPassword = "auth-secret",
            v3PrivPassword = "priv-secret",
            v3AuthProtocol = V3AuthProtocol.SHA256,
            v3PrivProtocol = V3PrivProtocol.AES128,
            maxHops = 3,
            queryDiscoveredNeighbors = false,
        )
    private val failure = TopologyUiState.Failure(message = "SNMP v3 failed")

    private val formFieldChanges: List<Pair<String, (TopologyParams) -> TopologyParams>> =
        listOf(
            "target IP" to { p -> p.copy(targetIp = "10.0.0.2") },
            "SNMP version" to { p -> p.copy(snmpVersion = SnmpVersion.V1) },
            "community string" to { p -> p.copy(communityString = "private") },
            "v3 username" to { p -> p.copy(v3Username = "other") },
            "v3 auth password" to { p -> p.copy(v3AuthPassword = "changed") },
            "v3 privacy password" to { p -> p.copy(v3PrivPassword = "changed") },
            "v3 auth protocol" to { p -> p.copy(v3AuthProtocol = V3AuthProtocol.MD5) },
            "v3 privacy protocol" to { p -> p.copy(v3PrivProtocol = V3PrivProtocol.DES) },
            "max hops" to { p -> p.copy(maxHops = 5) },
            "query discovered neighbors" to { p -> p.copy(queryDiscoveredNeighbors = true) },
        )

    @Nested
    inner class StaleFailure {
        @Test
        fun `changing any form field hides a failure from the run that used the earlier inputs`() {
            formFieldChanges.forEach { (field, change) ->
                val result = failure.withoutStaleFailure(runParams = base, formParams = change(base))
                assertEquals(TopologyUiState.Idle, result, "field: $field")
            }
        }

        @Test
        fun `an unchanged form keeps the failure visible`() {
            assertEquals(failure, failure.withoutStaleFailure(runParams = base, formParams = base))
        }

        @Test
        fun `a failure whose run is unknown is kept`() {
            assertEquals(failure, failure.withoutStaleFailure(runParams = null, formParams = base))
        }

        @Test
        fun `states other than a failure are never replaced`() {
            val discovering = TopologyUiState.Discovering(emptyList(), emptyList(), "", 0)
            val changed = base.copy(targetIp = "10.0.0.9")
            assertEquals(discovering, discovering.withoutStaleFailure(runParams = base, formParams = changed))
            assertEquals(TopologyUiState.Idle, TopologyUiState.Idle.withoutStaleFailure(base, changed))
        }
    }

    @Nested
    inner class CredentialErrors {
        @Test
        fun `a blank community string is flagged for v1 and v2c`() {
            val v2c = base.copy(snmpVersion = SnmpVersion.V2C, communityString = "")
            assertEquals(setOf(ErrorCode.SNMP_COMMUNITY_BLANK), topologyCredentialErrors(v2c))
            assertEquals(
                setOf(ErrorCode.SNMP_COMMUNITY_BLANK),
                topologyCredentialErrors(v2c.copy(snmpVersion = SnmpVersion.V1)),
            )
        }

        @Test
        fun `a v3 user without a username is flagged`() {
            val params = base.copy(v3Username = null)
            assertEquals(setOf(ErrorCode.SNMP_V3_USER_BLANK), topologyCredentialErrors(params))
        }

        @Test
        fun `authentication without a password is flagged`() {
            val params = base.copy(v3AuthPassword = null)
            assertEquals(setOf(ErrorCode.SNMP_V3_AUTH_PASSWORD_BLANK), topologyCredentialErrors(params))
        }

        @Test
        fun `privacy without authentication is flagged`() {
            val params = base.copy(v3AuthProtocol = V3AuthProtocol.NONE, v3AuthPassword = null)
            assertEquals(setOf(ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH), topologyCredentialErrors(params))
        }

        @Test
        fun `privacy without a password is flagged`() {
            val params = base.copy(v3PrivPassword = null)
            assertEquals(setOf(ErrorCode.SNMP_V3_PRIV_PASSWORD_BLANK), topologyCredentialErrors(params))
        }

        @Test
        fun `a complete v3 profile has no credential errors`() {
            assertTrue(topologyCredentialErrors(base).isEmpty())
        }

        @Test
        fun `a v3 password is not checked when the protocol that uses it is off`() {
            val params =
                base.copy(
                    v3AuthProtocol = V3AuthProtocol.NONE,
                    v3PrivProtocol = V3PrivProtocol.NONE,
                    v3AuthPassword = null,
                    v3PrivPassword = null,
                )
            assertTrue(topologyCredentialErrors(params).isEmpty())
        }

        @Test
        fun `target problems are not credential errors`() {
            assertTrue(
                topologyCredentialErrors(
                    base.copy(
                        targetIp = "",
                        v3Username = "u",
                        v3AuthProtocol = V3AuthProtocol.NONE,
                        v3PrivProtocol = V3PrivProtocol.NONE,
                    ),
                ).isEmpty(),
            )
        }
    }

    @Nested
    inner class DisabledReason {
        @Test
        fun `an invalid target is the first reason`() {
            assertEquals(
                R.string.topology_discover_disabled_target,
                topologyDisabledReasonRes(false, setOf(ErrorCode.SNMP_V3_USER_BLANK)),
            )
        }

        @Test
        fun `privacy without authentication keeps its own message`() {
            assertEquals(
                R.string.topology_discover_disabled_v3,
                topologyDisabledReasonRes(
                    true,
                    setOf(ErrorCode.SNMP_V3_PRIV_WITHOUT_AUTH, ErrorCode.SNMP_V3_USER_BLANK),
                ),
            )
        }

        @Test
        fun `other missing credentials point at the highlighted fields`() {
            assertEquals(
                R.string.topology_discover_disabled_credentials,
                topologyDisabledReasonRes(true, setOf(ErrorCode.SNMP_V3_USER_BLANK)),
            )
        }

        @Test
        fun `a valid form has no disabled reason`() {
            assertEquals(null, topologyDisabledReasonRes(true, emptySet()))
        }
    }
}
