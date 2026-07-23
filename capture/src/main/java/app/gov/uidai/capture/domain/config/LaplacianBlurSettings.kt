package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

object LaplacianBlurSettings : PreferenceGroup {
    override val title: String get() = "Laplacian Blur (live pre-filter)"

    val MIN_VARIANCE = PreferenceParam(
        key = "laplacian_blur.min_variance",
        displayName = "Min Variance (sharp threshold)",
        type = PreferenceType.FLOAT,
        defaultValue = 360.0f
    )
}