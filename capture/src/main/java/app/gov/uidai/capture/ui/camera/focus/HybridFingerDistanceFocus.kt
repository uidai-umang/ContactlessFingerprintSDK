package app.gov.uidai.capture.ui.camera.focus
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider
import java.util.concurrent.atomic.AtomicLong

class HybridFingerDistanceFocus(
    provider: CameraContextProvider
) : FocusManager(provider) {
    companion object {
        private val TAG = HybridFingerDistanceFocus::class.simpleName
        const val MANUAL_UPDATE_INTERVAL = 500L
        const val HARDWARE_CORRECTION_INTERVAL = 3000L  // real AF trigger every 3s
        private const val SMOOTHING_ALPHA = 0.3f
        private const val AF_SCAN_TIMEOUT_MS = 2500L
        private const val simulatedMinFocusDiopters = Float.MAX_VALUE
    }
    private var smoothedDistance: Float? = null
    private val lastManualUpdateTime = AtomicLong(0L)
    private val lastHardwareCorrectionTime = AtomicLong(0L)
    override fun lock(paramProvider: FocusLockParamProvider) {
        val now = SystemClock.uptimeMillis()
        if (getFocusState() == FocusState.TRIGGERING) {
            if (now - lastHardwareCorrectionTime.get() < AF_SCAN_TIMEOUT_MS) {
                return  // scan still in flight -- do not touch the lens
            }
            // Scan never resolved within the timeout -- give up on it
            // rather than block the manual path forever.
            updateFocusState(FocusState.UNLOCKED)
        }
        // Periodic real hardware correction — catches cases where the
        // manual estimate has drifted from reality (bad lighting, unusual
        // angle) without paying the full AF search cost every frame.
        if (now - lastHardwareCorrectionTime.get() >= HARDWARE_CORRECTION_INTERVAL) {
            lastHardwareCorrectionTime.set(now)
            triggerRealHardwareAF(paramProvider)
            return  // let the hardware trigger complete before resuming manual updates
        }
        // Primary path — cheap, fast manual estimate, smoothed
        if (now - lastManualUpdateTime.get() < MANUAL_UPDATE_INTERVAL) return
        lastManualUpdateTime.set(now)
        val raw = paramProvider.getFingerDistance().coerceIn(0.05f, 1f)
        val smoothed = smoothedDistance?.let { it + SMOOTHING_ALPHA * (raw - it) } ?: raw
        smoothedDistance = smoothed
        provider.captureRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, (1f / smoothed).coerceAtMost(simulatedMinFocusDiopters))
        }
        provider.captureSession.setRepeatingRequest(
            provider.captureRequestBuilder.build(),
            provider.captureCallback,
            provider.cameraPreviewHandler
        )
    }
    private fun triggerRealHardwareAF(paramProvider: FocusLockParamProvider) {
        // Same region-targeted trigger as FocusTriggerOnFinger — reuses
        // the existing, already-working confirmed-AF mechanism, just
        // called periodically instead of on every lock().
        updateFocusState(FocusState.TRIGGERING)
        provider.captureRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
            paramProvider.getMeteringRectangle()
                ?.let { set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it)) }
            set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        }
        provider.captureSession.capture(
            provider.captureRequestBuilder.build(),
            provider.captureCallback,
            provider.cameraPreviewHandler
        )
    }
    override fun unlock() {
        smoothedDistance = null; lastManualUpdateTime.set(0L); lastHardwareCorrectionTime.set(0L)
    }
    override fun setOptimalMode() {
        val requestBuilder = provider.captureRequestBuilder
        val characteristics = provider.characteristics
        val minFocusDistance =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val effectiveDiopters = minFocusDistance.coerceAtMost(simulatedMinFocusDiopters)
        requestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, effectiveDiopters)
        }
    }
}
