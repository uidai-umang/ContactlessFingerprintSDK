package app.gov.uidai.capture.usecase.runner

import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.FingerResultData
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.usecase.CutoutRectUtils
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ProcessingSettings
import app.gov.uidai.capture.utils.RollingConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference

/**
 * Owns BOTH finger checks (HSV live-loop + independent Mediapipe rescue
 * loop) and the sticky-success reconciliation between them:
 * - Either check's PASS always applies immediately.
 * - Either check's FAIL only applies if the OTHER check isn't currently
 *   holding a pass -- neither side's failure can overwrite the other's
 *   success. Confirmed on-device across multiple hard-lighting conditions.
 * run() and runMediapipe() are called from two SEPARATE loops in
 * ImageProcessor (different cadences) -- this class does not own its
 * own coroutine loop, it's ticked externally.
 */
class FingerCheckRunner(
    private val liveFinger: ImageProcessingMethod<FingerResultData>,
    private val mediapipeFinger: ImageProcessingMethod<FingerResultData>,
    private val provider: ImageProcessor.Provider,
    private val controller: ImageProcessor.Controller,
    private val listener: ImageProcessor.Listener,
    private val preferenceStore: PreferenceStore,
    private val coroutineScope: CoroutineScope
) {
    companion object {
        private const val TAG_HSV = "FINGER -- HSV"
        private const val TAG_MP = "FINGER -- Mediapipe"
    }

    private val confidence = RollingConfidence(windowSize = 4, requiredPassRate = 0.6f)
    private val hsvResult = AtomicReference<ProcessingResult<FingerResultData>?>(null)
    private val mediapipeResult = AtomicReference<ProcessingResult<FingerResultData>?>(null)
    private val combinedResult = AtomicReference<ProcessingResult<FingerResultData>?>(null)

    val result: ProcessingResult<FingerResultData>? get() = combinedResult.get()
    val passed: Boolean get() = combinedResult.get()?.passed ?: false
    fun isConfident(): Boolean = confidence.isConfident()

    fun runHsv(frame: CameraFrame, isCaptured: Boolean) {
        if (isCaptured) return
        try {
            val cutoutRect = provider.getCutoutRectInImageCoordinates(Size(frame.width, frame.height), frame.rotationDegrees)
            if (!CutoutRectUtils.isValid(cutoutRect)) {
                Log.w(TAG_HSV, "INVALID cutoutRect -- skipping this frame entirely")
                return
            }
            val (byteArray, byteArraySize) = frame.getByteArray(requiresCropping = true, cutoutRect = cutoutRect)
            val imageDataProvider = ImageDataProvider(byteArray, byteArraySize.width, byteArraySize.height, frame.rotationDegrees)
            if (preferenceStore.get(ProcessingSettings.SAVE_FINGER_CHECK_INPUT)) {
                coroutineScope.launch { controller.saveBitmap(imageDataProvider.getAsBitmap(), "FingerCheckInput") }
            }

            val callStart = android.os.SystemClock.uptimeMillis()
            val result = liveFinger.run(imageDataProvider)
            val callDuration = android.os.SystemClock.uptimeMillis() - callStart
            Log.d(TAG_HSV, "CALL_DURATION=${callDuration}ms")

            imageDataProvider.clearCache()
            hsvResult.set(result)
            if (result.passed) {
                apply(result, cutoutRect, frame, isCaptured)
                confidence.record(true)
                Log.d(TAG_HSV, "passed=true confidence=${result.confidence} RAW_DATA=${(result as? ProcessingResult.Passed)?.data}")
            } else {
                val mediapipeCurrentlyPassed = mediapipeResult.get()?.passed == true
                // Log the failure detail regardless of whether it gets applied,
                // so we can see WHY it's rejecting even when suppressed.
                val failedData = (result as? ProcessingResult.Failed)?.data
                Log.d(TAG_HSV, "passed=false confidence=${result.confidence} FAIL_DATA=$failedData")
                if (!mediapipeCurrentlyPassed) {
                    apply(result, cutoutRect, frame, isCaptured)
                    confidence.record(false)
                } else {
                    Log.d(TAG_HSV, "failed but Mediapipe holds success -- deferring, not overwriting")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_HSV, "Error in HSV finger check", e)
        }
    }

    fun runMediapipe(frame: CameraFrame, isCaptured: Boolean) {
        if (isCaptured) return
        try {
            val cutoutRect = provider.getCutoutRectInImageCoordinates(Size(frame.width, frame.height), frame.rotationDegrees)
            if (!CutoutRectUtils.isValid(cutoutRect)) {
                Log.w(TAG_MP, "INVALID cutoutRect -- skipping this frame entirely")
                return
            }
            val (byteArray, byteArraySize) = frame.getByteArray(requiresCropping = false, cutoutRect = cutoutRect)
            val imageDataProvider = ImageDataProvider(byteArray, byteArraySize.width, byteArraySize.height, frame.rotationDegrees)

            val callStart = android.os.SystemClock.uptimeMillis()
            val result = mediapipeFinger.run(imageDataProvider)
            val callDuration = android.os.SystemClock.uptimeMillis() - callStart
            Log.d(TAG_MP, "CALL_DURATION=${callDuration}ms")

            imageDataProvider.clearCache()
            mediapipeResult.set(result)
            if (result.passed) {
                apply(result, cutoutRect, frame, isCaptured)
                confidence.record(true)
                Log.d(TAG_MP, "passed=true confidence=${result.confidence} RAW_DATA=${(result as? ProcessingResult.Passed)?.data}")
            } else {
                val hsvCurrentlyPassed = hsvResult.get()?.passed == true
                val hsvCurrentlyFailed = hsvResult.get()?.passed == false
                val failedData = (result as? ProcessingResult.Failed)?.data
                Log.d(TAG_MP, "passed=false confidence=${result.confidence} FAIL_DATA=$failedData")
                if (!hsvCurrentlyPassed && hsvCurrentlyFailed) {
                    apply(result, cutoutRect, frame, isCaptured)
                    confidence.record(false)
                    Log.d(TAG_MP, "failed, HSV agrees -- shown")
                } else {
                    Log.d(TAG_MP, "failed but not confirmed by HSV -- ignored")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_MP, "Error in Mediapipe finger check", e)
        }
    }

    private fun apply(result: ProcessingResult<FingerResultData>, cutoutRect: android.graphics.RectF, frame: CameraFrame, isCaptured: Boolean) {
        when (result) {
            is ProcessingResult.Passed -> {
                listener.onFingerMaskResult(result.data.mask, frame.rotationDegrees)
                if (!isCaptured) controller.triggerFocusLock(result.data.box, cutoutRect, Size(frame.width, frame.height))
            }
            is ProcessingResult.Failed -> {
                result.data?.let { data ->
                    listener.onFingerMaskResult(null, frame.rotationDegrees)
                    if (!isCaptured) controller.triggerFocusLock(data.box, cutoutRect, Size(frame.width, frame.height))
                }
            }
        }
        combinedResult.set(result)
    }

    fun reset() {
        hsvResult.set(null)
        mediapipeResult.set(null)
        combinedResult.set(null)
    }
}