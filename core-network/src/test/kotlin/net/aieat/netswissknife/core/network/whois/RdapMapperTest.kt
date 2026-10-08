package net.aieat.netswissknife.core.network.whois

import java.time.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RdapMapperTest {
    @Test
    fun `maps domain fields and selects contacts by role`() {
        val raw = fixture("domain-example.json")
        val result = RdapMapper.map(raw, "example.com", WhoisQueryType.DOMAIN, "rdap.example", 17)

        assertEquals("example.com", result.domainName)
        assertEquals("Domain Registrar", result.registrar)
        assertEquals("https://registrar.example", result.registrarUrl)
        assertEquals("Example Holdings Inc.", result.registrantOrg)
        assertEquals("US", result.registrantCountry)
        assertEquals(Instant.parse("1995-08-14T04:00:00Z").toEpochMilli(), result.registeredOn)
        assertEquals(Instant.parse("2030-08-14T02:00:00.125Z").toEpochMilli(), result.expiresOn)
        assertEquals(Instant.parse("2025-08-14T04:00:00Z").toEpochMilli(), result.updatedOn)
        assertEquals(listOf("a.iana-servers.net", "B.IANA-SERVERS.NET", "ns.unicode.example"), result.nameServers)
        assertEquals(listOf("active", "client transfer prohibited"), result.statusCodes)
        assertEquals("Unsigned", result.dnssec)
        assertEquals(17L, result.totalQueryTimeMs)
        assertEquals(WhoisServerRole.RDAP, result.hops.single().server.role)
        assertEquals(raw, result.hops.single().rawResponse)

        val unsafeRegistrarUrl = raw.replace("https://registrar.example", "javascript:alert(1)")
        assertNull(
            RdapMapper.map(unsafeRegistrarUrl, "example.com", WhoisQueryType.DOMAIN, "rdap.example")
                .registrarUrl,
        )
    }

    @Test
    fun `maps IP network allocation fields`() {
        val result = RdapMapper.map(
            fixture("ip-8.8.8.8.json"),
            "8.8.8.8",
            WhoisQueryType.IPV4,
            "rdap.arin.net",
        )

        assertEquals("Google LLC", result.netName)
        assertEquals("8.8.8.0-8.8.8.255", result.netRange)
        assertEquals("Google LLC", result.orgName)
        assertEquals("US", result.country)
        assertEquals("NET-8-8-8-0-1", result.handle)
    }

    @Test
    fun `maps ASN name handle and range`() {
        val result = RdapMapper.map(
            fixture("asn-15169.json"),
            "AS15169",
            WhoisQueryType.ASN,
            "rdap.arin.net",
        )

        assertEquals("Google LLC", result.netName)
        assertEquals("Google LLC", result.orgName)
        assertEquals("AS15169", result.handle)
        assertEquals("15169-15169", result.netRange)
        assertEquals("US", result.country)
    }

    @Test
    fun `keeps registry handles separate from absent network names`() {
        val raw = fixture("asn-15169.json").replace("\"name\": \"Google LLC\"", "\"name\": 7")
        val result = RdapMapper.map(raw, "AS15169", WhoisQueryType.ASN, "rdap.arin.net")

        assertNull(result.netName)
        assertEquals("AS15169", result.handle)
        assertEquals("Google LLC", result.orgName)
    }

    @Test
    fun `rejects invalid and reversed ASN number ranges`() {
        val raw = fixture("asn-15169.json")
        val invalid = raw.replace("\"startAutnum\": 15169", "\"startAutnum\": -1")
        val tooLarge = raw.replace("\"startAutnum\": 15169", "\"startAutnum\": 4294967296")
        val reversed = raw
            .replace("\"startAutnum\": 15169", "\"startAutnum\": 20000")
            .replace("\"endAutnum\": 15169", "\"endAutnum\": 10000")
        val maximum = raw
            .replace("\"startAutnum\": 15169", "\"startAutnum\": 4294967295")
            .replace("\"endAutnum\": 15169", "\"endAutnum\": 4294967295")

        assertNull(RdapMapper.map(invalid, "AS15169", WhoisQueryType.ASN, "rdap.arin.net").netRange)
        assertNull(RdapMapper.map(tooLarge, "AS15169", WhoisQueryType.ASN, "rdap.arin.net").netRange)
        assertNull(RdapMapper.map(reversed, "AS15169", WhoisQueryType.ASN, "rdap.arin.net").netRange)
        assertEquals(
            "4294967295-4294967295",
            RdapMapper.map(maximum, "AS4294967295", WhoisQueryType.ASN, "rdap.arin.net").netRange,
        )
    }

    @Test
    fun `optional malformed members degrade to empty fields`() {
        val result = RdapMapper.map(
            """{"objectClassName":"domain","events":"bad","nameservers":[null,{},7,{"ldhName":42,"unicodeName":false},{"ldhName":["bad.example"]}],"status":[7,false,["bad"],"ok"],"entities":[null],"secureDNS":"bad","country":["US"]}""",
            "example.com",
            WhoisQueryType.DOMAIN,
            "rdap.example",
        )

        assertNull(result.domainName)
        assertNull(result.dnssec)
        assertNull(result.country)
        assertTrue(result.nameServers.isEmpty())
        assertEquals(listOf("ok"), result.statusCodes)
        assertTrue(result.hops.single().rawResponse.isNotBlank())
    }

    @Test
    fun `rejects missing unsupported and query-mismatched object classes`() {
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map("{}", "example.com", WhoisQueryType.DOMAIN, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map("[]", "example.com", WhoisQueryType.DOMAIN, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map("""{"objectClassName":["domain"]}""", "example.com", WhoisQueryType.DOMAIN, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map("""{"objectClassName":"error"}""", "example.com", WhoisQueryType.DOMAIN, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map(fixture("ip-8.8.8.8.json"), "8.8.8.8", WhoisQueryType.DOMAIN, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map(fixture("ip-8.8.8.8.json"), "8.8.8.8", WhoisQueryType.IPV6, "rdap.example")
        }
        assertThrows(IllegalArgumentException::class.java) {
            RdapMapper.map(
                fixture("ip-8.8.8.8.json").replace("\"v4\"", "\"v6\""),
                "8.8.8.8",
                WhoisQueryType.IPV4,
                "rdap.example",
            )
        }
        val ipv6 = RdapMapper.map(
            fixture("ip-8.8.8.8.json").replace("\"v4\"", "\"v6\""),
            "2001:4860:4860::8888",
            WhoisQueryType.IPV6,
            "rdap.example",
        )
        assertEquals("Google LLC", ipv6.netName)
    }

    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/rdap/$name")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
}
