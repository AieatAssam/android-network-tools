package net.aieat.netswissknife.core.network.lan

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

/** Probe used to establish whether an IPv4 host answers ICMP echo. */
fun interface IcmpProbe {
    suspend fun echo(ip: String, timeoutMs: Int): Long?
}
/** Probe used to establish host presence from TCP reset/open semantics. */
fun interface TcpPresenceProbe {
    suspend fun probe(ip: String, ports: List<Int>, timeoutMs: Int): TcpPresence
}

sealed interface TcpPresence {
    data class Open(val port: Int) : TcpPresence
    data class Refused(val port: Int) : TcpPresence
    data object None : TcpPresence
}

/** Name lookup used for enrichment and, for NetBIOS/mDNS, host presence. */
fun interface NameProbe {
    suspend fun resolveName(ip: String, timeoutMs: Int): String?
}

/** Best-effort MAC lookup. [supported] reflects whether the backing source exists. */
interface MacResolver {
    val supported: Boolean
    suspend fun resolve(ip: String): String?
}

/** Network-level protocol that led to host discovery. */
enum class DiscoveryMethod {
    ICMP,
    TCP_OPEN,
    TCP_REFUSED,
    NETBIOS,
    MDNS,
    RDNS,
}

enum class MacSource {
    ARP,
    NONE,
}

/** Ports used to prove that a host is alive when ICMP is filtered. */
val DEFAULT_PRESENCE_PORTS: List<Int> = listOf(
    80, 443, 22, 445, 139, 135, 8080, 62078, 7000, 5000,
)

/** Compatibility aliases retained for existing repository and test call sites. */
typealias HostChecker = (ip: String, timeoutMs: Int) -> Long?
typealias ArpTableReader = () -> String
typealias PortChecker = (ip: String, port: Int, timeoutMs: Int) -> Boolean

/** JVM default ICMP-like reachability probe. Android may replace this with icmpenguin. */
class ReachabilityIcmpProbe : IcmpProbe {
    override suspend fun echo(ip: String, timeoutMs: Int): Long? = try {
        val start = System.nanoTime()
        if (InetAddress.getByName(ip).isReachable(timeoutMs)) {
            ((System.nanoTime() - start).coerceAtLeast(0L) / 1_000_000L).coerceAtLeast(1L)
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

enum class TcpConnectOutcome {
    OPEN,
    REFUSED,
    NONE,
}

/** Sequential TCP presence probe. A reset is useful evidence that the host is alive. */
class SocketTcpPresenceProbe(
    private val connector: (ip: String, port: Int, timeoutMs: Int) -> TcpConnectOutcome = ::connect,
) : TcpPresenceProbe {
    override suspend fun probe(ip: String, ports: List<Int>, timeoutMs: Int): TcpPresence {
        val perPortTimeout = timeoutMs.coerceAtMost(400).coerceAtLeast(1)
        for (port in ports) {
            when (connector(ip, port, perPortTimeout)) {
                TcpConnectOutcome.OPEN -> return TcpPresence.Open(port)
                TcpConnectOutcome.REFUSED -> return TcpPresence.Refused(port)
                TcpConnectOutcome.NONE -> Unit
            }
        }
        return TcpPresence.None
    }

    companion object {
        private fun connect(ip: String, port: Int, timeoutMs: Int): TcpConnectOutcome {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                TcpConnectOutcome.OPEN
            } catch (_: ConnectException) {
                TcpConnectOutcome.REFUSED
            } catch (_: SocketTimeoutException) {
                TcpConnectOutcome.NONE
            } catch (_: NoRouteToHostException) {
                TcpConnectOutcome.NONE
            } catch (_: IOException) {
                TcpConnectOutcome.NONE
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                    // Best effort close.
                }
            }
        }
    }
}
