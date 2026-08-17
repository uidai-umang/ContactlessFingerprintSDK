package app.gov.uidai.registration.encryption

import android.util.Base64
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.inject.Inject

data class EncryptedCapturePayload(
    val encryptedImageBytes: ByteArray,
    val encryptedSessionKey: String,
    val iv: String,
    val hmac: String,
    val thumbprint: String
)

class EncryptionService @Inject constructor(
    private val encrypter: Encrypter,
    private val certificate: X509Certificate
) {
    /** Encrypts a captured fingerprint image for upload. Called once per
     * capture, right before building the multipart request -- lives in
     * the Host App's upload/networking layer, NOT the Camera SDK, since
     * the SDK never touches networking. */
    fun encryptImage(imageBytes: ByteArray): EncryptedCapturePayload {
        val sessionKey = encrypter.generateSessionKey()
        val iv = encrypter.generateIv()

        val encryptedImage = encrypter.encryptAesGcm(imageBytes, sessionKey, iv)
        val encryptedSessionKey = encrypter.encryptSessionKeyWithPublicKey(sessionKey)

        val plaintextHash = encrypter.sha256(imageBytes)
        val encryptedHash = encrypter.encryptAesGcm(plaintextHash, sessionKey, iv)

        val thumbprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)

        return EncryptedCapturePayload(
            encryptedImageBytes = encryptedImage,
            encryptedSessionKey = Base64.encodeToString(encryptedSessionKey, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP),
            hmac = Base64.encodeToString(encryptedHash, Base64.NO_WRAP),
            thumbprint = Base64.encodeToString(thumbprint, Base64.NO_WRAP)
        )
    }
}