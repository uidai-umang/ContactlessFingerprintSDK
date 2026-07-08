package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

data class BrightnessConfig(
    val enabled: Boolean,
    val darkPercentage: Float,
    val darkThreshold: Int,
    val brightPercentage: Float,
    val brightThreshold: Int,
)

object BrightnessSettings : PreferenceGroup {
    override val title: String
        get() = "Brightness Check"

    val ENABLED = PreferenceParam(
        "brightness.enabled",
        "Enabled",
        PreferenceType.BOOLEAN,
        true
    )
    val DARK_PERCENT = PreferenceParam(
        "brightness.dark_percent",
        "Dark Percent",
        PreferenceType.FLOAT,
        0.60f
    )
    val DARK_THRESHOLD = PreferenceParam(
        "brightness.dark_threshold",
        "Dark Threshold",
        PreferenceType.INT,
        50
    )
    val BRIGHT_PERCENT = PreferenceParam(
        "brightness.bright_percent",
        "Bright Percent",
        PreferenceType.FLOAT,
        0.60f
    )
    val BRIGHT_THRESHOLD = PreferenceParam(
        "brightness.bright_threshold",
        "Bright Threshold",
        PreferenceType.INT,
        200
    )
}