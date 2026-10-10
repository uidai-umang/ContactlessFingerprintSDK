package app.gov.uidai.capture.usecase.slap

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.media.ImageReader
import android.os.SystemClock
import android.util.Log
import android.util.Size
import app.gov.uidai.capture.domain.model.CameraFrame
import app.gov.uidai.capture.domain.model.SlapFrameResult
import app.gov.uidai.capture.slap.processing.SlapFingerBandDetector
import app.gov.uidai.capture.utils.extension.crop
import app.gov.uidai.capture.utils.extension.inflatedByPercent
import app.gov.uidai.capture.utils.extension.rotate
import app.gov.uidai.capture.utils.extension.toBitmap
import app.gov.uidai.capture.utils.extension.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SlapLiveState(
    val frameId: Long = 0L,
    val handDetected: Boolean = false,
    val areaRatio: Float = 0f,
    val fingertips: List<PointF> = emptyList(),
    val fingerBoxes: List<RectF> = emptyList(),
    val uprightFrameWidth: Int = 0,
    val uprightFrameHeight: Int = 0,
    val isReady: Boolean = false,
    val statusMessage: String = "Place your hand in frame",
    val handDistanceMM: Float? = null
)

class SlapCaptureListener(
    private val expectedHandType: String,
    private val analyzer: SlapFrameAnalyzer,
    private val blurChecker: SlapBlurChecker,
    private val coroutineScope: CoroutineScope,
    private val getRotationDegrees: () -> Int,
    private val triggerFocus: (handBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Unit,
    private val getFingerDistanceMM: (fingerBoxUpright: RectF, uprightImageSize: Size, rotationDegrees: Int) -> Float,
    private val targetHandDistanceMM: Float,
    private val handDistanceToleranceMM: Float
) : ImageReader.OnImageAvailableListener {

    companion object {
        private val TAG = SlapCaptureListener::class.simpleName
        private const val THROTTLE_MS = 100L
        private const val REQUIRED_CONSECUTIVE_PASSES = 4000
        private const val CROP_PADDING_PERCENT = 0.08f
        private const val GATE_ON_DISTANCE = false
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

        val rotationDegrees = getRotationDegrees()
        val now = SystemClock.uptimeMillis()

        val eligible = now - lastProcessedAt.get() >= THROTTLE_MS && isProcessing.compareAndSet(false, true)

        if (!eligible) {
            image.close()
            return
        }

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

        lastProcessedAt.set(now)
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

        val detectedCount = result.fingerBoxes.size

        val frameSize = Size(uprightWidth, uprightHeight)

        val perFingerMM = result.fingerBoxes.map { box ->
            try {
                getFingerDistanceMM(box, frameSize, rotationDegrees)
            } catch (e: Exception) {
                Log.e(TAG, "getFingerDistanceMM failed for one finger box -- skipping it", e)
                0f
            }
        }

        val handDistanceMM = perFingerMM
            .filter { it > 0f }
            .sorted()
            .let { distances -> distances.getOrNull(distances.size / 2) }

        Log.d(
            TAG,
            "SLAP HAND DISTANCE -- median=${handDistanceMM?.toInt()}mm " +
                    "target=${targetHandDistanceMM.toInt()}±${handDistanceToleranceMM.toInt()}mm " +
                    "fingers=$detectedCount frame=${uprightWidth}x${uprightHeight} " +
                    "perFinger=" + result.fingerBoxes.indices.joinToString { i ->
                "[w=${result.fingerBoxes[i].height().toInt()}px d=${perFingerMM[i].toInt()}mm]"
            }
        )

        val distanceGuidance = handDistanceMM?.let { distanceMM ->
            when {
                distanceMM > targetHandDistanceMM + handDistanceToleranceMM -> "Move hand closer to the camera"
                distanceMM < targetHandDistanceMM - handDistanceToleranceMM -> "Move hand farther from the camera"
                else -> null
            }
        }

        val distanceOk = !GATE_ON_DISTANCE || (handDistanceMM != null && distanceGuidance == null)
        val framePassed = result.handDetected && distanceOk

        if (!framePassed) {
            lastAttemptBlurFailed = false
        }

        consecutivePasses = if (framePassed) consecutivePasses + 1 else 0

        val statusMessage = when {
            detectedCount == 0 -> "Place your hand in frame"
            distanceGuidance != null -> distanceGuidance
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

        _liveState.update {
            it.copy(
                frameId = frame.processingId,
                handDetected = result.handDetected,
                areaRatio = result.areaRatio,
                fingertips = result.fingertips,
                fingerBoxes = result.fingerBoxes,
                uprightFrameWidth = uprightWidth,
                uprightFrameHeight = uprightHeight,
                isReady = framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES,
                statusMessage = statusMessage,
                handDistanceMM = handDistanceMM
            )
        }

        if (framePassed && consecutivePasses >= REQUIRED_CONSECUTIVE_PASSES &&
            isCaptured.compareAndSet(false, true)
        ) {
            attemptCapture(frame, result)
        }
    }

    private suspend fun attemptCapture(frame: CameraFrame, result: SlapFrameResult) {
        try {
            val (byteArray, size) = frame.getByteArray(requiresCropping = false, cutoutRect = RectF())
            val uprightBitmap = byteArray.toBitmap(size).rotate(frame.rotationDegrees)

            val blurResult = blurChecker.check(uprightBitmap)

            if (!blurResult.passed) {
                Log.w(
                    TAG,
                    "Slap capture blur check failed (densenet=${blurResult.densenetConfidence}) " +
                            "-- resetting, keep looping"
                )
                lastAttemptBlurFailed = true
                consecutivePasses = 0
                isCaptured.set(false)
                return
            }

            _capturedBitmap.value = uprightBitmap
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