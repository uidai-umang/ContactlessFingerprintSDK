package app.gov.uidai.capture.utils.extension

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun String.toBitmap(): Bitmap {
    val decodedBytes = Base64.decode(this, Base64.NO_WRAP)
    return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
}