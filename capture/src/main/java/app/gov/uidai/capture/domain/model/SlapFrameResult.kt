package app.gov.uidai.capture.domain.model

import android.graphics.PointF
import android.graphics.RectF

data class SlapFrameResult(
    val handDetected: Boolean,
    val areaRatio: Float,
    val fingertips: List<PointF>,
    val box: RectF?,
    // Individual per-finger boxes, in the SAME upright-bitmap coordinate
    // space as [box]. Populated by the Otsu/row-projection band detector
    // (SlapFrameAnalyzer) -- empty for analyzers that only ever produce one
    // whole-hand box (e.g. the old skin-blob detector, MediaPipe).
    val fingerBoxes: List<RectF> = emptyList()
)