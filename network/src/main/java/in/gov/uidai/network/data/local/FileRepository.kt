package `in`.gov.uidai.network.data.local

import android.graphics.Bitmap
import android.net.Uri

interface FileRepository {

    suspend fun saveBitmapAndGetUri(bitmap: Bitmap, captureName: String): Uri

    suspend fun deleteFile(uri: Uri)

    suspend fun getBitmapFromUriString(uriString: String): Bitmap?
}