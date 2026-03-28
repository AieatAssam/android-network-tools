package net.aieat.netswissknife.core.network.ping

/** The outcome of a single ICMP-style reachability probe. */
enum class PingStatus {
    /** Host responded within the timeout window. */
    SUCCESS,
    /** No response received before the timeout expired. */
    TIMEOUT,
    /** Network or resolution error prevented the probe from completing. */
    ERROR
}
