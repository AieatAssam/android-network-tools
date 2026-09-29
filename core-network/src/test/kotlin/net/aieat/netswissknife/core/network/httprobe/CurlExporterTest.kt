package net.aieat.netswissknife.core.network.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CurlExporterTest {
    @Test
    fun `HEAD export uses curl header-only mode and follows safe redirects`() {
        val request =
            HttpProbeRequest(
                url = "https://example.test/resource",
                method = HttpMethod.HEAD,
            )
        val command = CurlExporter.build(request)

        assertEquals(
            "curl --head --proto '=https' --proto-redir '=https' " +
                "--max-redirs 10 -L 'https://example.test/resource'",
            command,
        )
        assertFalse(command.contains("-X 'HEAD'"))
    }

    @Test
    fun `HEAD export retains custom headers and explicit no-redirect setting`() {
        val request =
            HttpProbeRequest(
                url = "https://example.test/resource",
                method = HttpMethod.HEAD,
                headers = listOf("X-Probe" to "check"),
                followRedirects = false,
            )

        val command = CurlExporter.build(request)

        assertEquals(
            "curl --head -H 'X-Probe: check' --proto '=https' 'https://example.test/resource'",
            command,
        )
        assertFalse(command.contains("-X 'HEAD'"))
        assertFalse(command.contains(" -L"))
    }

    @Test
    fun `HEAD export suppresses redirects when custom headers are present`() {
        val request =
            HttpProbeRequest(
                url = "https://example.test/resource",
                method = HttpMethod.HEAD,
                headers = listOf("Authorization" to "Bearer private-token"),
                followRedirects = true,
            )

        val command = CurlExporter.build(request)

        assertTrue(command.startsWith("curl --head -H 'Authorization: Bearer private-token'"))
        assertFalse(command.contains("-X 'HEAD'"))
        assertFalse(command.contains(" -L"))
        assertTrue(CurlExporter.redirectFollowingSuppressed(request))
    }
}
