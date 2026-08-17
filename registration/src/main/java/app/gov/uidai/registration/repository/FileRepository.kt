package app.gov.uidai.registration.repository

import android.net.Uri
import app.gov.uidai.registration.model.FingerType

interface FileRepository {
    suspend fun saveJP2FingerImageToGallery(uid: String, fingerType: FingerType, fileName: String, data: ByteArray)
    suspend fun readAsset(path: String): ByteArray
    suspend fun readJP2FingerImageFromGallery(uri: Uri): ByteArray?
}