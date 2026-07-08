package app.gov.uidai.capture.domain.model

import android.graphics.Bitmap
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap

class ImageDataProvider(
    private val byteArray: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int
) {

    private var uprightBitmapCache: Bitmap? = null
    private var bitmapCache: Bitmap? = null

    fun getAsByteArray(): ByteArray = synchronized(this) {
        return byteArray
    }

    fun getAsUprightBitmap(): Bitmap = synchronized(this) {
        return uprightBitmapCache ?: let {
            uprightBitmapCache = getAsByteArray()
                .toBitmap(width, height)
                .rotate(rotationDegrees)
            uprightBitmapCache!!
        }
    }

    fun getAsBitmap(): Bitmap = synchronized(this){
        bitmapCache ?: let {
            bitmapCache = getAsByteArray().toBitmap(width, height)
            bitmapCache!!
        }
    }

    fun clearCache() {
        uprightBitmapCache?.recycle()
        bitmapCache?.recycle()
        uprightBitmapCache = null
        bitmapCache = null
    }
}
