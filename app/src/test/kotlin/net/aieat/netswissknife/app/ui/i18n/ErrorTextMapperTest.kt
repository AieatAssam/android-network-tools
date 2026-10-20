package net.aieat.netswissknife.app.ui.i18n

import java.io.File
import net.aieat.netswissknife.app.R
import net.aieat.netswissknife.core.network.ErrorCode
import net.aieat.netswissknife.core.network.ErrorInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ErrorTextMapperTest {
    /** Representative argument lists audited from core ErrorInfo construction sites. */
    private val constructionArguments = mapOf(
        ErrorCode.HOST_INVALID to listOf("invalid.example"),
        ErrorCode.COUNT_OUT_OF_RANGE to listOf(1, 100),
        ErrorCode.CONCURRENCY_OUT_OF_RANGE to listOf(1, 500),
        ErrorCode.DOMAIN_TOO_LONG to listOf(253),
        ErrorCode.TIMEOUT_OUT_OF_RANGE to listOf(100, 30_000),
        ErrorCode.RESPONSE_SIZE_OUT_OF_RANGE to listOf(0, 1_048_576),
        ErrorCode.PORT_OUT_OF_RANGE to listOf(443, 1, 65_535),
        ErrorCode.PORT_RANGE_INVERTED to listOf(1, 65_535),
        ErrorCode.PORT_RANGE_TOO_LARGE to listOf(10_000, 10_001),
        ErrorCode.MAX_HOPS_OUT_OF_RANGE to listOf(1, 64),
        ErrorCode.PROBES_OUT_OF_RANGE to listOf(1, 5),
        ErrorCode.PACKET_SIZE_OUT_OF_RANGE to listOf(0, 28, 1_472),
        ErrorCode.PAYLOAD_OUT_OF_RANGE to listOf(0, 1_472),
        ErrorCode.TTL_OUT_OF_RANGE to listOf(1, 255),
        ErrorCode.INTERVAL_OUT_OF_RANGE to listOf(100, 10_000),
        ErrorCode.RETRIES_OUT_OF_RANGE to listOf(1, 5),
    )

    @Test
    fun `every coded error maps to a resource with its construction argument arity`() {
        ErrorCode.entries.forEach { code ->
            val args = constructionArguments[code].orEmpty()
            assertEquals(args.size, expectedArgumentCount(code), "$code argument contract drifted")
            val mapped = ErrorTextMapper.map(ErrorInfo(code, args), "developer copy")
            if (code == ErrorCode.UNKNOWN) {
                assertEquals(UiText.Plain("developer copy"), mapped)
                return@forEach
            }
            assertTrue(mapped is UiText.Res, "$code was not mapped to a resource")
            mapped as UiText.Res
            assertTrue(mapped.id != 0, "$code mapped to resource id zero")
            assertEquals(args, mapped.args, "$code arguments were not forwarded in order")
        }
    }

    @Test
    fun `error resource placeholders match typed construction argument arity`() {
        val stringsXml = File(repositoryRoot(), "app/src/main/res/values/strings.xml").readText()

        ErrorCode.entries.forEach { code ->
            val name = "err_${code.name.lowercase()}"
            val body = Regex("<string name=\"$name\">([^<]*)</string>")
                .find(stringsXml)
                ?.groupValues
                ?.get(1)
            assertTrue(body != null, "Missing resource $name")

            val placeholders = Regex("%([1-9][0-9]*)\\$([sd])")
                .findAll(body!!)
                .associate { it.groupValues[1].toInt() to it.groupValues[2] }
            val expectedArity = expectedArgumentCount(code)
            assertEquals(
                constructionArguments[code].orEmpty().size,
                expectedArity,
                "$code placeholder contract does not match its core construction sites",
            )
            assertEquals(
                (1..expectedArity).toList(),
                placeholders.keys.sorted(),
                "$name format placeholders do not match ErrorInfo.args arity $expectedArity",
            )
            formatArgumentTypes(code).forEachIndexed { index, argumentType ->
                val expectedFormat = when (argumentType) {
                    FormatArgumentType.STRING -> "s"
                    FormatArgumentType.INTEGER -> "d"
                }
                assertEquals(expectedFormat, placeholders[index + 1], "$name argument ${index + 1} type mismatch")
            }
        }
    }

    @Test
    fun `missing typed information keeps the provided fallback`() {
        assertEquals(UiText.Plain("legacy copy"), ErrorTextMapper.map(null, "legacy copy"))
    }

    @Test
    fun `typed error forwards arguments and diagnostic fallback`() {
        val mapped = ErrorTextMapper.map(
            ErrorInfo(ErrorCode.TIMEOUT_OUT_OF_RANGE, listOf(100, 30_000), "timeout diagnostic"),
            "timeout diagnostic",
        )

        assertEquals(
            UiText.Res(
                id = R.string.err_timeout_out_of_range,
                args = listOf(100, 30_000),
                developerFallback = "timeout diagnostic",
            ),
            mapped,
        )
    }

    @Test
    fun `argument arity mismatch keeps developer copy instead of formatting`() {
        val mapped = ErrorTextMapper.map(
            ErrorInfo(
                ErrorCode.PORT_OUT_OF_RANGE,
                args = listOf(1, 65_535),
                developerMessage = "Port must be between 1 and 65535",
            ),
            fallback = "fallback",
        )

        assertEquals(UiText.Plain("Port must be between 1 and 65535"), mapped)
    }

    @Test
    fun `same arity numeric argument with wrong type keeps developer copy`() {
        val mapped = ErrorTextMapper.map(
            ErrorInfo(
                ErrorCode.TIMEOUT_OUT_OF_RANGE,
                args = listOf("100", 30_000),
                developerMessage = "Timeout must be between 100 and 30000 ms",
            ),
            fallback = "fallback",
        )

        assertEquals(UiText.Plain("Timeout must be between 100 and 30000 ms"), mapped)
    }

    @Test
    fun `null format argument keeps developer copy`() {
        val mapped = ErrorTextMapper.map(
            ErrorInfo(
                ErrorCode.TIMEOUT_OUT_OF_RANGE,
                args = listOf(null, 30_000),
                developerMessage = "Timeout bounds are unavailable",
            ),
            fallback = "fallback",
        )

        assertEquals(UiText.Plain("Timeout bounds are unavailable"), mapped)
    }

    @Test
    fun `host argument with non-string type keeps developer copy`() {
        val mapped = ErrorTextMapper.map(
            ErrorInfo(
                ErrorCode.HOST_INVALID,
                args = listOf(42),
                developerMessage = "Invalid host",
            ),
            fallback = "fallback",
        )

        assertEquals(UiText.Plain("Invalid host"), mapped)
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (directory.resolve("PRIVACY_POLICY.md").isFile) return directory
            directory = directory.parentFile ?: error("Could not find repository root")
        }
    }
}
