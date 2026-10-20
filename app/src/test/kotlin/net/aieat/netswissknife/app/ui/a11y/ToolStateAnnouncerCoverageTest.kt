package net.aieat.netswissknife.app.ui.a11y

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ToolStateAnnouncerCoverageTest {
    @Test
    fun `every shipped tool screen has exactly one shared state announcer`() {
        val root = repositoryRoot().resolve("app/src/main/kotlin/net/aieat/netswissknife/app/ui/screens")
        val expectedScreens = setOf(
            "PingScreen.kt",
            "DnsScreen.kt",
            "TracerouteScreen.kt",
            "PortsScreen.kt",
            "lan/LanScanScreen.kt",
            "httprobe/HttpProbeScreen.kt",
            "tls/TlsInspectorScreen.kt",
            "whois/WhoisScreen.kt",
            "WifiScanScreen.kt",
            "subnet/SubnetCalculatorScreen.kt",
            "speedtest/SpeedTestScreen.kt",
            "wol/WakeOnLanScreen.kt",
            "mdns/MdnsDiscoveryScreen.kt",
            "topology/TopologyDiscoveryScreen.kt",
        )
        val nonToolScreens = setOf(
            "HomeScreen.kt",
            "LanScreen.kt",
            "debug/DebugLogScreen.kt",
            "settings/SettingsScreen.kt",
        )
        val discoveredScreens = root.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Screen.kt") }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .toSet()

        assertEquals(expectedScreens + nonToolScreens, discoveredScreens)
        assertEquals(14, expectedScreens.size)
        expectedScreens.forEach { relativePath ->
            val file = root.resolve(relativePath)
            assertTrue(file.isFile, "Expected shipped tool screen: $relativePath")
            val source = file.readText()
            val calls = Regex("\\bToolStateAnnouncer\\s*\\(").findAll(source).count()
            assertEquals(1, calls, "$relativePath must call ToolStateAnnouncer once")
            val mappingStart = source.indexOf("val announcementPhase =")
            assertTrue(mappingStart >= 0, "$relativePath must derive announcementPhase from tool state")
            val callStart = source.indexOf("ToolStateAnnouncer(", mappingStart)
            assertTrue(callStart > mappingStart, "$relativePath must pass its derived phase to the announcer")
            val mappingAndCall = source.substring(mappingStart, callStart)
            assertTrue(mappingAndCall.contains("uiState"), "$relativePath must map a UI state to its phase")
            assertTrue(
                Regex("ToolStateAnnouncer\\s*\\([^,]+,\\s*announcementPhase")
                    .containsMatchIn(source.substring(callStart)),
                "$relativePath must pass the derived announcementPhase",
            )
        }
    }

    private fun repositoryRoot(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (directory.resolve("PRIVACY_POLICY.md").isFile) return directory
            directory = directory.parentFile ?: error("Could not find repository root")
        }
    }
}
