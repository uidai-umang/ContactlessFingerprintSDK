package app.gov.uidai.capture.utils.extension

import android.graphics.Bitmap
import android.media.Image
import app.gov.uidai.capture.utils.nativelib.YuvConverter

private const val TAG = "ImageExtension"

fun Image.toBitmap(): Bitmap {
    return toByteArray().toBitmap(width, height)

}

fun Image.toByteArray(): ByteArray {
    return YuvConverter.yuv420ToNv21(this)
}