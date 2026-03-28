package net.aieat.netswissknife.app.traceroute

import net.aieat.netswissknife.core.network.traceroute.HopResult
import net.aieat.netswissknife.core.network.traceroute.HopStatus
import net.aieat.netswissknife.core.network.traceroute.TracerouteProbeType
import net.aieat.netswissknife.core.network.traceroute.TracerouteRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import me.impa.icmpenguin.ProbeType
import me.impa.icmpenguin.trace.PortStrategy
import me.impa.icmpenguin.trace.ProbeSize
import me.impa.icmpenguin.trace.Response
import me.impa.icmpenguin.trace.SimpleTracer
import java.net.InetAddress

/**
 * [TracerouteRepository] implementation powered by the **icmpenguin** library.
 *
 * Unlike the legacy binary-based approach, icmpenguin uses low-level ICMP/UDP sockets
 * via JNI and does not depend on the `traceroute` or `tracepath` system binaries.
 * This makes it work reliably on Android 16+ where those binaries have been removed.
 *
 * Coroutine integration: [SimpleTracer.trace] returns a cold [Flow] that emits one
 * [me.impa.icmpenguin.trace.HopStatus] per TTL level. We map each to our own [HopResult]
 * and enrich it with a reverse-DNS hostname lookup on the IO dispatcher.
 */
class IcmpEnginTracerouteRepositoryImpl : TracerouteRepository {

    override fun trace(
        host: String,
        maxHops: Int,
        timeoutMs: Int,
        probesPerHop: Int,
        probeType: TracerouteProbeType,
        packetSize: Int
    ): Flow<HopResult> {
        val icmpProbeType = when (probeType) {
            TracerouteProbeType.ICMP -> ProbeType.ICMP
            TracerouteProbeType.UDP  -> ProbeType.UDP
        }

        val probeSize = if (packetSize == 0) {
            ProbeSize.MtuDiscovery
        } else {
            ProbeSize.Static(packetSize)
        }

        val tracer = SimpleTracer(
            host          = host,
            probeType     = icmpProbeType,
            timeout       = timeoutMs,
            maxHops       = maxHops,
            probesPerHop  = probesPerHop,
            concurrency   = minOf(probesPerHop, 5),
            portStrategy  = PortStrategy.Sequential(),
            probeSize     = probeSize
        )

        return tracer.trace()
            .map { icmpHop ->
                val ip = icmpHop.ips.firstOrNull()
                val rttMs = icmpHop.probes
                    .filterIsInstance<Response.Success>()
                    .firstOrNull()
                    ?.timeUsec
                    ?.let { it.toLong() / 1_000L }

                val status = if (ip != null) HopStatus.SUCCESS else HopStatus.TIMEOUT
                val hostname = if (ip != null) resolveHostname(ip) else null

                HopResult(
                    hopNumber = icmpHop.num,
                    ip        = ip,
                    hostname  = hostname,
                    rtTimeMs  = rttMs,
                    status    = status
                )
            }
            .flowOn(Dispatchers.IO)
    }

    private fun resolveHostname(ip: String): String? = try {
        val addr = InetAddress.getByName(ip)
        val canonical = addr.canonicalHostName
        if (canonical == ip) null else canonical
    } catch (_: Exception) {
        null
    }
}
