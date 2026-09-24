package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider

class ManualFocusAtFixedDistance(
    provider: CameraContextProvider,
    private val manualDistanceM: Float
) : FocusManager(provider) {

    private val focusDistanceDiopters: Float
        get() = 1f / manualDistanceM.coerceIn(0.05f, 1f)

    override fun lock(paramProvider: FocusLockParamProvider) {
        applyFixedFocus()
    }

    override fun unlock() {
        // Pass
    }

    override fun setOptimalMode() {
        provider.captureRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistanceDiopters)
        }
    }

    private fun applyFixedFocus() {
        setOptimalMode()
        provider.captureSession.setRepeatingRequest(
            provider.captureRequestBuilder.build(),
            provider.captureCallback,
            provider.cameraPreviewHandler
        )
    }
}