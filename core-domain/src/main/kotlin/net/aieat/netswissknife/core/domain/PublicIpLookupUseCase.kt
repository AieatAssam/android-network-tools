package net.aieat.netswissknife.core.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.aieat.netswissknife.core.network.NetworkResult
import net.aieat.netswissknife.core.network.netinfo.IpFamily
import net.aieat.netswissknife.core.network.netinfo.NetworkInfoOperation
import net.aieat.netswissknife.core.network.netinfo.PublicAddressInfo
import net.aieat.netswissknife.core.network.netinfo.PublicIpReport
import net.aieat.netswissknife.core.network.netinfo.PublicIpRepository
import net.aieat.netswissknife.core.network.operation.OperationRunner
import net.aieat.netswissknife.core.network.traceroute.GeoIpRepository
import net.aieat.netswissknife.core.network.traceroute.TracerouteReverseDnsRepository

/**
 * Looks up both public address families, then adds reverse DNS and approximate geo/ASN for
 * each address found. The enrichment is optional: its failure drops that detail and keeps the
 * address. Call this only from an explicit user action.
 */
class PublicIpLookupUseCase(
    private val publicIp: PublicIpRepository,
    private val geoIp: GeoIpRepository,
    private val reverseDns: TracerouteReverseDnsRepository,
) : UseCase<Unit, PublicIpReport> {
    override suspend fun invoke(params: Unit): PublicIpReport =
        coroutineScope {
            val ipv4 = async { lookupFamily(IpFamily.IPV4) }
            val ipv6 = async { lookupFamily(IpFamily.IPV6) }
            PublicIpReport(ipv4 = ipv4.await(), ipv6 = ipv6.await())
        }

    private suspend fun lookupFamily(family: IpFamily): NetworkResult<PublicAddressInfo> =
        when (val fetched = publicIp.fetch(family)) {
            is NetworkResult.Success -> NetworkResult.Success(enrich(fetched.data, family))
            is NetworkResult.Error -> fetched
        }

    private suspend fun enrich(
        address: String,
        family: IpFamily,
    ): PublicAddressInfo =
        coroutineScope {
            val hostname = async { optional { reverseLookup(address) } }
            val geo = async { optional { geoIp.lookup(address) } }
            PublicAddressInfo(address = address, family = family, hostname = hostname.await(), geo = geo.await())
        }

    private suspend fun reverseLookup(address: String): String? {
        val session = NetworkInfoOperation.newSession()
        return OperationRunner.run(session) { reverseDns.lookup(address, session) }
    }

    /** Runs an optional enrichment; any failure yields null, but cancellation still propagates. */
    private suspend fun <T> optional(block: suspend () -> T?): T? =
        runCatching { block() }.getOrElse {
            if (it is CancellationException) throw it
            null
        }
}
