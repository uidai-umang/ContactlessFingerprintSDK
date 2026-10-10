package app.gov.uidai.registration.repository.impl

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.gov.uidai.registration.model.FingerType
import app.gov.uidai.registration.repository.FileRepository
import app.gov.uidai.registration.utils.toYYYYMMDDHHmmss
import dagger.hilt.android.qualifiers.ApplicationContext
import app.gov.uidai.registration.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Date
import javax.inject.Inject

class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileRepository {

    /*
    * [APP_NAME]
    *   [Contactless]
    *       [UID]
    *           CL Images
    *   [ContactBased]
    *       [UID]
    *           CB Images
    * */
    override suspend fun saveJP2FingerImageToGallery(uid: String, fingerType: FingerType, fileName: String, data: ByteArray) =
        withContext(Dispatchers.IO) {
            val timestamp = Date().toYYYYMMDDHHmmss()
            val appName = context.getString(R.string.app_name)
            val jp2FileName = "${fileName}_${timestamp}.jp2"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, jp2FileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jp2")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/$appName/${fingerType.name}/$uid"
                )
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream: OutputStream ->
                    outputStream.write(data)
                    outputStream.flush()
                }
            } ?: throw IllegalStateException("Failed to create file URI")
        }

    override suspend fun readAsset(path: String): ByteArray = withContext(Dispatchers.IO) {
        context.assets.open(path).use { inputStream ->
            // Pre-size buffer if length is known, else grow dynamically
            val size = inputStream.available()
            val buffer = ByteArrayOutputStream(size)
            var b: Int
            while (inputStream.read().also { b = it } != -1) {
                buffer.write(b)
                ensureActive()
            }
            buffer.toByteArray()
        }
    }

    override suspend fun readJP2FingerImageFromGallery(uri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val size = inputStream.available()
            val buffer = ByteArrayOutputStream(size)
            var b: Int
            while (inputStream.read().also { b = it } != -1) {
                buffer.write(b)
                ensureActive()
            }
            buffer.toByteArray()
        }
    }
}