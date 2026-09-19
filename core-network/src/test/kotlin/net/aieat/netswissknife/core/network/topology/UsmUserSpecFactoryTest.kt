package net.aieat.netswissknife.core.network.topology

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.snmp4j.mp.SnmpConstants
import org.snmp4j.security.AuthHMAC192SHA256
import org.snmp4j.security.AuthHMAC384SHA512
import org.snmp4j.security.AuthMD5
import org.snmp4j.security.AuthSHA
import org.snmp4j.security.PrivAES128
import org.snmp4j.security.PrivAES256
import org.snmp4j.security.PrivDES
import org.snmp4j.security.SecurityLevel

class UsmUserSpecFactoryTest {

    private val base = TopologyParams(
        targetIp = "192.168.1.1",
        snmpVersion = SnmpVersion.V3,
        v3Username = "operator"
    )

    @Test
    fun `MD5 DES maps to authPriv`() {
        val spec = UsmUserSpecFactory.from(
            base.copy(
                v3AuthProtocol = V3AuthProtocol.MD5,
                v3AuthPassword = "auth-pass",
                v3PrivProtocol = V3PrivProtocol.DES,
                v3PrivPassword = "priv-pass"
            )
        )

        assertEquals(AuthMD5.ID, spec.authOid)
        assertEquals(PrivDES.ID, spec.privOid)
        assertEquals(SecurityLevel.AUTH_PRIV, spec.securityLevel)
        assertEquals("auth-pass", spec.authPassphrase?.toString())
        assertEquals("priv-pass", spec.privPassphrase?.toString())
    }

    @Test
    fun `SHA without privacy maps to authNoPriv`() {
        val spec = UsmUserSpecFactory.from(
            base.copy(
                v3AuthProtocol = V3AuthProtocol.SHA,
                v3AuthPassword = "auth-pass"
            )
        )

        assertEquals(AuthSHA.ID, spec.authOid)
        assertNull(spec.privOid)
        assertEquals(SecurityLevel.AUTH_NOPRIV, spec.securityLevel)
    }

    @Test
    fun `noAuthNoPriv ignores supplied passphrases`() {
        val spec = UsmUserSpecFactory.from(
            base.copy(
                v3AuthPassword = "ignored",
                v3PrivPassword = "ignored"
            )
        )

        assertNull(spec.authOid)
        assertNull(spec.privOid)
        assertNull(spec.authPassphrase)
        assertNull(spec.privPassphrase)
        assertEquals(SecurityLevel.NOAUTH_NOPRIV, spec.securityLevel)
    }

    @Test
    fun `SHA256 and AES256 use SNMP4J extended protocol IDs`() {
        val spec = UsmUserSpecFactory.from(
            base.copy(
                v3AuthProtocol = V3AuthProtocol.SHA256,
                v3AuthPassword = "auth-pass",
                v3PrivProtocol = V3PrivProtocol.AES256,
                v3PrivPassword = "priv-pass"
            )
        )

        assertEquals(AuthHMAC192SHA256.ID, spec.authOid)
        assertEquals(PrivAES256.ID, spec.privOid)
    }

    @Test
    fun `SHA512 and AES128 are supported`() {
        val spec = UsmUserSpecFactory.from(
            base.copy(
                v3AuthProtocol = V3AuthProtocol.SHA512,
                v3AuthPassword = "auth-pass",
                v3PrivProtocol = V3PrivProtocol.AES128,
                v3PrivPassword = "priv-pass"
            )
        )

        assertEquals(AuthHMAC384SHA512.ID, spec.authOid)
        assertEquals(PrivAES128.ID, spec.privOid)
    }

    @Test
    fun `privacy without authentication is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UsmUserSpecFactory.from(
                base.copy(
                    v3PrivProtocol = V3PrivProtocol.DES,
                    v3PrivPassword = "priv-pass"
                )
            )
        }
    }

    @Test
    fun `non v3 parameters are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            UsmUserSpecFactory.from(
                base.copy(snmpVersion = SnmpVersion.V2C, communityString = "public")
            )
        }
    }
}
