package app.gov.uidai.capture.ui.camera.focus

import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import app.gov.uidai.capture.ui.camera.provider.CameraContextProvider
import app.gov.uidai.capture.ui.camera.provider.FocusLockParamProvider
import java.util.concurrent.atomic.AtomicLong

class FocusTriggerOnFinger(
    provider: CameraContextProvider
): FocusManager(provider) {

    companion object {
        private val TAG = FocusTriggerOnFinger::class.simpleName
        const val FOCUS_TRIGGER_INTERVAL = 5000L
    }

    private val lastFocusTriggerTimeAtomic = AtomicLong(SystemClock.uptimeMillis() - FOCUS_TRIGGER_INTERVAL)

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

        if (!getNeedFocusTrigger() && getFocusState() == FocusState.LOCKED) {
            Log.w(TAG, "FOCUS -- Focus does not need focus trigger and is Locked, Skipping...")
            return
        }

        try {
            updateFocusState(FocusState.TRIGGERING)
            lastFocusTriggerTimeAtomic.set(SystemClock.uptimeMillis())

            // Cancel any existing AF trigger and reset AF mode
            requestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            session.capture(requestBuilder.build(), null, cameraPreviewHandler)

            // Set the focus region
            requestBuilder.apply {
                // Set the focus area
                paramProvider.getMeteringRectangle()?.let {
                    set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(it))
                    // set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(it))
                }
                // Start the trigger
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
            }

            Log.i(TAG, "FOCUS -- Focus triggered")

            session.capture(
                requestBuilder.build(),
                captureCallback,
                cameraPreviewHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "FOCUS -- lockFocus failed", e)
        }
    }

    override fun unlock() {
        val requestBuilder = provider.captureRequestBuilder
        val session = provider.captureSession
        val captureCallback = provider.captureCallback
        val cameraPreviewHandler = provider.cameraPreviewHandler

        if (getFocusState() == FocusState.UNLOCKED) {
            Log.w(TAG, "FOCUS -- Focus already unlocked, Skipping...")
            return
        }

        try {
            updateFocusState(FocusState.UNLOCKED)

            requestBuilder.apply {
                setOptimalMode()
                set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
            }

            session.setRepeatingRequest(
                requestBuilder.build(),
                captureCallback,
                cameraPreviewHandler
            )

        } catch (e: CameraAccessException) {
            Log.e(TAG, "FOCUS -- unlockFocus failed", e)
        }
    }

    override fun setOptimalMode() {
        val characteristics = provider.characteristics
        val requestBuilder = provider.captureRequestBuilder

        val afModes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)

        if (afModes?.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) == true) {
            Log.d(TAG, "FOCUS -- Focus Mode Set To: AUTO FOCUS")
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        } else {
            Log.d(TAG, "FOCUS -- No AF available, Focus Mode Set To: OFF")
            requestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        }
    }
}