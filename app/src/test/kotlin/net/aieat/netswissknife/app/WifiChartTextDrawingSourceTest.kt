package net.aieat.netswissknife.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WifiChartTextDrawingSourceTest {
    @Test
    fun `production source does not draw chart text through native canvas`() {
        val sourceRoot = repositoryRoot().resolve("app/src/main")
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("nativeCanvas.drawText") }
            .map { it.relativeTo(sourceRoot).path }
            .toList()

        assertTrue(offenders.isEmpty(), "nativeCanvas.drawText remains in production source: $offenders")
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (directory.resolve("PRIVACY_POLICY.md").isFile) return directory
            directory = directory.parentFile ?: error("Could not find repository root")
        }
    }
}
