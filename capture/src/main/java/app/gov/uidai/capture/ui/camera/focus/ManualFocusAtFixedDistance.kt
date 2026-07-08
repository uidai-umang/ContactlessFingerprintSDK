package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider

class ManualFocusAtFixedDistance(
    provider: CameraContextProvider
) : FocusManager(provider) {

    override fun lock(paramProvider: FocusLockParamProvider) {
        val requestBuilder = provider.captureRequestBuilder
        val session = provider.captureSession
        val captureCallback = provider.captureCallback
        val cameraPreviewHandler = provider.cameraPreviewHandler

        val focusDistance = 1f / paramProvider.getManualDistance().coerceIn(0.05f, 1f)

        requestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, focusDistance)
        }

        session.setRepeatingRequest(
            requestBuilder.build(),
            captureCallback,
            cameraPreviewHandler
        )
    }

    override fun unlock() {
        // Pass
    }

    override fun setOptimalMode() {
        val requestBuilder = provider.captureRequestBuilder
        val characteristics = provider.characteristics

        val minFocusDistance =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE)

        requestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, minFocusDistance)
        }
    }
}