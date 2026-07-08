package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.domain.model.FingerCheckMethodType
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

data class FingerConfig(
    val enabled: Boolean,
    val modelPath: String,
    val fingerCategoryIds: List<UInt>,
    val minFingerArea: Float,
    val maxFingerArea: Float,
    val goodAreaMin: Float,
    val goodAreaMax: Float,
    val textureThresholdMin: Float,
    val textureThresholdMax: Float,
    val edgeDensityThresholdMin: Float,
    val edgeDensityThresholdMax: Float
)

object FingerResultStatus {
    const val RESULT_FINGER_OK = 0
    const val RESULT_FINGER_TOO_CLOSE = 1
    const val RESULT_FINGER_TOO_FAR = 2
    const val RESULT_FINGER_TOO_MUCH_CLOSE = 3
    const val RESULT_FINGER_NOT_DETECTED = -1
}

object FingerSettings : PreferenceGroup {
    override val title: String
        get() = "Finger Check"

    val METHOD = PreferenceParam(
        key = "finger.method",
        displayName = "Finger Check Method",
        type = PreferenceType.CHOICE(FingerCheckMethodType.entries),
        defaultValue = FingerCheckMethodType.MediapipeSelfieSegmenter
    )
    val ENABLED = PreferenceParam(
        "finger.enabled",
        "Enabled",
        PreferenceType.BOOLEAN,
        true
    )
    val MIN_FINGER_AREA = PreferenceParam(
        "finger.min_area",
        "Min Finger Area",
        PreferenceType.FLOAT,
        0.2f
    )
    val MAX_FINGER_AREA = PreferenceParam(
        "finger.max_area",
        "Max Finger Area",
        PreferenceType.FLOAT,
        0.90f
    )
    val GOOD_AREA_MIN = PreferenceParam(
        "finger.good_area_min",
        "Good Area Min",
        PreferenceType.FLOAT,
        0.7f
    )
    val GOOD_AREA_MAX = PreferenceParam(
        "finger.good_area_max",
        "Good Area Max",
        PreferenceType.FLOAT,
        0.85f
    )
    val TEXTURE_THRESHOLD_MIN = PreferenceParam(
        "finger.texture_threshold_min",
        "Texture Min",
        PreferenceType.FLOAT,
        20.0f
    )
    val TEXTURE_THRESHOLD_MAX = PreferenceParam(
        "finger.texture_threshold_max",
        "Texture Max",
        PreferenceType.FLOAT,
        120.0f
    )
    val EDGE_DENSITY_THRESHOLD_MIN = PreferenceParam(
        "finger.edge_density_threshold_min",
        "Edge Density Min",
        PreferenceType.FLOAT,
        0.02f
    )
    val EDGE_DENSITY_THRESHOLD_MAX = PreferenceParam(
        "finger.edge_density_threshold_max",
        "Edge Density Max",
        PreferenceType.FLOAT,
        5.0f
    )
}