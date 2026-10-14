package net.aieat.netswissknife.core.network.portscan

/** Application-level work performed after a TCP connection has been accepted. */
enum class ProbeKind { PASSIVE, HTTP, SMTP, FTP, SSH, POP3, IMAP, TLS_PEEK }

/** Stable service-port policy for optional, bounded port-scanner probes. */
internal object ServiceProbes {
    fun kindFor(port: Int): ProbeKind = when {
        port in HTTP_PORTS -> ProbeKind.HTTP
        port in SMTP_PORTS -> ProbeKind.SMTP
        port == 21 -> ProbeKind.FTP
        port == 22 -> ProbeKind.SSH
        port == 110 -> ProbeKind.POP3
        port == 143 -> ProbeKind.IMAP
        port in TLS_PORTS -> ProbeKind.TLS_PEEK
        else -> ProbeKind.PASSIVE
    }

    fun request(port: Int, host: String): ByteArray? = when (kindFor(port)) {
        ProbeKind.HTTP -> "HEAD / HTTP/1.0\r\nHost: ${httpAuthority(host, port)}\r\nConnection: close\r\n\r\n"
            .toByteArray(Charsets.US_ASCII)
        ProbeKind.SMTP -> "EHLO netswissknife\r\n".toByteArray(Charsets.US_ASCII)
        ProbeKind.PASSIVE, ProbeKind.FTP, ProbeKind.SSH, ProbeKind.POP3, ProbeKind.IMAP,
        ProbeKind.TLS_PEEK -> null
    }

    private fun httpAuthority(host: String, port: Int): String {
        val cleanHost = host.trim().removePrefix("[").removeSuffix("]")
            .filter { it.isLetterOrDigit() || it in ".:%-" }
            .take(253)
        val authorityHost = cleanHost.replace("%", "%25")
        val displayHost = if (':' in cleanHost) "[$authorityHost]" else authorityHost
        return displayHost + if (port == 80) "" else ":$port"
    }

    private val HTTP_PORTS = setOf(80, 8000, 8080)
    private val SMTP_PORTS = setOf(25, 587)
    private val TLS_PORTS = setOf(443, 8443, 993, 995, 465, 636)
}
