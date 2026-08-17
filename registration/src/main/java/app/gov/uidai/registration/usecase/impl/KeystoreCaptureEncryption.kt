package app.gov.uidai.registration.usecase.impl

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import app.gov.uidai.registration.usecase.CaptureEncryption
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeystoreCaptureEncryption @Inject constructor() : CaptureEncryption {

    companion object {
        private const val KEY_ALIAS = "capture_encryption_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    override fun encryptTo(file: File, bytes: ByteArray) {
        if (file.exists()) file.delete()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv   // GCM generates a fresh random IV per encryption — must be stored alongside ciphertext
        val ciphertext = cipher.doFinal(bytes)

        // File layout: [ivLength: 1 byte][iv: ivLength bytes][ciphertext: rest]
        file.outputStream().use { out ->
            out.write(iv.size)
            out.write(iv)
            out.write(ciphertext)
        }
    }

    override fun decryptFrom(file: File): ByteArray {
        val allBytes = file.readBytes()
        val ivLength = allBytes[0].toInt()
        val iv = allBytes.copyOfRange(1, 1 + ivLength)
        val ciphertext = allBytes.copyOfRange(1 + ivLength, allBytes.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }
}