package app.gov.uidai.capture.ui.camera.focus

import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.ui.camera.config.CameraSettings
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FocusFactory @Inject constructor(
    private val preferenceStore: PreferenceStore
) {
    fun create(
        provider: CameraContextProvider
    ): FocusManager {
        val focusType = preferenceStore.get(CameraSettings.FOCUS_TYPE)
        return when (focusType) {
            FocusType.ContinuousAF -> ContinuousAF(provider)
            FocusType.FocusTriggerOnFinger -> FocusTriggerOnFinger(provider)
            FocusType.ManualFocusAtFixedDistance -> ManualFocusAtFixedDistance(provider)
            FocusType.ManualFocusAtFingerDistance -> ManualFocusAtFingerDistance(provider)
            FocusType.MacroFocusTriggerOnFinger -> MacroFocusTriggerOnFinger(provider)
        }
    }
}