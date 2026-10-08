package net.aieat.netswissknife.core.network.lan

/** Evidence that can be used to confirm that the selected endpoint answered. */
sealed interface PresenceEvidence {
    data class IcmpEcho(val rttMs: Long) : PresenceEvidence
    data class TcpConnection(val port: Int) : PresenceEvidence
    data class LocalProtocolReply(
        val method: DiscoveryMethod,
        val name: String? = null,
    ) : PresenceEvidence
}

data class PresenceDecision(
    val confirmed: Boolean,
    val methods: Set<DiscoveryMethod> = emptySet(),
    val pingMs: Long = 0L,
    val name: String? = null,
)

/**
 * Pure host-presence policy. Failure observations are intentionally absent from this input:
 * they remain diagnostics and cannot become positive evidence through repetition.
 */
object PresenceClassifier {
    fun classify(evidence: Iterable<PresenceEvidence>): PresenceDecision {
        val observations = evidence.toList()
        val methods = linkedSetOf<DiscoveryMethod>()
        var pingMs = 0L
        var name: String? = null
        observations.forEach { observation ->
            when (observation) {
                is PresenceEvidence.IcmpEcho -> {
                    methods += DiscoveryMethod.ICMP
                    pingMs = observation.rttMs
                }
                is PresenceEvidence.TcpConnection -> methods += DiscoveryMethod.TCP_OPEN
                is PresenceEvidence.LocalProtocolReply -> {
                    when (observation.method) {
                        DiscoveryMethod.NETBIOS,
                        DiscoveryMethod.MDNS,
                        -> {
                            methods += observation.method
                            name = observation.name ?: name
                        }
                        else -> Unit
                    }
                }
            }
        }
        return PresenceDecision(
            confirmed = methods.isNotEmpty(),
            methods = methods,
            pingMs = pingMs,
            name = name,
        )
    }

}
