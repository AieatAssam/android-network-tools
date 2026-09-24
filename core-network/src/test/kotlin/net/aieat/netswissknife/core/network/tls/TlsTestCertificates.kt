package net.aieat.netswissknife.core.network.tls

import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal object TlsTestCertificates {
    fun read(name: String): X509Certificate {
        val stream = requireNotNull(javaClass.getResourceAsStream("/tls/$name.pem")) {
            "Missing TLS fixture: $name.pem"
        }
        return stream.use {
            CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
        }
    }
}
