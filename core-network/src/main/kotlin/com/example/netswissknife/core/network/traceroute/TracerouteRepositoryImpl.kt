package com.example.netswissknife.core.network.traceroute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.InetAddress

/**
 * Production [TracerouteRepository] that invokes the system `traceroute` binary
 * (or `tracepath` as fallback) and parses its output line by line.
 *
 * On Android, raw ICMP sockets require root privileges, so we rely on the
 * shell binary which uses TTL-expired ICMP probes internally.
 *
 * Binary search order:
 *   /system/bin/traceroute → /system/xbin/traceroute → traceroute (PATH)
 *   /system/bin/tracepath  → /system/xbin/tracepath  → tracepath  (PATH)
 *
 * @param commandBuilder  Injectable for testing – defaults to the real binary launcher.
 */
class TracerouteRepositoryImpl(
    private val commandBuilder: CommandBuilder = DefaultCommandBuilder
) : TracerouteRepository {

    fun interface CommandBuilder {
        fun build(host: String, maxHops: Int, timeoutSec: Int, queries: Int): List<String>
    }

    private enum class BinaryType { TRACEROUTE, TRACEPATH }

    companion object {
        private val BINARY_CANDIDATES: List<Pair<String, BinaryType>> = listOf(
            "/system/bin/traceroute" to BinaryType.TRACEROUTE,
            "/system/xbin/traceroute" to BinaryType.TRACEROUTE,
            "traceroute" to BinaryType.TRACEROUTE,
            "/system/bin/tracepath" to BinaryType.TRACEPATH,
            "/system/xbin/tracepath" to BinaryType.TRACEPATH,
            "tracepath" to BinaryType.TRACEPATH,
        )

        private fun isExecutable(binary: String): Boolean = try {
            if (binary.startsWith("/")) {
                File(binary).let { it.exists() && it.canExecute() }
            } else {
                ProcessBuilder(listOf("which", binary)).start().waitFor() == 0
            }
        } catch (_: Exception) { false }

        private fun detectBinary(): Pair<String, BinaryType>? =
            BINARY_CANDIDATES.firstOrNull { (bin, _) -> isExecutable(bin) }

        val DefaultCommandBuilder = CommandBuilder { host, maxHops, timeoutSec, queries ->
            val (binary, type) = detectBinary()
                ?: throw IllegalStateException(
                    "No traceroute binary found on this device. " +
                    "Tried: traceroute, tracepath in /system/bin, /system/xbin, and PATH."
                )
            when (type) {
                BinaryType.TRACEROUTE ->
                    listOf(binary, "-n", "-m", "$maxHops", "-q", "$queries", "-w", "$timeoutSec", host)
                BinaryType.TRACEPATH ->
                    listOf(binary, "-m", "$maxHops", host)
            }
        }
    }

    override fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        queriesPerHop: Int
    ): Flow<HopResult> = flow {
        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)

        val cmd = try {
            commandBuilder.build(host, maxHops, timeoutSec, queriesPerHop)
        } catch (e: Exception) {
            throw IllegalStateException("traceroute unavailable: ${e.message}", e)
        }

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw IllegalStateException("traceroute unavailable: ${e.message}", e)
        }

        val isTracepath = cmd.firstOrNull()?.contains("tracepath") == true

        try {
            val reader = process.inputStream.bufferedReader()
            var lineIndex = 0
            val emittedHops = mutableSetOf<Int>()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lineIndex++
                if (lineIndex == 1 && !isTracepath) continue  // skip traceroute header line
                val hop = if (isTracepath) parseTracepathLine(line!!) else parseTracerouteLine(line!!)
                hop ?: continue
                // tracepath may repeat the same hop number; emit only the first occurrence
                if (!emittedHops.add(hop.hopNumber)) continue
                val enriched = if (hop.ip != null && hop.status == HopStatus.SUCCESS) {
                    hop.copy(hostname = resolveHostname(hop.ip))
                } else hop
                emit(enriched)
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    // ── traceroute line parsing ────────────────────────────────────────────────

    /**
     * Parses a single `traceroute` output line.
     *
     * Expected formats:
     *   " 1  192.168.1.1  1.234 ms  1.234 ms  1.234 ms"
     *   " 2  * * *"
     *   " 3  10.0.0.1  10.0 ms  *  9.8 ms"
     */
    internal fun parseTracerouteLine(line: String): HopResult? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split(Regex("\\s+"))
        if (parts.isEmpty()) return null

        val hopNum = parts[0].toIntOrNull() ?: return null
        val rest = parts.drop(1)
        if (rest.isEmpty()) return null

        // All-timeout: " 3  * * *"
        if (rest.all { it == "*" }) {
            return HopResult(hopNum, null, null, null, HopStatus.TIMEOUT)
        }

        // Find first IP-like token
        val ip = rest.firstOrNull { looksLikeIp(it) }
            ?: return HopResult(hopNum, null, null, null, HopStatus.TIMEOUT)

        // Find first RTT (a float followed by "ms" or a token ending in "ms")
        val rtt = extractFirstRtt(parts)

        return HopResult(hopNum, ip, null, rtt, HopStatus.SUCCESS)
    }

    // ── tracepath line parsing ─────────────────────────────────────────────────

    /**
     * Parses a single `tracepath` output line.
     *
     * Expected formats:
     *   " 1?: [LOCALHOST]                                         pmtu 1500"  → skip
     *   " 1:  192.168.1.1                                           1.234ms"  → hop
     *   " 2:  no reply"                                                        → timeout
     *   " 3:  10.0.0.1                                              5.6ms asymm 3"
     */
    internal fun parseTracepathLine(line: String): HopResult? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Match "N:" or "N?:" prefix
        val hopMatch = Regex("""^(\d+)\??:""").find(trimmed) ?: return null
        val hopNum = hopMatch.groupValues[1].toIntOrNull() ?: return null

        // Skip localhost discovery / PMTU announcement lines
        if (trimmed.contains("[LOCALHOST]") || trimmed.contains("pmtu")) return null

        val rest = trimmed.removePrefix(hopMatch.value).trim()

        // Timeout
        if (rest.startsWith("no reply") || rest.isBlank()) {
            return HopResult(hopNum, null, null, null, HopStatus.TIMEOUT)
        }

        val parts = rest.split(Regex("\\s+"))
        val ip = parts.firstOrNull { looksLikeIp(it) }
            ?: return HopResult(hopNum, null, null, null, HopStatus.TIMEOUT)

        // RTT token looks like "1.234ms" (no space before "ms")
        val rtt = parts.mapNotNull { token ->
            token.trimEnd().removeSuffix("ms").toDoubleOrNull()?.toLong()
        }.firstOrNull()

        return HopResult(hopNum, ip, null, rtt, HopStatus.SUCCESS)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun looksLikeIp(token: String): Boolean {
        val parts = token.split(".")
        return parts.size == 4 && parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    private fun extractFirstRtt(parts: List<String>): Long? {
        for (i in parts.indices) {
            if (parts[i].equals("ms", ignoreCase = true) && i > 0) {
                return parts[i - 1].toDoubleOrNull()?.toLong()
            }
            if (parts[i].endsWith("ms", ignoreCase = true) && parts[i].length > 2) {
                return parts[i].dropLast(2).toDoubleOrNull()?.toLong()
            }
        }
        return null
    }

    // ── Reverse DNS ───────────────────────────────────────────────────────────

    private fun resolveHostname(ip: String): String? = try {
        val addr = InetAddress.getByName(ip)
        val canonical = addr.canonicalHostName
        if (canonical == ip) null else canonical
    } catch (_: Exception) {
        null
    }
}
