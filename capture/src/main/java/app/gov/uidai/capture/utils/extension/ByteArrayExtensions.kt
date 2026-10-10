package app.gov.uidai.capture.utils.extension

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Base64
import android.util.Size
import java.io.ByteArrayOutputStream

fun ByteArray.toBitmap(width: Int, height: Int): Bitmap {
    val stream = ByteArrayOutputStream()
    val yuvImage = YuvImage(this, ImageFormat.NV21, width, height, null)
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, stream)
    val imageBytes = stream.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

fun ByteArray.toBitmap(size: Size): Bitmap {
    return toBitmap(size.width, size.height)
}

/**
 * For Python output
 */
fun ByteArray.toBitmap(): Bitmap {
    return BitmapFactory.decodeByteArray(this, 0, this.size)
}

fun ByteArray.toBase64(width: Int, height: Int): String {
    val stream = ByteArrayOutputStream()
    val yuvImage = YuvImage(this, ImageFormat.NV21, width, height, null)
    yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, stream)
    val imageBytes = stream.toByteArray()
    return Base64.encodeToString(imageBytes, Base64.NO_WRAP)
}

fun ByteArray.cropNV21(
    width: Int,
    height: Int,
    cropRect: Rect
): Pair<ByteArray, Size> {
    // Align to even values for YUV420
    fun evenFloor(value: Int) = value and (Int.MAX_VALUE - 1)  // clears LSB without sign issues

    val cropX = evenFloor(cropRect.left)
    val cropY = evenFloor(cropRect.top)
    val cropWidth = evenFloor(cropRect.width())
    val cropHeight = evenFloor(cropRect.height())

    if (cropWidth <= 0 || cropHeight <= 0) {
        return ByteArray(0) to Size(0, 0)
    }

    val ySize = width * height
    val outSize = cropWidth * cropHeight * 3 / 2
    val out = ByteArray(outSize)

    // --- Copy Y plane ---
    var outYIndex = 0
    for (row in cropY until cropY + cropHeight) {
        val srcIndex = row * width + cropX
        System.arraycopy(this, srcIndex, out, outYIndex, cropWidth)
        outYIndex += cropWidth
    }

    // --- Copy UV plane ---
    val uvCropHeight = cropHeight / 2
    var outUVIndex = cropWidth * cropHeight
    for (row in 0 until uvCropHeight) {
        val srcRow = cropY / 2 + row  // UV row index
        val srcIndex = ySize + srcRow * width + cropX
        System.arraycopy(this, srcIndex, out, outUVIndex, cropWidth)
        outUVIndex += cropWidth
    }

    return out to Size(cropWidth, cropHeight)
}