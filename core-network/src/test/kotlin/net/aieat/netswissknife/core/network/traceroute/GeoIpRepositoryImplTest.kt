package net.aieat.netswissknife.core.network.traceroute

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import net.aieat.netswissknife.core.network.MonotonicClock
import net.aieat.netswissknife.core.network.operation.CancellationReason
import net.aieat.netswissknife.core.network.operation.OperationBudget
import net.aieat.netswissknife.core.network.operation.OperationCancellationException
import net.aieat.netswissknife.core.network.operation.OperationDeadlineExceededException
import net.aieat.netswissknife.core.network.operation.OperationRequirement
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.operation.OperationSession
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@DisplayName("GeoIpRepositoryImpl")
class GeoIpRepositoryImplTest {
    private val ipInfoFixture =
        """
        {"ip":"8.8.8.8","city":"Mountain View","country":"US","loc":"37.38,-122.08","org":"AS15169 Google LLC"}
        """.trimIndent()

    private val ipWhoFixture =
        """
        {"ip":"8.8.8.8","success":true,"country":"United States","country_code":"US","city":"Mountain View","latitude":37.38,"longitude":-122.08,"connection":{"asn":15169,"org":"Google LLC","isp":"Google LLC"}}
        """.trimIndent()

    private val ipApiFixture =
        """
        {"ip":"8.8.8.8","city":"Mountain View","country_code":"US","country_name":"United States","latitude":37.38,"longitude":-122.08,"asn":"AS15169","org":"Google LLC"}
        """.trimIndent()

