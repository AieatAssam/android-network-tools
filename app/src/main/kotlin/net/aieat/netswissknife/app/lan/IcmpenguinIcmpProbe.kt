package net.aieat.netswissknife.app.lan

import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.CancellationException
import me.impa.icmpenguin.ProbeResult
import me.impa.icmpenguin.ping.Pinger
import net.aieat.netswissknife.core.network.lan.IcmpProbe

/** Android ICMP implementation backed by the bundled icmpenguin native engine. */
class IcmpenguinIcmpProbe(
    private val echoFactory: suspend (String, Int) -> Long? = ::nativeEcho,
) : IcmpProbe {
    override suspend fun echo(ip: String, timeoutMs: Int): Long? = try {
        echoFactory(ip, timeoutMs)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        // Native loading is an unavailable ICMP observation, not a failed LAN scan.
        // TCP and local-protocol discovery can still establish host presence.
        null
    }
}

private suspend fun nativeEcho(ip: String, timeoutMs: Int): Long? {
    val result = Pinger(
        host = ip,
        ttl = 64,
        timeout = timeoutMs,
        maxPingCount = 1,
        interval = 0,
        probeSize = Pinger.DEFAULT_PROBE_SIZE,
        // null (not an empty array) is the library's own default: Pinger fills a
        // probeSize-length payload itself. Passing byteArrayOf() bypasses that and
        // sends a 0-byte ICMP echo instead (confirmed via bytecode inspection of the
        // icmpenguin 1.0.0-rc.4 AAR: the constructor's default-args path only
        // generates the payload when pattern == null).
        pattern = null,
        sourceIp = "",
    ).ping().firstOrNull()
    return (result as? ProbeResult.Success)?.elapsedUsec?.toLong()?.div(1_000L)
}
