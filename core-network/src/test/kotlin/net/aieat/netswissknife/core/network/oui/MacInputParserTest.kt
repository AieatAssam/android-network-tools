package net.aieat.netswissknife.core.network.oui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@DisplayName("MacInputParser")
class MacInputParserTest {
    @Test
    fun `accepts every full MAC notation in either case and surrounding whitespace`() {
        listOf(
            "b8:27:eb:11:22:33",
            "B8-27-EB-11-22-33",
            "B827.EB11.2233",
            "B827EB112233",
            "  b827eb112233\t",
        ).forEach { entry ->
            assertEquals(MacParseResult.Parsed("B827EB112233"), MacInputParser.parse(entry), entry)
        }
    }

    @Test
    fun `accepts a three byte OUI prefix in every notation`() {
        listOf("B8:27:EB", "b8-27-eb", "B827.EB", "b827eb").forEach { entry ->
            assertEquals(MacParseResult.Parsed("B827EB"), MacInputParser.parse(entry), entry)
        }
    }

    @Test
    fun `accepts a mix of separators within one address`() {
        assertEquals(MacParseResult.Parsed("B827EB112233"), MacInputParser.parse("b8:27-EB.11:22-33"))
    }

    @Test
    fun `reports characters that are neither hex digits nor separators`() {
        assertEquals(MacParseResult.Invalid(MacInputProblem.CHARACTERS), MacInputParser.parse("B8:27:EB/garbage"))
        assertEquals(MacParseResult.Invalid(MacInputProblem.CHARACTERS), MacInputParser.parse("B8:27:EB:ZZ:22:33"))
    }

    @Test
    fun `reports a digit count that is neither a MAC nor an OUI prefix`() {
        assertEquals(MacParseResult.Invalid(MacInputProblem.LENGTH), MacInputParser.parse("B8:27:EB:11:22"))
        assertEquals(MacParseResult.Invalid(MacInputProblem.LENGTH), MacInputParser.parse("B827EB1122"))
        assertEquals(MacParseResult.Invalid(MacInputProblem.LENGTH), MacInputParser.parse("B8:27:EB:11:22:33:44"))
        assertEquals(MacParseResult.Invalid(MacInputProblem.LENGTH), MacInputParser.parse("B8"))
    }

    @Test
    fun `reports groups of the wrong size or empty groups as separator errors`() {
        assertEquals(MacParseResult.Invalid(MacInputProblem.SEPARATORS), MacInputParser.parse("B8::27:EB"))
        assertEquals(MacParseResult.Invalid(MacInputProblem.SEPARATORS), MacInputParser.parse("B8:27EB:11:22:33"))
    }

    @Test
    fun `splits pasted text on newlines, commas, semicolons and spaces`() {
        val text = "B8:27:EB:11:22:33,\n 00-1b-c5;  FF:FF:FF:FF:FF:FF\n\n"
        assertEquals(
            listOf("B8:27:EB:11:22:33", "00-1b-c5", "FF:FF:FF:FF:FF:FF"),
            MacInputParser.splitEntries(text),
        )
    }

    @Test
    fun `splitting blank text yields no entries`() {
        assertEquals(emptyList<String>(), MacInputParser.splitEntries(" \n , ; "))
    }
}
