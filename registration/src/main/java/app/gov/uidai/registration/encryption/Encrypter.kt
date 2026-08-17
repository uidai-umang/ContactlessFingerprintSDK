package app.gov.uidai.registration.encryption

import java.security.PublicKey
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.spec.MGF1ParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class Encrypter(certificate: X509Certificate) {
    companion object {
        private const val ASYMMETRIC_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding"
        private const val AES_ALGORITHM = "AES"
        private const val AES_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val SYMMETRIC_KEY_SIZE = 256
        private const val IV_SIZE_BYTES = 12   // 96 bits -- GCM standard nonce size
        private const val AUTH_TAG_SIZE_BITS = 128
        private const val HASH_ALGORITHM = "SHA-256"
    }

    private val publicKey: PublicKey = certificate.publicKey
    private val secureRandom = SecureRandom()

    /** Fresh AES-256 session key, generated per capture -- never reused. */
    fun generateSessionKey(): ByteArray {
        val keyGen = KeyGenerator.getInstance(AES_ALGORITHM)
        keyGen.init(SYMMETRIC_KEY_SIZE, secureRandom)
        return keyGen.generateKey().encoded
    }

    /** Fresh random IV, one per capture -- paired 1:1 with a fresh session key. */
    fun generateIv(): ByteArray {
        val iv = ByteArray(IV_SIZE_BYTES)
        secureRandom.nextBytes(iv)
        return iv
    }

    /** RSA-OAEP-SHA256 encrypts the small AES session key -- matches the
     * reference app's padding exactly, so the Go backend's
     * rsa.DecryptOAEP(sha256.New, ...) call decrypts it correctly. */
    fun encryptSessionKeyWithPublicKey(sessionKey: ByteArray): ByteArray {
        val oaepSpec = OAEPParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT
        )
        val cipher = Cipher.getInstance(ASYMMETRIC_ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepSpec)
        return cipher.doFinal(sessionKey)
    }

    /** AES-256-GCM encrypts data with the given session key + IV. Output
     * includes the 128-bit auth tag appended at the end (standard Cipher
     * behavior for GCM), matching what Go's gcm.Open expects. */
    fun encryptAesGcm(data: ByteArray, sessionKey: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        val keySpec = SecretKeySpec(sessionKey, AES_ALGORITHM)
        val gcmSpec = GCMParameterSpec(AUTH_TAG_SIZE_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(data)
    }

    fun sha256(data: ByteArray): ByteArray {
        return MessageDigest.getInstance(HASH_ALGORITHM).digest(data)
    }
}