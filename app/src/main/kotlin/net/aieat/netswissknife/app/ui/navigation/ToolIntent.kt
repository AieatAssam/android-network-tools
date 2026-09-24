package net.aieat.netswissknife.app.ui.navigation

import net.aieat.netswissknife.core.network.HostValidator
import java.util.Locale

/** A validated host literal or IDN hostname carried between tool screens. */
data class ToolHost(val value: String) {
    init {
        require(isValid(value))
    }

    /** Canonical form suitable for network APIs (IDNs become ASCII). */
    val canonical: String get() = requireNotNull(HostValidator.normalize(value))

    companion object {
        fun parse(value: String): ToolHost? {
            if (!isValid(value)) return null
            return ToolHost(value)
        }

        private fun isValid(value: String): Boolean {
            if (value.isEmpty() || value != value.trim() || value.any(Char::isWhitespace)) return false
            if ('/' in value || '\\' in value || '?' in value || '#' in value) return false
            if (value.contains(':') && '%' in value) {
                val zoneMarker = value.indexOf('%')
                val zone = value.substring(zoneMarker + 1).removeSuffix("]")
                // Keep scoped literals portable across common interface naming
                // schemes, and prevent URI/path delimiters or nested escapes.
                if (zoneMarker != value.lastIndexOf('%') || !portableZone.matches(zone)) return false
            }
            return HostValidator.normalize(value) != null
        }

        private val portableZone = Regex("^[A-Za-z0-9_.-]+$")
    }
}

@JvmInline
value class ToolPort private constructor(val value: Int) {
    companion object {
        fun parse(value: Int): ToolPort? = value.takeIf { it in 1..65535 }?.let(::ToolPort)
    }
}

data class ToolMacAddress(val value: String) {
    init {
        require(pattern.matches(value) && ':' in value && '-' !in value && value == value.uppercase(Locale.ROOT))
    }

    companion object {
        private val pattern = Regex("^(?:[0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}$")

        fun parse(value: String): ToolMacAddress? {
            if (!pattern.matches(value)) return null
            if (':' in value && '-' in value) return null
            val normalized = value.replace('-', ':').uppercase(Locale.ROOT)
            return ToolMacAddress(normalized)
        }
    }
}

/** An IPv4 or IPv6 network in address/prefix form. */
data class ToolSubnet(val value: String) {
    init {
        require(isValidSubnet(value))
    }

    companion object {
        fun parse(value: String): ToolSubnet? {
            if (value != value.trim() || value.any(Char::isWhitespace)) return null
            val slash = value.lastIndexOf('/')
            if (slash <= 0 || slash == value.lastIndex) return null
            val address = value.substring(0, slash)
            val prefix = value.substring(slash + 1).toIntOrNull() ?: return null
            val isIpv6 = address.contains(':')
            val normalized = HostValidator.normalize(address) ?: return null
            if (isIpv6 && '%' in address) return null // A scoped interface is not a portable network prefix.
            if (prefix !in 0..if (isIpv6) 128 else 32) return null
            // Hostnames are not subnet addresses; require a literal.
            if (!isIpv6 && !HostValidator.isValidIpv4(address)) return null
            return ToolSubnet("$normalized/$prefix")
        }

        private fun isValidSubnet(value: String): Boolean {
            if (value != value.trim() || value.any(Char::isWhitespace)) return false
            val slash = value.lastIndexOf('/')
            if (slash <= 0 || slash == value.lastIndex) return false
            val address = value.substring(0, slash)
            val prefix = value.substring(slash + 1).toIntOrNull() ?: return false
            val isIpv6 = address.contains(':')
            if (isIpv6 && '%' in address) return false
            if (prefix !in 0..(if (isIpv6) 128 else 32)) return false
            return if (isIpv6) HostValidator.isValidIpv6(address) else HostValidator.isValidIpv4(address)
        }
    }
}

enum class ToolSource(val wireName: String) {
    LAN("lan"), MDNS("mdns"), PING("ping"), TRACEROUTE("traceroute"), DNS("dns"),
}

enum class HostTool(val wireName: String) {
    PING("ping"), PORTS("ports"), HTTP("http"), TLS("tls"),
}

sealed interface ToolDestination {
    data class HostTarget(
        val tool: HostTool,
        val host: ToolHost,
        val port: ToolPort? = null,
    ) : ToolDestination {
        init {
            require(port == null || tool in setOf(HostTool.PORTS, HostTool.HTTP, HostTool.TLS))
        }
    }

    data class WakeOnLan(val mac: ToolMacAddress) : ToolDestination

    data class Subnet(val subnet: ToolSubnet) : ToolDestination
}

/** Pure, platform-independent arguments for one user-initiated tool handoff. */
data class ToolIntent(
    val destination: ToolDestination,
    val source: ToolSource? = null,
)
