package app.gov.uidai.capture.usecase.runner

import android.annotation.SuppressLint
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.config.BlurSettings
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.ProcessingResult
import app.gov.uidai.capture.pref.PreferenceStore
import app.gov.uidai.capture.usecase.CutoutRectUtils
import app.gov.uidai.capture.usecase.ImageProcessor
import app.gov.uidai.capture.usecase.ProcessingSettings
import app.gov.uidai.capture.utils.BlurGate
import app.gov.uidai.capture.utils.RollingConfidence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

class BlurCheckRunner(
    private val laplacianBlur: ImageProcessingMethod<Unit>,
    private val densenetBlur: ImageProcessingMethod<Unit>,
    private val provider: ImageProcessor.Provider,
    private val controller: ImageProcessor.Controller,
    private val preferenceStore: PreferenceStore,
    private val coroutineScope: CoroutineScope,
    private val onBlurResult: (frame: CameraFrame, confidence: Float, passed: Boolean) -> Unit
) {
    companion object {
        private const val TAG = "BlurCheckRunner"
    }

    private val confidence = RollingConfidence(windowSize = 5, requiredPassRate = 0.7f)

    private val laplacianGate = BlurGate(targetThreshold = 300f, fallbackThreshold = 250f, maxWaitMs = 3_000L)
    private val densenetGate: BlurGate by lazy {
        val threshold = preferenceStore.get(BlurSettings.THRESHOLD)
        BlurGate(targetThreshold = threshold, fallbackThreshold = threshold, maxWaitMs = 3_000L)
    }

    @Volatile var isPassed: Boolean = false
        private set
    @Volatile var lastConfidence: Float = 0f
        private set

    fun currentThreshold(): Float = laplacianGate.currentThreshold()
    fun isConfident(): Boolean = confidence.isConfident()

    private data class NamedResult(val methodName: String, val result: ProcessingResult<Unit>, val passed: Boolean)

    @SuppressLint("DefaultLocale")
    suspend fun run(frame: CameraFrame) {
        try {
            if (provider.previewSize.width == 0 || provider.previewSize.height == 0) return
            val cutoutRect = provider.getCutoutRectInImageCoordinates(
                Size(frame.width, frame.height), frame.rotationDegrees
            )
            if (!CutoutRectUtils.isValid(cutoutRect)) return
            val (croppedByteArray, croppedByteArraySize) = frame.getByteArray(
                requiresCropping = true, cutoutRect = cutoutRect
            )
            val imageDataProvider = ImageDataProvider(
                croppedByteArray, croppedByteArraySize.width, croppedByteArraySize.height, frame.rotationDegrees
            )

            val laplacianDeferred = coroutineScope.async {
                try {
                    val r = laplacianBlur.run(imageDataProvider)
                    NamedResult("Laplacian", r, r.confidence >= laplacianGate.currentThreshold())
                } catch (e: Exception) {
                    Log.e(TAG, "Laplacian check failed", e)
                    null
                }
            }
            val densenetDeferred = coroutineScope.async {
                try {
                    val r = densenetBlur.run(imageDataProvider)
                    NamedResult("DenseNet", r, r.confidence >= densenetGate.currentThreshold())
                } catch (e: Exception) {
                    Log.e(TAG, "DenseNet check failed", e)
                    null
                }
            }

            val first = select<NamedResult?> {
                laplacianDeferred.onAwait { it }
                densenetDeferred.onAwait { it }
            }

            val winner = if (first?.passed == true) {
                first
            } else {
                val second = if (laplacianDeferred.isCompleted) densenetDeferred.await() else laplacianDeferred.await()
                when {
                    second?.passed == true -> second
                    first != null -> first
                    else -> second
                }
            }

            if (winner == null) {
                Log.w(TAG, "Both Laplacian and DenseNet failed for this frame")
                imageDataProvider.clearCache()
                return
            }

            val (methodName, blurResult, passed) = winner
            val gateForWinner = if (methodName == "Laplacian") laplacianGate else densenetGate

            if (preferenceStore.get(ProcessingSettings.SAVE_BLUR_INPUT)) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "BlurInput($methodName,$confFormatted)")
            }
            if (preferenceStore.get(ProcessingSettings.SAVE_SHARP_IMAGES) &&
                blurResult.confidence >= gateForWinner.currentThreshold()
            ) {
                val confFormatted = String.format("%.2f", blurResult.confidence).removePrefix("0")
                controller.saveBitmap(imageDataProvider.getAsUprightBitmap(), "SharpImage($methodName,$confFormatted)")
            }
            imageDataProvider.clearCache()

            Log.d(TAG, "Blur result via $methodName: passed=$passed confidence=${blurResult.confidence}")

            lastConfidence = blurResult.confidence
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
        laplacianGate.reset()
        densenetGate.reset()
    }
}