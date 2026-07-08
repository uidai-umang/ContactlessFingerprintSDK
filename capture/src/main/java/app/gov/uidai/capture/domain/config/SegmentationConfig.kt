package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.domain.model.SegmentationMethodType
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

data class SegmentationConfig (
    val enabled: Boolean,
    val modelPath: String,
    val yoloThreshold: Float,
)

object SegmentationSettings : PreferenceGroup {
    override val title: String
        get() = "Segmentation"

    val ENABLED = PreferenceParam(
        "segmentation.enabled",
        "Enabled",
        PreferenceType.BOOLEAN,
        true
    )
    val MODEL = PreferenceParam(
        key = "segmentation.model",
        displayName = "Segmentation Model",
        type = PreferenceType.CHOICE(SegmentationMethodType.entries),
        defaultValue = SegmentationMethodType.U2NetFloat32
    )
    val YOLO_THRESHOLD = PreferenceParam(
        "segmentation.confidence",
        "YOLO Confidence",
        PreferenceType.FLOAT,
        0.7f
    )
}