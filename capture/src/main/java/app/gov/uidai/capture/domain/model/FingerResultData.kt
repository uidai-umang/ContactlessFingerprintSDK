package app.gov.uidai.capture.domain.model

import android.graphics.Bitmap
import android.graphics.RectF

data class FingerResultData(
    val box: RectF,
    val mask: Bitmap? = null
)