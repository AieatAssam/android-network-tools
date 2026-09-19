package net.aieat.netswissknife.core.network.topology

import org.snmp4j.security.AuthHMAC192SHA256
import org.snmp4j.security.AuthHMAC384SHA512
import org.snmp4j.security.AuthMD5
import org.snmp4j.security.AuthSHA
import org.snmp4j.security.PrivAES128
import org.snmp4j.security.PrivAES192
import org.snmp4j.security.PrivAES256
import org.snmp4j.security.PrivDES
import org.snmp4j.security.SecurityLevel
import org.snmp4j.security.UsmUser
import org.snmp4j.smi.OID
import org.snmp4j.smi.OctetString

data class UsmUserSpec(
    val securityName: OctetString,
    val authOid: OID?,
    val authPassphrase: OctetString?,
    val privOid: OID?,
    val privPassphrase: OctetString?,
    val securityLevel: Int
) {
    fun toUsmUser(): UsmUser = UsmUser(
        securityName,
        authOid,
        authPassphrase,
        privOid,
        privPassphrase
    )
}

object UsmUserSpecFactory {

    fun from(params: TopologyParams): UsmUserSpec {
        require(params.snmpVersion == SnmpVersion.V3) { "USM credentials require SNMP v3" }
        val username = params.v3Username?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("SNMP v3 username must not be blank")
        require(params.v3PrivProtocol == V3PrivProtocol.NONE || params.v3AuthProtocol != V3AuthProtocol.NONE) {
            "SNMP v3 privacy requires authentication"
        }

        val authOid = params.v3AuthProtocol.toOid()
        val privOid = params.v3PrivProtocol.toOid()
        val authPassphrase = authOid?.let {
            params.v3AuthPassword?.takeIf { password -> password.isNotBlank() }
                ?.let(::OctetString)
                ?: throw IllegalArgumentException("SNMP v3 authentication password must not be blank")
        }
        val privPassphrase = privOid?.let {
            params.v3PrivPassword?.takeIf { password -> password.isNotBlank() }
                ?.let(::OctetString)
                ?: throw IllegalArgumentException("SNMP v3 privacy password must not be blank")
        }

        val securityLevel = when {
            authOid != null && privOid != null -> SecurityLevel.AUTH_PRIV
            authOid != null -> SecurityLevel.AUTH_NOPRIV
            else -> SecurityLevel.NOAUTH_NOPRIV
        }

        return UsmUserSpec(
            securityName = OctetString(username),
            authOid = authOid,
            authPassphrase = authPassphrase,
            privOid = privOid,
            privPassphrase = privPassphrase,
            securityLevel = securityLevel
        )
    }

    private fun V3AuthProtocol.toOid(): OID? = when (this) {
        V3AuthProtocol.NONE -> null
        V3AuthProtocol.MD5 -> AuthMD5.ID
        V3AuthProtocol.SHA -> AuthSHA.ID
        V3AuthProtocol.SHA256 -> AuthHMAC192SHA256.ID
        V3AuthProtocol.SHA512 -> AuthHMAC384SHA512.ID
    }

    private fun V3PrivProtocol.toOid(): OID? = when (this) {
        V3PrivProtocol.NONE -> null
        V3PrivProtocol.DES -> PrivDES.ID
        V3PrivProtocol.AES128 -> PrivAES128.ID
        V3PrivProtocol.AES192 -> PrivAES192.ID
        V3PrivProtocol.AES256 -> PrivAES256.ID
    }
}
