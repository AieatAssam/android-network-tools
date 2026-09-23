package net.aieat.netswissknife.core.network.lan

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
    data class Refused(val port: Int, val detail: String? = null) : TcpPresence
    data class TimedOut(val port: Int, val detail: String? = null) : TcpPresence
    data class Unreachable(val port: Int, val detail: String? = null) : TcpPresence
    data class PolicyDenied(val port: Int, val detail: String? = null) : TcpPresence
    data class UnknownFailure(val port: Int, val detail: String? = null) : TcpPresence
    data object None : TcpPresence
}

/** Name lookup used for enrichment and, for NetBIOS/mDNS, host presence. */
fun interface NameProbe {
    suspend fun resolveName(ip: String, timeoutMs: Int): String?
}

data class LocalProtocolReply(
    val method: DiscoveryMethod,
    val name: String,
)

/** Name probe that can prove target presence only after validating its response correlation. */
interface PresenceNameProbe : NameProbe {
    suspend fun probePresence(ip: String, timeoutMs: Int): LocalProtocolReply?
}

/** Best-effort MAC lookup. [supported] reflects whether the backing source exists. */
interface MacResolver {
    val supported: Boolean
    suspend fun resolve(ip: String): String?

    /** Returns a resolver view for a completed scan, refreshing sources that cache data. */
    fun snapshot(): MacResolver = this
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
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }
}

enum class TcpConnectOutcome {
    OPEN,
    REFUSED,
    TIMED_OUT,
    UNREACHABLE,
    POLICY_DENIED,
    UNKNOWN_FAILURE,
    /** No usable outcome was available from the connector. */
    NONE,
}

data class TcpConnectResult(
    val outcome: TcpConnectOutcome,
    val detail: String? = null,
)

/** Sequential TCP presence probe. Only a completed connection is positive evidence. */
class SocketTcpPresenceProbe(
    private val connector: (ip: String, port: Int, timeoutMs: Int) -> TcpConnectResult = ::connect,
) : TcpPresenceProbe {
    override suspend fun probe(ip: String, ports: List<Int>, timeoutMs: Int): TcpPresence {
        val perPortTimeout = timeoutMs.coerceAtMost(400).coerceAtLeast(1)
        var firstFailure: TcpPresence? = null
        for (port in ports) {
            currentCoroutineContext().ensureActive()
            val result = connector(ip, port, perPortTimeout)
            val detail = result.detail?.take(160)
            when (result.outcome) {
                TcpConnectOutcome.OPEN -> return TcpPresence.Open(port)
                TcpConnectOutcome.REFUSED -> if (firstFailure == null) firstFailure = TcpPresence.Refused(port, detail)
                TcpConnectOutcome.TIMED_OUT -> if (firstFailure == null) firstFailure = TcpPresence.TimedOut(port, detail)
                TcpConnectOutcome.UNREACHABLE -> if (firstFailure == null) firstFailure = TcpPresence.Unreachable(port, detail)
                TcpConnectOutcome.POLICY_DENIED -> if (firstFailure == null) firstFailure = TcpPresence.PolicyDenied(port, detail)
                TcpConnectOutcome.UNKNOWN_FAILURE -> if (firstFailure == null) firstFailure = TcpPresence.UnknownFailure(port, detail)
                TcpConnectOutcome.NONE -> Unit
            }
        }
        return firstFailure ?: TcpPresence.None
    }

    companion object {
        private fun connect(ip: String, port: Int, timeoutMs: Int): TcpConnectResult {
            var socket: Socket? = null
            return try {
                socket = Socket()
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                TcpConnectResult(TcpConnectOutcome.OPEN)
            } catch (error: ConnectException) {
                // ConnectException alone does not identify who rejected or filtered the path.
                TcpConnectResult(TcpConnectOutcome.UNKNOWN_FAILURE, error.diagnosticDetail())
            } catch (error: SocketTimeoutException) {
                TcpConnectResult(TcpConnectOutcome.TIMED_OUT, error.diagnosticDetail())
            } catch (error: NoRouteToHostException) {
                TcpConnectResult(TcpConnectOutcome.UNREACHABLE, error.diagnosticDetail())
            } catch (error: SecurityException) {
                TcpConnectResult(TcpConnectOutcome.POLICY_DENIED, error.diagnosticDetail())
            } catch (error: IOException) {
                TcpConnectResult(TcpConnectOutcome.UNKNOWN_FAILURE, error.diagnosticDetail())
            } finally {
                try {
                    socket?.close()
                } catch (_: IOException) {
                    // Best effort close.
                }
            }
        }

        private fun Throwable.diagnosticDetail(): String =
            generateSequence(this) { it.cause }
                .take(3)
                .joinToString(" <- ") { error ->
                    buildString {
                        append(error.javaClass.simpleName)
                        error.message?.trim()?.takeIf(String::isNotEmpty)?.let {
                            append(": ")
                            append(it)
                        }
                    }
                }
                .take(160)
    }
}
