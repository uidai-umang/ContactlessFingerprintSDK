package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

data class GlareConfig(
    val enabled: Boolean,
    val threshold: Int,
    val varianceMin: Int,
    val varianceMax: Int,
    val maxGlareValue: Int,
)

object GlareSettings : PreferenceGroup {
    override val title: String
        get() = "Glare Check"

    val ENABLED = PreferenceParam(
        key = "glare.enabled",
        displayName = "Enabled",
        type = PreferenceType.BOOLEAN,
        defaultValue = true
    )
    val THRESHOLD = PreferenceParam(
        key = "glare.binary_threshold",
        displayName = "Binary Threshold",
        type = PreferenceType.INT,
        defaultValue = 200
    )
    val VARIANCE_MIN = PreferenceParam(
        key = "glare.variance_min",
        displayName = "Variance Min",
        type = PreferenceType.INT,
        defaultValue = 10500
    )
    val VARIANCE_MAX = PreferenceParam(
        key = "glare.variance_max",
        displayName = "Variance Max",
        type = PreferenceType.INT,
        defaultValue = 15000
    )
    val MAX_GLARE_VALUE = PreferenceParam(
        key = "glare.max_glare_value",
        displayName = "Max Glare Value",
        type = PreferenceType.INT,
        defaultValue = 20000
    )
}