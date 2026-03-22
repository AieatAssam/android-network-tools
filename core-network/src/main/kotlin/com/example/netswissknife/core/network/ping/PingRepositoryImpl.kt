package com.example.netswissknife.core.network.ping

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress

/**
 * Production [PingRepository] that uses [InetAddress.isReachable] for each probe.
 *
 * On Android, [InetAddress.isReachable] uses ICMP echo when the process has the
 * required privilege, otherwise it falls back to TCP echo (port 7).  For diagnostic
 * purposes this gives accurate round-trip times.
 *
 * The [checker] parameter is a functional hook injected for testing; the default
 * implementation delegates to [InetAddress.isReachable].
 *
 * @param checker  Function that performs a single reachability probe. Defaults to the
 *                 real [InetAddress] implementation.
 * @param delayBetweenProbesMs  How long to wait between successive probes (ms). Defaults to 1 000.
 */
class PingRepositoryImpl(
    private val checker: (host: String, timeoutMs: Int) -> ReachabilityResult = DEFAULT_CHECKER,
    private val delayBetweenProbesMs: Long = 1_000L
) : PingRepository {

    companion object {
        val DEFAULT_CHECKER: (String, Int) -> ReachabilityResult = { host, timeoutMs ->
            val start = System.currentTimeMillis()
            try {
                val addr = InetAddress.getByName(host)
                val reachable = addr.isReachable(timeoutMs)
                ReachabilityResult(
                    reachable = reachable,
                    rtTimeMs = System.currentTimeMillis() - start
                )
            } catch (e: Exception) {
                ReachabilityResult(
                    reachable = false,
                    rtTimeMs = System.currentTimeMillis() - start,
                    errorMessage = e.message ?: e.javaClass.simpleName
                )
            }
        }
    }

    override fun ping(
        host: String,
        count: Int,
        timeoutMs: Int,
        packetSize: Int
    ): Flow<PingPacketResult> = flow {
        for (seq in 1..count) {
            val result = checker(host, timeoutMs)

            val packet = PingPacketResult(
                sequence = seq,
                host = host,
                rtTimeMs = if (result.reachable) result.rtTimeMs else null,
                status = when {
                    result.errorMessage != null -> PingStatus.ERROR
                    result.reachable -> PingStatus.SUCCESS
                    else -> PingStatus.TIMEOUT
                },
                errorMessage = result.errorMessage
            )

            emit(packet)

            if (seq < count) {
                delay(delayBetweenProbesMs)
            }
        }
    }.flowOn(Dispatchers.IO)
}
