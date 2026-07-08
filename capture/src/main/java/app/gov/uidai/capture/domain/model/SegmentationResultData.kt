package app.gov.uidai.capture.domain.model

import android.graphics.Bitmap
import android.graphics.RectF

data class SegmentationResultData(
    val box: RectF,
    val mask: Bitmap? = null,
    val label: String = "Fingerprint",
    val confidence: Float = 0f
)