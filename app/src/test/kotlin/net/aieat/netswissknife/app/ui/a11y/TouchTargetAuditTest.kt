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

    private fun findUndersizedIconButtons(source: String): List<Int> = buildList {
        val invocation = Regex("\\b(?:FilledTonal|Filled|Outlined)?IconButton\\s*\\(")
        invocation.findAll(source).forEach { match ->
            val openParen = source.indexOf('(', match.range.first)
            val closeParen = matchingParen(source, openParen) ?: return@forEach
            val arguments = source.substring(openParen + 1, closeParen)
            val modifier = Regex("\\bmodifier\\s*=\\s*([\\s\\S]*?)(?=,\\s*\\w+\\s*=|$)")
                .find(arguments)
                ?.groupValues
                ?.get(1)
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
