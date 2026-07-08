package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.domain.model.BlurCheckMethodType
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

data class BlurConfig (
    val enabled: Boolean,
    val modelPath: String,
    val threshold: Float,
)

object BlurSettings : PreferenceGroup {
    override val title: String
        get() = "Blur Check"

    val ENABLED = PreferenceParam(
        key = "blur.enabled",
        displayName = "Enabled",
        type = PreferenceType.BOOLEAN,
        defaultValue = true
    )
    val MODEL = PreferenceParam(
        key = "blur.model",
        displayName = "Blur Model",
        type = PreferenceType.CHOICE(BlurCheckMethodType.entries),
        defaultValue = BlurCheckMethodType.NewDensenet
    )
    val THRESHOLD = PreferenceParam(
        key = "blur.threshold",
        displayName = "Blur Threshold",
        type = PreferenceType.FLOAT,
        defaultValue = 0.85f
    )
}