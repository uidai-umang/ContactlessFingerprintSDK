package app.gov.uidai.capture.ui.camera.config

import app.gov.uidai.capture.pref.model.PreferenceGroup
import app.gov.uidai.capture.pref.model.PreferenceParam
import app.gov.uidai.capture.pref.model.PreferenceType

object SessionSettings : PreferenceGroup {
    override val title: String
        get() = "# Session Timer"

    val TIMEOUT = PreferenceParam(
        "camera.session_timeout",
        "Session Timeout (s)",
        PreferenceType.INT,
        30
    )
}