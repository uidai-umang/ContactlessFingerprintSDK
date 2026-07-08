package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.util.Log
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider

class ContinuousAF(
    provider: CameraContextProvider
): FocusManager(provider) {
    companion object {
        private val TAG = ContinuousAF::class.simpleName
    }

    override fun lock(paramProvider: FocusLockParamProvider) {
        // Pass
    }

    override fun unlock() {
        // Pass
    }

    override fun setOptimalMode() {
        val characteristics = provider.characteristics
        val requestBuilder = provider.captureRequestBuilder

        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)
        if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) == true) {
            Log.d(TAG, "FOCUS -- Focus Mode Set To: CONTINUOUS PICTURE")
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
        } else {
            Log.d(TAG, "FOCUS -- No Continuous AF available, Focus Mode Set To: OFF")
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        }
    }
}