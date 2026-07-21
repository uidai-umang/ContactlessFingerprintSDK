package app.gov.uidai.capture.domain.config

import app.gov.uidai.capture.domain.model.FingerCheckMethodType
import app.gov.uidai.capture.domain.model.LiveBlurMethodType
import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

// Live Stage 1 check model selection. ONE flat setting per check,
// shared across StableStrategy and FastStrategy — rolling mechanism
// is the only thing that differs by strategy; model choice is
// independent of it, letting either be tested against either.
//
// Stage 2's dedicated checks (BlurSettings.MODEL, FingerSettings.METHOD)
// are separate, unchanged, and NOT affected by this group.
object LiveCheckSettings : PreferenceGroup {
    override val title: String get() = "Live Stage 1 Check Models"

    val LIVE_BLUR_MODEL = PreferenceParam(
        key = "live_check.blur.model",
        displayName = "Live Blur Model",
        type = PreferenceType.CHOICE(LiveBlurMethodType.entries),
        defaultValue = LiveBlurMethodType.Laplacian
    )

    val LIVE_FINGER_MODEL = PreferenceParam(
        key = "live_check.finger.model",
        displayName = "Live Finger Model",
        type = PreferenceType.CHOICE(FingerCheckMethodType.entries),
        defaultValue = FingerCheckMethodType.PythonHSVContourDetection
    )
}