package net.aieat.netswissknife.core.network.netinfo

import net.aieat.netswissknife.core.network.NetworkResult

/** Asks an external service which public address this device uses for one IP family. */
interface PublicIpRepository {
    /** Performs one explicit request. Callers must only invoke this from a user action. */
    suspend fun fetch(family: IpFamily): NetworkResult<String>
}

/** Accepts only a bare address literal of the requested family. Anything else is not an address. */
object PublicIpParser {
    private const val MAX_TEXT_LENGTH = 64

    fun parse(
        body: String,
        family: IpFamily,
    ): String? {
        val text = body.trim()
        if (text.isEmpty() || text.length > MAX_TEXT_LENGTH || text.contains('%')) return null
        return text.takeIf { IpAddressClassifier.familyOf(it) == family }
    }
}
