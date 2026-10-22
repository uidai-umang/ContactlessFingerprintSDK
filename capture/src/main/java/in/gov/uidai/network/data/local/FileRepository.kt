package `in`.gov.uidai.network.data.local

import android.graphics.Bitmap
import android.net.Uri
import app.gov.uidai.registration.model.FingerType

interface FileRepository {

    suspend fun saveBitmapAndGetUri(bitmap: Bitmap, captureName: String): Uri

    suspend fun deleteFile(uri: Uri)

    suspend fun getBitmapFromUriString(uriString: String): Bitmap?

    suspend fun saveJP2FingerImageToGallery(uid: String, fingerType: FingerType, fileName: String, data: ByteArray)

    suspend fun readAsset(path: String): ByteArray

    suspend fun readJP2FingerImageFromGallery(uri: Uri): ByteArray?
}
