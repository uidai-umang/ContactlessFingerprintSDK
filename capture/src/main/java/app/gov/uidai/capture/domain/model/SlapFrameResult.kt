package app.gov.uidai.capture.domain.model

import android.graphics.PointF
import android.graphics.RectF

data class SlapFrameResult(
    val handDetected: Boolean,
    val areaRatio: Float,
    val fingertips: List<PointF>,
    val box: RectF?
)
