package app.gov.uidai.capture.usecase.runner

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.usecase.CutoutRectUtils
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ProcessingSettings
import app.gov.uidai.capture.utils.BlurGate
import app.gov.uidai.capture.utils.RollingConfidence
import kotlinx.coroutines.runInterruptible

/**
 * Owns blur's live-loop guard, BlurGate threshold degradation, and rolling
 * confidence. onBlurResult lets ImageProcessor do the best-frame tracking
 * cross-check (needs finger's state too, which this class deliberately
 * doesn't know about -- kept in ImageProcessor as genuinely cross-cutting).
 */
class BlurCheckRunner(
    private val liveBlur: ImageProcessingMethod<Unit>,
    private val provider: ImageProcessor.Provider,
    private val controller: ImageProcessor.Controller,
    private val preferenceStore: PreferenceStore,
    private val onBlurResult: (frame: CameraFrame, confidence: Float, passed: Boolean) -> Unit
) {
    companion object {
        private const val TAG = "BlurCheckRunner"
    }

    private val confidence = RollingConfidence(windowSize = 5, requiredPassRate = 0.7f)
    private val blurGate = BlurGate(targetThreshold = 350f, fallbackThreshold = 300f, maxWaitMs = 3_000L)

    @Volatile var isPassed: Boolean = false
        private set
    @Volatile var lastConfidence: Float = 0f
        private set

    fun currentThreshold(): Float = blurGate.currentThreshold()
    fun isConfident(): Boolean = confidence.isConfident()

    @SuppressLint("DefaultLocale")
    suspend fun run(frame: CameraFrame) {
        try {
            // Guard against the startup race: skip frames until the overlay's
            // real screen position and preview's real measured size are both
            // known. Before that, getCutoutRectInImageCoordinates() divides
            // against zero-valued placeholders and produces NaN/Infinity.
            if (provider.previewSize.width == 0 || provider.previewSize.height == 0) return
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height), frame.rotationDegrees
            )
            if (!CutoutRectUtils.isValid(cutoutRect)) return

            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true, cutoutRect = cutoutRect
            )
            Log.d(TAG, "BLUR_CRASH_CHECK -- cutoutRect=$cutoutRect frameSize=${frame.width}x${frame.height} croppedSize=${croppedByteArraySize.width}x${croppedByteArraySize.height} arrayLen=${croppedByteArray.size}")

            val imageDataProvider = ImageDataProvider(
                croppedByteArray, croppedByteArraySize.width, croppedByteArraySize.height, frame.rotationDegrees
            )
            val blurResult = runInterruptible { liveBlur.run(imageDataProvider) }

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "BlurInput($confFormatted)")
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_SHARP_IMAGES) &&
                blurResult.confidence >= preferenceStore.get(BlurSettings.THRESHOLD)
            ) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "SharpImage($confFormatted)")
            }
            imageDataProvider.clearCache()

            lastConfidence = blurResult.confidence
            // BlurGate only supplies the threshold -- degrading from target to
            // fallback after maxWaitMs. Pass/fail check and rolling confidence
            // are the same mechanism as before, just checked against a
            // threshold that can relax over time instead of a fixed constant.
            val passed = blurResult.confidence >= blurGate.currentThreshold()
            isPassed = passed
            confidence.record(passed)

            onBlurResult(frame, blurResult.confidence, passed)
        } catch (e: Exception) {
            Log.e(TAG, "Error in Blur processing", e)
        }
    }

    fun reset() {
        isPassed = false
        lastConfidence = 0f
        blurGate.reset()
    }
}