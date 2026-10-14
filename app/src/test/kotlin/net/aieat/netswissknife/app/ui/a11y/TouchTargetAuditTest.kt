package net.aieat.netswissknife.app.ui.a11y

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TouchTargetAuditTest {
    @Test
    fun `production icon button modifiers do not impose literal dimensions below 48dp`() {
        val sourceRoot = repositoryRoot().resolve("app/src/main/kotlin")
        val undersizedTargets = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                findUndersizedIconButtons(file.readText()).map { line ->
                    "${file.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')}:$line"
                }
            }
            .toList()

        assertTrue(
            undersizedTargets.isEmpty(),
            "IconButton modifiers impose dimensions below ${MIN_TOUCH_TARGET_DP}dp: " +
                undersizedTargets.joinToString(),
        )
    }

    @Test
    fun `source guard catches common explicit fixed-size modifier forms`() {
        val source = """
            IconButton(onClick = {}, modifier = Modifier.requiredSize(40.dp)) {}
            FilledTonalIconButton(onClick = {}, modifier = Modifier.width(52.dp).height(36.dp)) {}
            OutlinedIconButton(onClick = {}, modifier = Modifier.sizeIn(maxWidth = 44.dp)) {}
            FilledIconButton(onClick = {}, modifier = Modifier.minimumInteractiveComponentSize()) {}
        """.trimIndent()

        assertEquals(3, findUndersizedIconButtons(source).size)
    }

    @Test
    fun `source guard reads dimension limits after nested modifier commas`() {
        val source = """
            IconButton(
                onClick = {},
                modifier = Modifier.sizeIn(maxWidth = 52.dp, maxHeight = 40.dp)
            ) {}
            OutlinedIconButton(
                onClick = {},
                modifier = Modifier.widthIn(min = 24.dp, max = 40.dp)
            ) {}
        """.trimIndent()

        assertEquals(2, findUndersizedIconButtons(source).size)
    }

    private fun findUndersizedIconButtons(source: String): List<Int> = buildList {
        val invocation = Regex("\\b(?:FilledTonal|Filled|Outlined)?IconButton\\s*\\(")
        invocation.findAll(source).forEach { match ->
            val openParen = source.indexOf('(', match.range.first)
            val closeParen = matchingParen(source, openParen) ?: return@forEach
            val arguments = source.substring(openParen + 1, closeParen)
            val modifier = namedArgumentValue(arguments, "modifier")
                ?: return@forEach
            val fixedDimensions = Regex("\\.(?:size|requiredSize|width|height)\\s*\\(([^)]*)\\)")
                .findAll(modifier)
                .flatMap { dimension ->
                    Regex("(\\d+)\\s*\\.dp").findAll(dimension.groupValues[1])
                }
                .map { it.groupValues[1].toInt() }
            val undersizedSizeInLimit = Regex("\\.(?:sizeIn|widthIn|heightIn)\\s*\\(([^)]*)\\)")
                .findAll(modifier)
                .flatMap { limits ->
                    Regex("max(?:Width|Height)?\\s*=\\s*(\\d+)\\s*\\.dp")
                        .findAll(limits.groupValues[1])
                }
                .map { it.groupValues[1].toInt() }
            if ((fixedDimensions + undersizedSizeInLimit).any { it < MIN_TOUCH_TARGET_DP }) {
                add(source.substring(0, match.range.first).count { it == '\n' } + 1)
            }
        }
    }

    private fun matchingParen(source: String, openParen: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in openParen until source.length) {
            val char = source[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '(' -> depth++
                    ')' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
        }
        return null
    }

    private fun namedArgumentValue(arguments: String, argumentName: String): String? {
        var segmentStart = 0
        var parentheses = 0
        var braces = 0
        var brackets = 0
        var inString = false
        var inChar = false
        var escaped = false

        fun valueInSegment(endExclusive: Int): String? {
            val segment = arguments.substring(segmentStart, endExclusive).trim()
            val equalsIndex = segment.indexOf('=')
            if (equalsIndex < 0 || segment.substring(0, equalsIndex).trim() != argumentName) return null
            return segment.substring(equalsIndex + 1).trim()
        }

        arguments.forEachIndexed { index, char ->
            if (inString || inChar) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    inString && char == '"' -> inString = false
                    inChar && char == '\'' -> inChar = false
                }
                return@forEachIndexed
            }

            when (char) {
                '"' -> inString = true
                '\'' -> inChar = true
                '(' -> parentheses++
                ')' -> parentheses--
                '{' -> braces++
                '}' -> braces--
                '[' -> brackets++
                ']' -> brackets--
                ',' -> if (parentheses == 0 && braces == 0 && brackets == 0) {
                    valueInSegment(index)?.let { return it }
                    segmentStart = index + 1
                }
            }
        }

        return valueInSegment(arguments.length)
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (directory.resolve("PRIVACY_POLICY.md").isFile) return directory
            directory = directory.parentFile ?: error("Could not find repository root")
        }
    }

    private companion object {
        const val MIN_TOUCH_TARGET_DP = 48
    }
}
