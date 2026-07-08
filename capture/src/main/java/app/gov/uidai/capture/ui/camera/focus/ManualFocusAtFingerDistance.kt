package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider
import java.util.concurrent.atomic.AtomicLong

class ManualFocusAtFingerDistance(
    provider: CameraContextProvider
) : FocusManager(provider) {
    companion object {
        private val TAG = ContinuousAF::class.simpleName
        const val FOCUS_TRIGGER_INTERVAL = 1000L
    }

    private val lastFocusTriggerTimeAtomic = AtomicLong(SystemClock.uptimeMillis())

    override fun lock(paramProvider: FocusLockParamProvider) {
        val requestBuilder = provider.captureRequestBuilder
        val session = provider.captureSession
        val captureCallback = provider.captureCallback
        val cameraPreviewHandler = provider.cameraPreviewHandler

        if (
            getFocusState() == FocusState.TRIGGERING &&
            ((SystemClock.uptimeMillis() - lastFocusTriggerTimeAtomic.get()) < FOCUS_TRIGGER_INTERVAL)
        ) {
            Log.w(TAG, "FOCUS -- Focus triggered within ${FOCUS_TRIGGER_INTERVAL}ms, Skipping...")
            return
        }

        lastFocusTriggerTimeAtomic.set(SystemClock.uptimeMillis())

        val focusDistance = 1f / paramProvider.getFingerDistance().coerceIn(0.05f, 1f)

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