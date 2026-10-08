package net.aieat.netswissknife.app.crash

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CrashReportBuilderTest {
    @Test
    fun boundsAndRedactsHugeMessagesCausesAndFrames() {
        val cause = IllegalStateException(
            "Authorization: Bearer bearer-secret\nCookie: sid=cookie-secret" +
                "\npassword=pass-secret https://alice:url-secret@example.test/path?token=query-secret " +
                "192.168.1.44 10.0.0.0/24 2001:db8::5/64\n" +
                "at /home/alice/private/config " + "x".repeat(2_000_000),
        ).apply {
            stackTrace = Array(5_000) { index ->
                StackTraceElement("example.Class$index", "method", "/Users/alice/source.kt", index)
            }
        }
        val malformedUnicode = String(charArrayOf(0xD800.toChar()))
        val outer = RuntimeException("outer $malformedUnicode " + "m".repeat(2_000_000), cause)
        val report = CrashReportBuilder.build(
            thread = Thread("worker 10.0.0.4 /home/user/private"),
            throwable = outer,
            timestamp = "2026-09-24 00:00:00",
        )
        val intentFields = listOf(
            report.stackTrace, report.exceptionClass, report.exceptionMessage, report.threadName, report.timestamp,
        )
        val extras = intentFields.sumOf { it.toByteArray(Charsets.UTF_8).size }
        assertTrue(extras <= CrashReportBuilder.MAX_REPORT_BYTES)
        assertTrue(report.stackTrace.contains("[truncated]"))
        assertTrue(report.stackTrace.contains("[AUTH REDACTED]") || report.stackTrace.contains("[REDACTED]"))
        assertTrue(
            report.stackTrace.contains("[URL REDACTED]"),
            "Missing URL redaction in stack tail: ${report.stackTrace.takeLast(2_000)}",
        )
        assertTrue(report.stackTrace.contains("[PATH REDACTED]"))
        assertTrue(report.stackTrace.contains("[IP REDACTED]"))
        assertTrue(report.exceptionMessage.contains("[truncated]"))
        listOf(
            "bearer-secret", "cookie-secret", "pass-secret", "query-secret",
            "192.168.1.44", "2001:db8::5", "/home/alice/private",
            "10.0.0.4", "/home/user/private",
        ).forEach { secret ->
            intentFields.forEach { field -> assertFalse(field.contains(secret), "leaked $secret in Intent field") }
        }
        assertFalse(report.exceptionMessage.contains(malformedUnicode))
    }

    @Test
    fun authorizationAndCookieHeaderValuesAreFullyRedacted() {
        val report = CrashReportBuilder.build(
            Thread("main"),
            IllegalArgumentException(
                "Authorization: Bearer exact-bearer-secret\n" +
                    "Proxy-Authorization: Basic exact-basic-secret\n" +
                    "Cookie: session=exact-cookie-secret; theme=dark\n" +
                    "Set-Cookie: id=exact-set-cookie-secret; Secure\n" +
                    "Authorization=Bearer equals-bearer-secret Cookie=session=equals-cookie-secret",
            ),
            "now",
        )
        val combined = report.exceptionMessage + report.stackTrace
        listOf(
            "exact-bearer-secret", "exact-basic-secret", "exact-cookie-secret", "exact-set-cookie-secret",
            "equals-bearer-secret", "equals-cookie-secret",
        ).forEach { assertFalse(combined.contains(it), "leaked $it") }
        assertTrue(combined.contains("authorization=[REDACTED]", ignoreCase = true))
        assertTrue(combined.contains("cookie=[REDACTED]", ignoreCase = true))
    }

    @Test
    fun redactsQuotedJsonSecretKeysAndEveryUriScheme() {
        val report = CrashReportBuilder.build(
            Thread("main"),
            IllegalArgumentException(
                """{"password":"json-password","token":"json-token"} """ +
                    """{"password":"escaped-prefix\"escaped-suffix"} """ +
                    "ssh://alice:ssh-password@host.example/home/alice " +
                    "postgresql://dbuser:db-password@db.example/private?password=query-password",
            ),
            "now",
        )
        listOf(
            "json-password", "json-token", "escaped-prefix", "escaped-suffix", "ssh-password",
            "db-password", "query-password", "alice:",
        ).forEach { assertFalse(report.exceptionMessage.contains(it), "leaked $it") }
        assertTrue(report.exceptionMessage.contains("[URL REDACTED]"))
    }

    @Test
    fun redactsUnixWindowsAndUncPathsIncludingSpaces() {
        val message = listOf(
            "unix=/home/Pat Smith/Private Documents/secret file.txt",
            "windows=C:\\Users\\Pat Smith\\App Data\\secret file.txt",
            "unc=\\\\server01\\team share\\Pat Smith\\secret file.txt",
        ).joinToString("\n")
        val report = CrashReportBuilder.build(Thread("main"), IllegalStateException(message), "now")

        assertTrue(report.exceptionMessage.lines().count { it.contains("[PATH REDACTED]") } == 3)
        listOf("Pat Smith", "Private Documents", "App Data", "team share", "secret file.txt")
            .forEach { assertFalse(report.exceptionMessage.contains(it), "leaked path text $it") }
    }

    @Test
    fun redactsFoldedAuthorizationAndCookieContinuationsAndFlattensMetadata() {
        val report = CrashReportBuilder.build(
            Thread("main"),
            IllegalArgumentException(
                "Authorization: Bearer header-token\n    folded-auth-secret\n" +
                    "Cookie: sid=cookie-one;\n\ttheme=dark; folded-cookie-secret\n" +
                    "Cookie:\nsid=empty-header-one; session=empty-header-two\n" +
                    "Authorization:\nBearer empty-auth-token",
            ),
            "now",
        )
        listOf(
            "header-token", "folded-auth-secret", "cookie-one", "folded-cookie-secret",
            "empty-header-one", "empty-header-two", "empty-auth-token",
        )
            .forEach { assertFalse(report.exceptionMessage.contains(it), "leaked $it") }

        val malformedUnicode = String(charArrayOf(0xD800.toChar()))
        val metadata = CrashReportBuilder.safeMetadata(
            "worker" + malformedUnicode + "\nCookie:\nsid=one; session=two",
        )
        assertFalse(metadata.contains("\n"))
        assertFalse(metadata.contains(malformedUnicode))
        assertFalse(metadata.contains("sid=one"))
        assertFalse(metadata.contains("session=two"))

        val oversizedMetadata = CrashReportBuilder.safeMetadata("x".repeat(100_000))
        assertTrue(oversizedMetadata.toByteArray(Charsets.UTF_8).size <= 512)
        assertTrue(oversizedMetadata.contains("[truncated]"))
    }

    @Test
    fun copiedAndSharedReportIsRedactedAndHasStrictByteCap() {
        val full = CrashReportBuilder.buildFullReport(
            CrashReport(
                stackTrace = "https://user:pw@example.test/?token=secret " + "z".repeat(100_000),
                exceptionClass = "Problem",
                exceptionMessage = "password=hunter2",
                threadName = "main",
                timestamp = "now",
            ),
        )
        assertTrue(full.toByteArray(Charsets.UTF_8).size <= CrashReportBuilder.MAX_REPORT_BYTES)
        assertFalse(full.contains("hunter2"))
        assertFalse(full.contains("user:pw"))
        assertFalse(full.contains("token=secret"))
    }
}
