package app.gov.uidai.capture.usecase.slap

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.ImageDataProvider
import app.gov.uidai.capture.domain.model.ImageProcessingMethod
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.inflatedByPercent
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import app.gov.uidai.capture.utils.extension.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SlapLiveState(
    val areaRatio: Float = 0f,
    // Already rotated to upright-image space (see rotateACW below) -- the
    // Compose layer only has to do one simple proportional scale to the
    // displayed preview size, not a rotation-aware transform.
    val fingertips: List<PointF> = emptyList(),
    val uprightFrameWidth: Int = 0,
    val uprightFrameHeight: Int = 0,
    val isReady: Boolean = false,
    val statusMessage: String = "Move hand closer"
)

/**
 * Standalone ImageReader.OnImageAvailableListener for slap capture --
 * deliberately NOT an ImageProcessor subclass and NOT using the
 * FingerCheckRunner/BlurCheckRunner dual fast/slow runner pattern. One
 * signal per throttled frame (SlapFrameAnalyzer), one debounce counter,
 * one blur check, done.
 */
class SlapCaptureListener(
    private val expectedHandType: String,
    private val analyzer: SlapFrameAnalyzer,
    private val blurCheck: ImageProcessingMethod<Unit>,
    private val coroutineScope: CoroutineScope,
    private val getRotationDegrees: () -> Int,
    private val triggerFocus: (handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Unit
) : ImageReader.OnImageAvailableListener {

    companion object {
        private val TAG = SlapCaptureListener::class.simpleName
        private const val THROTTLE_MS = 100L // ~10fps -- Python bridge call cost, not every camera frame needs MediaPipe
        private const val AREA_RATIO_THRESHOLD = 0.65f
        private const val REQUIRED_CONSECUTIVE_PASSES = 2 // simple debounce, not a longer accumulation buffer
        private const val EXPECTED_FINGERTIP_COUNT = 5
        private const val CROP_PADDING_PERCENT = 0.08f
    }

    private val processingCounter = AtomicLong(0)
    private val lastProcessedAt = AtomicLong(0L)
    private val isProcessing = AtomicBoolean(false)
    private val isCaptured = AtomicBoolean(false)

    @Volatile
    private var consecutivePasses = 0

    private val _liveState = MutableStateFlow(SlapLiveState())
    val liveState = _liveState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap = _capturedBitmap.asStateFlow()

    override fun onImageAvailable(reader: ImageReader) {
        Log.d(TAG, "onImageAvailable ENTRY")
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire image", e)
            null
        } ?: return

        if (isCaptured.get()) {
            image.close()
            return
        }

        val now = SystemClock.uptimeMillis()
        if (now - lastProcessedAt.get() < THROTTLE_MS || !isProcessing.compareAndSet(false, true)) {
            Log.d(TAG, "onImageAvailable -- gated (throttle or isProcessing busy)")
            image.close()
            return
        }
        lastProcessedAt.set(now)

        val rotationDegrees = getRotationDegrees()
        val frame = try {
            image.use {
                CameraFrame(
                    processingId = processingCounter.incrementAndGet(),
                    byteArray = it.toByteArray(),
                    width = it.width,
                    height = it.height,
                    timestamp = System.currentTimeMillis(),
                    rotationDegrees = rotationDegrees,
                    yRowStride = it.planes[0].rowStride
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to build CameraFrame", e)
            isProcessing.set(false)
            return
        }

        Log.d(TAG, "onImageAvailable -- launching processFrame")
        coroutineScope.launch {
            try {
                processFrame(frame, rotationDegrees)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun processFrame(frame: CameraFrame, rotationDegrees: Int) {
        Log.d(TAG, "processFrame ENTRY")
        // analyzer.analyze() rotates the frame upright before detecting, so
        // result.fingertips/box are ALREADY in upright bitmap coordinate
        // space (bitmap.width x bitmap.height post-rotation) -- no further
        // point rotation needed here, just matching dimensions for the
        // Compose layer's scale-to-preview-size math.
        val result = analyzer.analyze(frame, expectedHandType)

        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frame.height else frame.width
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frame.width else frame.height
        val uprightFingertips = result.fingertips

        val fingertipsOk = result.handDetected && result.fingertips.size == EXPECTED_FINGERTIP_COUNT
        val areaOk = result.areaRatio >= AREA_RATIO_THRESHOLD
        val framePassed = fingertipsOk && areaOk

        consecutivePasses = if (framePassed) consecutivePasses + 1 else 0

        val statusMessage = when {
            !areaOk -> "Move hand closer"
            !fingertipsOk -> "Hold steady"
            consecutivePasses < REQUIRED_CONSECUTIVE_PASSES -> "Hold steady"
            else -> "Capturing automatically..."
        }

        result.box?.let { box ->
            triggerFocus(box, Size(uprightWidth, uprightHeight), rotationDegrees)
        }

        _liveState.value = SlapLiveState(
            areaRatio = result.areaRatio,
            fingertips = uprightFingertips,
            uprightFrameWidth = uprightWidth,
            uprightFrameHeight = uprightHeight,
            isReady = framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES,
            statusMessage = statusMessage
        )

        if (framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES &&
            isCaptured.compareAndSet(false, true)
        ) {
            attemptCapture(frame, result)
        }
    }

    private suspend fun attemptCapture(frame: CameraFrame, result: SlapFrameResult) {
        try {
            val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
            val provider = ImageDataProvider(byteArray, size.width, size.height, frame.rotationDegrees)
            val blurResult = blurCheck.run(provider)
            provider.clearCache()

            if (!blurResult.passed) {
                Log.w(TAG, "Slap capture blur check failed (confidence=${blurResult.confidence}) -- resetting, keep looping")
                consecutivePasses = 0
                isCaptured.set(false)
                return
            }

            // result.box is in UPRIGHT bitmap space (see SlapFrameAnalyzer),
            // so crop from an upright bitmap directly rather than the raw
            // NV21 bytes -- those are still in sensor-native (pre-rotation)
            // space and would crop the wrong region.
            val uprightBitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)
            val box = result.box ?: RectF(0f, 0f, uprightBitmap.width.toFloat(), uprightBitmap.height.toFloat())
            val paddedBox = box.inflatedByPercent(CROP_PADDING_PERCENT)
            val croppedBitmap = uprightBitmap.crop(paddedBox)

            _capturedBitmap.value = croppedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error finishing slap capture", e)
            consecutivePasses = 0
            isCaptured.set(false)
        }
    }

    fun reset() {
        consecutivePasses = 0
        isCaptured.set(false)
        _capturedBitmap.value = null
        _liveState.value = SlapLiveState()
    }
}
