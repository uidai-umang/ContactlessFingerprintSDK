package app.gov.uidai.registration.encryption

import android.util.Base64
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

class X509CertificateFactory {
    companion object {
        private const val CERTIFICATE_TYPE = "X.509"
        private const val TAG_BEGIN = "-----BEGIN CERTIFICATE-----"
        private const val TAG_END = "-----END CERTIFICATE-----"
    }

    fun generateCertificate(certificatePem: String): X509Certificate {
        val content = certificatePem
            .replace(TAG_BEGIN, "")
            .replace(TAG_END, "")
            .trim()
        val certBytes = Base64.decode(content, Base64.DEFAULT)
        return CertificateFactory.getInstance(CERTIFICATE_TYPE)
            .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
    }
}