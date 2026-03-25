package com.example.netswissknife.core.network.traceroute

/** Protocol used for sending traceroute probes. */
enum class TracerouteProbeType {
    /** ICMP Echo requests (default). Works best on most networks. */
    ICMP,

    /** UDP probes to high-numbered ports. Useful when ICMP is rate-limited or filtered. */
    UDP
}
