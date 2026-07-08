package app.gov.uidai.capture.utils.extension

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Base64
import android.util.Size
import app.gov.uidai.capture.utils.logExecutionTime
import app.gov.uidai.capture.utils.nativelib.BitmapRotator
import java.io.ByteArrayOutputStream

private const val TAG = "BitmapExtension"

fun Bitmap.toBase64(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 100
): String {
    return Base64.encodeToString(toByteArray(format, quality), Base64.NO_WRAP)
}

fun Bitmap.toByteArray(
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
    quality: Int = 100
): ByteArray {
    val stream = ByteArrayOutputStream()
    compress(format, quality, stream)
    return stream.toByteArray()
}

fun Bitmap.crop(rect: RectF): Bitmap {
    val left = rect.left.toInt().coerceAtLeast(0)
    val top = rect.top.toInt().coerceAtLeast(0)
    return logExecutionTime(TAG, "BitmapCropping") {
        Bitmap.createBitmap(
            this,
            left,
            top,
            rect.width().toInt().coerceAtMost(width - left),
            rect.height().toInt().coerceAtMost(height - top)
        )
    }
}

fun Bitmap.rotate(rotation: Int): Bitmap {
    return logExecutionTime(TAG, "BitmapRotation") {
        BitmapRotator.rotate(this, rotation)
    }
}

fun Bitmap.size(): Size {
    return Size(width, height)
}