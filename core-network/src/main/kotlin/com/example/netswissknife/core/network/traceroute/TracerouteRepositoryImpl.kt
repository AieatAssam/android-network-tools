package com.example.netswissknife.core.network.traceroute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress

/**
 * Production [TracerouteRepository] that invokes the system `traceroute` binary
 * (or `tracepath` as fallback) and parses its output line by line.
 *
 * On Android, raw ICMP sockets require root privileges, so we rely on the
 * shell binary which uses TTL-expired ICMP probes internally.
 *
 * @param commandBuilder  Injectable for testing – defaults to the real binary launcher.
 */
class TracerouteRepositoryImpl(
    private val commandBuilder: CommandBuilder = DefaultCommandBuilder
) : TracerouteRepository {

    fun interface CommandBuilder {
        fun build(host: String, maxHops: Int, timeoutSec: Int, queries: Int): List<String>
    }

    companion object {
        val DefaultCommandBuilder = CommandBuilder { host, maxHops, timeoutSec, queries ->
            listOf("traceroute", "-n", "-m", "$maxHops", "-q", "$queries", "-w", "$timeoutSec", host)
        }
    }

    override fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        queriesPerHop: Int
    ): Flow<HopResult> = flow {
        val timeoutSec = (timeoutMs / 1000).coerceAtLeast(1)
        val cmd = commandBuilder.build(host, maxHops, timeoutSec, queriesPerHop)

        val process = try {
            ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
        } catch (e: Exception) {
            throw IllegalStateException("traceroute unavailable: ${e.message}", e)
        }

        try {
            val reader = process.inputStream.bufferedReader()
            var lineIndex = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                lineIndex++
                if (lineIndex == 1) continue          // skip header
                val hop = parseTracerouteLine(line!!) ?: continue
                val enriched = if (hop.ip != null && hop.status == HopStatus.SUCCESS) {
                    hop.copy(hostname = resolveHostname(hop.ip))
                } else hop
                emit(enriched)
            }
        } finally {
            process.destroy()
        }
    }.flowOn(Dispatchers.IO)

    // ── Line parsing ──────────────────────────────────────────────────────────

    /**
     * Parses a single traceroute output line.
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
