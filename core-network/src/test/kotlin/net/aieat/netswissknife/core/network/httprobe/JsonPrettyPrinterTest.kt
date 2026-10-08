package net.aieat.netswissknife.core.network.httprobe

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JsonPrettyPrinterTest {
    @Test
    fun `formats nested objects arrays and empty containers`() {
        val input = """{"emptyObject":{},"emptyArray":[],"list":[1,{"flag":true,"items":[null,{}]}]}"""
        val expected =
            """
            {
              "emptyObject": {},
              "emptyArray": [],
              "list": [
                1,
                {
                  "flag": true,
                  "items": [
                    null,
                    {}
                  ]
                }
              ]
            }
            """.trimIndent()

        assertEquals(expected, JsonPrettyPrinter.prettyPrint(input))
    }

    @Test
    fun `preserves every valid string escape and escaped punctuation`() {
        val input =
            """{"key\"\\/":"quote: \" slash \/ backslash \\ controls \b\f\n\r\t unicode \u0041 and punctuation ,:{}[]"}"""
        val expected =
            """
            {
              "key\"\\/": "quote: \" slash \/ backslash \\ controls \b\f\n\r\t unicode \u0041 and punctuation ,:{}[]"
            }
            """.trimIndent()

        assertEquals(expected, JsonPrettyPrinter.prettyPrint(input))
    }

    @Test
    fun `preserves large number lexemes and duplicate object keys`() {
        val input = """{"id":1234567890123456789012345678901234567890,"id":-1.2300E+009}"""
        val expected =
            """
            {
              "id": 1234567890123456789012345678901234567890,
              "id": -1.2300E+009
            }
            """.trimIndent()

        assertEquals(expected, JsonPrettyPrinter.prettyPrint(input))
    }

    @Test
    fun `formats root primitives and ignores only permitted surrounding whitespace`() {
        val cases =
            listOf(
                "true \t\r\n" to "true",
                "-0.000e+000 " to "-0.000e+000",
                "\"scalar\"\t" to "\"scalar\"",
                "null\n" to "null",
                " [ \t ] \r\n" to "[]",
            )

        cases.forEach { (input, expected) ->
            assertEquals(expected, JsonPrettyPrinter.prettyPrint(input), input)
        }
    }

    @Test
    fun `rejects trailing tokens separators truncated values invalid numbers whitespace and escapes`() {
        val invalidInputs =
            listOf(
                "{}[]",
                "true false",
                "[1,,2]",
                "{\"a\":1,}",
                "[1",
                "{\"a\":[1}",
                "1.",
                "1e",
                "-01",
                "+1",
                "NaN",
                ".5",
                "true\u00a0",
                "\u2003true",
                "\"bad \\u12G4\"",
                "\"bad \\q\"",
                "\"unfinished\\",
            )

        invalidInputs.forEach { input ->
            assertNull(JsonPrettyPrinter.prettyPrint(input), input)
        }
    }

    @Test
    fun `accepts exactly the maximum nesting depth and rejects one more level`() {
        val maxDepthDocument = "[".repeat(200) + "0" + "]".repeat(200)
        val tooDeepDocument = "[".repeat(201) + "0" + "]".repeat(201)

        assertNotNull(JsonPrettyPrinter.prettyPrint(maxDepthDocument))
        assertNull(JsonPrettyPrinter.prettyPrint(tooDeepDocument))
    }

    @Test
    fun `rejects formatted output expansion while input remains small`() {
        val deepestArray = "0,".repeat(44_999) + "0"
        val input = "[".repeat(200) + deepestArray + "]".repeat(200)

        assertTrue(input.length < 1_000_000)
        assertNull(JsonPrettyPrinter.prettyPrint(input))
    }
}
