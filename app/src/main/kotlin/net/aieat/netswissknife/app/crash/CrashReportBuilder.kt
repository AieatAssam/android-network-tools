package net.aieat.netswissknife.app.crash

/** Sanitized crash data that is safe to put in an Activity Intent. */
internal data class CrashReport(
    val stackTrace: String,
    val exceptionClass: String,
    val exceptionMessage: String,
    val threadName: String,
    val timestamp: String,
)

/** Pure, bounded crash-report formatting and redaction. */
internal object CrashReportBuilder {
    const val MAX_REPORT_BYTES = 48 * 1024
    private const val STACK_LIMIT_BYTES = 36 * 1024
    private const val MESSAGE_LIMIT_BYTES = 6 * 1024
    private const val FIELD_LIMIT_BYTES = 512
    private const val MAX_CAUSE_DEPTH = 8
    private const val MAX_FRAMES = 160

    private val lineSeparators = Regex("[\\r\\n\\t\\u0085\\u2028\\u2029]+")
    private val url = Regex("(?i)\\b[A-Za-z][A-Za-z0-9+.-]*://[^\\s<>\\\"]+")
    private val authScheme = Regex("(?i)\\b(?:Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+")
    private val sensitiveHeader = Regex(
        "(?im)\\b(authorization|proxy-authorization|cookie|set-cookie)[ \\t]*[:=](?:[ \\t]*[^ \\t\\r\\n][^\\r\\n]*(?:\\r?\\n[ \\t]+[^\\r\\n]*)*|[ \\t]*\\r?\\n[^\\r\\n]*(?:\\r?\\n[ \\t]+[^\\r\\n]*)*)?",
    )
    private val secretAssignment = Regex(
        "(?i)(?<![A-Za-z0-9_])[\\\"']?(authorization|proxy-authorization|cookie|set-cookie|password|passwd|pwd|token|access[_-]?token|refresh[_-]?token|api[_-]?key|secret)[\\\"']?\\s*[:=]\\s*(?:\\\"(?:\\\\.|[^\\\"\\\\])*\\\"|'(?:\\\\.|[^'\\\\])*'|[^\\s,;}]+)"
    )
    private val ipv4OrCidr = Regex("(?<![A-Za-z0-9_.])(?:\\d{1,3}\\.){3}\\d{1,3}(?:/\\d{1,2})?(?![A-Za-z0-9_.])")
    private val ipv6OrCidr = Regex("(?i)(?<![0-9a-f:])(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}(?:/\\d{1,3})?(?![0-9a-f:])")
    // Absolute paths can contain spaces. Once one is seen, redact to end of line rather than
    // guessing whether a later word belongs to the path or to the surrounding message.
    private val unixPath = Regex("(?<![A-Za-z0-9])/(?:[^\\r\\n]*)")
    private val windowsOrUncPath = Regex("(?i)(?<![A-Za-z0-9])(?:[A-Z]:\\\\|\\\\\\\\)[^\\r\\n]*")
    private val bearerToken = Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]+")

    fun build(thread: Thread, throwable: Throwable, timestamp: String): CrashReport {
        val stack = buildStack(throwable)
        return CrashReport(
            stackTrace = sanitize(stack, STACK_LIMIT_BYTES),
            exceptionClass = sanitize(throwable.javaClass.name, FIELD_LIMIT_BYTES),
            exceptionMessage = sanitize(throwable.message ?: "No message", MESSAGE_LIMIT_BYTES),
            threadName = sanitize(thread.name ?: "unknown", FIELD_LIMIT_BYTES),
            timestamp = sanitize(timestamp, FIELD_LIMIT_BYTES),
        )
    }

    fun buildFullReport(report: CrashReport): String {
        val safeStack = sanitize(report.stackTrace, STACK_LIMIT_BYTES)
        val safeClass = sanitize(report.exceptionClass, FIELD_LIMIT_BYTES)
        val safeMessage = sanitize(report.exceptionMessage, MESSAGE_LIMIT_BYTES)
        val safeThread = sanitize(report.threadName, FIELD_LIMIT_BYTES)
        val safeTimestamp = sanitize(report.timestamp, FIELD_LIMIT_BYTES)
        val assembled = buildString {
            appendLine("=== Net Swiss Knife Crash Report ===")
            appendLine("Time:      $safeTimestamp")
            appendLine("Thread:    $safeThread")
            appendLine("Exception: $safeClass")
            appendLine("Message:   $safeMessage")
            appendLine()
            appendLine("--- Stack Trace ---")
            appendLine(safeStack)
        }
        return sanitize(assembled, MAX_REPORT_BYTES)
    }

    /** Safe short value for the crash hook's metadata-only log entry. */
    fun safeMetadata(value: String, maxBytes: Int = FIELD_LIMIT_BYTES): String =
        sanitize(value, maxBytes).replace(lineSeparators, " ")

    private fun buildStack(root: Throwable): String = buildString {
        var current: Throwable? = root
        var depth = 0
        var frames = 0
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        while (current != null && depth < MAX_CAUSE_DEPTH && seen.add(current) && frames < MAX_FRAMES) {
            if (depth > 0) append("Caused by: ")
            append(sanitize(current.javaClass.name, FIELD_LIMIT_BYTES))
            val message = current.message
            if (!message.isNullOrEmpty()) append(": ").append(sanitize(message, 1024))
            appendLine()
            // Throwable.stackTrace returns a defensive copy; retrieve it once and never retain it.
            val trace = current.stackTrace
            for (frame in trace) {
                if (frames >= MAX_FRAMES) {
                    appendLine("    ... stack truncated ...")
                    return@buildString
                }
                append("    at ").append(sanitize(frame.className, FIELD_LIMIT_BYTES))
                    .append('.').append(sanitize(frame.methodName, FIELD_LIMIT_BYTES))
                append('(').append(sanitize(frame.fileName ?: "Unknown Source", FIELD_LIMIT_BYTES))
                if (frame.lineNumber >= 0) append(':').append(frame.lineNumber)
                appendLine(")")
                frames++
                // Keep intermediate construction bounded even for synthetic huge traces.
                if (length >= STACK_LIMIT_BYTES * 2) {
                    appendLine("    ... stack truncated ...")
                    return@buildString
                }
            }
            current = current.cause
            depth++
        }
        if (current != null) appendLine("Caused by: ... causes truncated ...")
    }

    private fun sanitize(input: String, maxBytes: Int): String {
        // Limit work on attacker-controlled exception messages before regex processing.
        val boundedInput = normalizeUnicodeAndControls(input.take(maxBytes * 2))
        val redacted = boundedInput
            .replace(url, "[URL REDACTED]")
            .replace(sensitiveHeader) { match ->
                "${match.groupValues[1]}: [REDACTED]" + "\n".repeat(match.value.count { it.code == 10 })
            }
            .replace(secretAssignment) { match -> "${match.groupValues[1]}=[REDACTED]" }
            .replace(authScheme, "[AUTH REDACTED]")
            .replace(bearerToken, "[AUTH REDACTED]")
            .replace(ipv4OrCidr, "[IP REDACTED]")
            .replace(ipv6OrCidr, "[IP REDACTED]")
            .replace(windowsOrUncPath, "[PATH REDACTED]")
            .replace(unixPath, "[PATH REDACTED]")
        return truncateUtf8(redacted, maxBytes)
    }

    private fun normalizeUnicodeAndControls(input: String): String = buildString(input.length) {
        var index = 0
        while (index < input.length) {
            val ch = input[index]
            when {
                ch.isHighSurrogate() -> {
                    if (index + 1 < input.length && input[index + 1].isLowSurrogate()) {
                        append(ch).append(input[index + 1])
                        index++
                    } else append('\uFFFD')
                }
                ch.isLowSurrogate() -> append('\uFFFD')
                ch == '\n' || ch == '\r' || ch == '\t' || !ch.isISOControl() -> append(ch)
                else -> append(' ')
            }
            index++
        }
    }

    private fun truncateUtf8(value: String, maxBytes: Int): String {
        if (value.toByteArray(Charsets.UTF_8).size <= maxBytes) return value
        val marker = "… [truncated]"
        val budget = (maxBytes - marker.toByteArray(Charsets.UTF_8).size).coerceAtLeast(0)
        val result = StringBuilder()
        var used = 0
        var index = 0
        while (index < value.length) {
            val cp = value.codePointAt(index)
            val chars = Character.charCount(cp)
            val bytes = when {
                cp <= 0x7f -> 1
                cp <= 0x7ff -> 2
                cp <= 0xffff -> 3
                else -> 4
            }
            if (used + bytes > budget) break
            result.appendCodePoint(cp)
            used += bytes
            index += chars
        }
        return result.append(marker).toString()
    }
}
