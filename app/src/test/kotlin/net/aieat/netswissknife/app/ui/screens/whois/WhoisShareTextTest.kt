package net.aieat.netswissknife.app.ui.screens.whois

import net.aieat.netswissknife.core.network.whois.WhoisQueryType
import net.aieat.netswissknife.core.network.whois.WhoisResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WhoisShareTextTest {
    @Test
    fun `golden text lists the parsed fields without a raw section when there is no raw response`() {
        val result =
            WhoisResult(
                query = "example.com",
                queryType = WhoisQueryType.DOMAIN,
                hops = emptyList(),
                domainName = "example.com",
                registrar = "Example Registrar",
                registrarUrl = null,
                registeredOn = null,
                expiresOn = null,
                updatedOn = null,
                nameServers = listOf("ns1.example.com", "ns2.example.com"),
                statusCodes = listOf("clientTransferProhibited"),
                registrantOrg = null,
                registrantCountry = null,
                dnssec = null,
                netName = null,
                netRange = null,
                orgName = null,
                country = null,
                totalQueryTimeMs = 30,
            )
        val expected =
            listOf(
                "WHOIS – example.com",
                "",
                "Registrar: Example Registrar",
                "Name Servers: ns1.example.com, ns2.example.com",
                "Status: clientTransferProhibited",
            ).joinToString(separator = "\n", postfix = "\n")

        assertEquals(expected, buildWhoisShareText(result))
    }

    @Test
    fun `dates are pasted as calendar dates, never as epoch milliseconds`() {
        val result =
            WhoisResult(
                query = "example.com",
                queryType = WhoisQueryType.DOMAIN,
                hops = emptyList(),
                domainName = "example.com",
                registrar = null,
                registrarUrl = null,
                registeredOn = 808_372_800_000L,
                expiresOn = 1_818_129_600_000L,
                updatedOn = 1_786_694_503_000L,
                nameServers = emptyList(),
                statusCodes = emptyList(),
                registrantOrg = null,
                registrantCountry = null,
                dnssec = null,
                netName = null,
                netRange = null,
                orgName = null,
                country = null,
                totalQueryTimeMs = 30,
            )
        val text = buildWhoisShareText(result)

        assertFalse(text.contains("808372800000"))
        assertFalse(text.contains("1818129600000"))
        assertTrue(text.contains("Created: ${calendarDate(808_372_800_000L)}"))
        assertTrue(text.contains("Expires: ${calendarDate(1_818_129_600_000L)}"))
    }

    private fun calendarDate(epochMs: Long): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return format.format(Date(epochMs))
    }
}