    private var server: HttpServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop(0)
    }

    private fun startServer(handler: (String) -> Pair<Int, String>): String {
        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        httpServer.createContext("/") { exchange ->
            val (status, body) = handler(exchange.requestURI.path)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        httpServer.start()
        server = httpServer
        return "http://127.0.0.1:${httpServer.address.port}"
    }

    @Test
    @DisplayName("lookup does not throw and returns null when the GeoIP API errors")
    fun `lookup does not throw when API returns non-200`() =
        runTest {
            val baseUrl = startServer { 500 to "error" }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            val result = repo.lookup("8.8.8.8")

            assertNull(result)
        }

    @Test
    @DisplayName("repeated lookups after an API failure keep returning null without crashing")
    fun `repeated lookups after failure do not throw`() =
        runTest {
            val baseUrl = startServer { 500 to "error" }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            assertNull(repo.lookup("8.8.8.8"))
            assertNull(repo.lookup("8.8.8.8"))
        }

    @Test
    @DisplayName("lookup returns null for malformed JSON without crashing")
    fun `lookup does not throw on malformed response body`() =
        runTest {
            val baseUrl = startServer { 200 to "{ not valid json" }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            val result = repo.lookup("8.8.8.8")

            assertNull(result)
        }

    @Test
    @DisplayName("lookup parses a successful response and caches it")
    fun `lookup parses successful response`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","city":"Mountain View","country":"US","loc":"37.38,-122.08","org":"AS15169 Google LLC"}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            val result = repo.lookup("8.8.8.8")

            assertEquals("United States", result?.country)
            assertEquals("Mountain View", result?.city)
        }

    @Test
    @DisplayName("successful locations use a bounded least-recently-used cache")
    fun `cache evicts least recently used location at its capacity`() =
        runTest {
            val connectionAttempts = AtomicInteger()
            val fillerIps =
                buildList {
                    addAll((1..254).map { "8.8.8.$it" }.filterNot { it == "8.8.8.8" })
                    addAll((1..254).map { "8.8.4.$it" })
                    addAll(listOf("1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "208.67.222.222"))
                }
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES, fillerIps.size)
            assertEquals(fillerIps.size, fillerIps.toSet().size)

            val repo =
                GeoIpRepositoryImpl(
                    providers = listOf(IpInfoGeoIpProvider("https://geo.test")),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            connectionAttempts.incrementAndGet()
                            ResponseGeoIpConnection(url)
                        },
                    clock = MonotonicClock { 0L },
                )
            val first = "8.8.8.8"

            assertNotNull(repo.lookup(first))
            fillerIps.take(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES - 1).forEach { ip ->
                assertNotNull(repo.lookup(ip), "Expected fixture response for $ip")
            }
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES, connectionAttempts.get())

            // A hit refreshes recency so the oldest filler entry, rather than `first`, is evicted.
            assertNotNull(repo.lookup(first))
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES, connectionAttempts.get())
            assertNotNull(repo.lookup(fillerIps.last()))
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES + 1, connectionAttempts.get())
            assertNotNull(repo.lookup(fillerIps.last()))
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES + 1, connectionAttempts.get())
            assertNotNull(repo.lookup(first))
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES + 1, connectionAttempts.get())

            // The least-recently-used filler item was removed and must be fetched again.
            assertNotNull(repo.lookup(fillerIps.first()))
            assertEquals(GeoIpRepositoryImpl.GEOIP_CACHE_MAX_ENTRIES + 2, connectionAttempts.get())
        }

    @Test
    @DisplayName("provider fixtures map the documented response fields")
    fun `provider parsers map deterministic fixtures`() {
        val ipInfo = (IpInfoGeoIpProvider().parse("8.8.8.8", ipInfoFixture) as GeoIpParseOutcome.Found).location
        val ipWho = (IpWhoGeoIpProvider().parse("8.8.8.8", ipWhoFixture) as GeoIpParseOutcome.Found).location
        val ipApi = (IpApiGeoIpProvider().parse("8.8.8.8", ipApiFixture) as GeoIpParseOutcome.Found).location

        listOf(ipInfo, ipWho, ipApi).forEach { location ->
            assertNotNull(location)
            assertEquals("United States", location.country)
            assertEquals("US", location.countryCode)
            assertEquals("Mountain View", location.city)
            assertEquals(37.38, location.lat)
            assertEquals(-122.08, location.lon)
        }
        assertEquals("AS15169", ipInfo.asn)
        assertEquals("AS15169", ipWho.asn)
        assertEquals("AS15169", ipApi.asn)
    }

    @Test
    @DisplayName("provider parsers distinguish healthy no-data and invalid responses")
    fun `provider parsers type no data and invalid responses`() {
        assertEquals(
            GeoIpParseOutcome.NoData,
            IpInfoGeoIpProvider().parse("8.8.8.8", """{"ip":"8.8.8.8","bogon":true}"""),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpWhoGeoIpProvider().parse("8.8.8.8", """{"success":false,"message":"private range"}"""),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpInfoGeoIpProvider().parse("8.8.8.8", """{"ip":"8.8.8.8","country":"US","loc":"bogus,37,-122"}"""),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpWhoGeoIpProvider().parse("8.8.8.8", "not json"),
        )
    }

    @Test
    @DisplayName("provider parsers reject mismatched IPs, invalid coordinates, and null ASNs")
    fun `provider parsers reject invalid provider identity and data`() {
        val wrongIp = ipInfoFixture.replace("8.8.8.8", "1.1.1.1")
        assertEquals(GeoIpParseOutcome.Invalid, IpInfoGeoIpProvider().parse("8.8.8.8", wrongIp))
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpWhoGeoIpProvider().parse("8.8.8.8", ipWhoFixture.replace("8.8.8.8", "1.1.1.1")),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", ipApiFixture.replace("8.8.8.8", "1.1.1.1")),
        )
        val canonicalIpv6 = """{"ip":"2001:4860:4860:0:0:0:0:8888","city":"Mountain View","country":"US","loc":"37.38,-122.08"}"""
        assertTrue(
            IpInfoGeoIpProvider().parse("2001:4860:4860::8888", canonicalIpv6) is GeoIpParseOutcome.Found,
            "Equivalent IPv6 spellings should match after canonicalization",
        )

        val malformedCoordinates = ipInfoFixture.replace("37.38,-122.08", "bogus,37,-122")
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpInfoGeoIpProvider().parse("8.8.8.8", malformedCoordinates),
        )
        val outOfRangeLatitude = ipWhoFixture.replace("37.38", "91.0")
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpWhoGeoIpProvider().parse("8.8.8.8", outOfRangeLatitude),
        )
        val outOfRangeLongitude = ipApiFixture.replace("-122.08", "181.0")
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", outOfRangeLongitude),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", ipApiFixture.replace("37.38", "1e309")),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", ipApiFixture.replace("\"ip\":\"8.8.8.8\"", "\"ip\":\"8.8.8.8\",\"error\":true")),
        )
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", ipApiFixture.replace("\"ip\":\"8.8.8.8\"", "\"ip\":\"8.8.8.8\",\"error\":\"false\"")),
        )
    }

    @Test
    @DisplayName("provider parsers use organization ASN fallback and allow missing ASN")
    fun `provider parsers validate optional ASN metadata`() {
        val orgOnlyAsn =
            ipApiFixture
                .replace("\"AS15169\"", "null")
                .replace("Google LLC", "AS15169 Google LLC")
        val orgOnlyParsed = IpApiGeoIpProvider().parse("8.8.8.8", orgOnlyAsn) as GeoIpParseOutcome.Found
        assertEquals("AS15169", orgOnlyParsed.location.asn)
        val outOfRangeOrgAsn = orgOnlyAsn.replace("AS15169 Google LLC", "AS99999999999999999999 Provider")
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpApiGeoIpProvider().parse("8.8.8.8", outOfRangeOrgAsn),
        )

        val nullAsn = ipWhoFixture.replace("15169", "null")
        val parsed = IpWhoGeoIpProvider().parse("8.8.8.8", nullAsn) as GeoIpParseOutcome.Found
        assertEquals(null, parsed.location.asn)
        assertEquals(
            GeoIpParseOutcome.Invalid,
            IpWhoGeoIpProvider().parse("8.8.8.8", ipWhoFixture.replace("15169", "\"not-asn\"")),
        )
    }

    @Test
    @DisplayName("healthy no-data responses fall through without opening a provider breaker")
    fun `no-data provider responses do not count as failures`() =
        runTest {
            val calls = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpInfoGeoIpProvider("https://ipinfo.test"),
                            IpWhoGeoIpProvider("https://ipwho.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            calls += url.host
                            val body =
                                if (url.host == "ipinfo.test") {
                                    """{"ip":"${ipFromPath(url)}","bogon":true}"""
                                } else {
                                    ipWhoFixture.replace("8.8.8.8", ipFromPath(url))
                                }
                            ScriptedGeoIpConnection(url, 200, body)
                        },
                    clock = MonotonicClock { System.nanoTime() },
                )

            repeat(4) { index -> assertNotNull(repo.lookup("8.8.8.${index + 1}")) }

            assertEquals(4, calls.count { it == "ipinfo.test" }, "No-data is not a provider failure")
            assertEquals(4, calls.count { it == "ipwho.test" }, "No-data falls through to the next provider")
        }

    @Test
    @DisplayName("ipwho success false is treated as provider failure and falls back")
    fun `ipwho false success opens its breaker and uses ipapi fallback`() =
        runTest {
            val calls = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpWhoGeoIpProvider("https://ipwho.test"),
                            IpApiGeoIpProvider("https://ipapi.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            calls += url.host
                            if (url.host == "ipwho.test") {
                                ScriptedGeoIpConnection(url, 200, """{"ip":"${ipFromPath(url)}","success":false,"message":"quota exceeded"}""")
                            } else {
                                ScriptedGeoIpConnection(url, 200, ipApiFixture.replace("8.8.8.8", ipFromPath(url)))
                            }
                        },
                    clock = MonotonicClock { System.nanoTime() },
                )

            repeat(4) { index -> assertNotNull(repo.lookup("8.8.8.${index + 1}")) }

            assertEquals(
                3,
                calls.count { it == "ipwho.test" },
                "Three success=false payloads open the provider breaker",
            )
            assertEquals(4, calls.count { it == "ipapi.test" }, "Fallback continues while ipwho is open")
        }

    @Test
    @DisplayName("lookup falls back to the next provider after an HTTP failure")
    fun `lookup falls back after provider failure`() =
        runTest {
            val calls = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpInfoGeoIpProvider("https://ipinfo.test"),
                            IpWhoGeoIpProvider("https://ipwho.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            calls += url.host
                            when (url.host) {
                                "ipinfo.test" -> {
                                    ScriptedGeoIpConnection(url, 503, "unavailable")
                                }

                                "ipwho.test" -> {
                                    ScriptedGeoIpConnection(
                                        url,
                                        200,
                                        ipWhoFixture.replace("8.8.8.8", ipFromPath(url)),
                                    )
                                }

                                else -> {
                                    error("Unexpected provider ${url.host}")
                                }
                            }
                        },
                    clock = MonotonicClock { System.nanoTime() },
                )

            val result = repo.lookup("8.8.8.8")

            assertEquals(listOf("ipinfo.test", "ipwho.test"), calls)
            assertEquals("United States", result?.country)
            assertEquals("AS15169", result?.asn)
        }

    @Test
    @DisplayName("provider breaker opens after three failures and retries after sixty seconds")
    fun `provider breaker skips failed provider for sixty seconds`() =
        runTest {
            val fakeClock = MutableGeoIpClock()
            val calls = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpInfoGeoIpProvider("https://ipinfo.test"),
                            IpWhoGeoIpProvider("https://ipwho.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            calls += url.host
                            if (url.host == "ipinfo.test") {
                                ScriptedGeoIpConnection(url, 503, "unavailable")
                            } else {
                                ScriptedGeoIpConnection(url, 200, ipWhoFixture.replace("8.8.8.8", ipFromPath(url)))
                            }
                        },
                    clock = fakeClock,
                )

            repeat(3) { index -> assertNotNull(repo.lookup("8.8.8.${index + 1}")) }
            assertEquals(3, calls.count { it == "ipinfo.test" })

            assertNotNull(repo.lookup("8.8.8.4"))
            assertEquals(3, calls.count { it == "ipinfo.test" }, "Open provider must be skipped")
            assertEquals(4, calls.count { it == "ipwho.test" })

            fakeClock.nowNanos = 59_999_999_999L
            assertNotNull(repo.lookup("8.8.8.5"))
            assertEquals(3, calls.count { it == "ipinfo.test" }, "Provider remains open before 60 seconds")

            fakeClock.nowNanos = 60_000_000_000L
            assertNotNull(repo.lookup("8.8.8.6"))
            assertEquals(4, calls.count { it == "ipinfo.test" }, "Provider retries at the 60-second boundary")
        }

    @Test
    @DisplayName("a valid response resets a provider's partial failure streak")
    fun `provider success resets consecutive failure count`() =
        runTest {
            val calls = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpInfoGeoIpProvider("https://ipinfo.test"),
                            IpWhoGeoIpProvider("https://ipwho.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            calls += url.host
                            val ip = ipFromPath(url)
                            when {
                                url.host == "ipinfo.test" && ip == "8.8.8.3" -> {
                                    ScriptedGeoIpConnection(url, 200, ipInfoFixture.replace("8.8.8.8", ip))
                                }

                                url.host == "ipinfo.test" -> {
                                    ScriptedGeoIpConnection(url, 503, "unavailable")
                                }

                                else -> {
                                    ScriptedGeoIpConnection(url, 200, ipWhoFixture.replace("8.8.8.8", ip))
                                }
                            }
                        },
                    clock = MonotonicClock { System.nanoTime() },
                )

            assertNotNull(repo.lookup("8.8.8.1"))
            assertNotNull(repo.lookup("8.8.8.2"))
            assertNotNull(repo.lookup("8.8.8.3"))
            assertNotNull(repo.lookup("8.8.8.4"))
            assertNotNull(repo.lookup("8.8.8.5"))
            assertNotNull(repo.lookup("8.8.8.6"))
            assertEquals(6, calls.count { it == "ipinfo.test" })

            assertNotNull(repo.lookup("8.8.8.7"))
            assertEquals(6, calls.count { it == "ipinfo.test" }, "Three post-success failures open the breaker")
        }

    @Test
    @DisplayName("only one lookup probes a provider during half-open recovery")
    fun `provider breaker admits a single half-open request`() =
        runTest {
            val fakeClock = MutableGeoIpClock()
            val ipInfoCalls = AtomicInteger()
            val ipWhoCalls = AtomicInteger()
            val gate = AtomicReference<GatedGeoIpConnection?>()
            val gateCreated = CountDownLatch(1)
            val repo =
                GeoIpRepositoryImpl(
                    providers =
                        listOf(
                            IpInfoGeoIpProvider("https://ipinfo.test"),
                            IpWhoGeoIpProvider("https://ipwho.test"),
                        ),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            when (url.host) {
                                "ipinfo.test" -> {
                                    ipInfoCalls.incrementAndGet()
                                    if (ipFromPath(url) == "8.8.8.4") {
                                        GatedGeoIpConnection(url, ipInfoFixture.replace("8.8.8.8", "8.8.8.4"))
                                            .also {
                                                gate.set(it)
                                                gateCreated.countDown()
                                            }
                                    } else {
                                        ScriptedGeoIpConnection(url, 503, "unavailable")
                                    }
                                }

                                "ipwho.test" -> {
                                    ipWhoCalls.incrementAndGet()
                                    ScriptedGeoIpConnection(
                                        url,
                                        200,
                                        ipWhoFixture.replace("8.8.8.8", ipFromPath(url)),
                                    )
                                }

                                else -> {
                                    error("Unexpected provider ${url.host}")
                                }
                            }
                        },
                    clock = fakeClock,
                )

            repeat(3) { index -> assertNotNull(repo.lookup("8.8.8.${index + 1}")) }
            fakeClock.nowNanos = 60_000_000_000L

            val halfOpenLookup =
                async(Dispatchers.IO) {
                    repo.lookup("8.8.8.4", newSession(clock = fakeClock))
                }
            assertTrue(withContext(Dispatchers.IO) { gateCreated.await(2, TimeUnit.SECONDS) })
            val activeGate = checkNotNull(gate.get())
            assertTrue(withContext(Dispatchers.IO) { activeGate.readEntered.await(2, TimeUnit.SECONDS) })

            assertNotNull(repo.lookup("8.8.8.5"), "Concurrent lookup falls through to the healthy second provider")
            assertEquals(4, ipInfoCalls.get(), "Concurrent lookup must skip the in-flight half-open provider")
            assertEquals(4, ipWhoCalls.get(), "Concurrent lookup falls through to the healthy second provider")

            activeGate.release()
            assertNotNull(withContext(Dispatchers.IO) { halfOpenLookup.await() })
            assertEquals(4, ipInfoCalls.get(), "Only the single half-open request reached provider one")
        }

    @Test
    @DisplayName("cancelling an older lookup cannot release another lookup's half-open probe")
    @Suppress("LongMethod")
    fun `stale cancel keeps probe token`() =
        runTest {
            val fakeClock = MutableGeoIpClock()
            val ipInfoCalls = AtomicInteger()
            val staleGate = AtomicReference<GatedGeoIpConnection?>()
            val probeGate = AtomicReference<GatedGeoIpConnection?>()
            val staleGateCreated = CountDownLatch(1)
            val probeGateCreated = CountDownLatch(1)
            val repo =
                GeoIpRepositoryImpl(
                    providers = listOf(IpInfoGeoIpProvider("https://ipinfo.test")),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            ipInfoCalls.incrementAndGet()
                            when (ipFromPath(url)) {
                                "8.8.8.99" -> {
                                    GatedGeoIpConnection(
                                        url,
                                        ipInfoFixture.replace("8.8.8.8", "8.8.8.99"),
                                    ).also {
                                        staleGate.set(it)
                                        staleGateCreated.countDown()
                                    }
                                }

                                "8.8.8.4" -> {
                                    GatedGeoIpConnection(
                                        url,
                                        ipInfoFixture.replace("8.8.8.8", "8.8.8.4"),
                                    ).also {
                                        probeGate.set(it)
                                        probeGateCreated.countDown()
                                    }
                                }

                                else -> {
                                    ScriptedGeoIpConnection(url, 503, "unavailable")
                                }
                            }
                        },
                    clock = fakeClock,
                )

            val staleSession = newSession(clock = fakeClock, timeoutMillis = 120_000)
            val staleLookup = async(Dispatchers.IO) { repo.lookup("8.8.8.99", staleSession) }
            assertTrue(withContext(Dispatchers.IO) { staleGateCreated.await(2, TimeUnit.SECONDS) })
            assertTrue(
                withContext(Dispatchers.IO) { checkNotNull(staleGate.get()).readEntered.await(2, TimeUnit.SECONDS) },
            )

            repeat(3) { index -> assertNull(repo.lookup("8.8.8.${index + 1}", newSession(clock = fakeClock))) }
            fakeClock.nowNanos = 60_000_000_000L
            val probeLookup =
                async(Dispatchers.IO) {
                    repo.lookup("8.8.8.4", newSession(clock = fakeClock, timeoutMillis = 120_000))
                }
            assertTrue(withContext(Dispatchers.IO) { probeGateCreated.await(2, TimeUnit.SECONDS) })
            assertTrue(
                withContext(Dispatchers.IO) { checkNotNull(probeGate.get()).readEntered.await(2, TimeUnit.SECONDS) },
            )

            staleSession.cancel(CancellationReason.USER_STOP)
            checkNotNull(staleGate.get()).release()
            val staleFailure = runCatching { withTimeoutIo { staleLookup.await() } }.exceptionOrNull()
            assertTrue(staleFailure is CancellationException, "Expected stale lookup cancellation, got $staleFailure")

            assertNull(repo.lookup("8.8.8.5", newSession(clock = fakeClock, timeoutMillis = 120_000)))
            assertEquals(5, ipInfoCalls.get(), "The active half-open probe must keep exclusive ownership")

            checkNotNull(probeGate.get()).release()
            assertNotNull(withContext(Dispatchers.IO) { probeLookup.await() })
            assertEquals(5, ipInfoCalls.get(), "The successful probe is the only provider call after recovery")
        }

    @Test
    @DisplayName("multiple hop lookups join one caller-owned operation")
    fun `multiple lookups share one session`() =
        runTest {
            val calls = AtomicInteger()
            val baseUrl =
                startServer {
                    calls.incrementAndGet()
                    val ip = it.substringAfter('/').substringBefore('/')
                    200 to """{"ip":"$ip","country":"US","loc":"37.38,-122.08"}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)
            val session = newSession()

            val locations =
                OperationRunner.run(session) {
                    listOf(
                        repo.lookup("8.8.8.8", session),
                        repo.lookup("1.1.1.1", session),
                    )
                }

            assertEquals(2, locations.size)
            assertTrue(locations.all { it?.country == "United States" })
            assertEquals(2, calls.get())
            assertTrue(session.resources.isClosed)
        }

    @Test
    @DisplayName("lookup accepts a public response with bogon false")
    fun `lookup accepts explicit false bogon flag`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","city":"Mountain View","country":"US","loc":"37.38,-122.08","org":"AS15169 Google LLC","bogon":false}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            val result = repo.lookup("8.8.8.8")

            assertEquals("United States", result?.country)
            assertEquals("Mountain View", result?.city)
        }

    @Test
    @DisplayName("lookup returns null when bogon is explicitly true")
    fun `lookup rejects explicit true bogon flag`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","country":"US","loc":"37.38,-122.08","bogon":true}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            assertNull(repo.lookup("8.8.8.8"))
        }

    @Test
    @DisplayName("lookup accepts a response with no bogon field")
    fun `lookup accepts missing bogon flag`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","country":"US","loc":"37.38,-122.08"}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            assertEquals("United States", repo.lookup("8.8.8.8")?.country)
        }

    @Test
    @DisplayName("lookup ignores bogon text inside an escaped JSON string")
    fun `lookup ignores bogon text inside string`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","city":"Mountain View","note":"text says \"bogon\":true","country":"US","loc":"37.38,-122.08"}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            val result = repo.lookup("8.8.8.8")

            assertEquals("United States", result?.country)
            assertEquals("Mountain View", result?.city)
        }

    @Test
    @DisplayName("lookup ignores a nested bogon field")
    fun `lookup ignores nested bogon flag`() =
        runTest {
            val baseUrl =
                startServer {
                    200 to """{"ip":"8.8.8.8","meta":{"bogon":true},"country":"US","loc":"37.38,-122.08"}"""
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            assertEquals("United States", repo.lookup("8.8.8.8")?.country)
        }

    @Test
    @DisplayName("private and reserved IPv4 ranges are skipped without opening a connection")
    fun `private and reserved address cases short-circuit lookup`() =
        runTest {
            val connectionAttempts = AtomicInteger()
            val repo =
                GeoIpRepositoryImpl("https://example.invalid") {
                    connectionAttempts.incrementAndGet()
                    error("Must not connect")
                }

            listOf(
                "192.168.1.100", // RFC 1918 192.168/16
                "10.0.0.1", // RFC 1918 10/8
                "172.16.5.5", // RFC 1918 172.16/12
                "127.0.0.1", // loopback
                "169.254.1.1", // link-local
            ).forEach { ip ->
                assertNull(repo.lookup(ip), "Expected $ip to be skipped")
            }

            assertEquals(0, connectionAttempts.get(), "Private-range lookups must not open connections")
        }

    @Test
    @DisplayName("all non-public IPv4 special-use ranges are skipped before opening a connection")
    fun `special use IPv4 ranges short circuit lookup`() =
        runTest {
            val connectionAttempts = AtomicInteger()
            val repo =
                GeoIpRepositoryImpl("https://example.invalid") {
                    connectionAttempts.incrementAndGet()
                    error("Must not connect")
                }

            listOf(
                "100.64.0.1", // Shared address space / CGNAT (100.64.0.0/10)
                "100.127.255.254",
                "192.0.2.5", // Documentation (TEST-NET-1)
                "198.51.100.7", // Documentation (TEST-NET-2)
                "203.0.113.9", // Documentation (TEST-NET-3)
                "198.18.0.1", // Benchmarking (198.18.0.0/15)
                "198.19.255.254",
                "224.0.0.1", // Multicast (224.0.0.0/4)
                "239.255.255.250",
            ).forEach { ip ->
                assertNull(repo.lookup(ip), "Expected special-use address $ip to be skipped")
            }

            assertEquals(0, connectionAttempts.get(), "Special-use IPv4 addresses must not open connections")
        }

    @Test
    @DisplayName("non-public IPv6 ranges and IPv4-mapped private addresses are skipped")
    fun `special use IPv6 and mapped IPv4 ranges short circuit lookup`() =
        runTest {
            val connectionAttempts = AtomicInteger()
            val repo =
                GeoIpRepositoryImpl("https://example.invalid") {
                    connectionAttempts.incrementAndGet()
                    error("Must not connect")
                }

            listOf(
                "::1", // IPv6 loopback
                "fe80::1", // IPv6 link-local
                "febf:ffff::1", // Last address in fe80::/10
                "fc00::1", // IPv6 unique-local
                "fdff:ffff::1", // Last address in fc00::/7
                "2001:db8::1", // IPv6 documentation (2001:db8::/32)
                "::ffff:10.0.0.1", // IPv4-mapped RFC 1918 address
                "::ffff:100.64.0.1", // IPv4-mapped CGNAT address
                "::ffff:192.0.2.1", // IPv4-mapped documentation address
                "64:ff9b::a00:1", // NAT64 with embedded RFC 1918 address
                "64:ff9b::c000:201", // NAT64 with embedded documentation address
            ).forEach { ip ->
                assertNull(repo.lookup(ip), "Expected special-use IPv6/mapped address $ip to be skipped")
            }

            assertEquals(0, connectionAttempts.get(), "Special-use IPv6 addresses must not open connections")
        }

    @Test
    @DisplayName("public IPv4 and IPv6 literals reach the injected connection factory")
    fun `public address literals are passed to the connection factory`() =
        runTest {
            val requestedPaths = mutableListOf<String>()
            val repo =
                GeoIpRepositoryImpl(
                    baseUrl = "https://geo.test",
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            requestedPaths += url.path
                            ResponseGeoIpConnection(url)
                        },
                )

            assertEquals("United States", repo.lookup("8.8.8.8")?.country)
            assertEquals("United States", repo.lookup("2001:4860:4860::8888")?.country)
            assertEquals("United States", repo.lookup("::ffff:8.8.8.8")?.country)
            assertEquals(
                listOf("/8.8.8.8/json", "/2001:4860:4860::8888/json", "/::ffff:8.8.8.8/json"),
                requestedPaths,
                "Public IPv4, IPv6, and IPv4-mapped IPv6 literals must reach the injected transport",
            )
        }

    @Test
    @DisplayName("malformed address input fails closed before opening a connection")
    fun `malformed address inputs do not reach DNS or connection factory`() =
        runTest {
            val connectionAttempts = AtomicInteger()
            val repo =
                GeoIpRepositoryImpl("https://example.invalid") {
                    connectionAttempts.incrementAndGet()
                    error("Malformed addresses must be rejected before transport")
                }

            listOf(
                "not-an-address.example",
                "bad host",
                "256.1.1.1",
                "8.8.8.999",
                "2001:::1",
            ).forEach { input ->
                assertNull(repo.lookup(input), "Malformed input $input must fail closed")
            }

            assertEquals(0, connectionAttempts.get(), "Malformed addresses must not cause DNS/network activity")
        }

    @Test
    @DisplayName("Stop disconnects a blocked GeoIP response and remains typed cancellation")
    fun `stop closes blocked response once without a late location`() =
        runTest {
            val connection = BlockingGeoIpConnection()
            val repo =
                GeoIpRepositoryImpl(
                    baseUrl = "https://ipinfo.io",
                    connectionFactory = GeoIpConnectionFactory { connection },
                )
            val session = newSession()
            val lookup = async(Dispatchers.IO) { repo.lookup("8.8.8.8", session) }

            assertTrue(withContext(Dispatchers.IO) { connection.readEntered.await(2, TimeUnit.SECONDS) })
            session.cancel(CancellationReason.USER_STOP)
            val failure = runCatching { withTimeoutIo { lookup.await() } }.exceptionOrNull()

            assertTrue(failure is CancellationException)
            assertEquals(CancellationReason.USER_STOP, session.cancellationReason)
            assertEquals(1, connection.disconnectCount.get())
        }

    @Test
    @DisplayName("deadline checks close-induced I/O before returning optional GeoIP data")
    fun `expired caller deadline remains typed and closes connection once`() =
        runTest {
            val fakeClock = MutableGeoIpClock()
            val connection = ExpiringGeoIpConnection(fakeClock)
            val repo =
                GeoIpRepositoryImpl(
                    providers = listOf(IpInfoGeoIpProvider("https://ipinfo.io")),
                    connectionFactory = GeoIpConnectionFactory { connection },
                    clock = fakeClock,
                )
            val session = newSession(clock = fakeClock)

            val failure = runCatching { repo.lookup("8.8.8.8", session) }.exceptionOrNull()

            assertTrue(
                failure is OperationDeadlineExceededException ||
                    (failure is OperationCancellationException && failure.reason == CancellationReason.DEADLINE_EXCEEDED),
                "Expected typed deadline, got ${failure?.javaClass?.name}: ${failure?.message}",
            )
            assertEquals(1, connection.disconnectCount.get())
        }

    @Test
    @DisplayName("typed cancellation and deadline do not increment provider failure state")
    fun `cancellation and deadline are excluded from provider failures`() =
        runTest {
            assertAbortDoesNotIncrementProviderFailures(deadline = false)
            assertAbortDoesNotIncrementProviderFailures(deadline = true)
        }

    private suspend fun TestScope.assertAbortDoesNotIncrementProviderFailures(deadline: Boolean) =
        supervisorScope {
            val fakeClock = MutableGeoIpClock()
            val firstConnection = if (deadline) null else BlockingGeoIpConnection()
            val connectionAttempts = AtomicInteger()
            val repo =
                GeoIpRepositoryImpl(
                    providers = listOf(IpInfoGeoIpProvider("https://ipinfo.test")),
                    connectionFactory =
                        GeoIpConnectionFactory { url ->
                            if (connectionAttempts.incrementAndGet() == 1) {
                                firstConnection ?: ExpiringGeoIpConnection(fakeClock)
                            } else {
                                ScriptedGeoIpConnection(url, 503, "unavailable")
                            }
                        },
                    clock = fakeClock,
                )
            val session = newSession(clock = fakeClock, timeoutMillis = 5_000)
            val firstLookup = async(Dispatchers.IO) { repo.lookup("8.8.8.1", session) }

            val failure =
                if (deadline) {
                    runCatching { withTimeoutIo { firstLookup.await() } }.exceptionOrNull()
                } else {
                    val blocked = checkNotNull(firstConnection)
                    assertTrue(withContext(Dispatchers.IO) { blocked.readEntered.await(2, TimeUnit.SECONDS) })
                    session.cancel(CancellationReason.USER_STOP)
                    runCatching { withTimeoutIo { firstLookup.await() } }.exceptionOrNull()
                }
            if (deadline) {
                assertTrue(
                    failure is OperationDeadlineExceededException ||
                        (failure is OperationCancellationException && failure.reason == CancellationReason.DEADLINE_EXCEEDED),
                    "Expected typed deadline, got $failure",
                )
            } else {
                assertTrue(failure is CancellationException, "Expected typed cancellation, got $failure")
            }

            assertNull(repo.lookup("8.8.8.2"))
            assertNull(repo.lookup("8.8.8.3"))
            assertNull(repo.lookup("8.8.8.4"))
            assertEquals(4, connectionAttempts.get(), "Three actual failures should be needed to open the breaker")
            assertNull(repo.lookup("8.8.8.5"))
            assertEquals(4, connectionAttempts.get(), "Provider should be open after three transport failures")
        }

    @Test
    @DisplayName("lookup rejects a response body beyond its operation byte limit")
    fun `oversized body is not parsed or cached`() =
        runTest {
            val calls = AtomicInteger()
            val response = """{"city":"${"c".repeat(66_000)}","country":"US","loc":"37.38,-122.08"}"""
            val baseUrl =
                startServer {
                    calls.incrementAndGet()
                    200 to response
                }
            val repo = GeoIpRepositoryImpl(baseUrl = baseUrl)

            assertNull(repo.lookup("8.8.8.8"))
            assertNull(repo.lookup("8.8.8.8"))
            assertEquals(2, calls.get())
        }

    private fun newSession(
        clock: MonotonicClock = MonotonicClock { System.nanoTime() },
        timeoutMillis: Long = 5_000L,
    ) = OperationSession(
        OperationBudget.start(
            requirement = OperationRequirement.INTERNET,
            timeoutMillis = timeoutMillis,
            maxConcurrentProbes = 1,
            maxResponseBytes = 65_536,
            clock = clock,
        ),
    )

    private suspend fun <T> withTimeoutIo(block: suspend () -> T): T =
        withContext(Dispatchers.IO) {
            kotlinx.coroutines.withTimeout(2_000) { block() }
        }

    private class MutableGeoIpClock : MonotonicClock {
        @Volatile var nowNanos: Long = 0

        override fun nowNanos(): Long = nowNanos
    }

    private open class FakeGeoIpConnection(
        url: URL,
    ) : HttpURLConnection(url) {
        val disconnectCount = AtomicInteger()

        override fun connect() = Unit

        override fun disconnect() {
            disconnectCount.incrementAndGet()
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int = HTTP_OK
    }

    private class ScriptedGeoIpConnection(
        url: URL,
        private val status: Int,
        private val body: String,
    ) : FakeGeoIpConnection(url) {
        override fun getResponseCode(): Int = status

        override fun getInputStream(): InputStream = body.byteInputStream()
    }

    private class GatedGeoIpConnection(
        url: URL,
        body: String,
    ) : FakeGeoIpConnection(url) {
        val readEntered = CountDownLatch(1)
        private val released = CountDownLatch(1)
        private val delegate = body.byteInputStream()

        fun release() = released.countDown()

        override fun getInputStream(): InputStream =
            object : InputStream() {
                override fun read(): Int {
                    readEntered.countDown()
                    released.await(2, TimeUnit.SECONDS)
                    return delegate.read()
                }

                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    readEntered.countDown()
                    released.await(2, TimeUnit.SECONDS)
                    return delegate.read(buffer, offset, length)
                }
            }
    }

    private class ResponseGeoIpConnection(
        url: URL,
    ) : FakeGeoIpConnection(url) {
        override fun getInputStream(): InputStream {
            val response = """{"ip":"${ipFromPath(url)}","country":"US","loc":"37.38,-122.08"}"""
            return response.byteInputStream()
        }
    }

    private class BlockingGeoIpConnection : FakeGeoIpConnection(URL("https://ipinfo.io/8.8.8.8/json")) {
        val readEntered = CountDownLatch(1)
        private val disconnected = CountDownLatch(1)
        private val response =
            object : InputStream() {
                override fun read(): Int {
                    readEntered.countDown()
                    disconnected.await(2, TimeUnit.SECONDS)
                    throw IOException("connection closed while reading")
                }

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = read()
            }

        override fun getInputStream(): InputStream = response

        override fun disconnect() {
            super.disconnect()
            disconnected.countDown()
        }
    }

    private class ExpiringGeoIpConnection(
        private val clock: MutableGeoIpClock,
    ) : FakeGeoIpConnection(URL("https://ipinfo.io/8.8.8.8/json")) {
        private val response =
            object : InputStream() {
                override fun read(): Int {
                    clock.nowNanos = 5_000_000_000L
                    throw IOException("socket closed at deadline")
                }

                override fun read(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int = read()
            }

        override fun getInputStream(): InputStream = response
    }
}

private fun ipFromPath(url: URL): String = url.path.removePrefix("/").substringBefore('/')
