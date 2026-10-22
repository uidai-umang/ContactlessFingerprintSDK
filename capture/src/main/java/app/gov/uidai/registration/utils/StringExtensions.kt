package app.gov.uidai.registration.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64

fun String.toBitmap(): Bitmap {
    val decodedBytes = Base64.decode(this, Base64.NO_WRAP)
    val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    return bitmap
}