package com.example.netswissknife.core.network.portscan

/** The result status of scanning a single port. */
enum class PortStatus {
    /** TCP connection succeeded – the port is accepting connections. */
    OPEN,
    /** TCP connection was actively refused. */
    CLOSED,
    /** Connection attempt timed out – port may be firewalled. */
    FILTERED
}
