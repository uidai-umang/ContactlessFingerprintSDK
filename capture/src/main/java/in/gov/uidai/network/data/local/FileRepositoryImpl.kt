package `in`.gov.uidai.network.data.local

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.core.net.toUri
import app.gov.uidai.capture.R
import app.gov.uidai.registration.model.FingerType
import `in`.gov.uidai.utility.extension.toYYYYMMDDHHmmss
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Date

class FileRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : FileRepository {

    companion object {
        private const val PATH = "/ContactlessFingerSDK"
    }

    override suspend fun saveBitmapAndGetUri(bitmap: Bitmap, captureName: String): Uri =
        withContext(Dispatchers.IO) {
            val timeStamp = Date().toYYYYMMDDHHmmss()
            val fileName = "${captureName}_$timeStamp.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + PATH
                    )
                }
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            uri?.let {
                val outputStream = context.contentResolver.openOutputStream(it)
                outputStream?.use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                }
            }
            uri!!
        }

    override suspend fun deleteFile(uri: Uri) {
        withContext(Dispatchers.IO) {
            context.contentResolver.delete(uri, null, null)
        }
    }

    override suspend fun getBitmapFromUriString(uriString: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val uri = uriString.toUri()
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                null
            }
        }

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
