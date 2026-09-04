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
    val frameId: Long = 0L,
    val handDetected: Boolean = false,
    val areaRatio: Float = 0f,
    val fingertips: List<PointF> = emptyList(),
    val uprightFrameWidth: Int = 0,
    val uprightFrameHeight: Int = 0,
    val isReady: Boolean = false,
    val statusMessage: String = "Move hand closer"
)

class SlapCaptureListener(
    private val expectedHandType: String,
    private val analyzer: SlapFrameAnalyzer,
    private val blurChecker: SlapBlurChecker,
    private val coroutineScope: CoroutineScope,
    private val getRotationDegrees: () -> Int,
    private val triggerFocus: (handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Unit
) : ImageReader.OnImageAvailableListener {

    companion object {
        private val TAG = SlapCaptureListener::class.simpleName
        private const val THROTTLE_MS = 100L
        private const val AREA_RATIO_THRESHOLD = 0.30f
        private const val REQUIRED_CONSECUTIVE_PASSES = 2
        private const val CROP_PADDING_PERCENT = 0.08f
    }

    private val processingCounter = AtomicLong(0)
    private val lastProcessedAt = AtomicLong(0L)
    private val isProcessing = AtomicBoolean(false)
    private val isCaptured = AtomicBoolean(false)

    @Volatile
    private var consecutivePasses = 0

    @Volatile
    private var lastAttemptBlurFailed = false

    private val _liveState = MutableStateFlow(SlapLiveState())
    val liveState = _liveState.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<Bitmap?>(null)
    val capturedBitmap = _capturedBitmap.asStateFlow()

    override fun onImageAvailable(reader: ImageReader) {
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

        coroutineScope.launch {
            try {
                processFrame(frame, rotationDegrees)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "processFrame failed unexpectedly", e)
            } finally {
                isProcessing.set(false)
            }
        }
    }

    private suspend fun processFrame(frame: CameraFrame, rotationDegrees: Int) {
        val result = analyzer.analyze(frame, expectedHandType)

        val uprightWidth = if (rotationDegrees == 90 || rotationDegrees == 270) frame.height else frame.width
        val uprightHeight = if (rotationDegrees == 90 || rotationDegrees == 270) frame.width else frame.height
        val uprightFingertips = result.fingertips

        val areaOk = result.areaRatio >= AREA_RATIO_THRESHOLD
        val framePassed = result.handDetected && areaOk

        if (!result.handDetected || !areaOk) {
            lastAttemptBlurFailed = false
        }

        consecutivePasses = if (framePassed) consecutivePasses + 1 else 0

        val statusMessage = when {
            !result.handDetected -> "No hand detected"
            !areaOk -> "Move hand closer"
            lastAttemptBlurFailed -> "Too blurry — hold steady"
            consecutivePasses < REQUIRED_CONSECUTIVE_PASSES -> "Hold steady"
            else -> "Capturing automatically..."
        }

        result.box?.let { box ->
            try {
                triggerFocus(box, Size(uprightWidth, uprightHeight), rotationDegrees)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "triggerFocus failed -- continuing without it", e)
            }
        }

        _liveState.value = SlapLiveState(
            frameId = frame.processingId,
            handDetected = result.handDetected,
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

            val uprightBitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

            val blurResult = blurChecker.check(provider, uprightBitmap)
            provider.clearCache()

            if (!blurResult.passed) {
                Log.w(
                    TAG,
                    "Slap capture blur check failed (laplacian=${blurResult.laplacianVariance}, " +
                            "densenet=${blurResult.densenetConfidence}) -- resetting, keep looping"
                )
                lastAttemptBlurFailed = true
                consecutivePasses = 0
                isCaptured.set(false)
                return
            }

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
        lastAttemptBlurFailed = false
        isCaptured.set(false)
        _capturedBitmap.value = null
        _liveState.value = SlapLiveState()
    }
}