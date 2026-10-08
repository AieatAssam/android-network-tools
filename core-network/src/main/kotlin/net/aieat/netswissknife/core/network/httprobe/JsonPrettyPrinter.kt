package net.aieat.netswissknife.core.network.httprobe

import java.util.ArrayDeque

/** Strict JSON validator/formatter. Invalid or non-JSON text returns null. */
object JsonPrettyPrinter {
    fun prettyPrint(input: String): String? =
        try {
            if (input.length > MAX_FORMATTED_JSON_LENGTH) return null
            JsonFormatter(input).format()
        } catch (_: IllegalArgumentException) {
            null
        }
}

private class JsonFormatter(
    private val input: String,
) {
    private val output = BoundedJsonOutput(input.length)
    private val tokens = JsonTokenReader(input, output)
    private val containers = ArrayDeque<Container>()
    private var rootValueRead = false

    fun format(): String {
        while (true) {
            tokens.skipWhitespace()
            if (containers.isEmpty()) {
                if (rootValueRead) {
                    require(tokens.index == input.length)
                    return output.toString()
                }
                readValue()
                rootValueRead = true
            } else {
                processContainer(containers.peekLast())
            }
        }
    }

    private fun processContainer(container: Container) {
        when (container.state) {
            State.KEY_OR_END, State.KEY -> {
                processObjectKey(container)
            }

            State.COLON -> {
                require(tokens.take(':'))
                output.append(": ")
                container.state = State.VALUE
            }

            State.VALUE, State.VALUE_OR_END -> {
                processContainerValue(container)
            }

            State.COMMA_OR_END -> {
                processSeparator(container)
            }
        }
    }

    private fun processObjectKey(container: Container) {
        if (container.state == State.KEY_OR_END && closeContainer(container)) return
        require(container.isObject && tokens.peek() == '"')
        tokens.readString()
        container.state = State.COLON
    }

    private fun processContainerValue(container: Container) {
        if (container.state == State.VALUE_OR_END && closeContainer(container)) return
        readValue()
        container.state = State.COMMA_OR_END
    }

    private fun processSeparator(container: Container) {
        if (tokens.take(',')) {
            output.append(',')
            output.append('\n')
            output.appendIndent(containers.size)
            container.state = if (container.isObject) State.KEY else State.VALUE
        } else {
            require(closeContainer(container))
        }
    }

    private fun readValue() {
        when (tokens.peek()) {
            '{' -> startContainer(isObject = true)
            '[' -> startContainer(isObject = false)
            '"' -> tokens.readString()
            't' -> tokens.readLiteral("true")
            'f' -> tokens.readLiteral("false")
            'n' -> tokens.readLiteral("null")
            else -> tokens.readNumber()
        }
    }

    private fun startContainer(isObject: Boolean) {
        val opening = if (isObject) '{' else '['
        val closing = if (isObject) '}' else ']'
        require(tokens.take(opening))
        require(containers.size < MAX_JSON_DEPTH) { "JSON nesting exceeds the limit" }
        output.append(opening)
        if (tokens.consumeEmptyContainer(closing)) {
            output.append(closing)
            return
        }

        output.append('\n')
        output.appendIndent(containers.size + 1)
        containers.addLast(
            Container(
                isObject = isObject,
                state = if (isObject) State.KEY_OR_END else State.VALUE_OR_END,
                close = closing,
            ),
        )
    }

    private fun closeContainer(container: Container): Boolean {
        if (!tokens.take(container.close)) return false
        if (container.state != State.KEY_OR_END && container.state != State.VALUE_OR_END) {
            output.append('\n')
            output.appendIndent(containers.size - 1)
        }
        output.append(container.close)
        containers.removeLast()
        return true
    }
}

