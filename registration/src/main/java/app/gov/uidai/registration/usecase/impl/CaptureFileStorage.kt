package app.gov.uidai.registration.usecase.impl

import android.content.Context
import app.gov.uidai.registration.usecase.CaptureEncryption
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptureFileStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val encryption: CaptureEncryption   // interface, not the concrete class
) {
    fun write(bytes: ByteArray, fileName: String): String {
        val file = File(context.filesDir, "pending_captures/$fileName")
        file.parentFile?.mkdirs()
        encryption.encryptTo(file, bytes)
        return file.absolutePath
    }

    fun read(filePath: String): ByteArray {
        return encryption.decryptFrom(File(filePath))
    }

    fun delete(filePath: String) {
        File(filePath).delete()
    }
}