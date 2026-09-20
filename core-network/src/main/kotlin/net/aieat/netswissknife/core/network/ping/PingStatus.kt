package net.aieat.netswissknife.core.network.ping

/** The outcome of a single platform reachability probe. */
enum class PingStatus {
    /** Host responded within the timeout window. */
    SUCCESS,
    /** No response received before the timeout expired. */
    TIMEOUT,
    /** An explicit ICMP/network rejection was received before the timeout. */
    UNREACHABLE,
    /** Network or resolution error prevented the probe from completing. */
    ERROR
}
