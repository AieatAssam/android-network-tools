package net.aieat.netswissknife.core.network.httprobe

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.ArrayDeque

/** Strict JSON validator/formatter. Invalid or non-JSON text returns null. */
object JsonPrettyPrinter {
    private val validator = Json {}

    fun prettyPrint(input: String): String? =
        runCatching {
            // Validate with the strict JSON parser, then format the original tokens directly. This
            // avoids building a second serialized copy of large response strings just to add spaces.
            validate(validator.parseToJsonElement(input))
            formatValidatedJson(input)
        }.getOrNull()

    private fun validate(element: JsonElement) {
        when (element) {
            is JsonObject -> {
                element.values.forEach(::validate)
            }

            is JsonArray -> {
                element.forEach(::validate)
            }

            is JsonPrimitive -> {
                if (!element.isString && element.content !in JSON_LITERALS &&
                    !JSON_NUMBER.matches(element.content)
                ) {
                    throw IllegalArgumentException("Invalid JSON primitive")
                }
            }
        }
    }

    private fun formatValidatedJson(input: String): String {
        val formatter = ValidatedJsonFormatter(input)
        return formatter.format()
    }

    private class ValidatedJsonFormatter(
        private val input: String,
    ) {
        private val output = StringBuilder(input.length)
        private val containers = ArrayDeque<Boolean>()
        private var depth = 0
        private var inString = false
        private var escaped = false

        fun format(): String {
            for (index in input.indices) append(input[index], index)
            return output.toString()
        }

        private fun append(
            char: Char,
            index: Int,
        ) {
            if (inString) {
                appendStringCharacter(char)
                return
            }

            when (char) {
                '"' -> {
                    inString = true
                    output.append(char)
                }

                '{', '[' -> {
                    appendContainerStart(char, index)
                }

                '}', ']' -> {
                    appendContainerEnd(char)
                }

                ',' -> {
                    appendComma()
                }

                ':' -> {
                    output.append(": ")
                }

                else -> {
                    if (!char.isJsonWhitespace()) output.append(char)
                }
            }
        }

        private fun appendStringCharacter(char: Char) {
            output.append(char)
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        }

        private fun appendContainerStart(
            char: Char,
            index: Int,
        ) {
            val close = if (char == '{') '}' else ']'
            val nextToken = input.indexOfFirstNonWhitespace(index + 1)
            val empty = nextToken < input.length && input[nextToken] == close
            output.append(char)
            containers.addLast(empty)
            if (!empty) {
                depth++
                output.append('\n')
                appendIndent()
            }
        }

        private fun appendContainerEnd(char: Char) {
            if (!containers.removeLast()) {
                depth--
                output.append('\n')
                appendIndent()
            }
            output.append(char)
        }

        private fun appendComma() {
            output.append(',').append('\n')
            appendIndent()
        }

        private fun appendIndent() {
            repeat(depth * JSON_INDENT_WIDTH) { output.append(' ') }
        }
    }

    private val JSON_LITERALS = setOf("true", "false", "null")
    private val JSON_NUMBER = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
}

private const val JSON_INDENT_WIDTH = 2

private fun String.indexOfFirstNonWhitespace(from: Int): Int {
    var index = from
    while (index < length && this[index].isJsonWhitespace()) index++
    return index
}

private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\n' || this == '\r' || this == '\t'
