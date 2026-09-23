package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CaptureRequest
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider

/**
 * Slap-only focus strategy: locks the lens to a fixed distance in mm,
 * given directly at construction time -- NEVER derived from the device's
 * own LENS_INFO_MINIMUM_FOCUS_DISTANCE. That value is hardware-specific
 * (varies with lens/macro capability per device), so using it as a stand-
 * in for "the correct slap distance" gives a different real-world focus
 * point on every phone -- which is exactly what field logs showed
 * (CameraController.setOptimalMode-driven resets landing at whatever the
 * device's own minimum happens to be, not a consistent 12cm).
 *
 * Deliberately its OWN class, not a change to ManualFocusAtFixedDistance,
 * ManualFocusAtFingerDistance or HybridFingerDistanceFocus -- all three
 * are shared with single-finger capture via the same FOCUS_TYPE
 * preference and FocusFactory, and none of them are touched here.
 * CameraController.useSlapFixedFocus() opts a Slap CameraController
 * instance into this strategy directly, bypassing FocusFactory/
 * FOCUS_TYPE entirely, so single-finger capture's focus behavior is
 * completely unaffected by anything in this file.
 */
class SlapFixedDistanceFocus(
    provider: CameraContextProvider,
    private val fixedDistanceMM: Float
) : FocusManager(provider) {

    private val fixedDistanceDiopters: Float
        get() = 1000f / fixedDistanceMM.coerceAtLeast(1f)

    override fun lock(paramProvider: FocusLockParamProvider) {
        // Ignores paramProvider entirely -- unlike ManualFocusAtFingerDistance
        // or HybridFingerDistanceFocus, this strategy never re-derives its
        // target from the live detected box, so it can't drift the way
        // those did. Every call just reasserts the same fixed value --
        // cheap, and matches the existing re-assert-on-every-tick pattern
        // the other manual strategies already use.
        applyFixedFocus()
    }

    override fun unlock() {
        // Pass -- nothing to release, the lens just stays at the fixed
        // distance.
    }

    override fun setOptimalMode() {
        // This is the fix for the bug found in the other manual
        // strategies: their setOptimalMode() hardcodes the device's
        // LENS_INFO_MINIMUM_FOCUS_DISTANCE instead of their own
        // configured/computed distance, so every time CameraController
        // re-applies "optimal mode" (session start, and after any
        // hardware AF lock) the lens gets silently reset to a
        // device-specific value instead of staying at the intended
        // target. Here "optimal mode" always means the same fixed
        // distance this class was built with -- nothing device-specific
        // ever gets substituted in.
        provider.captureRequestBuilder.apply {
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            set(CaptureRequest.LENS_FOCUS_DISTANCE, fixedDistanceDiopters)
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