private class JsonTokenReader(
    private val input: String,
    private val output: BoundedJsonOutput,
) {
    var index: Int = 0
        private set

    fun skipWhitespace() {
        while (index < input.length && input[index].isJsonWhitespace()) index++
    }

    fun take(char: Char): Boolean {
        if (index >= input.length || input[index] != char) return false
        index++
        return true
    }

    fun peek(): Char = input.getOrElse(index) { '\u0000' }

    fun consumeEmptyContainer(close: Char): Boolean {
        skipWhitespace()
        return take(close)
    }

    fun readString() {
        val start = index
        require(take('"'))
        while (index < input.length) {
            val char = input[index++]
            when {
                char == '"' -> {
                    output.append(input, start, index)
                    return
                }

                char == '\\' -> {
                    readEscape()
                }

                char.code < JSON_CONTROL_CHARACTER_LIMIT -> {
                    throw IllegalArgumentException("Invalid JSON string")
                }
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    fun readLiteral(literal: String) {
        require(input.regionMatches(index, literal, 0, literal.length))
        val end = index + literal.length
        output.append(input, index, end)
        index = end
    }

    fun readNumber() {
        val start = index
        if (take('-')) require(index < input.length)
        readInteger()
        if (take('.')) readFraction()
        readExponent()
        output.append(input, start, index)
    }

    private fun readEscape() {
        require(index < input.length)
        when (input[index++]) {
            '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {
                Unit
            }

            'u' -> {
                repeat(JSON_UNICODE_ESCAPE_LENGTH) {
                    require(index < input.length && input[index].isHexDigit())
                    index++
                }
            }

            else -> {
                throw IllegalArgumentException("Invalid JSON escape")
            }
        }
    }

    private fun readInteger() {
        when {
            take('0') -> {
                require(index >= input.length || !input[index].isDigit())
            }

            index < input.length && input[index] in '1'..'9' -> {
                index++
                while (index < input.length && input[index] in '0'..'9') index++
            }

            else -> {
                throw IllegalArgumentException("Invalid JSON number")
            }
        }
    }

    private fun readFraction() {
        val start = index
        while (index < input.length && input[index] in '0'..'9') index++
        require(index > start)
    }

    private fun readExponent() {
        if (index >= input.length || (input[index] != 'e' && input[index] != 'E')) return
        index++
        if (index < input.length && (input[index] == '+' || input[index] == '-')) index++
        readFraction()
    }
}

private class BoundedJsonOutput(
    inputLength: Int,
) {
    private val builder = StringBuilder(inputLength.coerceAtMost(MAX_FORMATTED_JSON_LENGTH))

    fun append(char: Char) {
        require(builder.length < MAX_FORMATTED_JSON_LENGTH) { "Formatted JSON exceeds the limit" }
        builder.append(char)
    }

    fun append(value: String) {
        require(value.length <= MAX_FORMATTED_JSON_LENGTH - builder.length) { "Formatted JSON exceeds the limit" }
        builder.append(value)
    }

    fun append(
        value: String,
        start: Int,
        end: Int,
    ) {
        require(end - start <= MAX_FORMATTED_JSON_LENGTH - builder.length) { "Formatted JSON exceeds the limit" }
        builder.append(value, start, end)
    }

    fun appendIndent(depth: Int) {
        val indentLength = depth * JSON_INDENT_WIDTH
        require(indentLength <= MAX_FORMATTED_JSON_LENGTH - builder.length) { "Formatted JSON exceeds the limit" }
        repeat(indentLength) { builder.append(' ') }
    }

    override fun toString(): String = builder.toString()
}

private data class Container(
    val isObject: Boolean,
    var state: State,
    val close: Char,
)

private enum class State {
    KEY_OR_END,
    KEY,
    COLON,
    VALUE,
    VALUE_OR_END,
    COMMA_OR_END,
}

private const val JSON_CONTROL_CHARACTER_LIMIT = 0x20
private const val JSON_INDENT_WIDTH = 2
private const val JSON_UNICODE_ESCAPE_LENGTH = 4
private const val MAX_JSON_DEPTH = 200
private const val MAX_FORMATTED_JSON_LENGTH = 16 * 1024 * 1024

private fun Char.isJsonWhitespace(): Boolean = this == ' ' || this == '\n' || this == '\r' || this == '\t'

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
